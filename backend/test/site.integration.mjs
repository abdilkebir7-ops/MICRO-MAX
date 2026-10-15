// Integration test: boots the real server against a real Postgres and a mock RouterOS.
// Run with: TEST_DATABASE_URL=postgres://... node test/site.integration.mjs   (skipped when unset)
import test from "node:test"; import assert from "node:assert/strict"; import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url"; import path from "node:path";
import os from "node:os";
import { startMockRouter } from "./helpers/mockros.mjs";
const DB = process.env.TEST_DATABASE_URL;
if (!DB) { console.log("TEST_DATABASE_URL not set — skipping site integration tests"); process.exit(0); }
const PORT = 18000 + Math.floor(Math.random()*1000), BASE = `http://127.0.0.1:${PORT}`;
const root = path.join(path.dirname(fileURLToPath(import.meta.url)),"..");
let child; let mock;
const LAN = Object.values(os.networkInterfaces()).flat().find(i=>i&&i.family==="IPv4"&&!i.internal)?.address;
const j = async (method, url, {token, body, raw}={}) => { const r = await fetch(BASE+url,{method,redirect:"manual",headers:{...(body?{"content-type":"application/json"}:{}),...(token?{authorization:"Bearer "+token}:{})},body:body?JSON.stringify(body):undefined}); if(raw) return r; const t=await r.text(); let d; try{d=JSON.parse(t)}catch{d=t} return {status:r.status,data:d,headers:r.headers}; };
const reg = async (email,extra={}) => j("POST","/api/auth/register",{body:{email,password:"Passw0rd!x",name:"T",...extra}});

test.before(async () => {
  mock = await startMockRouter({ user:"admin", password:"pw", tables:{ "/ip/address":[{".id":"*1",address:"10.0.0.1/24",interface:"ether1",disabled:"false"}], "/ip/hotspot/user":[], "/log":[{".id":"*9",time:"10:00",topics:"system",message:"hello"}] } });
  child = spawn(process.execPath,["src/server.js"],{cwd:root,env:{...process.env,NODE_ENV:"test",PORT:String(PORT),DATABASE_URL:DB,JWT_SECRET:"integration-secret",ROUTER_ENCRYPTION_KEY:"integration-key",ADMIN_SETUP_KEY:"setup-key-123456",ALLOW_PRIVATE_ROUTER_HOSTS:"true",ROUTER_ALLOWED_PORTS:"8728,8729,"+mock.port},stdio:["ignore","pipe","pipe"]});
  child.stderr.on("data",d=>process.stderr.write(d));
  for(let i=0;i<80;i++){ try{ const r=await fetch(BASE+"/health"); if(r.ok) return; }catch{} await new Promise(r=>setTimeout(r,250)); }
  throw new Error("server did not start");
});
test.after(() => { child?.kill(); mock?.close(); });

