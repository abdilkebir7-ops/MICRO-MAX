import express from "express";
import cors from "cors";
import helmet from "helmet";
import dotenv from "dotenv";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import crypto from "crypto";
import net from "net";
import tls from "tls";
import dns from "dns";
import rateLimit from "express-rate-limit";
import { OAuth2Client } from "google-auth-library";
import PDFDocument from "pdfkit";
import ExcelJS from "exceljs";
import { RouterOS } from "./routeros.js";
import { assertPinStrength, buildWifiQr, generateBatch, sanitizePrefix, usernameSpace, usernameRegex, qrSecretFrom, buildQrUrl, parseQr, auditCards, scanVerdict, classifyRouterUser } from "./cards.js";
import { binancePayConfigured, createBinancePayOrder, queryBinancePayOrder, verifyBinancePayNotification } from "./binancePay.js";

dotenv.config();
const { Pool } = pg;
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const app = express();
const allowedOrigins = String(process.env.CORS_ORIGIN || "").split(",").map(x => x.trim()).filter(Boolean);
if (allowedOrigins.includes("*") && process.env.NODE_ENV === "production") throw new Error("CORS_ORIGIN must be an explicit allowlist in production");
app.set("trust proxy", process.env.TRUST_PROXY === "true" ? 1 : false);
app.disable("x-powered-by");
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({ origin(origin, callback) {
  if (!origin || allowedOrigins.includes("*") || allowedOrigins.includes(origin)) return callback(null, true);
  return callback(new Error("CORS_ORIGIN_NOT_ALLOWED"));
}}));
app.use(express.json({ limit: "2mb", verify: (req, _res, buf) => { req.rawBody = Buffer.from(buf); } }));

const loginLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 10, standardHeaders: true, legacyHeaders: false });
const apiLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 600, standardHeaders: true, legacyHeaders: false, skip: req => req.path === "/health" });
app.use("/api", apiLimiter);
const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID || undefined);
const configuredJwtSecret = String(process.env.JWT_SECRET || "");
const configuredEncryptionSecret = String(process.env.ROUTER_ENCRYPTION_KEY || "");
const devMode = ["development","test"].includes(process.env.NODE_ENV);
if (!devMode && (configuredJwtSecret.length < 32 || configuredEncryptionSecret.length < 32 || /replace|change-me/i.test(configuredJwtSecret + configuredEncryptionSecret))) {
  throw new Error("JWT_SECRET and ROUTER_ENCRYPTION_KEY must be strong production secrets");
}
const ENC_KEY = crypto.createHash("sha256").update(configuredEncryptionSecret || configuredJwtSecret || crypto.randomBytes(32)).digest();
const QR_SECRET = qrSecretFrom(configuredEncryptionSecret || configuredJwtSecret || "dev");
const IV_LEN = 12;
function encrypt(text) { const iv=crypto.randomBytes(IV_LEN); const c=crypto.createCipheriv("aes-256-gcm", ENC_KEY, iv); const data=Buffer.concat([c.update(String(text),"utf8"),c.final()]); return `${iv.toString("base64")}.${c.getAuthTag().toString("base64")}.${data.toString("base64")}`; }
function decrypt(value) { const [iv,tag,data]=String(value).split(".").map(x=>Buffer.from(x,"base64")); const d=crypto.createDecipheriv("aes-256-gcm",ENC_KEY,iv); d.setAuthTag(tag); return Buffer.concat([d.update(data),d.final()]).toString("utf8"); }
const runtimeJwtSecret = configuredJwtSecret || crypto.randomBytes(32).toString("base64url");
function sign(user) { return jwt.sign({ sub:user.id,email:user.email,role:user.role }, runtimeJwtSecret, { expiresIn:"12h" }); }
function auth(req,res,next){ const h=req.headers.authorization||""; try{ const t=h.startsWith("Bearer ")?h.slice(7):""; req.user=jwt.verify(t,runtimeJwtSecret); next(); }catch{res.status(401).json({error:"UNAUTHORIZED"});} }
const decPass = v => String(v||"").startsWith("enc:") ? decrypt(String(v).slice(4)) : String(v||"");
const encPass = v => "enc:" + encrypt(v);
function cardOut(row){ const password = decPass(row.password); const passwordMode = password===row.username?"pin":"userpass"; const qr = row.portal_url ? buildQrUrl({portalUrl:row.portal_url,username:row.username,password:passwordMode==="pin"?"":password,cardId:row.id,secret:QR_SECRET}) : (row.qr_content||null); return { ...row, password, passwordMode, qr_content: qr, qrContent: qr, wifiQr: buildWifiQr(row.ssid) }; }
async function bootstrapAdmin({email,hash=null,googleSub=null}){ const client=await pool.connect(); try{ await client.query("BEGIN"); await client.query("SELECT pg_advisory_xact_lock(7734001)"); const n=(await client.query("SELECT count(*)::int n FROM users")).rows[0].n; if(n>0){ await client.query("ROLLBACK"); return null; } const id=crypto.randomUUID(); const r=await client.query("INSERT INTO users(id,email,password_hash,google_sub,role) VALUES($1,$2,$3,$4,'admin') RETURNING id,email,role",[id,email,hash,googleSub]); await client.query("INSERT INTO app_settings(user_id) VALUES($1) ON CONFLICT DO NOTHING",[id]); await client.query("COMMIT"); return r.rows[0]; }catch(e){ try{await client.query("ROLLBACK");}catch{} throw e; } finally{ client.release(); } }
async function registerUser({email,hash}){ const client=await pool.connect(); try{ await client.query("BEGIN"); await client.query("SELECT pg_advisory_xact_lock(7734002)"); const exists=await client.query("SELECT id FROM users WHERE email=$1",[email]); if(exists.rowCount){ await client.query("ROLLBACK"); return null; } const id=crypto.randomUUID(); const r=await client.query("INSERT INTO users(id,email,password_hash,role) VALUES($1,$2,$3,'staff') RETURNING id,email,role",[id,email,hash]); await client.query("INSERT INTO app_settings(user_id) VALUES($1) ON CONFLICT DO NOTHING",[id]); await client.query("COMMIT"); return r.rows[0]; }catch(e){ try{await client.query("ROLLBACK");}catch{} throw e; } finally{ client.release(); } }
function role(...roles){ return (req,res,next)=>roles.includes(req.user.role)?next():res.status(403).json({error:"FORBIDDEN"}); }
async function audit(userId,action,entity,entityId,details={}){ await pool.query("INSERT INTO audit_logs(id,user_id,action,entity,entity_id,details) VALUES(gen_random_uuid(),$1,$2,$3,$4,$5)",[userId,action,entity,entityId,JSON.stringify(details)]); }

async function init(){
 await pool.query(`CREATE EXTENSION IF NOT EXISTS pgcrypto;
 CREATE TABLE IF NOT EXISTS users(id UUID PRIMARY KEY,email TEXT UNIQUE NOT NULL,password_hash TEXT,google_sub TEXT UNIQUE,role TEXT NOT NULL DEFAULT 'staff',created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub TEXT UNIQUE;
 ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
 CREATE TABLE IF NOT EXISTS routers(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,name TEXT NOT NULL,host TEXT NOT NULL,port INTEGER NOT NULL DEFAULT 8728,username TEXT NOT NULL,password_enc TEXT NOT NULL,tls BOOLEAN NOT NULL DEFAULT false,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS cards(id UUID PRIMARY KEY,router_id UUID NOT NULL REFERENCES routers(id) ON DELETE CASCADE,username TEXT NOT NULL,password TEXT NOT NULL,profile TEXT NOT NULL DEFAULT 'default',price NUMERIC(14,2) NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'available',sold_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(router_id,username));
 CREATE TABLE IF NOT EXISTS sales(id UUID PRIMARY KEY,card_id UUID REFERENCES cards(id) ON DELETE SET NULL,seller_id UUID REFERENCES users(id) ON DELETE SET NULL,amount NUMERIC(14,2) NOT NULL,payment_method TEXT NOT NULL,payment_status TEXT NOT NULL DEFAULT 'pending',reference TEXT,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS payments(id UUID PRIMARY KEY,sale_id UUID REFERENCES sales(id) ON DELETE CASCADE,provider TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'pending',external_reference TEXT,amount NUMERIC(14,2) NOT NULL,raw JSONB,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS audit_logs(id UUID PRIMARY KEY,user_id UUID REFERENCES users(id) ON DELETE SET NULL,action TEXT NOT NULL,entity TEXT NOT NULL,entity_id TEXT,details JSONB,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS notifications(id UUID PRIMARY KEY,user_id UUID REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL,body TEXT NOT NULL,level TEXT NOT NULL DEFAULT 'info',read_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS hotspot_themes(id UUID PRIMARY KEY,router_id UUID REFERENCES routers(id) ON DELETE CASCADE UNIQUE,name TEXT NOT NULL,logo_url TEXT,primary_color TEXT NOT NULL,background TEXT NOT NULL,updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS hotspot_profiles(id UUID PRIMARY KEY,router_id UUID NOT NULL REFERENCES routers(id) ON DELETE CASCADE,name TEXT NOT NULL,price NUMERIC(14,2) NOT NULL DEFAULT 0,duration_minutes INTEGER NOT NULL DEFAULT 0,rate_limit TEXT NOT NULL DEFAULT '',session_timeout TEXT NOT NULL DEFAULT '',idle_timeout TEXT NOT NULL DEFAULT '',shared_users INTEGER NOT NULL DEFAULT 1,enabled BOOLEAN NOT NULL DEFAULT true,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(router_id,name));
 CREATE TABLE IF NOT EXISTS hotspot_plans(id UUID PRIMARY KEY,router_id UUID NOT NULL REFERENCES routers(id) ON DELETE CASCADE,profile_name TEXT NOT NULL,name TEXT NOT NULL,price NUMERIC(14,2) NOT NULL DEFAULT 0,currency TEXT NOT NULL DEFAULT 'XOF',active BOOLEAN NOT NULL DEFAULT true,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(router_id,name));
 CREATE TABLE IF NOT EXISTS plan_price_history(id UUID PRIMARY KEY,plan_id UUID NOT NULL REFERENCES hotspot_plans(id) ON DELETE CASCADE,old_price NUMERIC(14,2),new_price NUMERIC(14,2) NOT NULL,changed_by UUID REFERENCES users(id) ON DELETE SET NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 ALTER TABLE cards ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES hotspot_plans(id) ON DELETE SET NULL;
 ALTER TABLE cards ADD COLUMN IF NOT EXISTS price_currency TEXT NOT NULL DEFAULT 'XOF';
ALTER TABLE cards ADD COLUMN IF NOT EXISTS batch_id UUID;
 ALTER TABLE cards ADD COLUMN IF NOT EXISTS qr_content TEXT;
 ALTER TABLE cards ADD COLUMN IF NOT EXISTS portal_url TEXT;
 ALTER TABLE cards ADD COLUMN IF NOT EXISTS ssid TEXT;
 CREATE TABLE IF NOT EXISTS app_settings(user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,theme TEXT NOT NULL DEFAULT 'light',currency TEXT NOT NULL DEFAULT 'XOF');
 CREATE TABLE IF NOT EXISTS store_items(id UUID PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT 'template',price NUMERIC(14,2) NOT NULL DEFAULT 0,active BOOLEAN NOT NULL DEFAULT true,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
 INSERT INTO store_items(id,name,description,category,price,active)
 SELECT gen_random_uuid(),'MICRO-MAX Glass','قالب زجاجي مجاني لواجهة HotSpot','template',0,true
 WHERE NOT EXISTS (SELECT 1 FROM store_items WHERE name='MICRO-MAX Glass');
 INSERT INTO store_items(id,name,description,category,price,active)
 SELECT gen_random_uuid(),'VIP HotSpot','قالب VIP مجاني لواجهة HotSpot','template',0,true
 WHERE NOT EXISTS (SELECT 1 FROM store_items WHERE name='VIP HotSpot');
 INSERT INTO store_items(id,name,description,category,price,active)
 SELECT gen_random_uuid(),'QR / Barcode Pack','أدوات إنشاء QR وBarcode للكروت بدون اشتراك','tools',0,true
 WHERE NOT EXISTS (SELECT 1 FROM store_items WHERE name='QR / Barcode Pack');
 CREATE TABLE IF NOT EXISTS store_entitlements(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,item_id UUID NOT NULL REFERENCES store_items(id) ON DELETE CASCADE,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(user_id,item_id));`);
}

app.get("/health",(_,res)=>res.json({ok:true,service:"MICRO-MAX API",version:"2.0.0"}));

// Protected administrator account recovery. Requires ADMIN_EMAIL and ADMIN_RECOVERY_SECRET.
app.post("/api/auth/admin-recover", loginLimiter, async (req, res) => {
  const expected = String(process.env.ADMIN_RECOVERY_SECRET || "");
  const supplied = String(req.get("x-admin-recovery-secret") || "");
  const a = Buffer.from(expected);
  const b = Buffer.from(supplied);

  if (a.length < 32 || a.length !== b.length ||
      !crypto.timingSafeEqual(a, b)) {
    return res.status(403).json({ error: "RECOVERY_NOT_AUTHORIZED" });
  }

  const email = String(req.body.email || "").trim().toLowerCase();
  const adminEmail = String(process.env.ADMIN_EMAIL || "").trim().toLowerCase();
  const password = String(req.body.password || "");

  if (!adminEmail || email !== adminEmail)
    return res.status(403).json({ error: "ADMIN_EMAIL_MISMATCH" });
  if (password.length < 12)
    return res.status(400).json({ error: "PASSWORD_TOO_SHORT" });

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const found = await client.query(
      "SELECT id FROM users WHERE email=$1 FOR UPDATE", [email]
    );
    const hash = await bcrypt.hash(password, 12);

    if (found.rowCount) {
      await client.query(
        "UPDATE users SET password_hash=$1, role='admin' WHERE id=$2",
        [hash, found.rows[0].id]
      );
    } else {
      const id = crypto.randomUUID();
      await client.query(
        "INSERT INTO users(id,email,password_hash,role) VALUES($1,$2,$3,'admin')",
        [id, email, hash]
      );
      await client.query(
        "INSERT INTO app_settings(user_id) VALUES($1) ON CONFLICT DO NOTHING",
        [id]
      );
    }

    await client.query("COMMIT");
    return res.json({ ok: true });
  } catch (e) {
    await client.query("ROLLBACK");
    console.error("Admin recovery failed:", e.message);
    return res.status(500).json({ error: "ADMIN_RECOVERY_FAILED" });
  } finally {
    client.release();
  }
});

