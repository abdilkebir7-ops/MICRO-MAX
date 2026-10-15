// MICRO-MAX website API: template store, releases/updates, announcements, admin tools, Winbox-style router menus.
import crypto from "crypto";
import { PRESETS, renderLoginHtml, normalizeOptions } from "./templates.js";
import { menuList, buildAction, buildPrint, shapeItems, MenuError } from "./menu.js";

export const SITE_SCHEMA = `
 ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled BOOLEAN NOT NULL DEFAULT false;
 CREATE TABLE IF NOT EXISTS store_templates(id UUID PRIMARY KEY,slug TEXT UNIQUE NOT NULL,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT 'عام',price NUMERIC(14,2) NOT NULL DEFAULT 0,html TEXT NOT NULL,active BOOLEAN NOT NULL DEFAULT true,downloads INTEGER NOT NULL DEFAULT 0,created_by UUID REFERENCES users(id) ON DELETE SET NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS template_downloads(slug TEXT PRIMARY KEY,downloads INTEGER NOT NULL DEFAULT 0);
 CREATE TABLE IF NOT EXISTS app_releases(id UUID PRIMARY KEY,version TEXT UNIQUE NOT NULL,notes TEXT NOT NULL DEFAULT '',apk_url TEXT NOT NULL,size_mb NUMERIC(8,2),sha256 TEXT,channel TEXT NOT NULL DEFAULT 'stable',mandatory BOOLEAN NOT NULL DEFAULT false,published BOOLEAN NOT NULL DEFAULT true,downloads INTEGER NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS announcements(id UUID PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',level TEXT NOT NULL DEFAULT 'info',active BOOLEAN NOT NULL DEFAULT true,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
`;

export function adminEmails(env=process.env){
  const raw = env.ADMIN_EMAILS===undefined ? "abdilkebir7@gmail.com" : env.ADMIN_EMAILS;
  return new Set(String(raw).split(",").map(x=>x.trim().toLowerCase()).filter(Boolean));
}
export const isAdminEmail = (email, env=process.env) => adminEmails(env).has(String(email||"").trim().toLowerCase());

export function parseVersion(v){
  const m = /^v?(\d{1,4})\.(\d{1,4})(?:\.(\d{1,4}))?$/.exec(String(v||"").trim());
  return m ? [Number(m[1]),Number(m[2]),Number(m[3]||0)] : null;
}
export function compareVersions(a,b){
  const x=parseVersion(a), y=parseVersion(b); if(!x||!y) return 0;
  for(let i=0;i<3;i++){ if(x[i]!==y[i]) return x[i]>y[i]?1:-1; } return 0;
}
const httpsUrl = u => /^https:\/\/[^\s"'<>]{4,500}$/i.test(String(u||""));
const slugify = s => String(s||"").toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-+|-+$/g,"").slice(0,48);
const PREVIEW_CSP = "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src https: data:; form-action 'none'; base-uri 'none'; sandbox allow-scripts";