const tag = Date.now().toString(36);
let admin, user, user2;
test("admin email: password registration needs setup key; key grants admin", async () => {
  let r = await reg("abdilkebir7@gmail.com"); assert.equal(r.status,403); assert.equal(r.data.error,"ADMIN_EMAIL_REQUIRES_GOOGLE_OR_SETUP_KEY");
  r = await reg("abdilkebir7@gmail.com",{setupKey:"wrong-key-123456"}); assert.equal(r.status,403);
  r = await reg("abdilkebir7@gmail.com",{setupKey:"setup-key-123456"}); if(r.status===409){ r = await j("POST","/api/auth/login",{body:{email:"abdilkebir7@gmail.com",password:"Passw0rd!x"}}); }
  assert.ok([200,201].includes(r.status),JSON.stringify(r.data)); assert.equal(r.data.user.role,"admin"); admin=r.data.token;
  const me = await j("GET","/api/me",{token:admin}); assert.equal(me.data.role,"admin");
});
test("regular users stay owners and cannot reach admin APIs", async () => {
  let r = await reg(`u1-${tag}@example.com`); assert.equal(r.status,201); assert.notEqual(r.data.user.role,"admin"); user=r.data; 
  r = await reg(`u2-${tag}@example.com`); user2=r.data;
  for (const [m,u] of [["GET","/api/admin/stats"],["GET","/api/admin/users"],["POST","/api/admin/templates"],["POST","/api/admin/releases"]]) { r=await j(m,u,{token:user.token,body:m==="POST"?{}:undefined}); assert.equal(r.status,403,u); }
  r = await j("GET","/api/admin/stats"); assert.equal(r.status,401);
});
test("public templates: list, preview (CSP sandbox), download, render", async () => {
  let r = await j("GET","/api/site/templates"); assert.equal(r.data.presets.length,12);
  const p = await j("GET","/api/site/templates/preset-aurora/preview",{raw:true}); assert.match(p.headers.get("content-security-policy"),/sandbox/); assert.match(await p.text(),/link-login-only/);
  const d = await j("GET","/api/site/templates/preset-neon/download",{raw:true}); assert.match(d.headers.get("content-disposition"),/login\.html/); const body=await d.text(); assert.match(body,/\$\(error\)/);
  r = await j("GET","/api/site/templates"); assert.ok(r.data.presets.find(x=>x.slug==="preset-neon").downloads>=1);
  r = await j("GET","/api/site/templates/preset-zzz/download"); assert.equal(r.status,404);
  r = await j("POST","/api/site/templates/render",{body:{options:{brand:"Café <b>",preset:"snow",layout:"split",whatsapp:"218930090401",plans:[{name:"يومي",note:"1GB"}]}}}); assert.equal(r.data.validation.valid,true); assert.ok(r.data.html.includes("Café &lt;b&gt;"));
});
let customSlug, customId;
test("admin manages custom templates (validated)", async () => {
  let r = await j("POST","/api/admin/templates",{token:admin,body:{name:"bad",html:"<html></html>"}}); assert.equal(r.status,400); assert.equal(r.data.error,"TEMPLATE_INVALID");
  const html = (await (await j("GET","/api/site/templates/preset-royal/download",{raw:true})).text());
  r = await j("POST","/api/admin/templates",{token:admin,body:{name:"My Tpl",html,category:"مخصص",price:0}}); assert.equal(r.status,201); customSlug=r.data.slug; customId=r.data.id;
  r = await j("GET","/api/site/templates"); assert.ok(r.data.custom.find(x=>x.slug===customSlug));
  r = await j("GET",`/api/site/templates/${customSlug}/download`,{raw:true}); assert.equal(r.status,200);
  r = await j("PATCH",`/api/admin/templates/${customId}`,{token:admin,body:{price:5}}); assert.equal(r.status,200);
  r = await j("GET",`/api/site/templates/${customSlug}/download`); assert.equal(r.status,402);
  r = await j("PATCH",`/api/admin/templates/${customId}`,{token:admin,body:{active:false}}); r = await j("GET",`/api/site/templates/${customSlug}/preview`); assert.equal(r.status,404);
  r = await j("DELETE",`/api/admin/templates/${customId}`,{token:admin}); assert.equal(r.status,200); r = await j("DELETE",`/api/admin/templates/${customId}`,{token:admin}); assert.equal(r.status,404);
});
test("releases + update-check + download redirect", async () => {
  let r = await j("POST","/api/admin/releases",{token:admin,body:{version:"1.0",apkUrl:"http://x/a.apk"}}); assert.equal(r.status,400);
  r = await j("POST","/api/admin/releases",{token:admin,body:{version:"abc",apkUrl:"https://x.io/a.apk"}}); assert.equal(r.status,400);
  const v1=`1.${tag.length}.0`; 
  r = await j("POST","/api/admin/releases",{token:admin,body:{version:"11.3.0",apkUrl:"https://github.com/o/r/releases/download/v11.3.0/micromax.apk",notes:"first",sizeMb:24.5}}); assert.ok([201,409].includes(r.status));
  r = await j("POST","/api/admin/releases",{token:admin,body:{version:"11.10.0",apkUrl:"https://github.com/o/r/releases/download/v11.10.0/micromax.apk",notes:"semver sort",mandatory:true}}); assert.ok([201,409].includes(r.status));
  r = await j("POST","/api/admin/releases",{token:admin,body:{version:"11.10.0",apkUrl:"https://x.io/a.apk"}}); assert.equal(r.status,409);
  r = await j("GET","/api/site/update-check?current=11.9.0"); assert.equal(r.data.updateAvailable,true); assert.equal(r.data.latest.version,"11.10.0"); assert.equal(r.data.mandatory,true);
  r = await j("GET","/api/site/update-check?current=11.10.0"); assert.equal(r.data.updateAvailable,false);
  r = await j("GET","/api/site/releases"); assert.equal(r.data.items[0].version,"11.10.0");
  r = await j("GET","/download/latest",{raw:true}); assert.equal(r.status,302); assert.match(r.headers.get("location"),/v11\.10\.0/);
  r = await j("GET","/download/9.9.9"); assert.equal(r.status,404);
});
test("announcements", async () => {
  let r = await j("POST","/api/admin/announcements",{token:admin,body:{title:"صيانة",body:"غدا",level:"warning"}}); assert.equal(r.status,201); const id=r.data.id;
  r = await j("GET","/api/site/announcements"); assert.ok(r.data.items.find(x=>x.id===id));
  await j("PATCH","/api/admin/announcements/"+id,{token:admin,body:{active:false}}); r = await j("GET","/api/site/announcements"); assert.ok(!r.data.items.find(x=>x.id===id));
});
test("admin stats, users, disable/role changes take effect immediately", async () => {
  let r = await j("GET","/api/admin/stats",{token:admin}); assert.ok(r.data.users>=3); assert.equal(typeof r.data.revenue,"number");
  r = await j("GET","/api/admin/users?q="+encodeURIComponent(`u2-${tag}`),{token:admin}); assert.equal(r.data.items.length,1); const uid=r.data.items[0].id;
  r = await j("GET","/api/me",{token:user2.token}); assert.equal(r.status,200);
  r = await j("PATCH","/api/admin/users/"+uid,{token:admin,body:{disabled:true}}); assert.equal(r.status,200);
  // role/disabled changes are visible immediately (cache invalidated)
  r = await j("GET","/api/me",{token:user2.token}); assert.equal(r.status,403); assert.equal(r.data.error,"ACCOUNT_DISABLED");
  r = await j("POST","/api/auth/login",{body:{email:`u2-${tag}@example.com`,password:"Passw0rd!x"}}); assert.equal(r.status,403);
  r = await j("PATCH","/api/admin/users/"+uid,{token:admin,body:{disabled:false,role:"staff"}}); assert.equal(r.data.role,"staff");
  r = await j("PATCH","/api/admin/users/"+uid,{token:admin,body:{role:"root"}}); assert.equal(r.status,400);
  const me = await j("GET","/api/me",{token:admin}); r = await j("PATCH","/api/admin/users/"+me.data.id,{token:admin,body:{role:"staff"}}); assert.equal(r.status,400);
  r = await j("GET","/api/admin/audit",{token:admin}); assert.ok(r.data.items.length>0);
});
test("Winbox menus: print/add/set/disable/remove via mock RouterOS", async () => {
  const q = await j("POST","/api/routers",{token:user.token,body:{name:"R",host:LAN,port:mock.port,username:"admin",password:"pw"}});
  assert.ok([200,201].includes(q.status),JSON.stringify(q.data)); const rid=q.data.id||q.data.router?.id; assert.ok(rid);
  let r = await j("GET","/api/menus",{token:user.token}); assert.ok(r.data.items.length>=15);
  r = await j("GET",`/api/routers/${rid}/menu/ip-address`,{token:user.token}); assert.equal(r.status,200); assert.equal(r.data.items[0].address,"10.0.0.1/24");
  r = await j("GET",`/api/routers/${rid}/menu/nope`,{token:user.token}); assert.equal(r.status,400); assert.equal(r.data.error,"MENU_NOT_FOUND");
  r = await j("POST",`/api/routers/${rid}/menu/hotspot-users/add`,{token:user.token,body:{values:{name:"c1",password:"c1",profile:"default"}}}); assert.equal(r.status,200);
  r = await j("GET",`/api/routers/${rid}/menu/hotspot-users`,{token:user.token}); assert.equal(r.data.items[0].name,"c1"); const id=r.data.items[0][".id"];
  r = await j("POST",`/api/routers/${rid}/menu/hotspot-users/disable`,{token:user.token,body:{id}}); assert.equal(r.status,200);
  r = await j("GET",`/api/routers/${rid}/menu/hotspot-users`,{token:user.token}); assert.equal(r.data.items[0].disabled,"true");
  r = await j("POST",`/api/routers/${rid}/menu/hotspot-users/set`,{token:user.token,body:{id,values:{comment:"hi"}}}); assert.equal(r.status,200);
  r = await j("POST",`/api/routers/${rid}/menu/hotspot-users/remove`,{token:user.token,body:{id:"*1; /system reset"}}); assert.equal(r.status,400);
  r = await j("POST",`/api/routers/${rid}/menu/hotspot-users/remove`,{token:user.token,body:{id}}); assert.equal(r.status,200); assert.equal(mock.tables["/ip/hotspot/user"].length,0);
  r = await j("POST",`/api/routers/${rid}/menu/log/remove`,{token:user.token,body:{id:"*9"}}); assert.equal(r.status,400); assert.equal(r.data.error,"ACTION_NOT_ALLOWED");
  r = await j("POST",`/api/routers/${rid}/menu/hotspot-users/remove`,{token:user.token,body:{id:"*77"}}); assert.equal(r.status,502);
  // another user's router is not reachable
  r = await j("GET",`/api/routers/${rid}/menu/ip-address`,{token:user2.token}); assert.equal(r.status,404);
  r = await j("GET",`/api/routers/${rid}/menu/ip-address`); assert.equal(r.status,401);
});
test("static site, 404 page, status template, config", async () => {
  let r = await j("GET","/",{raw:true}); assert.equal(r.status,200); assert.match(r.headers.get("content-type"),/html/);
  r = await j("GET","/does-not-exist",{raw:true}); assert.equal(r.status,404);
  r = await j("GET","/api/does-not-exist"); assert.equal(r.status,404);
  r = await j("GET","/api/site/status-template",{raw:true}); assert.match(await r.text(),/<html/i);
  r = await j("GET","/api/site/config"); assert.equal(typeof r.data.registration,"boolean");
  r = await j("GET","/../server.js",{raw:true}); assert.notEqual(r.status,200);
});
test("regression: card batches/dashboard queries work on this Postgres (no min(uuid))", async () => {
  const r = await j("GET","/api/card-batches",{token:user.token}); assert.equal(r.status,200,JSON.stringify(r.data)); assert.ok(Array.isArray(r.data.batches));
});