app.post("/api/auth/register",loginLimiter,async(req,res)=>{const email=String(req.body.email||"").trim().toLowerCase(),password=String(req.body.password||"");if(!email||password.length<8)return res.status(400).json({error:"INVALID_INPUT"});try{const count=Number((await pool.query("SELECT count(*)::int n FROM users")).rows[0].n);let user;if(count===0){user=await bootstrapAdmin({email,hash:await bcrypt.hash(password,12)});}else if(process.env.ALLOW_REGISTRATION==="true"){user=await registerUser({email,hash:await bcrypt.hash(password,12)});}else{user=null;}if(!user)return res.status(403).json({error:count===0?"REGISTRATION_FAILED":"REGISTRATION_CLOSED"});await audit(user.id,"REGISTER","user",user.id,{role:user.role});res.status(201).json({token:sign(user),user});}catch(e){if(String(e?.code)==="23505")return res.status(409).json({error:"EMAIL_EXISTS"});res.status(500).json({error:"REGISTRATION_FAILED"});}});
app.post("/api/auth/login",loginLimiter,async(req,res)=>{const email=String(req.body.email||"").trim().toLowerCase(),password=String(req.body.password||"");const r=await pool.query("SELECT * FROM users WHERE email=$1",[email]);if(!r.rowCount||!r.rows[0].password_hash||!(await bcrypt.compare(password,r.rows[0].password_hash)))return res.status(401).json({error:"INVALID_CREDENTIALS"});await audit(r.rows[0].id,"LOGIN","user",r.rows[0].id);res.json({token:sign(r.rows[0]),user:{id:r.rows[0].id,email:r.rows[0].email,role:r.rows[0].role}});});
app.post("/api/auth/google",loginLimiter,async(req,res)=>{
  const idToken=String(req.body.idToken||"").trim();
  if(!idToken)return res.status(400).json({error:"GOOGLE_ID_TOKEN_REQUIRED"});
  if(!process.env.GOOGLE_CLIENT_ID)return res.status(503).json({error:"GOOGLE_AUTH_NOT_CONFIGURED"});
  try{
    const ticket=await googleClient.verifyIdToken({idToken,audience:process.env.GOOGLE_CLIENT_ID});
    const payload=ticket.getPayload()||{};
    const sub=String(payload.sub||"");
    const email=String(payload.email||"").trim().toLowerCase();
    if(!sub||!email||payload.email_verified!==true)return res.status(401).json({error:"GOOGLE_ACCOUNT_NOT_VERIFIED"});
    let r=await pool.query("SELECT id,email,role,google_sub FROM users WHERE google_sub=$1 OR email=$2 LIMIT 1",[sub,email]);
    let user;
    if(r.rowCount){
      user=r.rows[0];
      if(!user.google_sub){await pool.query("UPDATE users SET google_sub=$1,password_hash=NULL WHERE id=$2",[sub,user.id]); user.google_sub=sub;}
    }else{
      user=await bootstrapAdmin({email,googleSub:sub});
      if(!user)return res.status(403).json({error:"REGISTRATION_CLOSED"});
    }
    await audit(user.id,"GOOGLE_LOGIN","user",user.id,{provider:"google"});
    res.json({token:sign(user),user:{id:user.id,email:user.email,role:user.role}});
  }catch(e){res.status(401).json({error:"INVALID_GOOGLE_TOKEN",detail:e.message});}
});

app.get("/api/me",auth,async(req,res)=>{const r=await pool.query("SELECT id,email,role,created_at FROM users WHERE id=$1",[req.user.sub]);res.json(r.rows[0]);});
app.get("/api/features",auth,async(req,res)=>{res.json({subscriptionRequired:false,storeOpen:true,paymentsOptional:true,paymentsEnabled:process.env.ENABLE_ONLINE_PAYMENTS === "true",features:{googleSignIn:true,emailPasswordAuth:true,mikrotik:true,routerManagement:true,routerOsApi:true,routerOsApiSsl:true,dashboard:true,hotspotCards:true,qrCode:true,barcode:true,batchCenter:true,cardPreflight:true,inventory:true,sales:true,reports:true,usersAndRoles:true,auditLog:true,notifications:true,backup:true,hotspotThemes:true,hotspotLoginEditor:true,hotspotStatusEditor:true,ipBindings:true,terminal:true,store:true,binancePay:process.env.ENABLE_ONLINE_PAYMENTS === "true" && binancePayConfigured(),nita:false}});});
app.get("/api/store",auth,async(req,res)=>{const r=await pool.query("SELECT id,name,description,category,price,active,created_at FROM store_items WHERE active=true ORDER BY created_at DESC");res.json({open:true,subscriptionRequired:false,items:r.rows});});
app.post("/api/store/:id/activate",auth,async(req,res)=>{const r=await pool.query("SELECT id,name,description,category,price FROM store_items WHERE id=$1 AND active=true",[req.params.id]);if(!r.rowCount)return res.status(404).json({error:"STORE_ITEM_NOT_FOUND"});await pool.query("INSERT INTO store_entitlements(id,user_id,item_id) VALUES($1,$2,$3) ON CONFLICT(user_id,item_id) DO NOTHING",[crypto.randomUUID(),req.user.sub,req.params.id]);await audit(req.user.sub,"ACTIVATE","store_item",req.params.id,{name:r.rows[0].name});res.json({ok:true,subscriptionRequired:false,item:r.rows[0],message:"تم تفعيل العنصر مجاناً داخل حسابك."});});
app.get("/api/store/my",auth,async(req,res)=>{const r=await pool.query("SELECT e.created_at,i.id,i.name,i.description,i.category,i.price FROM store_entitlements e JOIN store_items i ON i.id=e.item_id WHERE e.user_id=$1 ORDER BY e.created_at DESC",[req.user.sub]);res.json({items:r.rows});});
app.get("/api/payment-methods",auth,async(req,res)=>{const online=process.env.ENABLE_ONLINE_PAYMENTS==="true";res.json({methods:[{id:"cash",name:"نقداً / يدوي",available:true},...(online?[{id:"binance_pay",name:"Binance Pay",available:binancePayConfigured()}]:[])]});});

app.get("/api/routers",auth,async(req,res)=>{const r=await pool.query("SELECT id,name,host,port,username,tls,created_at FROM routers WHERE user_id=$1 ORDER BY created_at DESC",[req.user.sub]);res.json({routers:r.rows});});

function tcpProbe(host, port, useTls=false, timeout=5000) {
  return new Promise(resolve => {
    const started=Date.now(); let settled=false;
    const finish=(result)=>{if(settled)return;settled=true;try{socket?.destroy();}catch{};resolve({...result,latencyMs:Date.now()-started});};
    const socket=useTls ? tls.connect({host,port,servername:host,rejectUnauthorized:false}) : net.createConnection({host,port});
    const timer=setTimeout(()=>finish({ok:false,code:"TIMEOUT",message:"لم يصل رد من المنفذ خلال المهلة"}),timeout);
    socket.once("connect",()=>{clearTimeout(timer);finish({ok:true,code:"OPEN",message:"المنفذ مفتوح ويمكن الوصول إليه"});});
    socket.once("secureConnect",()=>{clearTimeout(timer);finish({ok:true,code:"OPEN_TLS",message:"منفذ TLS مفتوح ويمكن الوصول إليه"});});
    socket.once("error",e=>{clearTimeout(timer);finish({ok:false,code:String(e.code||"SOCKET_ERROR"),message:String(e.message||e.code||"فشل الاتصال").slice(0,180)});});
  });
}

function privateIpv4(host) {
  const p=String(host).split(".").map(Number);
  return p.length===4 && p.every(x=>Number.isInteger(x)&&x>=0&&x<=255) && (p[0]===10 || p[0]===192&&p[1]===168 || p[0]===172&&p[1]>=16&&p[1]<=31);
}
app.post("/api/routers/discover",auth,async(req,res)=>{
  const network=String(req.body?.network||"192.168.88.0/24").trim(); const m=network.match(/^(\d+\.\d+\.\d+)\.0\/24$/);
  if(!m || !privateIpv4(`${m[1]}.1`)) return res.status(400).json({ok:false,error:"PRIVATE_24_NETWORK_REQUIRED",detail:"اكتشاف الراوتر يعمل فقط على شبكة خاصة بصيغة 192.168.88.0/24"});
  const hosts=Array.from({length:254},(_,i)=>`${m[1]}.${i+1}`); const found=[]; let cursor=0;
  async function worker(){while(cursor<hosts.length){const host=hosts[cursor++]; const [api,ssl]=await Promise.all([tcpProbe(host,8728,false,450),tcpProbe(host,8729,true,450)]); if(api.ok||ssl.ok) found.push({host,port:ssl.ok?8729:8728,tls:ssl.ok,service:ssl.ok?"API-SSL":"API",latencyMs:Math.min(api.latencyMs||9999,ssl.latencyMs||9999)});}}
  await Promise.all(Array.from({length:24},worker));
  res.json({ok:true,network,source:"backend",routers:found.sort((a,b)=>a.host.localeCompare(b.host,{numeric:true}))});
});