export function registerSiteRoutes(app,{pool,auth,role,audit,withRos,validateLoginHtml,statusHtml,invalidateUser=()=>{}}){
  const wrap = fn => (req,res) => Promise.resolve(fn(req,res)).catch(e=>{ if(res.headersSent) return; if(e instanceof MenuError) return res.status(400).json({error:e.code,detail:e.detail}); console.error("site route error:",req.method,req.path,e.message); res.status(500).json({error:"SERVER_ERROR"}); });
  const admin = [auth, role("admin")];
  const html = (res,body,name) => { res.setHeader("Content-Type","text/html; charset=utf-8"); if(name) res.setHeader("Content-Disposition",`attachment; filename="${name}"`); res.setHeader("X-Content-Type-Options","nosniff"); res.send(body); };

  // ---------- public: templates ----------
  const presetRow = p => ({ slug:`preset-${p.id}`, name:p.name, description:`قالب جاهز بنمط ${p.group} — قابل للتخصيص بالكامل`, category:p.group, price:0, kind:"preset", preset:p.id, accent:p.accent, accent2:p.accent2, bg1:p.bg1, bg2:p.bg2, base:p.base, dark:p.dark });
  async function findTemplate(slug){
    const m = /^preset-(.+)$/.exec(slug);
    if(m){ const p=PRESETS.find(x=>x.id===m[1]); return p?{kind:"preset",row:presetRow(p),html:renderLoginHtml({preset:p.id,brand:"MICRO-MAX WiFi",whatsapp:"",showQrHint:true})}:null; }
    const r = await pool.query("SELECT * FROM store_templates WHERE slug=$1 AND active=true",[slug]);
    return r.rowCount?{kind:"custom",row:r.rows[0],html:r.rows[0].html}:null;
  }
  app.get("/api/site/templates",wrap(async(_req,res)=>{
    const c = await pool.query("SELECT slug,name,description,category,price,downloads,created_at FROM store_templates WHERE active=true ORDER BY created_at DESC");
    const d = await pool.query("SELECT slug,downloads FROM template_downloads"); const dm = Object.fromEntries(d.rows.map(x=>[x.slug,x.downloads]));
    res.json({ presets:PRESETS.map(p=>({...presetRow(p),downloads:dm[`preset-${p.id}`]||0})), custom:c.rows.map(x=>({...x,kind:"custom"})) });
  }));
  app.get("/api/site/templates/:slug/preview",wrap(async(req,res)=>{ const t=await findTemplate(req.params.slug); if(!t) return res.status(404).json({error:"TEMPLATE_NOT_FOUND"}); res.setHeader("Content-Security-Policy",PREVIEW_CSP); html(res,t.html); }));
  app.get("/api/site/templates/:slug/download",wrap(async(req,res)=>{
    const t=await findTemplate(req.params.slug); if(!t) return res.status(404).json({error:"TEMPLATE_NOT_FOUND"});
    if(Number(t.row.price)>0) return res.status(402).json({error:"PAID_TEMPLATE"});
    await pool.query("INSERT INTO template_downloads(slug,downloads) VALUES($1,1) ON CONFLICT(slug) DO UPDATE SET downloads=template_downloads.downloads+1",[req.params.slug]);
    html(res,t.html,"login.html");
  }));
  const renderLimiterBody = req => (req.body && typeof req.body==="object") ? (req.body.options||req.body) : {};
  app.post("/api/site/templates/render",wrap(async(req,res)=>{
    const o = renderLimiterBody(req); const out = renderLoginHtml(o);
    res.json({ html:out, options:(({p,...r})=>r)(normalizeOptions(o)), validation:validateLoginHtml(out) });
  }));
  app.post("/api/site/templates/preview",wrap(async(req,res)=>{ res.setHeader("Content-Security-Policy",PREVIEW_CSP); html(res,renderLoginHtml(renderLimiterBody(req))); }));
  app.get("/api/site/status-template",(_req,res)=>html(res,statusHtml));

  // ---------- public: releases / updates / announcements ----------
  const relOut = r => ({ version:r.version, notes:r.notes, apkUrl:r.apk_url, sizeMb:r.size_mb==null?null:Number(r.size_mb), sha256:r.sha256||null, channel:r.channel, mandatory:r.mandatory, downloads:r.downloads, createdAt:r.created_at });
  app.get("/api/site/releases",wrap(async(_req,res)=>{ const r=await pool.query("SELECT * FROM app_releases WHERE published=true ORDER BY created_at DESC LIMIT 50"); const items=r.rows.map(relOut).sort((a,b)=>compareVersions(b.version,a.version)||0); res.json({items}); }));
  async function latestRelease(channel="stable"){ const r=await pool.query("SELECT * FROM app_releases WHERE published=true AND channel=$1",[channel]); return r.rows.sort((a,b)=>compareVersions(b.version,a.version))[0]||null; }
  app.get("/api/site/update-check",wrap(async(req,res)=>{
    const channel = req.query.channel==="beta"?"beta":"stable"; const cur=String(req.query.current||"");
    const l = await latestRelease(channel); if(!l) return res.json({updateAvailable:false,latest:null});
    const newer = parseVersion(cur) ? compareVersions(l.version,cur)>0 : true;
    res.json({ updateAvailable:newer, mandatory:newer&&l.mandatory, latest:relOut(l) });
  }));
  app.get("/download/latest",wrap(async(req,res)=>{ const l=await latestRelease(req.query.channel==="beta"?"beta":"stable"); if(!l) return res.status(404).json({error:"NO_RELEASE"}); await pool.query("UPDATE app_releases SET downloads=downloads+1 WHERE id=$1",[l.id]); res.redirect(302,l.apk_url); }));
  app.get("/download/:version",wrap(async(req,res)=>{ const r=await pool.query("SELECT * FROM app_releases WHERE version=$1 AND published=true",[req.params.version]); if(!r.rowCount) return res.status(404).json({error:"RELEASE_NOT_FOUND"}); await pool.query("UPDATE app_releases SET downloads=downloads+1 WHERE id=$1",[r.rows[0].id]); res.redirect(302,r.rows[0].apk_url); }));
  app.get("/api/site/announcements",wrap(async(_req,res)=>{ const r=await pool.query("SELECT id,title,body,level,created_at FROM announcements WHERE active=true ORDER BY created_at DESC LIMIT 10"); res.json({items:r.rows}); }));
  app.get("/api/site/config",(_req,res)=>res.json({registration:String(process.env.ALLOW_REGISTRATION||"true").toLowerCase()==="true",google:!!process.env.GOOGLE_CLIENT_ID,googleClientId:process.env.GOOGLE_CLIENT_ID||null,version:"2.1.0"}));

  // ---------- admin ----------
  app.get("/api/admin/stats",...admin,wrap(async(_req,res)=>{
    const q = async s => (await pool.query(s)).rows[0];
    const [u,rt,c,s,t,d,rel] = await Promise.all([ q("SELECT count(*)::int n,count(*) FILTER (WHERE created_at>now()-interval '7 days')::int week FROM users"), q("SELECT count(*)::int n FROM routers"), q("SELECT count(*)::int n FROM cards"), q("SELECT count(*)::int n,COALESCE(sum(amount),0)::float total FROM sales"), q("SELECT count(*)::int n FROM store_templates"), q("SELECT COALESCE(sum(downloads),0)::int n FROM template_downloads"), q("SELECT COALESCE(sum(downloads),0)::int n,count(*)::int c FROM app_releases") ]);
    res.json({ users:u.n, usersWeek:u.week, routers:rt.n, cards:c.n, sales:s.n, revenue:s.total, customTemplates:t.n, templateDownloads:d.n, apkDownloads:rel.n, releases:rel.c });
  }));
  app.get("/api/admin/users",...admin,wrap(async(req,res)=>{
    const qs = String(req.query.q||"").trim().slice(0,80);
    const r = await pool.query(`SELECT u.id,u.email,u.role,u.disabled,u.display_name,u.created_at,(SELECT count(*)::int FROM routers r WHERE r.user_id=u.id) routers FROM users u WHERE ($1='' OR u.email ILIKE '%'||$1||'%' OR u.display_name ILIKE '%'||$1||'%') ORDER BY u.created_at DESC LIMIT 200`,[qs.replace(/[%_\\]/g,"")]);
    res.json({items:r.rows});
  }));
  app.patch("/api/admin/users/:id",...admin,wrap(async(req,res)=>{
    const { role:newRole, disabled } = req.body||{};
    if(req.params.id===req.user.sub) return res.status(400).json({error:"CANNOT_MODIFY_SELF"});
    if(newRole!==undefined && !["admin","owner","staff"].includes(newRole)) return res.status(400).json({error:"INVALID_ROLE"});
    if(disabled!==undefined && typeof disabled!=="boolean") return res.status(400).json({error:"INVALID_INPUT"});
    const r = await pool.query("UPDATE users SET role=COALESCE($2,role),disabled=COALESCE($3,disabled) WHERE id=$1 RETURNING id,email,role,disabled",[req.params.id,newRole??null,disabled??null]);
    if(!r.rowCount) return res.status(404).json({error:"USER_NOT_FOUND"});
    invalidateUser(req.params.id); await audit(req.user.sub,"ADMIN_USER_UPDATE","user",req.params.id,{role:newRole,disabled}); res.json(r.rows[0]);
  }));
  app.get("/api/admin/audit",...admin,wrap(async(_req,res)=>{ const r=await pool.query("SELECT a.id,a.action,a.entity,a.entity_id,a.created_at,u.email FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id ORDER BY a.created_at DESC LIMIT 100"); res.json({items:r.rows}); }));

  app.get("/api/admin/templates",...admin,wrap(async(_req,res)=>{ const r=await pool.query("SELECT id,slug,name,description,category,price,active,downloads,created_at FROM store_templates ORDER BY created_at DESC"); res.json({items:r.rows}); }));
  app.post("/api/admin/templates",...admin,wrap(async(req,res)=>{
    const b=req.body||{}; const name=String(b.name||"").trim().slice(0,60); const content=String(b.html||"");
    if(!name) return res.status(400).json({error:"NAME_REQUIRED"}); if(content.length>200_000) return res.status(413).json({error:"TOO_LARGE"});
    const v=validateLoginHtml(content); if(!v.valid) return res.status(400).json({error:"TEMPLATE_INVALID",validation:v});
    const price=Number(b.price||0); if(!(price>=0&&price<100000)) return res.status(400).json({error:"INVALID_PRICE"});
    const slug = (slugify(b.slug||name)||"tpl")+"-"+crypto.randomBytes(2).toString("hex");
    const r=await pool.query("INSERT INTO store_templates(id,slug,name,description,category,price,html,created_by) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id,slug",[crypto.randomUUID(),slug,name,String(b.description||"").slice(0,300),String(b.category||"عام").slice(0,30),price,content,req.user.sub]);
    await audit(req.user.sub,"TEMPLATE_CREATE","store_template",r.rows[0].id); res.status(201).json(r.rows[0]);
  }));
  app.patch("/api/admin/templates/:id",...admin,wrap(async(req,res)=>{
    const b=req.body||{}; if(b.html!==undefined){ const v=validateLoginHtml(String(b.html)); if(!v.valid) return res.status(400).json({error:"TEMPLATE_INVALID",validation:v}); }
    const r=await pool.query("UPDATE store_templates SET name=COALESCE($2,name),description=COALESCE($3,description),category=COALESCE($4,category),price=COALESCE($5,price),html=COALESCE($6,html),active=COALESCE($7,active) WHERE id=$1 RETURNING id",[req.params.id,b.name?String(b.name).slice(0,60):null,b.description!==undefined?String(b.description).slice(0,300):null,b.category?String(b.category).slice(0,30):null,b.price!==undefined?Number(b.price):null,b.html!==undefined?String(b.html):null,typeof b.active==="boolean"?b.active:null]);
    if(!r.rowCount) return res.status(404).json({error:"TEMPLATE_NOT_FOUND"}); res.json({ok:true});
  }));
  app.delete("/api/admin/templates/:id",...admin,wrap(async(req,res)=>{ const r=await pool.query("DELETE FROM store_templates WHERE id=$1",[req.params.id]); if(!r.rowCount) return res.status(404).json({error:"TEMPLATE_NOT_FOUND"}); await audit(req.user.sub,"TEMPLATE_DELETE","store_template",req.params.id); res.json({ok:true}); }));

  app.get("/api/admin/releases",...admin,wrap(async(_req,res)=>{ const r=await pool.query("SELECT * FROM app_releases ORDER BY created_at DESC"); res.json({items:r.rows.map(x=>({id:x.id,published:x.published,...relOut(x)}))}); }));
  app.post("/api/admin/releases",...admin,wrap(async(req,res)=>{
    const b=req.body||{}; const version=String(b.version||"").trim().replace(/^v/,"");
    if(!parseVersion(version)) return res.status(400).json({error:"INVALID_VERSION"}); if(!httpsUrl(b.apkUrl)) return res.status(400).json({error:"INVALID_APK_URL"});
    const sha=String(b.sha256||"").trim().toLowerCase(); if(sha&&!/^[0-9a-f]{64}$/.test(sha)) return res.status(400).json({error:"INVALID_SHA256"});
    try{ const r=await pool.query("INSERT INTO app_releases(id,version,notes,apk_url,size_mb,sha256,channel,mandatory,published) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id,version",[crypto.randomUUID(),version,String(b.notes||"").slice(0,4000),b.apkUrl,b.sizeMb?Number(b.sizeMb):null,sha||null,b.channel==="beta"?"beta":"stable",!!b.mandatory,b.published!==false]); await audit(req.user.sub,"RELEASE_CREATE","app_release",r.rows[0].id,{version}); res.status(201).json(r.rows[0]); }
    catch(e){ if(e.code==="23505") return res.status(409).json({error:"VERSION_EXISTS"}); throw e; }
  }));
  app.patch("/api/admin/releases/:id",...admin,wrap(async(req,res)=>{ const b=req.body||{}; if(b.apkUrl!==undefined&&!httpsUrl(b.apkUrl)) return res.status(400).json({error:"INVALID_APK_URL"}); const r=await pool.query("UPDATE app_releases SET notes=COALESCE($2,notes),apk_url=COALESCE($3,apk_url),mandatory=COALESCE($4,mandatory),published=COALESCE($5,published) WHERE id=$1 RETURNING id",[req.params.id,b.notes!==undefined?String(b.notes).slice(0,4000):null,b.apkUrl??null,typeof b.mandatory==="boolean"?b.mandatory:null,typeof b.published==="boolean"?b.published:null]); if(!r.rowCount) return res.status(404).json({error:"RELEASE_NOT_FOUND"}); res.json({ok:true}); }));
  app.delete("/api/admin/releases/:id",...admin,wrap(async(req,res)=>{ const r=await pool.query("DELETE FROM app_releases WHERE id=$1",[req.params.id]); if(!r.rowCount) return res.status(404).json({error:"RELEASE_NOT_FOUND"}); res.json({ok:true}); }));

  app.get("/api/admin/announcements",...admin,wrap(async(_req,res)=>{ const r=await pool.query("SELECT * FROM announcements ORDER BY created_at DESC LIMIT 100"); res.json({items:r.rows}); }));
  app.post("/api/admin/announcements",...admin,wrap(async(req,res)=>{ const b=req.body||{}; const title=String(b.title||"").trim().slice(0,100); if(!title) return res.status(400).json({error:"TITLE_REQUIRED"}); const level=["info","warning","success"].includes(b.level)?b.level:"info"; const r=await pool.query("INSERT INTO announcements(id,title,body,level) VALUES($1,$2,$3,$4) RETURNING id",[crypto.randomUUID(),title,String(b.body||"").slice(0,1000),level]); res.status(201).json(r.rows[0]); }));
  app.patch("/api/admin/announcements/:id",...admin,wrap(async(req,res)=>{ const r=await pool.query("UPDATE announcements SET active=COALESCE($2,active) WHERE id=$1 RETURNING id",[req.params.id,typeof req.body?.active==="boolean"?req.body.active:null]); if(!r.rowCount) return res.status(404).json({error:"NOT_FOUND"}); res.json({ok:true}); }));
  app.delete("/api/admin/announcements/:id",...admin,wrap(async(req,res)=>{ await pool.query("DELETE FROM announcements WHERE id=$1",[req.params.id]); res.json({ok:true}); }));

  // ---------- Winbox-style router menus ----------
  const rosErr = (res,e) => {
    const m=String(e.message||"");
    if(m==="ROUTER_NOT_FOUND") return res.status(404).json({error:m});
    if(m==="ROUTER_IS_AGENT_MODE") return res.status(409).json({error:m});
    if(m==="HOST_NOT_ALLOWED"||m==="ROUTER_IS_AGENT_MODE") return res.status(400).json({error:m});
    return res.status(502).json({error:"ROUTER_COMMAND_FAILED",detail:m.slice(0,300)});
  };
  app.get("/api/menus",auth,(_req,res)=>res.json({items:menuList()}));
  app.get("/api/routers/:id/menu/:key",auth,wrap(async(req,res)=>{
    const spec=buildPrint(req.params.key);
    try{ const items=await withRos(req,req.params.id,ros=>ros.command(spec.path,spec.args)); res.json({key:req.params.key,items:shapeItems(req.params.key,items)}); }catch(e){ if(e instanceof MenuError) throw e; rosErr(res,e); }
  }));
  app.post("/api/routers/:id/menu/:key/:action",auth,role("admin","owner"),wrap(async(req,res)=>{
    const b=req.body||{}; const cmd=buildAction(req.params.key,req.params.action,b.id,b.values);
    try{ const out=await withRos(req,req.params.id,ros=>ros.command(cmd.path,cmd.args)); await audit(req.user.sub,`MENU_${req.params.action.toUpperCase()}`,"router",req.params.id,{menu:req.params.key,id:b.id||null}); res.json({ok:true,result:out}); }catch(e){ if(e instanceof MenuError) throw e; rosErr(res,e); }
  }));
}