app.post("/api/routers/diagnose",auth,async(req,res)=>{
  const host=String(req.body?.host||"").trim(); const port=Number(req.body?.port)||8728; const tlsMode=!!req.body?.tls;
  if(!host)return res.status(400).json({ok:false,error:"ROUTER_INPUT_REQUIRED",steps:[]});
  const steps=[]; let addresses=[];
  try { addresses=await dns.promises.lookup(host,{all:true}); steps.push({name:"DNS",ok:true,message:`تم حل ${host}`,addresses:addresses.map(x=>x.address)}); }
  catch(e){ steps.push({name:"DNS",ok:false,code:String(e.code||"DNS_ERROR"),message:"تعذر حل اسم المضيف من Backend"}); }
  const tcp=await tcpProbe(host,port,tlsMode); steps.push({name:tlsMode?"TCP/TLS":"TCP",...tcp});
  let login=null;
  if(req.body?.username && typeof req.body?.password === "string" && tcp.ok){
    let ros;
    try { ros=new RouterOS({host,port,username:String(req.body.username),password:req.body.password,tls:tlsMode,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true",timeout:8000}); const r=await ros.command("/system/resource/print",[]); login={ok:true,code:"AUTH_OK",message:"تم تسجيل الدخول إلى RouterOS",resource:r[0]||{}}; }
    catch(e){ login={ok:false,code:String(e.code||e.message||"ROUTER_LOGIN_FAILED"),message:String(e.message||e.code||"فشل تسجيل الدخول").slice(0,240)}; }
    finally { try{await ros?.close();}catch{} }
    steps.push({name:"RouterOS",...login});
  } else steps.push({name:"RouterOS",ok:false,code:tcp.ok?"CREDENTIALS_NOT_TESTED":"TCP_UNREACHABLE",message:tcp.ok?"تم فتح المنفذ لكن لم تُرسل بيانات الدخول":"لا يمكن اختبار بيانات الدخول قبل فتح المنفذ"});
  const ok=steps.every(x=>x.ok);
  res.json({ok,source:"backend",host,port,tls:tlsMode,steps,advice:ok?"الاتصال جاهز من مكان تشغيل Backend":"إذا فشل TCP فالمشكلة شبكة/VPN/Firewall، وليست كلمة مرور التطبيق"});
});
app.post("/api/routers/test",auth,async(req,res)=>{const {host,port=8728,username,password,tls=false}=req.body;let ros;try{if(!String(host||"").trim()||!String(username||"").trim()||!String(password||""))return res.status(400).json({ok:false,error:"ROUTER_INPUT_REQUIRED",detail:"host, username and password are required"});ros=new RouterOS({host:String(host).trim(),port:Number(port)||8728,username:String(username).trim(),password,tls:!!tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});const resource=await ros.command("/system/resource/print",[]);res.json({ok:true,resource:resource[0]||{}});}catch(e){const detail=String(e?.code||e?.message||"UNKNOWN").replace(/\s+/g," ").slice(0,240);res.status(502).json({ok:false,error:"ROUTER_CONNECTION_FAILED",detail,help:tls?"تحقق من API-SSL والمنفذ 8729 والشهادة وALLOW_INSECURE_ROUTER_TLS":"تحقق من API والمنفذ 8728 وIP الراوتر والجدار الناري"});}finally{try{await ros?.close();}catch{}}});
app.post("/api/routers",auth,async(req,res)=>{const {name,host,port=8728,username,password,tls=false}=req.body;if(!name||!host||!username||!password)return res.status(400).json({error:"INVALID_INPUT"});const id=crypto.randomUUID();await pool.query("INSERT INTO routers(id,user_id,name,host,port,username,password_enc,tls) VALUES($1,$2,$3,$4,$5,$6,$7,$8)",[id,req.user.sub,name,host,Number(port),username,encrypt(password),!!tls]);await audit(req.user.sub,"CREATE","router",id,{name,host});res.status(201).json({id,name,host,port:Number(port),username,tls:!!tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});});
app.delete("/api/routers/:id",auth,role("admin"),async(req,res)=>{await pool.query("DELETE FROM routers WHERE id=$1 AND user_id=$2",[req.params.id,req.user.sub]);await audit(req.user.sub,"DELETE","router",req.params.id);res.status(204).end();});
async function getRouter(req,id){const r=await pool.query("SELECT * FROM routers WHERE id=$1 AND user_id=$2",[id,req.user.sub]);if(!r.rowCount)throw Error("ROUTER_NOT_FOUND");return r.rows[0];}
async function withRos(req,id,fn){const router=await getRouter(req,id);const ros=new RouterOS({host:router.host,port:router.port,username:router.username,password:decrypt(router.password_enc),tls:router.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});try{return await fn(ros,router);}finally{await ros.close().catch(()=>{});}}
app.get("/api/routers/:id/hotspot/users",auth,async(req,res)=>{try{const items=await withRos(req,req.params.id,async ros=>await ros.command("/ip/hotspot/user/print",[]));res.json(items);}catch(e){res.status(502).json({error:"HOTSPOT_USERS_QUERY_FAILED",detail:e.message});}});
app.get("/api/routers/:id/network/:kind",auth,async(req,res)=>{const commands={interfaces:"/interface/print","dhcp-leases":"/ip/dhcp-server/lease/print",arp:"/ip/arp/print","dns-static":"/ip/dns/static/print","ip-addresses":"/ip/address/print",routes:"/ip/route/print",firewall:"/ip/firewall/filter/print",queues:"/queue/simple/print",logs:"/log/print"};const cmd=commands[req.params.kind];if(!cmd)return res.status(404).json({error:"NETWORK_KIND_NOT_FOUND"});try{const items=await withRos(req,req.params.id,async ros=>await ros.command(cmd,[]));res.json({kind:req.params.kind,items});}catch(e){res.status(502).json({error:"NETWORK_QUERY_FAILED",detail:e.message});}});
app.post("/api/routers/:id/terminal",auth,role("admin"),async(req,res)=>{
  const command=String(req.body.command||"").trim();
  if(!command||command.length>1000)return res.status(400).json({error:"INVALID_COMMAND"});
  try{
    const result=await withRos(req,req.params.id,async ros=>{
      const parts=command.split(/\s+/).filter(Boolean);
      const path=parts.shift()||"/";
      const args=parts.filter(x=>x.startsWith("="));
      return ros.command(path,args);
    });
    await audit(req.user.sub,"TERMINAL","router",req.params.id,{command});
    res.json({ok:true,command,result});
  }catch(e){res.status(502).json({error:"TERMINAL_COMMAND_FAILED",detail:String(e.code||e.message||"UNKNOWN").slice(0,240),help:"تحقق من صلاحية api والأمر ومن اتصال Backend بالراوتر"});}
});
app.get("/api/routers/:id/dashboard",auth,async(req,res)=>{try{const data=await withRos(req,req.params.id,async ros=>{const [resource,active,interfaces]=await Promise.all([ros.command("/system/resource/print",[]),ros.command("/ip/hotspot/active/print",[]),ros.command("/interface/print",[])]);return {resource:resource[0]||{},activeUsers:active.length,interfaces};});res.json(data);}catch(e){res.status(502).json({error:"ROUTER_QUERY_FAILED",detail:e.message});}});
function hotspotDuration(minutes){
  const m=Math.max(0,Number(minutes)||0);
  if(!m) return "";
  if(m%1440===0) return `${m/1440}d`;
  if(m%60===0) return `${m/60}h`;
  return `${m}m`;
}
function profilePayload(body){
  const name=String(body.name||"").trim();
  if(!name || name.length>64) throw Error("INVALID_PROFILE_NAME");
  const price=0;
  const durationMinutes=Math.max(0,Math.floor(Number(body.durationMinutes)||0));
  const rateLimit=String(body.rateLimit||"").trim();
  const sessionTimeout=String(body.sessionTimeout||hotspotDuration(durationMinutes)).trim();
  const idleTimeout=String(body.idleTimeout||"").trim();
  const sharedUsers=Math.min(Math.max(Math.floor(Number(body.sharedUsers)||1),1),100);
  return {name,price,durationMinutes,rateLimit,sessionTimeout,idleTimeout,sharedUsers};
}

app.get("/api/routers/:id/hotspot-profiles",auth,async(req,res)=>{
  try{
    const items=await withRos(req,req.params.id,async ros=>{
      const [routerProfiles,local]=await Promise.all([
        ros.command("/ip/hotspot/user/profile/print",[]),
        pool.query("SELECT name,price,duration_minutes,rate_limit,session_timeout,idle_timeout,shared_users,enabled FROM hotspot_profiles WHERE router_id=$1 ORDER BY name",[req.params.id])
      ]);
      const meta=new Map(local.rows.map(x=>[x.name,x]));
      return routerProfiles.map(x=>({name:x.name||"",rateLimit:x["rate-limit"]||"",sessionTimeout:x["session-timeout"]||"",idleTimeout:x["idle-timeout"]||"",sharedUsers:Number(x["shared-users"]||1),price:Number(meta.get(x.name)?.price||0),durationMinutes:Number(meta.get(x.name)?.duration_minutes||0),enabled:meta.get(x.name)?.enabled!==false}));
    });
    res.json({profiles:items});
  }catch(e){res.status(502).json({error:"HOTSPOT_PROFILE_QUERY_FAILED",detail:e.message});}
});
app.post("/api/routers/:id/hotspot-profiles",auth,async(req,res)=>{
  try{
    const p=profilePayload(req.body);
    const result=await withRos(req,req.params.id,async ros=>{
      const existing=await ros.command("/ip/hotspot/user/profile/print",[]);
      const found=existing.find(x=>x.name===p.name);
      const args=[`=name=${p.name}`,`=shared-users=${p.sharedUsers}`];
      if(p.rateLimit) args.push(`=rate-limit=${p.rateLimit}`);
      if(p.sessionTimeout) args.push(`=session-timeout=${p.sessionTimeout}`);
      if(p.idleTimeout) args.push(`=idle-timeout=${p.idleTimeout}`);
      if(found?.[".id"]){
        const setArgs=[`=.id=${found[".id"]}`,...args.slice(1)];
        await ros.command("/ip/hotspot/user/profile/set",setArgs);
      }else{
        await ros.command("/ip/hotspot/user/profile/add",args);
      }
      const q=await pool.query(`INSERT INTO hotspot_profiles(id,router_id,name,price,duration_minutes,rate_limit,session_timeout,idle_timeout,shared_users,enabled)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,true)
        ON CONFLICT(router_id,name) DO UPDATE SET price=0,duration_minutes=EXCLUDED.duration_minutes,rate_limit=EXCLUDED.rate_limit,session_timeout=EXCLUDED.session_timeout,idle_timeout=EXCLUDED.idle_timeout,shared_users=EXCLUDED.shared_users,enabled=true,updated_at=now()
        RETURNING *`,[crypto.randomUUID(),req.params.id,p.name,p.price,p.durationMinutes,p.rateLimit,p.sessionTimeout,p.idleTimeout,p.sharedUsers]);
      return q.rows[0];
    });
    await audit(req.user.sub,"UPSERT","hotspot_profile",req.params.id,{name:p.name,price:p.price,durationMinutes:p.durationMinutes});
    res.status(201).json({profile:result});
  }catch(e){res.status(400).json({error:"HOTSPOT_PROFILE_SAVE_FAILED",detail:e.message});}
});

// Smart selling layer: MICRO-MAX Plan owns price; MikroTik Profile owns network behavior.
app.get("/api/routers/:id/plans",auth,async(req,res)=>{try{const r=await pool.query(`SELECT p.*,hp.duration_minutes,hp.rate_limit,hp.session_timeout,hp.idle_timeout,hp.shared_users FROM hotspot_plans p LEFT JOIN hotspot_profiles hp ON hp.router_id=p.router_id AND hp.name=p.profile_name WHERE p.router_id=$1 ORDER BY p.active DESC,p.name`,[req.params.id]);res.json({plans:r.rows});}catch(e){res.status(500).json({error:"PLANS_QUERY_FAILED",detail:e.message});}});
app.post("/api/routers/:id/plans",auth,async(req,res)=>{try{const profileName=String(req.body.profileName||"").trim(),name=String(req.body.name||profileName).trim(),currency=String(req.body.currency||"XOF").trim().toUpperCase(),price=Math.max(0,Number(req.body.price)||0);if(!profileName||!name||!Number.isFinite(price))return res.status(400).json({error:"INVALID_PLAN"});const check=await pool.query("SELECT 1 FROM hotspot_profiles WHERE router_id=$1 AND name=$2",[req.params.id,profileName]);if(!check.rowCount)return res.status(400).json({error:"HOTSPOT_PROFILE_NOT_FOUND"});const existing=await pool.query("SELECT id,price FROM hotspot_plans WHERE router_id=$1 AND name=$2",[req.params.id,name]);let q;if(existing.rowCount){const old=Number(existing.rows[0].price);q=await pool.query("UPDATE hotspot_plans SET profile_name=$1,price=$2,currency=$3,active=true,updated_at=now() WHERE id=$4 RETURNING *",[profileName,price,currency,existing.rows[0].id]);if(Math.abs(old-price)>0.000001)await pool.query("INSERT INTO plan_price_history(id,plan_id,old_price,new_price,changed_by) VALUES($1,$2,$3,$4,$5)",[crypto.randomUUID(),existing.rows[0].id,old,price,req.user.sub]);}else{q=await pool.query("INSERT INTO hotspot_plans(id,router_id,profile_name,name,price,currency) VALUES($1,$2,$3,$4,$5,$6) RETURNING *",[crypto.randomUUID(),req.params.id,profileName,name,price,currency]);await pool.query("INSERT INTO plan_price_history(id,plan_id,old_price,new_price,changed_by) VALUES($1,$2,$3,$4,$5)",[crypto.randomUUID(),q.rows[0].id,null,price,req.user.sub]);}await audit(req.user.sub,"UPSERT","hotspot_plan",q.rows[0].id,{name,profileName,price,currency});res.status(201).json({plan:q.rows[0]});}catch(e){res.status(400).json({error:"PLAN_SAVE_FAILED",detail:e.message});}});
app.get("/api/plans/:id/price-history",auth,async(req,res)=>{try{const r=await pool.query("SELECT h.*,u.email changed_by_email FROM plan_price_history h LEFT JOIN users u ON u.id=h.changed_by JOIN hotspot_plans p ON p.id=h.plan_id JOIN routers rr ON rr.id=p.router_id WHERE h.plan_id=$1 AND rr.user_id=$2 ORDER BY h.created_at DESC",[req.params.id,req.user.sub]); if(!r.rowCount)return res.status(404).json({error:"PLAN_NOT_FOUND"}); res.json({history:r.rows});}catch(e){res.status(500).json({error:"PRICE_HISTORY_QUERY_FAILED"});}});


function validateLoginHtml(html){
  const src=String(html||"");
  const warnings=[];
  const errors=[];
  const hasForm=/<form\b/i.test(src);
  const hasUser=/(name=["']username["']|id=["']username["'])/i.test(src);
  const hasPass=/(name=["']password["']|id=["']password["'])/i.test(src);
  const hasLoginAction=/(action=["'][^"']*login[^"']*["'])/i.test(src) || /\$\(link-login-only\)/i.test(src);
  const hasLinkLogin= /\$\(link-login-only\)/i.test(src);
  const hasLinkOrig= /\$\(link-orig\)/i.test(src);
  const hasError= /\$\(error\)/i.test(src);
  const hasMd5= /\$\(chap-id\)|\$\(chap-challenge\)/i.test(src);
  const hasAutoSubmit=/\.submit\s*\(|requestSubmit\s*\(/i.test(src);
  if(!hasForm) errors.push("LOGIN_FORM_MISSING");
  if(!hasUser) errors.push("USERNAME_FIELD_MISSING");
  if(!hasPass) errors.push("PASSWORD_FIELD_MISSING");
  if(!hasLoginAction) errors.push("LOGIN_ACTION_MISSING");
  if(!hasLinkLogin) warnings.push("LINK_LOGIN_ONLY_MISSING");
  if(!hasLinkOrig) warnings.push("LINK_ORIG_MISSING");
  if(!hasError) warnings.push("ERROR_VARIABLE_MISSING");
  if(hasMd5 && !/md5\.js|hexMD5|doLogin/i.test(src)) warnings.push("CHAP_VARIABLES_FOUND_BUT_CHAP_HANDLER_NOT_DETECTED");
  return {valid:errors.length===0,errors,warnings,checks:{hasForm,hasUser,hasPass,hasLoginAction,hasLinkLogin,hasLinkOrig,hasError,hasMd5,hasAutoSubmit}};
}

async function getRouterForUser(req,id){
  const q=await pool.query("SELECT * FROM routers WHERE id=$1 AND user_id=$2",[id,req.user.sub]);
  if(!q.rowCount) throw Error("ROUTER_NOT_FOUND");
  return q.rows[0];
}

app.get("/api/routers/:id/hotspot-login",auth,async(req,res)=>{
  let ros;
  try{
    const r=await getRouterForUser(req,req.params.id);
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const files=await ros.command("/file/print",["?name=hotspot/login.html"]);
    const f=files[0]||{};
    const content=String(f.contents||"");
    const validation=validateLoginHtml(content);
    res.json({routerId:r.id,name:r.name,file:"hotspot/login.html",content,validation,updatedAt:f.creation_time||f["creation-time"]||null});
  }catch(e){res.status(502).json({error:"HOTSPOT_LOGIN_READ_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

app.post("/api/routers/:id/hotspot-login/validate",auth,async(req,res)=>{
  try{
    await getRouterForUser(req,req.params.id);
    const validation=validateLoginHtml(String(req.body.content||""));
    res.json(validation);
  }catch(e){res.status(400).json({valid:false,error:"HOTSPOT_LOGIN_VALIDATE_FAILED",detail:e.message});}
});

app.post("/api/routers/:id/hotspot-login/publish",auth,role("admin"),async(req,res)=>{
  let ros;
  try{
    const r=await getRouterForUser(req,req.params.id);
    const content=String(req.body.content||"");
    if(content.length<100 || content.length>500000) return res.status(400).json({error:"INVALID_LOGIN_HTML_SIZE"});
    const validation=validateLoginHtml(content);
    if(!validation.valid) return res.status(400).json({error:"LOGIN_HTML_INCOMPATIBLE",validation});
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const current=await ros.command("/file/print",["?name=hotspot/login.html"]);
    const old=String(current[0]?.contents||"");
    const backupName=`hotspot/login.html.micromax-${new Date().toISOString().replace(/[:.]/g,"-")}.bak`;
    if(old){
      try{ await ros.command("/file/add",[`=name=${backupName}`,`=contents=${old}`]); }catch{}
    }
    const target=current[0]?.[".id"] || current[0]?.id;
    if(target) await ros.command("/file/set",[`=.id=${target}`,`=contents=${content}`]);
    else await ros.command("/file/add",["=name=hotspot/login.html",`=contents=${content}`]);
    await audit(req.user.sub,"PUBLISH","hotspot_login",req.params.id,{backup:backupName,bytes:Buffer.byteLength(content,"utf8"),validation});
    res.json({ok:true,file:"hotspot/login.html",backup:old?backupName:null,validation});
  }catch(e){res.status(502).json({ok:false,error:"HOTSPOT_LOGIN_PUBLISH_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

app.get("/api/routers/:id/hotspot-login/backups",auth,async(req,res)=>{
  let ros;
  try{
    const r=await getRouterForUser(req,req.params.id);
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const files=await ros.command("/file/print",[]);
    const backups=files.filter(f=>/^hotspot\/login\.html\.micromax-[A-Za-z0-9_.-]+\.bak$/.test(String(f.name||""))).map(f=>({name:f.name,size:Number(f.size||0),creationTime:f.creation_time||f["creation-time"]||null})).sort((a,b)=>String(b.creationTime).localeCompare(String(a.creationTime)));
    res.json({backups});
  }catch(e){res.status(502).json({error:"HOTSPOT_LOGIN_BACKUPS_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

app.post("/api/routers/:id/hotspot-login/restore",auth,role("admin"),async(req,res)=>{
  let ros;
  try{
    const r=await getRouterForUser(req,req.params.id);
    const backup=String(req.body.backupName||"").trim();
    if(!/^hotspot\/login\.html\.micromax-[A-Za-z0-9_.-]+\.bak$/.test(backup)) return res.status(400).json({error:"INVALID_BACKUP_NAME"});
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const b=await ros.command("/file/print",[`?name=${backup}`]);
    const content=String(b[0]?.contents||"");
    const validation=validateLoginHtml(content);
    if(!validation.valid) return res.status(400).json({error:"BACKUP_INCOMPATIBLE",validation});
    const current=await ros.command("/file/print",["?name=hotspot/login.html"]);
    const target=current[0]?.[".id"] || current[0]?.id;
    if(!target) throw Error("LOGIN_HTML_NOT_FOUND");
    await ros.command("/file/set",[`=.id=${target}`,`=contents=${content}`]);
    await audit(req.user.sub,"RESTORE","hotspot_login",req.params.id,{backup});
    res.json({ok:true,restoredFrom:backup,validation});
  }catch(e){res.status(502).json({ok:false,error:"HOTSPOT_LOGIN_RESTORE_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

function validateHotspotPage(page,content){
  const src=String(content||"");
  if(page === "login") return validateLoginHtml(src);
  const errors=[]; const warnings=[];
  if(src.length<100) errors.push("HTML_TOO_SHORT");
  if(!/<html[\s>]/i.test(src)) errors.push("HTML_DOCUMENT_MISSING");
  if(!/\$\(username\)/i.test(src)) warnings.push("USERNAME_VARIABLE_MISSING");
  if(!/\$\(session-time-left\)/i.test(src)) warnings.push("SESSION_TIME_VARIABLE_MISSING");
  if(!/\$\(link-logout\)/i.test(src)) warnings.push("LOGOUT_LINK_MISSING");
  return {valid:errors.length===0,errors,warnings,checks:{hasHtml:/<html[\s>]/i.test(src),hasUsername:/\$\(username\)/i.test(src),hasSessionTime:/\$\(session-time-left\)/i.test(src),hasLogout:/\$\(link-logout\)/i.test(src)}};
}

app.get("/api/routers/:id/hotspot-pages",auth,async(req,res)=>{
  let ros;
  try{
    const r=await getRouterForUser(req,req.params.id);
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const result={routerId:r.id,pages:{}};
    for(const page of ["login","status"]){const name=`hotspot/${page}.html`;const f=(await ros.command("/file/print",[`?name=${name}`]))[0]||{};const content=String(f.contents||"");result.pages[page]={file:name,content,validation:validateHotspotPage(page,content),updatedAt:f.creation_time||f["creation-time"]||null};}
    res.json(result);
  }catch(e){res.status(502).json({error:"HOTSPOT_PAGES_READ_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

app.post("/api/routers/:id/hotspot-pages/validate",auth,async(req,res)=>{
  try{await getRouterForUser(req,req.params.id);const page=String(req.body.page||"").toLowerCase();if(!["login","status"].includes(page))return res.status(400).json({error:"INVALID_HOTSPOT_PAGE"});res.json(validateHotspotPage(page,String(req.body.content||"")));}
  catch(e){res.status(400).json({valid:false,error:"HOTSPOT_PAGE_VALIDATE_FAILED",detail:e.message});}
});

app.post("/api/routers/:id/hotspot-pages/publish",auth,role("admin"),async(req,res)=>{
  let ros;
  try{
    const page=String(req.body.page||"").toLowerCase(); const content=String(req.body.content||"");
    if(!["login","status"].includes(page))return res.status(400).json({error:"INVALID_HOTSPOT_PAGE"});
    if(content.length<100 || content.length>500000)return res.status(400).json({error:"INVALID_HOTSPOT_HTML_SIZE"});
    const validation=validateHotspotPage(page,content); if(!validation.valid)return res.status(400).json({error:"HOTSPOT_HTML_INCOMPATIBLE",validation});
    const r=await getRouterForUser(req,req.params.id);
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"}); await ros.connect();
    const file=`hotspot/${page}.html`; const current=await ros.command("/file/print",[`?name=${file}`]); const old=String(current[0]?.contents||"");
    const backup=`${file}.micromax-${new Date().toISOString().replace(/[:.]/g,"-")}.bak`; if(old)try{await ros.command("/file/add",[`=name=${backup}`,`=contents=${old}`]);}catch{}
    const target=current[0]?.[".id"]||current[0]?.id; if(target)await ros.command("/file/set",[`=.id=${target}`,`=contents=${content}`]); else await ros.command("/file/add",[`=name=${file}`,`=contents=${content}`]);
    await audit(req.user.sub,"PUBLISH",`hotspot_${page}`,req.params.id,{backup,bytes:Buffer.byteLength(content,"utf8"),validation}); res.json({ok:true,file,backup:old?backup:null,validation});
  }catch(e){res.status(502).json({ok:false,error:"HOTSPOT_PAGE_PUBLISH_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

const routerCommands={dhcp:"/ip/dhcp-server/lease/print",arp:"/ip/arp/print",dns:"/ip/dns/print",ip:"/ip/address/print",routes:"/ip/route/print",firewall:"/ip/firewall/filter/print",queues:"/queue/simple/print",logs:"/log/print"};
for(const [key,cmd] of Object.entries(routerCommands)) app.get(`/api/routers/:id/${key}`,auth,async(req,res)=>{try{res.json({items:await withRos(req,req.params.id,ros=>ros.command(cmd,[]))});}catch(e){res.status(502).json({error:"ROUTER_QUERY_FAILED",detail:e.message});}});

// Smart preflight: validates a bulk-card request without creating or changing anything.
app.post("/api/routers/:id/cards/preflight",auth,async(req,res)=>{
  let ros=null;
  try{
    const requested=Number(req.body.count);
    const count=Math.min(Math.max(Number.isFinite(requested)?Math.floor(requested):1,1),5000);
    const prefix=sanitizePrefix(req.body.prefix);
    const usernameDigits=Math.min(Math.max(Number.isFinite(Number(req.body.usernameDigits))?Math.floor(Number(req.body.usernameDigits)):6,0),20);
    const usernameLetters=Math.min(Math.max(Number.isFinite(Number(req.body.usernameLetters))?Math.floor(Number(req.body.usernameLetters)):0,0),12);
    const planId=String(req.body.planId||"").trim();
    if(usernameDigits+usernameLetters===0)throw Error("USERNAME_FORMAT_EMPTY");
    if(String(req.body.passwordMode||"userpass")==="pin")assertPinStrength(usernameLetters,usernameDigits);
    const planQ=await pool.query(`SELECT p.*,hp.duration_minutes,hp.rate_limit,hp.session_timeout,hp.idle_timeout,hp.shared_users FROM hotspot_plans p LEFT JOIN hotspot_profiles hp ON hp.router_id=p.router_id AND hp.name=p.profile_name WHERE p.id=$1 AND p.router_id=$2 AND p.active=true`,[planId,req.params.id]);
    if(!planQ.rowCount)throw Error("PLAN_REQUIRED");
    const plan=planQ.rows[0];
    const routerQ=await pool.query("SELECT id,host,port,username,tls FROM routers WHERE id=$1 AND user_id=$2",[req.params.id,req.user.sub]);
    if(!routerQ.rowCount)throw Error("ROUTER_NOT_FOUND");
    const r=routerQ.rows[0];
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt((await pool.query("SELECT password_enc FROM routers WHERE id=$1",[req.params.id])).rows[0].password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const profiles=await ros.command("/ip/hotspot/user/profile/print",[]);
    const profileVerified=profiles.some(x=>String(x.name||"")===String(plan.profile_name));
    if(!profileVerified)throw Error("HOTSPOT_PROFILE_NOT_FOUND");
    const existingRos=await ros.command("/ip/hotspot/user/print",[]);
    const dbQ=await pool.query("SELECT username FROM cards WHERE router_id=$1",[req.params.id]);
    const used=new Set(existingRos.map(x=>String(x.name||"")));
    dbQ.rows.forEach(x=>used.add(String(x.username||"")));
    const space=usernameSpace(usernameLetters,usernameDigits);
    const available=Math.max(0,space-usedPrefixCount(used,prefix,usernameLetters,usernameDigits));
    const enough=available>=count;
    res.json({
      ready:enough,
      request:{count,prefix,letters:usernameLetters,digits:usernameDigits,totalLength:prefix.length+usernameLetters+usernameDigits,planId,planName:plan.name,price:Number(plan.price),currency:plan.currency},
      validation:{routerVerified:true,profileVerified,duplicateCheck:true,usernameCollisionCheck:true},
      usernamePool:{theoretical:space,usedMatchingPrefix:space-available,available,required:count},
      warnings:enough?[]:["USERNAME_POOL_TOO_SMALL"],
      generatedBy:"mikrotik",note:"Preflight does not create, delete, modify, or sell any card."
    });
  }catch(e){res.status(400).json({ready:false,error:"CARD_PREFLIGHT_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});


// QR HotSpot compatibility check: validates the real portal URL and RouterOS login method
// before a QR is treated as a connection QR. It never changes MikroTik configuration.
app.post("/api/routers/:id/qr-preflight",auth,async(req,res)=>{
  let ros=null;
  try{
    const portalUrl=String(req.body.portalUrl||"").trim();
    const planId=String(req.body.planId||"").trim();
    if(!portalUrl) return res.status(400).json({ready:false,error:"HOTSPOT_URL_REQUIRED"});
    let url;
    try{ url=new URL(portalUrl); }catch{ return res.status(400).json({ready:false,error:"INVALID_HOTSPOT_URL"}); }
    if(!["http:","https:"].includes(url.protocol)) return res.status(400).json({ready:false,error:"HOTSPOT_URL_MUST_BE_HTTP_OR_HTTPS"});
    const routerQ=await pool.query("SELECT * FROM routers WHERE id=$1 AND user_id=$2",[req.params.id,req.user.sub]);
    if(!routerQ.rowCount)throw Error("ROUTER_NOT_FOUND");
    const r=routerQ.rows[0];
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const hotspotProfiles=await ros.command("/ip/hotspot/profile/print",[]);
    const hotspotServers=await ros.command("/ip/hotspot/print",[]);
    const loginBy=[...new Set(hotspotProfiles.flatMap(x=>String(x["login-by"]||x.login_by||"").split(",").map(v=>v.trim()).filter(Boolean)))];
    const serverAddress=hotspotProfiles.find(x=>x["hotspot-address"]||x["dns-name"]||x["dns-name"])||{};
    const controllerHost=String(url.host);
    let portalStatus=null,portalReachable=false,html="",fetchError=null;
    try{
      const controller=new AbortController(); const timer=setTimeout(()=>controller.abort(),5000);
      const response=await fetch(url,{redirect:"follow",signal:controller.signal,headers:{"User-Agent":"MICRO-MAX-HotSpot-QR-Check/1.0"}});
      clearTimeout(timer); portalStatus=response.status; html=(await response.text()).slice(0,500000); portalReachable=response.ok;
    }catch(e){fetchError=e.message||"PORTAL_UNREACHABLE";}
    const hasLoginForm=/<form[^>]+action=["'][^"']*\/login/i.test(html)||/<form[^>]*>/i.test(html)&&/name=["']username["']/i.test(html)&&/name=["']password["']/i.test(html);
    const hasAutoSubmit=/\.submit\s*\(|requestSubmit\s*\(/i.test(html);
    const papAllowed=loginBy.some(v=>v.toLowerCase().includes("http-pap"));
    const httpsAllowed=loginBy.some(v=>v.toLowerCase().includes("https"));
    const chapOnly=loginBy.length>0 && loginBy.every(v=>v.toLowerCase().includes("http-chap")||v.toLowerCase().includes("cookie"));
    let mode="portal-prefill";
    let ready=portalReachable && hasLoginForm && (papAllowed || httpsAllowed || !chapOnly);
    if(ready && hasAutoSubmit && (papAllowed||httpsAllowed)) mode="auto-submit";
    const warnings=[];
    if(!portalReachable)warnings.push("HOTSPOT_PORTAL_NOT_REACHABLE_FROM_API");
    if(portalReachable&&!hasLoginForm)warnings.push("LOGIN_FORM_NOT_DETECTED");
    if(chapOnly)warnings.push("CHAP_REQUIRES_LOGIN_PAGE_CHALLENGE_HANDLING");
    if(mode==="portal-prefill")warnings.push("QR_OPENS_REAL_HOTSPOT_LOGIN_PAGE;_AUTO_LOGIN_REQUIRES_COMPATIBLE_LOGIN_HTML");
    res.json({
      ready,mode,portal:{url:portalUrl,host:controllerHost,status:portalStatus,reachable:portalReachable,loginFormDetected:hasLoginForm,autoSubmitDetected:hasAutoSubmit,fetchError},
      router:{verified:true,hotspotServers:hotspotServers.length,loginBy,papAllowed,httpsAllowed,chapOnly},
      recommendedQr:`${portalUrl.replace(/\/$/,"")}?username={USERNAME}&password={PASSWORD}&dst=${encodeURIComponent(url.origin+"/")}`,
      planId:planId||null,
      note:"MICRO-MAX never changes MikroTik login settings during QR preflight. Auto-submit is only marked ready when the real login page already contains compatible form handling."
    });
  }catch(e){res.status(400).json({ready:false,error:"QR_PREFLIGHT_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

function usedPrefixCount(used,prefix,letters,digits){
  let n=0; const re=usernameRegex(prefix,letters,digits);
  for(const u of used)if(re.test(u))n++;
  return n;
}

app.post("/api/routers/:id/cards/generate",auth,async(req,res)=>{
  const requested=Number(req.body.count);
  const count=Math.min(Math.max(Number.isFinite(requested)?Math.floor(requested):1,1),5000);
  const prefix=sanitizePrefix(req.body.prefix);
  const usernameDigits=Math.min(Math.max(Number.isFinite(Number(req.body.usernameDigits))?Math.floor(Number(req.body.usernameDigits)):6,0),20);
  const usernameLetters=Math.min(Math.max(Number.isFinite(Number(req.body.usernameLetters))?Math.floor(Number(req.body.usernameLetters)):0,0),12);
  const planId=String(req.body.planId||"").trim();
  const portalUrl=String(req.body.portalUrl||"").trim();
  // A Wi-Fi QR must never claim an unknown secured network is open.
  const ssid=String(req.body.wifiOpen === true ? req.body.ssid||"" : "").trim().slice(0,32);
  const batchId=crypto.randomUUID();
  const created=[];
  let routerTouched=false,committed=false;
  let ros=null;
  try{
    const planQ=await pool.query(`SELECT p.*,hp.duration_minutes,hp.rate_limit,hp.session_timeout,hp.idle_timeout,hp.shared_users
      FROM hotspot_plans p LEFT JOIN hotspot_profiles hp ON hp.router_id=p.router_id AND hp.name=p.profile_name
      WHERE p.id=$1 AND p.router_id=$2 AND p.active=true`,[planId,req.params.id]);
    if(!planQ.rowCount)throw Error("PLAN_REQUIRED");
    const plan=planQ.rows[0];
    if(Number(plan.price)<0)throw Error("INVALID_PLAN_PRICE");
    let portal; try{ portal=new URL(portalUrl); }catch{ throw Error("INVALID_HOTSPOT_URL"); }
    if(!["http:","https:"].includes(portal.protocol))throw Error("HOTSPOT_URL_MUST_BE_HTTP_OR_HTTPS");
    const qrBase=portalUrl.replace(/\/$/,"");
    // Smart preflight: validate the real MikroTik profile before generating anything.
    const routerQ=await pool.query("SELECT * FROM routers WHERE id=$1 AND user_id=$2",[req.params.id,req.user.sub]);
    if(!routerQ.rowCount)throw Error("ROUTER_NOT_FOUND");
    const r=routerQ.rows[0];
    ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
    await ros.connect();
    const profiles=await ros.command("/ip/hotspot/user/profile/print",[]);
    if(!profiles.some(x=>x.name===plan.profile_name))throw Error("HOTSPOT_PROFILE_NOT_FOUND");

    // MikroTik generates the usernames/passwords itself (RouterOS :rndstr). Duplicates against the
    // database are detected after read-back, removed from the router and regenerated.
    const passLen=Math.min(Math.max(Number.isFinite(Number(req.body.passwordLength))?Math.floor(Number(req.body.passwordLength)):8,4),16);
    const pin=String(req.body.passwordMode||"userpass")==="pin";
    if(pin)assertPinStrength(usernameLetters,usernameDigits);
    const dbHas=async names=>{const q=await pool.query("SELECT username FROM cards WHERE router_id=$1 AND username=ANY($2::text[])",[req.params.id,names]);return new Set(q.rows.map(x=>x.username));};
    routerTouched=true;
    const made=await generateBatch({ros,count,prefix,digits:usernameDigits,letters:usernameLetters,passLen,pin,profile:plan.profile_name,batchId,dbHas});
    for(const m of made)created.push({...m,id:crypto.randomUUID()});

    // Persist in chunks. Passwords are stored encrypted; the QR is rebuilt on read (never stored in clear).
    const client=await pool.connect();
    try{
      await client.query("BEGIN");
      for(let i=0;i<created.length;i+=250){
        const chunk=created.slice(i,i+250);
        const values=[]; const params=[];
        chunk.forEach((c,j)=>{
          const o=j*11;
          values.push(`($${o+1},$${o+2},$${o+3},$${o+4},$${o+5},$${o+6},$${o+7},$${o+8},$${o+9},$${o+10},$${o+11})`);
          params.push(c.id,req.params.id,c.username,encPass(c.password),plan.profile_name,plan.price,plan.id,plan.currency,batchId,qrBase,ssid||null);
        });
        await client.query(`INSERT INTO cards(id,router_id,username,password,profile,price,plan_id,price_currency,batch_id,portal_url,ssid) VALUES ${values.join(",")}`,params);
      }
      await client.query("COMMIT"); committed=true;
    }catch(e){ try{await client.query("ROLLBACK");}catch{} throw e; }
    finally{client.release();}

    await audit(req.user.sub,"CREATE","cards",req.params.id,{count,planId,price:Number(plan.price),currency:plan.currency,batchId,generatedBy:"mikrotik",usernameDigits,usernameLetters});
    res.status(201).json({
      success:true,count,batchId,generatedBy:"mikrotik",pricingSource:"micromax_plan",planId,planName:plan.name,price:Number(plan.price),currency:plan.currency,
      usernameFormat:{prefix,letters:usernameLetters,digits:usernameDigits,totalLength:prefix.length+usernameLetters+usernameDigits,passwordMode:pin?"pin":"userpass",passwordLength:passLen},
      validation:{profileVerified:true,duplicateCheck:true,usernameCollisionCheck:true,routerVerified:true,qrSigned:true},
      cards:created.map(c=>({id:c.id,username:c.username,password:c.password,passwordMode:pin?"pin":"userpass",profile:plan.profile_name,planId:plan.id,planName:plan.name,price:Number(plan.price),currency:plan.currency,durationMinutes:Number(plan.duration_minutes||0),rateLimit:plan.rate_limit||"",sessionTimeout:plan.session_timeout||"",idleTimeout:plan.idle_timeout||"",sharedUsers:Number(plan.shared_users||1),qrContent:buildQrUrl({portalUrl:qrBase,username:c.username,password:pin?"":c.password,cardId:c.id,secret:QR_SECRET}),wifiQr:buildWifiQr(ssid)}))
    });
  }catch(e){
    // Failed before the DB commit: remove only this batch from the router (never after a successful commit).
    if(ros && routerTouched && !committed){try{const batchUsers=await ros.command("/ip/hotspot/user/print",[`?comment=MICRO-MAX:${batchId}`]);for(const u of batchUsers){if(u[".id"])await ros.command("/ip/hotspot/user/remove",[`=.id=${u[".id"]}`]);}}catch{}}
    res.status(400).json({error:"CARD_GENERATION_FAILED",detail:e.message,batchId});
  }finally{try{await ros?.close();}catch{}}
});

// ---- Smart detection: DB <-> MikroTik reconciliation (read-only unless apply=true) ----
async function rosForRouter(req,id){const q=await pool.query("SELECT * FROM routers WHERE id=$1 AND user_id=$2",[id,req.user.sub]);if(!q.rowCount)throw Error("ROUTER_NOT_FOUND");const r=q.rows[0];return new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS==="true"});}
app.post("/api/routers/:id/cards/audit",auth,async(req,res)=>{
  let ros=null;
  try{
    ros=await rosForRouter(req,req.params.id); await ros.connect();
    const routerUsers=await ros.command("/ip/hotspot/user/print",[]);
    const activeNames=new Set((await ros.command("/ip/hotspot/active/print",[])).map(x=>String(x.user||"")));
    const dbq=req.body.batchId?await pool.query("SELECT id,username,password,status FROM cards WHERE router_id=$1 AND batch_id=$2",[req.params.id,String(req.body.batchId)]):await pool.query("SELECT id,username,password,status FROM cards WHERE router_id=$1",[req.params.id]);
    const report=auditCards({routerUsers,activeNames,dbCards:dbq.rows,readPassword:c=>decPass(c.password)});
    let synced=0;
    if(req.body.apply===true){
      const byName=new Map(routerUsers.map(u=>[String(u.name),u]));
      for(const d of report.issues.statusDrift){
        const st=d.routerState==="unused"?null:(d.routerState==="active"?"used":d.routerState);
        if(!st)continue;
        const u=await pool.query("UPDATE cards SET status=$1 WHERE router_id=$2 AND username=$3 AND status='available'",[st,req.params.id,d.username]); synced+=u.rowCount;
      }
      await audit(req.user.sub,"UPDATE","cards_sync",req.params.id,{synced});
    }
    res.json({...report,synced,applied:req.body.apply===true,note:"Audit is read-only unless apply=true; apply only syncs availability status, never deletes cards."});
  }catch(e){res.status(400).json({error:"CARD_AUDIT_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

// Scan a card QR (full URL) or a username: verifies signature, DB record and live MikroTik state.
app.post("/api/cards/scan",auth,async(req,res)=>{
  let ros=null;
  try{
    const qr=req.body.qr?parseQr(req.body.qr):null;
    const username=String(qr?.username||req.body.username||"").trim();
    if(!username)return res.status(400).json({error:"QR_OR_USERNAME_REQUIRED"});
    const params=[req.user.sub,username]; let extra="";
    if(req.body.routerId){params.push(String(req.body.routerId));extra=" AND c.router_id=$3";}
    const q=await pool.query("SELECT c.* FROM cards c JOIN routers r ON r.id=c.router_id WHERE r.user_id=$1 AND c.username=$2"+extra,params);
    if(q.rowCount>1)return res.json({verdict:"AMBIGUOUS_ROUTER",sellable:false,routers:q.rows.map(x=>x.router_id)});
    const card=q.rows[0]||null;
    let routerUser=null,activeNames=new Set();
    if(card){ros=await rosForRouter(req,card.router_id);await ros.connect();routerUser=(await ros.command("/ip/hotspot/user/print",[`?name=${username}`]))[0]||null;activeNames=new Set((await ros.command("/ip/hotspot/active/print",[`?user=${username}`])).map(x=>String(x.user||"")));}
    const result=scanVerdict({card,routerUser,activeNames,qrParsed:qr,secret:QR_SECRET});
    if(qr&&card&&result.verdict!=="QR_TAMPERED"&&qr.password&&qr.password!==decPass(card.password))result.verdict="PASSWORD_MISMATCH",result.sellable=false;
    res.json({...result,card:card?{id:card.id,username:card.username,profile:card.profile,status:card.status,price:Number(card.price),currency:card.price_currency,batchId:card.batch_id}:null,qrSignatureChecked:Boolean(qr?.sig)});
  }catch(e){res.status(400).json({error:"CARD_SCAN_FAILED",detail:e.message});}
  finally{try{await ros?.close();}catch{}}
});

app.get("/api/cards",auth,async(req,res)=>{const r=await pool.query("SELECT c.*,r.name router_name,p.name plan_name,p.currency plan_currency,hp.duration_minutes profile_duration_minutes,hp.rate_limit profile_rate_limit,hp.session_timeout profile_session_timeout,hp.idle_timeout profile_idle_timeout,hp.shared_users profile_shared_users FROM cards c JOIN routers r ON r.id=c.router_id LEFT JOIN hotspot_profiles hp ON hp.router_id=c.router_id AND hp.name=c.profile LEFT JOIN hotspot_plans p ON p.id=c.plan_id WHERE r.user_id=$1 ORDER BY c.created_at DESC LIMIT 1000",[req.user.sub]);res.json({cards:r.rows.map(cardOut)});});

// Smart Batch Center: reporting and safe lifecycle operations. Does not change card pricing/profile/username.
app.get("/api/card-batches",auth,async(req,res)=>{try{const r=await pool.query(`SELECT c.batch_id,MIN(c.created_at) created_at,COUNT(*)::int total,COUNT(*) FILTER(WHERE c.status='available')::int available,COUNT(*) FILTER(WHERE c.status='sold')::int sold,COUNT(*) FILTER(WHERE c.status='used')::int used,COUNT(*) FILTER(WHERE c.status='expired')::int expired,COUNT(*) FILTER(WHERE c.status='disabled')::int disabled,MIN(c.plan_id) plan_id,MIN(p.name) plan_name,MIN(r.name) router_name,MIN(c.price_currency) currency,MIN(c.price) price FROM cards c JOIN routers r ON r.id=c.router_id LEFT JOIN hotspot_plans p ON p.id=c.plan_id WHERE r.user_id=$1 AND c.batch_id IS NOT NULL GROUP BY c.batch_id ORDER BY MIN(c.created_at) DESC LIMIT 500`,[req.user.sub]);res.json({batches:r.rows});}catch(e){res.status(500).json({error:"BATCH_QUERY_FAILED",detail:e.message});}});
app.get("/api/card-batches/:id",auth,async(req,res)=>{try{const r=await pool.query(`SELECT c.*,r.name router_name,p.name plan_name,p.currency plan_currency FROM cards c JOIN routers r ON r.id=c.router_id LEFT JOIN hotspot_plans p ON p.id=c.plan_id WHERE c.batch_id=$1 AND r.user_id=$2 ORDER BY c.created_at DESC`,[req.params.id,req.user.sub]);if(!r.rowCount)return res.status(404).json({error:"BATCH_NOT_FOUND"});res.json({batchId:req.params.id,cards:r.rows.map(cardOut)});}catch(e){res.status(500).json({error:"BATCH_DETAIL_FAILED",detail:e.message});}});
app.post("/api/card-batches/:id/disable",auth,async(req,res)=>{const client=await pool.connect();let routerId=null;try{await client.query("BEGIN");const q=await client.query(`SELECT c.id,c.router_id,c.username,c.status FROM cards c JOIN routers r ON r.id=c.router_id WHERE c.batch_id=$1 AND r.user_id=$2 FOR UPDATE`,[req.params.id,req.user.sub]);if(!q.rowCount)throw Error("BATCH_NOT_FOUND");routerId=q.rows[0].router_id;const candidates=q.rows.filter(x=>["available","expired"].includes(x.status));for(const c of candidates)await client.query("UPDATE cards SET status='disabled' WHERE id=$1",[c.id]);await client.query("COMMIT");await audit(req.user.sub,"DISABLE","card_batch",req.params.id,{changed:candidates.length,protected:q.rows.length-candidates.length});res.json({success:true,batchId:req.params.id,disabled:candidates.length,protected:q.rows.length-candidates.length});}catch(e){await client.query("ROLLBACK");res.status(400).json({error:e.message});}finally{client.release();}});
app.delete("/api/card-batches/:id",auth,async(req,res)=>{const client=await pool.connect();let ros=null;try{await client.query("BEGIN");const q=await client.query(`SELECT c.*,r.host,r.port,r.username router_username,r.password_enc,r.tls FROM cards c JOIN routers r ON r.id=c.router_id WHERE c.batch_id=$1 AND r.user_id=$2 FOR UPDATE`,[req.params.id,req.user.sub]);if(!q.rowCount)throw Error("BATCH_NOT_FOUND");const protectedCards=q.rows.filter(x=>!["available","expired","disabled"].includes(x.status));if(protectedCards.length)throw Error("BATCH_CONTAINS_SOLD_OR_USED_CARDS");const deletable=q.rows.filter(x=>["available","expired","disabled"].includes(x.status));for(const c of deletable)await client.query("DELETE FROM cards WHERE id=$1",[c.id]);await client.query("COMMIT");try{const r=q.rows[0];ros=new RouterOS({host:r.host,port:r.port,username:r.router_username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});await ros.connect();const users=await ros.command("/ip/hotspot/user/print",[`?comment=MICRO-MAX:${req.params.id}`]);for(const u of users){if(u[".id"])await ros.command("/ip/hotspot/user/remove",[`=.id=${u[".id"]}`]);}}catch{}await audit(req.user.sub,"DELETE","card_batch",req.params.id,{deleted:deletable.length});res.json({success:true,batchId:req.params.id,deleted:deletable.length});}catch(e){await client.query("ROLLBACK");res.status(400).json({error:e.message});}finally{client.release();try{await ros?.close();}catch{}}});
app.get("/api/card-batches/:id/csv",auth,async(req,res)=>{try{const r=await pool.query(`SELECT c.username,c.password,c.profile,c.status,c.price,c.price_currency,c.plan_id,c.batch_id,c.created_at,c.sold_at,r.name router_name,p.name plan_name FROM cards c JOIN routers r ON r.id=c.router_id LEFT JOIN hotspot_plans p ON p.id=c.plan_id WHERE c.batch_id=$1 AND r.user_id=$2 ORDER BY c.created_at`,[req.params.id,req.user.sub]);if(!r.rowCount)return res.status(404).json({error:"BATCH_NOT_FOUND"});const esc=v=>`"${String(v??"").replaceAll('"','""')}"`;const lines=["username,password,profile,status,price,currency,plan_id,batch_id,created_at,sold_at,router,plan"];for(const x of r.rows)lines.push([x.username,decPass(x.password),x.profile,x.status,x.price,x.price_currency,x.plan_id,x.batch_id,x.created_at?.toISOString?.()||x.created_at,x.sold_at?.toISOString?.()||x.sold_at,x.router_name,x.plan_name].map(esc).join(","));res.setHeader("Content-Type","text/csv; charset=utf-8");res.setHeader("Content-Disposition",`attachment; filename="micromax-batch-${req.params.id}.csv"`);res.send("\uFEFF"+lines.join("\n"));}catch(e){res.status(500).json({error:"BATCH_CSV_FAILED",detail:e.message});}});

const CARD_REPORT_COLUMNS = [
  ["username", "Username"], ["profile", "Profile"], ["status", "Status"],
  ["price", "Price"], ["price_currency", "Currency"], ["batch_id", "Batch ID"],
  ["created_at", "Created At"], ["sold_at", "Sold At"], ["router_name", "Router"], ["plan_name", "Plan"]
];
async function cardReportRows(req) {
  const params = [req.user.sub];
  const filters = ["r.user_id=$1"];
  const routerId = String(req.query.routerId || "").trim();
  const status = String(req.query.status || "").trim().toLowerCase();
  if (routerId) { params.push(routerId); filters.push(`c.router_id=$${params.length}`); }
  if (["available", "sold", "used", "expired", "disabled"].includes(status)) { params.push(status); filters.push(`c.status=$${params.length}`); }
  const result = await pool.query(`SELECT c.username,c.profile,c.status,c.price,c.price_currency,c.batch_id,c.created_at,c.sold_at,r.name router_name,p.name plan_name FROM cards c JOIN routers r ON r.id=c.router_id LEFT JOIN hotspot_plans p ON p.id=c.plan_id WHERE ${filters.join(" AND ")} ORDER BY c.created_at DESC LIMIT 10000`, params);
  return result.rows;
}
function reportCell(value) { return value instanceof Date ? value.toISOString() : String(value ?? ""); }
app.get("/api/reports/cards.csv", auth, async (req, res) => {
  try {
    const rows = await cardReportRows(req);
    const esc = value => `"${reportCell(value).replaceAll('"', '""')}"`;
    const lines = [CARD_REPORT_COLUMNS.map(([, title]) => title).map(esc).join(",")];
    for (const row of rows) lines.push(CARD_REPORT_COLUMNS.map(([key]) => esc(row[key])).join(","));
    res.setHeader("Content-Type", "text/csv; charset=utf-8");
    res.setHeader("Content-Disposition", 'attachment; filename="micromax-card-report.csv"');
    res.send("\uFEFF" + lines.join("\n"));
  } catch (e) { res.status(500).json({ error: "CARD_REPORT_CSV_FAILED", detail: e.message }); }
});
app.get("/api/reports/cards.xlsx", auth, async (req, res) => {
  try {
    const rows = await cardReportRows(req);
    const workbook = new ExcelJS.Workbook();
    workbook.creator = "MICRO-MAX";
    const sheet = workbook.addWorksheet("Card Report");
    sheet.columns = CARD_REPORT_COLUMNS.map(([key, header]) => ({ key, header, width: Math.max(14, header.length + 3) }));
    rows.forEach(row => sheet.addRow(Object.fromEntries(CARD_REPORT_COLUMNS.map(([key]) => [key, reportCell(row[key])]))));
    sheet.getRow(1).font = { bold: true, color: { argb: "FFFFFFFF" } };
    sheet.getRow(1).fill = { type: "pattern", pattern: "solid", fgColor: { argb: "123B66" } };
    sheet.views = [{ state: "frozen", ySplit: 1 }];
    sheet.autoFilter = { from: "A1", to: `${String.fromCharCode(64 + CARD_REPORT_COLUMNS.length)}${rows.length + 1}` };
    const buffer = await workbook.xlsx.writeBuffer();
    res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    res.setHeader("Content-Disposition", 'attachment; filename="micromax-card-report.xlsx"');
    res.send(Buffer.from(buffer));
  } catch (e) { res.status(500).json({ error: "CARD_REPORT_XLSX_FAILED", detail: e.message }); }
});
app.get("/api/reports/cards.pdf", auth, async (req, res) => {
  try {
    const rows = await cardReportRows(req);
    res.setHeader("Content-Type", "application/pdf");
    res.setHeader("Content-Disposition", 'attachment; filename="micromax-card-report.pdf"');
    const doc = new PDFDocument({ margin: 42, size: "A4" });
    doc.pipe(res);
    doc.fontSize(20).fillColor("#123B66").text("MICRO-MAX - Card Report");
    doc.fontSize(9).fillColor("#64748B").text(`Generated: ${new Date().toISOString()}   |   Rows: ${rows.length}`);
    doc.moveDown();
    rows.forEach((row, index) => {
      const line = `${index + 1}. ${row.username} | ${row.profile || "-"} | ${row.status} | ${row.price} ${row.price_currency || ""} | ${row.router_name || "-"} | ${reportCell(row.created_at).slice(0, 10)}`;
      doc.fontSize(9).fillColor("#1E293B").text(line, { lineGap: 3 });
      if (doc.y > 760) doc.addPage();
    });
    doc.end();
  } catch (e) { res.status(500).json({ error: "CARD_REPORT_PDF_FAILED", detail: e.message }); }
});
app.post("/api/sales",auth,async(req,res)=>{const {cardId,paymentMethod="cash",reference=null}=req.body;const requestedAmount=req.body.amount; if(!cardId)return res.status(400).json({error:"CARD_REQUIRED"});if(paymentMethod!=="cash" && process.env.ENABLE_ONLINE_PAYMENTS!=="true")return res.status(403).json({error:"ONLINE_PAYMENTS_DISABLED"});const client=await pool.connect();try{await client.query("BEGIN");const c=await client.query("SELECT c.*,hp.duration_minutes profile_duration_minutes FROM cards c JOIN routers r ON r.id=c.router_id LEFT JOIN hotspot_profiles hp ON hp.router_id=c.router_id AND hp.name=c.profile WHERE c.id=$1 AND r.user_id=$2 FOR UPDATE",[cardId,req.user.sub]);if(!c.rowCount||c.rows[0].status!=="available")throw Error("CARD_NOT_AVAILABLE");const card=c.rows[0];const planPrice=Number(card.price||0);const numericAmount=planPrice;if(!Number.isFinite(numericAmount)||numericAmount<=0)return res.status(400).json({error:"PLAN_PRICE_NOT_SET",plan:card.plan_name||card.profile});const saleId=crypto.randomUUID();const finalCash=paymentMethod==="cash";await client.query("UPDATE cards SET status='sold',sold_at=now() WHERE id=$1",[cardId]);await client.query("INSERT INTO sales(id,card_id,seller_id,amount,payment_method,payment_status,reference) VALUES($1,$2,$3,$4,$5,$6,$7)",[saleId,cardId,req.user.sub,numericAmount,paymentMethod,finalCash?"success":"pending",reference]);await client.query("COMMIT");await audit(req.user.sub,"CREATE","sale",saleId,{cardId,amount:numericAmount,paymentMethod,paymentStatus:finalCash?"success":"pending",profile:card.profile,plan:card.plan_name||null,profileDurationMinutes:Number(card.profile_duration_minutes||0)});res.status(201).json({saleId,status:finalCash?"success":"pending",amount:numericAmount,profile:card.profile,durationMinutes:Number(card.profile_duration_minutes||0),card:finalCash?{username:card.username,password:decPass(card.password),profile:card.profile,price:numericAmount}:undefined});}catch(e){await client.query("ROLLBACK");res.status(400).json({error:e.message});}finally{client.release();}});
app.get("/api/sales",auth,async(req,res)=>{const r=await pool.query("SELECT s.*,c.username card_username FROM sales s LEFT JOIN cards c ON c.id=s.card_id WHERE s.seller_id=$1 ORDER BY s.created_at DESC LIMIT 1000",[req.user.sub]);res.json({sales:r.rows});});
app.post("/api/payments/intents",auth,async(req,res)=>{
  const { saleId, provider="cash" } = req.body;
  if(!saleId||!provider)return res.status(400).json({error:"INVALID_INPUT"});
  const sale=await pool.query("SELECT s.*,c.username card_username FROM sales s LEFT JOIN cards c ON c.id=s.card_id WHERE s.id=$1 AND s.seller_id=$2",[saleId,req.user.sub]);
  if(!sale.rowCount)return res.status(404).json({error:"SALE_NOT_FOUND"});
  const s=sale.rows[0];
  if(s.payment_status!=="pending")return res.status(409).json({error:"SALE_ALREADY_FINALIZED",status:s.payment_status});
  const id=crypto.randomUUID();

  if(provider === "cash"){
    await pool.query("INSERT INTO payments(id,sale_id,provider,status,amount) VALUES($1,$2,$3,'success',$4)",[id,saleId,provider,s.amount]);
    await pool.query("UPDATE sales SET payment_status='success' WHERE id=$1",[saleId]);
    await audit(req.user.sub,"UPDATE","payment",id,{status:"success",provider});
    return res.status(201).json({paymentId:id,status:"success",provider,card:{username:s.card_username,password:(await pool.query("SELECT password FROM cards WHERE id=$1",[s.card_id])).rows[0]?.password?decPass((await pool.query("SELECT password FROM cards WHERE id=$1",[s.card_id])).rows[0].password):null}});
  }

  if(provider !== "binance_pay"){
    await pool.query("INSERT INTO payments(id,sale_id,provider,status,amount) VALUES($1,$2,$3,'pending',$4)",[id,saleId,provider,s.amount]);
    return res.status(201).json({paymentId:id,status:"pending",provider,notice:"هذا المزود ما زال يحتاج تكامل API وWebhook رسمي."});
  }

  if(!binancePayConfigured()){await pool.query("UPDATE cards SET status='available',sold_at=NULL WHERE id=(SELECT card_id FROM sales WHERE id=$1) AND status='sold'",[saleId]);return res.status(503).json({error:"BINANCE_PAY_NOT_CONFIGURED",message:"أضف Binance Pay Certificate SN + Secret Key في بيئة السيرفر، وليس داخل Android."});}
  const merchantTradeNo=`MM${Date.now()}${crypto.randomBytes(4).toString("hex").toUpperCase()}`.slice(0,32);
  try{
    const created=await createBinancePayOrder({merchantTradeNo,amount:s.amount,currency:process.env.BINANCE_PAY_CURRENCY||"USDT",referenceGoodsId:s.card_id||saleId,goodsName:`MICRO-MAX Card ${s.card_username||""}`.trim(),goodsDetail:"Hotspot access card"});
    const d=created.data||{};
    await pool.query("INSERT INTO payments(id,sale_id,provider,status,external_reference,amount,raw) VALUES($1,$2,'binance_pay','pending',$3,$4,$5)",[id,saleId,merchantTradeNo,s.amount,JSON.stringify(created)]);
    await audit(req.user.sub,"CREATE","payment",id,{provider:"binance_pay",merchantTradeNo});
    return res.status(201).json({paymentId:id,status:"pending",provider:"binance_pay",merchantTradeNo,prepayId:d.prepayId||null,checkoutUrl:d.checkoutUrl||null,qrContent:d.qrContent||null,qrcodeLink:d.qrcodeLink||null,deeplink:d.deeplink||null,universalUrl:d.universalUrl||null});
  }catch(e){
    await pool.query("INSERT INTO payments(id,sale_id,provider,status,external_reference,amount,raw) VALUES($1,$2,'binance_pay','failed',$3,$4,$5)",[id,saleId,merchantTradeNo,s.amount,JSON.stringify(e.providerResponse||{error:e.message})]);
    await pool.query("UPDATE cards SET status='available',sold_at=NULL WHERE id=(SELECT card_id FROM sales WHERE id=$1) AND status='sold'",[saleId]);
    await pool.query("UPDATE sales SET payment_status='failed' WHERE id=$1",[saleId]);
    return res.status(502).json({error:"BINANCE_PAY_CREATE_FAILED",detail:e.message});
  }
});

app.post("/api/payments/:id/sync",auth,async(req,res)=>{
  const p=await pool.query("SELECT p.*,s.seller_id,s.amount sale_amount FROM payments p JOIN sales s ON s.id=p.sale_id WHERE p.id=$1 AND s.seller_id=$2",[req.params.id,req.user.sub]);
  if(!p.rowCount)return res.status(404).json({error:"PAYMENT_NOT_FOUND"});
  const payment=p.rows[0];
  if(payment.provider!=="binance_pay")return res.status(400).json({error:"SYNC_NOT_SUPPORTED_FOR_PROVIDER"});
  try{
    const q=await queryBinancePayOrder({merchantTradeNo:payment.external_reference});
    const d=q.data||{};
    const paid=d.status==="PAID" && Number(d.totalFee)===Number(payment.sale_amount) && String(d.currency||"").toUpperCase()===(process.env.BINANCE_PAY_CURRENCY||"USDT").toUpperCase();
    let status=paid?"success":(["CANCELED","ERROR","EXPIRED","REFUNDED"].includes(d.status)?"failed":"pending");
    await pool.query("UPDATE payments SET status=$1,raw=$2,updated_at=now() WHERE id=$3",[status,JSON.stringify(q),payment.id]);
    await pool.query("UPDATE sales SET payment_status=$1 WHERE id=$2",[status,payment.sale_id]);
    if(status==="failed")await pool.query("UPDATE cards SET status='available',sold_at=NULL WHERE id=(SELECT card_id FROM sales WHERE id=$1) AND status='sold'",[payment.sale_id]);
    let card=null;
    if(status==="success"){
      const cr=await pool.query("SELECT c.username,password,profile FROM cards c JOIN sales s ON s.card_id=c.id WHERE s.id=$1",[payment.sale_id]);
      card=cr.rows[0]?{...cr.rows[0],password:decPass(cr.rows[0].password)}:null;
      await audit(req.user.sub,"UPDATE","payment",payment.id,{status,binanceStatus:d.status,transactionId:d.transactionId||null});
    }
    return res.json({paymentId:payment.id,status,binanceStatus:d.status,transactionId:d.transactionId||null,totalFee:d.totalFee||null,currency:d.currency||null,card});
  }catch(e){return res.status(502).json({error:"BINANCE_PAY_QUERY_FAILED",detail:e.message});}
});

app.post("/api/webhooks/binance-pay",async(req,res)=>{
  try{
    const raw=Buffer.isBuffer(req.rawBody)?req.rawBody.toString("utf8"):JSON.stringify(req.body||{});
    const headerSignature=req.headers["binancepay-signature"] || req.headers["binance-pay-signature"];
    const headerTimestamp=req.headers["binancepay-timestamp"] || req.headers["binance-pay-timestamp"];
    const headerNonce=req.headers["binancepay-nonce"] || req.headers["binance-pay-nonce"];
    if(!verifyBinancePayNotification({body:raw,timestamp:headerTimestamp,nonce:headerNonce,signature:headerSignature})){
      return res.status(401).json({returnCode:"FAIL",returnMessage:"INVALID_SIGNATURE"});
    }
    const payload=req.body||{};
    if(payload.bizType!=="PAY")return res.status(200).json({returnCode:"SUCCESS",returnMessage:null});
    const data=typeof payload.data==="string"?JSON.parse(payload.data):payload.data||{};
    const merchantTradeNo=data.merchantTradeNo;
    if(!merchantTradeNo)return res.status(200).json({returnCode:"SUCCESS",returnMessage:null});
    const p=await pool.query("SELECT p.*,s.amount sale_amount,s.card_id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE p.provider='binance_pay' AND p.external_reference=$1",[merchantTradeNo]);
    if(!p.rowCount)return res.status(200).json({returnCode:"SUCCESS",returnMessage:null});
    // Never trust the notification itself for fulfillment. Re-query Binance and verify amount/currency.
    const q=await queryBinancePayOrder({merchantTradeNo});
    const d=q.data||{};
    const row=p.rows[0];
    const paid=d.status==="PAID" && Number(d.totalFee)===Number(row.sale_amount) && String(d.currency||"").toUpperCase()===(process.env.BINANCE_PAY_CURRENCY||"USDT").toUpperCase();
    const status=paid?"success":(["CANCELED","ERROR","EXPIRED","REFUNDED"].includes(d.status)?"failed":"pending");
    await pool.query("UPDATE payments SET status=$1,raw=$2,updated_at=now() WHERE id=$3",[status,JSON.stringify(q),row.id]);
    await pool.query("UPDATE sales SET payment_status=$1 WHERE id=$2",[status,row.sale_id]);
    if(status==="failed")await pool.query("UPDATE cards SET status='available',sold_at=NULL WHERE id=$1 AND status='sold'",[row.card_id]);
    return res.status(200).json({returnCode:"SUCCESS",returnMessage:null});
  }catch(e){
    console.error("Binance Pay webhook error",e);
    return res.status(200).json({returnCode:"FAIL",returnMessage:"Temporary processing error"});
  }
});

app.get("/api/binance-pay/status",auth,role("admin"),(req,res)=>res.json({configured:binancePayConfigured(),currency:process.env.BINANCE_PAY_CURRENCY||"USDT",terminalType:process.env.BINANCE_PAY_TERMINAL_TYPE||"APP"}));
app.post("/api/payments/:id/status",auth,role("admin"),async(req,res)=>{const status=String(req.body.status||"pending");if(!["pending","success","failed","refunded"].includes(status))return res.status(400).json({error:"INVALID_STATUS"});const p=await pool.query("UPDATE payments SET status=$1,updated_at=now() WHERE id=$2 RETURNING *",[status,req.params.id]);if(!p.rowCount)return res.status(404).json({error:"PAYMENT_NOT_FOUND"});await pool.query("UPDATE sales SET payment_status=$1 WHERE id=$2",[status,p.rows[0].sale_id]);await audit(req.user.sub,"UPDATE","payment",req.params.id,{status});res.json(p.rows[0]);});

app.get("/api/reports/sales",auth,async(req,res)=>{const r=await pool.query("SELECT date_trunc('day',created_at) day,count(*) sales,sum(amount) revenue,count(*) FILTER(WHERE payment_status='success') paid,count(*) FILTER(WHERE payment_status='failed') failed FROM sales WHERE seller_id=$1 GROUP BY 1 ORDER BY 1 DESC LIMIT 90",[req.user.sub]);res.json({rows:r.rows});});
app.get("/api/reports/sales.csv",auth,async(req,res)=>{const r=await pool.query("SELECT created_at,amount,payment_method,payment_status,reference FROM sales WHERE seller_id=$1 ORDER BY created_at DESC",[req.user.sub]);const lines=["created_at,amount,payment_method,payment_status,reference",...r.rows.map(x=>[x.created_at.toISOString(),x.amount,x.payment_method,x.payment_status,x.reference||""].map(v=>`"${String(v).replaceAll('"','""')}"`).join(","))];res.type("text/csv").send(lines.join("\n"));});
app.get("/api/reports/sales.pdf",auth,async(req,res)=>{const r=await pool.query("SELECT created_at,amount,payment_method,payment_status FROM sales WHERE seller_id=$1 ORDER BY created_at DESC LIMIT 200",[req.user.sub]);res.type("application/pdf");const doc=new PDFDocument({margin:40});doc.pipe(res);doc.fontSize(20).text("MICRO-MAX - Sales Report");doc.moveDown();for(const x of r.rows)doc.fontSize(10).text(`${x.created_at.toISOString()} | ${x.amount} | ${x.payment_method} | ${x.payment_status}`);doc.end();});

app.get("/api/audit",auth,role("admin"),async(req,res)=>{const r=await pool.query("SELECT a.*,u.email FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id ORDER BY a.created_at DESC LIMIT 500");res.json({logs:r.rows});});
app.get("/api/users",auth,role("admin"),async(req,res)=>{const r=await pool.query("SELECT id,email,role,created_at FROM users ORDER BY created_at DESC");res.json({users:r.rows});});
app.post("/api/users",auth,role("admin"),async(req,res)=>{const email=String(req.body.email||"").trim().toLowerCase(),password=String(req.body.password||""),roleName=req.body.role==="admin"?"admin":"staff";if(!email||password.length<8)return res.status(400).json({error:"INVALID_INPUT"});const id=crypto.randomUUID();const hash=await bcrypt.hash(password,12);await pool.query("INSERT INTO users(id,email,password_hash,role) VALUES($1,$2,$3,$4)",[id,email,hash,roleName]);await audit(req.user.sub,"CREATE","user",id,{email,role:roleName});res.status(201).json({id,email,role:roleName});});

app.get("/api/notifications",auth,async(req,res)=>{const r=await pool.query("SELECT * FROM notifications WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100",[req.user.sub]);res.json({notifications:r.rows});});

function validateStatusHtml(src){
  const errors=[],warnings=[]; const hasUser=/\$\(username\)/i.test(src); const hasLogout=/\$\(link-logout\)/i.test(src); const hasStatus=/\$\((?:uptime|session-time-left|ip)\)/i.test(src);
  if(!/<html[\s>]/i.test(src)) errors.push("HTML_DOCUMENT_MISSING");
  if(!hasUser) warnings.push("USERNAME_VARIABLE_MISSING");
  if(!hasLogout) warnings.push("LOGOUT_LINK_MISSING");
  if(!hasStatus) warnings.push("STATUS_VARIABLES_MISSING");
  return {valid:errors.length===0,errors,warnings,checks:{hasUser,hasLogout,hasStatus}};
}
function hotspotFileName(kind){ return kind === "status" ? "hotspot/status.html" : "hotspot/login.html"; }
function hotspotBackupPattern(kind){ return kind === "status" ? /^hotspot\/status\.html\.micromax-[A-Za-z0-9_.-]+\.bak$/ : /^hotspot\/login\.html\.micromax-[A-Za-z0-9_.-]+\.bak$/; }
function validateHotspot(kind, content){ return kind === "status" ? validateStatusHtml(content) : validateLoginHtml(content); }

async function readHotspotFile(req,id,kind){
  const r=await getRouterForUser(req,id); const ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});
  try{ await ros.connect(); const file=hotspotFileName(kind); const files=await ros.command("/file/print",[`?name=${file}`]); const f=files[0]||{}; const content=String(f.contents||""); return {router:r,file,content,validation:validateHotspot(kind,content),updatedAt:f.creation_time||f["creation-time"]||null}; } finally { try{await ros.close();}catch{} }
}

app.get("/api/routers/:id/hotspot-status",auth,async(req,res)=>{try{res.json(await readHotspotFile(req,req.params.id,"status"));}catch(e){res.status(502).json({error:"HOTSPOT_STATUS_READ_FAILED",detail:e.message});}});
app.post("/api/routers/:id/hotspot-status/validate",auth,async(req,res)=>{try{await getRouterForUser(req,req.params.id);res.json(validateStatusHtml(String(req.body.content||"")));}catch(e){res.status(400).json({valid:false,error:"HOTSPOT_STATUS_VALIDATE_FAILED",detail:e.message});}});
app.post("/api/routers/:id/hotspot-status/publish",auth,role("admin"),async(req,res)=>{
  let ros; try{const r=await getRouterForUser(req,req.params.id); const content=String(req.body.content||""); if(content.length<100||content.length>500000)return res.status(400).json({error:"INVALID_STATUS_HTML_SIZE"}); const validation=validateStatusHtml(content); if(!validation.valid)return res.status(400).json({error:"STATUS_HTML_INCOMPATIBLE",validation}); ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"}); await ros.connect(); const current=await ros.command("/file/print",["?name=hotspot/status.html"]); const old=String(current[0]?.contents||""); const backupName=`hotspot/status.html.micromax-${new Date().toISOString().replace(/[:.]/g,"-")}.bak`; if(old){try{await ros.command("/file/add",[`=name=${backupName}`,`=contents=${old}`]);}catch{}} const target=current[0]?.[".id"]||current[0]?.id; if(target)await ros.command("/file/set",[`=.id=${target}`,`=contents=${content}`]); else await ros.command("/file/add",["=name=hotspot/status.html",`=contents=${content}`]); await audit(req.user.sub,"PUBLISH","hotspot_status",req.params.id,{backup:backupName,bytes:Buffer.byteLength(content,"utf8")}); res.json({ok:true,file:"hotspot/status.html",backup:old?backupName:null,validation}); }catch(e){res.status(502).json({ok:false,error:"HOTSPOT_STATUS_PUBLISH_FAILED",detail:e.message});} finally{try{await ros?.close();}catch{}}
});
app.get("/api/routers/:id/hotspot-status/backups",auth,async(req,res)=>{let ros;try{const r=await getRouterForUser(req,req.params.id);ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});await ros.connect();const files=await ros.command("/file/print",[]);const backups=files.filter(f=>hotspotBackupPattern("status").test(String(f.name||""))).map(f=>({name:f.name,size:Number(f.size||0),creationTime:f.creation_time||f["creation-time"]||null})).sort((a,b)=>String(b.creationTime).localeCompare(String(a.creationTime)));res.json({backups});}catch(e){res.status(502).json({error:"HOTSPOT_STATUS_BACKUPS_FAILED",detail:e.message});}finally{try{await ros?.close();}catch{}}});
app.post("/api/routers/:id/hotspot-status/restore",auth,role("admin"),async(req,res)=>{let ros;try{const r=await getRouterForUser(req,req.params.id);const backup=String(req.body.backupName||"").trim();if(!hotspotBackupPattern("status").test(backup))return res.status(400).json({error:"INVALID_BACKUP_NAME"});ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});await ros.connect();const b=await ros.command("/file/print",[`?name=${backup}`]);const content=String(b[0]?.contents||"");const validation=validateStatusHtml(content);if(!validation.valid)return res.status(400).json({error:"BACKUP_INCOMPATIBLE",validation});const current=await ros.command("/file/print",["?name=hotspot/status.html"]);const target=current[0]?.[".id"]||current[0]?.id;if(!target)throw Error("STATUS_HTML_NOT_FOUND");await ros.command("/file/set",[`=.id=${target}`,`=contents=${content}`]);await audit(req.user.sub,"RESTORE","hotspot_status",req.params.id,{backup});res.json({ok:true,backup});}catch(e){res.status(502).json({error:"HOTSPOT_STATUS_RESTORE_FAILED",detail:e.message});}finally{try{await ros?.close();}catch{}}});

app.post("/api/routers/:id/hotspot-design/publish",auth,role("admin"),async(req,res)=>{
  const {loginHtml,statusHtml}=req.body||{}; if(typeof loginHtml!=="string"||typeof statusHtml!=="string")return res.status(400).json({error:"LOGIN_AND_STATUS_REQUIRED"});
  const lv=validateLoginHtml(loginHtml), sv=validateStatusHtml(statusHtml); if(!lv.valid||!sv.valid)return res.status(400).json({error:"HOTSPOT_DESIGN_INVALID",login:lv,status:sv});
  try{await getRouterForUser(req,req.params.id); const results={}; for(const [kind,content] of [["login",loginHtml],["status",statusHtml]]){const r=await getRouterForUser(req,req.params.id);const ros=new RouterOS({host:r.host,port:r.port,username:r.username,password:decrypt(r.password_enc),tls:r.tls,allowInsecureTls:process.env.ALLOW_INSECURE_ROUTER_TLS === "true"});try{await ros.connect();const file=hotspotFileName(kind);const current=await ros.command("/file/print",[`?name=${file}`]);const old=String(current[0]?.contents||"");const backupName=`${file}.micromax-${new Date().toISOString().replace(/[:.]/g,"-")}.bak`;if(old){try{await ros.command("/file/add",[`=name=${backupName}`,`=contents=${old}`]);}catch{}}const target=current[0]?.[".id"]||current[0]?.id;if(target)await ros.command("/file/set",[`=.id=${target}`,`=contents=${content}`]);else await ros.command("/file/add",[`=name=${file}`,`=contents=${content}`]);results[kind]={file,backup:old?backupName:null};}finally{try{await ros.close();}catch{}}}await audit(req.user.sub,"PUBLISH","hotspot_design",req.params.id,{loginBytes:Buffer.byteLength(loginHtml),statusBytes:Buffer.byteLength(statusHtml)});res.json({ok:true,results});}catch(e){res.status(502).json({error:"HOTSPOT_DESIGN_PUBLISH_FAILED",detail:e.message});}
});

app.get("/api/themes/:routerId",auth,async(req,res)=>{const r=await pool.query("SELECT * FROM hotspot_themes t JOIN routers r ON r.id=t.router_id WHERE t.router_id=$1 AND r.user_id=$2",[req.params.routerId,req.user.sub]);res.json(r.rows[0]||null);});
app.post("/api/themes/:routerId",auth,async(req,res)=>{try{const owner=await pool.query("SELECT 1 FROM routers WHERE id=$1 AND user_id=$2",[req.params.routerId,req.user.sub]);if(!owner.rowCount)return res.status(404).json({error:"ROUTER_NOT_FOUND"});const {name="Hotspot",logoUrl=null,primaryColor="#0B5FA5",background="#F4F7FB"}=req.body;const id=crypto.randomUUID();const r=await pool.query("INSERT INTO hotspot_themes(id,router_id,name,logo_url,primary_color,background) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(router_id) DO UPDATE SET name=EXCLUDED.name,logo_url=EXCLUDED.logo_url,primary_color=EXCLUDED.primary_color,background=EXCLUDED.background,updated_at=now() RETURNING *",[id,req.params.routerId,String(name).slice(0,100),logoUrl,primaryColor,background]);res.json(r.rows[0]);}catch(e){res.status(400).json({error:"THEME_SAVE_FAILED"});}});

app.get("/api/backup/export",auth,role("admin"),async(req,res)=>{
  try{
    const uid=req.user.sub;
    const data={version:3,createdAt:new Date().toISOString(),tables:{}};
    const scoped={
      users:["SELECT id,email,role,created_at FROM users WHERE id=$1",[uid]],
      routers:["SELECT id,user_id,name,host,port,username,tls,created_at FROM routers WHERE user_id=$1",[uid]],
      cards:["SELECT c.id,c.router_id,c.username,c.password,c.profile,c.price,c.status,c.sold_at,c.created_at,c.plan_id,c.price_currency,c.batch_id,c.qr_content FROM cards c JOIN routers r ON r.id=c.router_id WHERE r.user_id=$1",[uid]],
      sales:["SELECT s.* FROM sales s WHERE s.seller_id=$1",[uid]],
      payments:["SELECT p.* FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.seller_id=$1",[uid]],
      audit_logs:["SELECT * FROM audit_logs WHERE user_id=$1",[uid]],
      notifications:["SELECT * FROM notifications WHERE user_id=$1",[uid]],
      hotspot_themes:["SELECT t.* FROM hotspot_themes t JOIN routers r ON r.id=t.router_id WHERE r.user_id=$1",[uid]],
      app_settings:["SELECT * FROM app_settings WHERE user_id=$1",[uid]],
      store_items:["SELECT * FROM store_items WHERE active=true",[]],
      store_entitlements:["SELECT e.* FROM store_entitlements e WHERE e.user_id=$1",[uid]]
    };
    for(const [table,[sql,params]] of Object.entries(scoped)){const r=await pool.query(sql,params);data.tables[table]=r.rows;}
    res.json(data);
  }catch(e){res.status(500).json({error:"BACKUP_EXPORT_FAILED"});}
});
app.post("/api/backup/restore",auth,role("admin"),async(req,res)=>{
  const tables=req.body?.tables; if(!tables||typeof tables!=="object")return res.status(400).json({error:"INVALID_BACKUP"});
  const client=await pool.connect(); const uid=req.user.sub; const restored={app_settings:0,hotspot_themes:0,store_entitlements:0,cards:0,sales:0}; const skipped=[];
  try{
    await client.query("BEGIN");
    const user=await client.query("SELECT 1 FROM users WHERE id=$1",[uid]); if(!user.rowCount)throw Error("USER_NOT_FOUND");
    for(const row of Array.isArray(tables.app_settings)?tables.app_settings:[]){await client.query("INSERT INTO app_settings(user_id,theme,currency) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET theme=EXCLUDED.theme,currency=EXCLUDED.currency",[uid,String(row.theme||"light"),String(row.currency||"XOF")]);restored.app_settings++;}
    for(const row of Array.isArray(tables.hotspot_themes)?tables.hotspot_themes:[]){const owner=await client.query("SELECT 1 FROM routers WHERE id=$1 AND user_id=$2",[row.router_id,uid]);if(!owner.rowCount){skipped.push({table:"hotspot_themes",id:row.id,reason:"ROUTER_NOT_OWNED"});continue;}await client.query("INSERT INTO hotspot_themes(id,router_id,name,logo_url,primary_color,background,updated_at) VALUES($1,$2,$3,$4,$5,$6,now()) ON CONFLICT(router_id) DO UPDATE SET name=EXCLUDED.name,logo_url=EXCLUDED.logo_url,primary_color=EXCLUDED.primary_color,background=EXCLUDED.background,updated_at=now()",[row.id||crypto.randomUUID(),row.router_id,String(row.name||"Hotspot").slice(0,100),row.logo_url||null,row.primary_color||"#0B5FA5",row.background||"#F4F7FB"]);restored.hotspot_themes++;}
    for(const row of Array.isArray(tables.store_entitlements)?tables.store_entitlements:[]){const item=await client.query("SELECT 1 FROM store_items WHERE id=$1 AND active=true",[row.item_id]);if(!item.rowCount){skipped.push({table:"store_entitlements",id:row.id,reason:"ITEM_NOT_FOUND"});continue;}await client.query("INSERT INTO store_entitlements(id,user_id,item_id) VALUES($1,$2,$3) ON CONFLICT(user_id,item_id) DO NOTHING",[row.id||crypto.randomUUID(),uid,row.item_id]);restored.store_entitlements++;}
    for(const row of Array.isArray(tables.cards)?tables.cards:[]){const owner=await client.query("SELECT 1 FROM routers WHERE id=$1 AND user_id=$2",[row.router_id,uid]);if(!owner.rowCount){skipped.push({table:"cards",id:row.id,reason:"ROUTER_NOT_OWNED"});continue;}await client.query("INSERT INTO cards(id,router_id,username,password,profile,price,status,sold_at,created_at,plan_id,price_currency,batch_id,qr_content) VALUES($1,$2,$3,$4,$5,$6,$7,$8,COALESCE($9,now()),$10,$11,$12,$13) ON CONFLICT(router_id,username) DO UPDATE SET password=EXCLUDED.password,profile=EXCLUDED.profile,price=EXCLUDED.price,status=EXCLUDED.status,sold_at=EXCLUDED.sold_at,plan_id=EXCLUDED.plan_id,price_currency=EXCLUDED.price_currency,batch_id=EXCLUDED.batch_id,qr_content=EXCLUDED.qr_content",[row.id||crypto.randomUUID(),row.router_id,String(row.username||""),String(row.password||""),String(row.profile||"default"),Number(row.price||0),String(row.status||"available"),row.sold_at||null,row.created_at||null,row.plan_id||null,String(row.price_currency||"XOF"),row.batch_id||null,row.qr_content||null]);restored.cards++;}
    for(const row of Array.isArray(tables.sales)?tables.sales:[]){const owner=await client.query("SELECT 1 FROM cards c JOIN routers r ON r.id=c.router_id WHERE c.id=$1 AND r.user_id=$2",[row.card_id,uid]);if(!owner.rowCount){skipped.push({table:"sales",id:row.id,reason:"CARD_NOT_OWNED"});continue;}await client.query("INSERT INTO sales(id,card_id,seller_id,amount,payment_method,payment_status,reference,created_at) VALUES($1,$2,$3,$4,$5,$6,$7,COALESCE($8,now())) ON CONFLICT(id) DO UPDATE SET amount=EXCLUDED.amount,payment_method=EXCLUDED.payment_method,payment_status=EXCLUDED.payment_status,reference=EXCLUDED.reference",[row.id||crypto.randomUUID(),row.card_id,uid,Number(row.amount||0),String(row.payment_method||"cash"),String(row.payment_status||"pending"),row.reference||null,row.created_at||null]);restored.sales++;}
    await client.query("COMMIT"); await audit(uid,"RESTORE","backup",null,{restored,skipped:skipped.length}); res.json({ok:true,restored,skipped,paymentDataNotRestored:true,message:"تمت استعادة بيانات الحساب الآمنة. لم تتم استعادة كلمات مرور الراوتر أو أسرار الدفع."});
  }catch(e){await client.query("ROLLBACK");res.status(400).json({error:"BACKUP_RESTORE_FAILED",detail:e.message});}finally{client.release();}
});

const port=Number(process.env.PORT||8080);init().then(()=>app.listen(port,()=>console.log(`MICRO-MAX API on :${port}`))).catch(e=>{console.error(e);process.exit(1)});
