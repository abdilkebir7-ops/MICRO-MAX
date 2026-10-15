// Winbox-style menu catalogue: a strict whitelist of RouterOS menus the web console may read/modify.
// Nothing outside this list is reachable through /menu endpoints (the free-form terminal is separate and role-gated).

const A = (...x) => x;
export const MENUS = {
  "interfaces":      { path:"/interface",                 title:"Interfaces",        group:"Interfaces", cols:["name","type","running","disabled","rx-byte","tx-byte","comment"], actions:A("set","enable","disable") },
  "ip-address":      { path:"/ip/address",                title:"IP Addresses",      group:"IP",         cols:["address","network","interface","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "ip-routes":       { path:"/ip/route",                  title:"Routes",            group:"IP",         cols:["dst-address","gateway","distance","active","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "dhcp-leases":     { path:"/ip/dhcp-server/lease",      title:"DHCP Leases",       group:"IP",         cols:["address","mac-address","host-name","status","server","comment"], actions:A("add","set","remove","enable","disable") },
  "arp":             { path:"/ip/arp",                    title:"ARP",               group:"IP",         cols:["address","mac-address","interface","status"], actions:A() },
  "dns-static":      { path:"/ip/dns/static",             title:"DNS Static",        group:"IP",         cols:["name","address","type","ttl","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "firewall-filter": { path:"/ip/firewall/filter",        title:"Firewall Filter",   group:"IP",         cols:["chain","action","protocol","src-address","dst-port","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "firewall-nat":    { path:"/ip/firewall/nat",           title:"Firewall NAT",      group:"IP",         cols:["chain","action","out-interface","src-address","to-addresses","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "address-list":    { path:"/ip/firewall/address-list",  title:"Address Lists",     group:"IP",         cols:["list","address","timeout","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "queues":          { path:"/queue/simple",              title:"Simple Queues",     group:"Queues",     cols:["name","target","max-limit","rate","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "hotspot-users":   { path:"/ip/hotspot/user",           title:"Hotspot Users",     group:"Hotspot",    cols:["name","profile","uptime","limit-uptime","bytes-in","bytes-out","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "hotspot-active":  { path:"/ip/hotspot/active",         title:"Hotspot Active",    group:"Hotspot",    cols:["user","address","mac-address","uptime","bytes-in","bytes-out"], actions:A("remove") },
  "hotspot-profiles":{ path:"/ip/hotspot/user/profile",   title:"User Profiles",     group:"Hotspot",    cols:["name","rate-limit","shared-users","session-timeout","idle-timeout"], actions:A("add","set","remove") },
  "ppp-secrets":     { path:"/ppp/secret",                title:"PPP Secrets",       group:"PPP",        cols:["name","service","profile","remote-address","disabled","comment"], actions:A("add","set","remove","enable","disable") },
  "ppp-active":      { path:"/ppp/active",                title:"PPP Active",        group:"PPP",        cols:["name","service","address","uptime","caller-id"], actions:A("remove") },
  "scheduler":       { path:"/system/scheduler",          title:"Scheduler",         group:"System",     cols:["name","start-time","interval","run-count","disabled","comment"], actions:A("enable","disable","remove") },
  "log":             { path:"/log",                       title:"Log",               group:"System",     cols:["time","topics","message"], actions:A(), limit:300 },
  "resource":        { path:"/system/resource",           title:"Resources",         group:"System",     cols:["uptime","version","board-name","cpu-load","free-memory","total-memory","free-hdd-space","platform"], actions:A(), single:true },
  "identity":        { path:"/system/identity",           title:"Identity",          group:"System",     cols:["name"], actions:A("set"), single:true, noId:true }
};

export function menuList(){ return Object.entries(MENUS).map(([key,m])=>({key,title:m.title,group:m.group,cols:m.cols,actions:m.actions,single:!!m.single})); }

const KEY_RE = /^[a-zA-Z][a-zA-Z0-9.-]{0,40}$/;
const ID_RE = /^\*[0-9A-Fa-f]{1,8}$/;
const BAD_VAL = /[\u0000-\u001f\u007f]/;
const BLOCKED_KEYS = new Set([".proplist",".query","numbers",".id"]);

export class MenuError extends Error { constructor(code,detail){ super(code); this.code=code; this.detail=detail; } }

export function resolveMenu(key){ const m=MENUS[key]; if(!m) throw new MenuError("MENU_NOT_FOUND"); return m; }

export function sanitizeValues(values){
  if(values==null) return [];
  if(typeof values!=="object"||Array.isArray(values)) throw new MenuError("INVALID_VALUES");
  const out=[]; const entries=Object.entries(values);
  if(entries.length>30) throw new MenuError("TOO_MANY_VALUES");
  for(const [k,v] of entries){
    if(!KEY_RE.test(k)||BLOCKED_KEYS.has(k)) throw new MenuError("INVALID_KEY",k);
    if(v===null||v===undefined) continue;
    if(typeof v==="object") throw new MenuError("INVALID_VALUE",k);
    const s=String(v); if(s.length>500||BAD_VAL.test(s)) throw new MenuError("INVALID_VALUE",k);
    out.push(`=${k}=${s}`);
  }
  return out;
}

// Returns { path, args } for a RouterOS API command, or throws MenuError.
export function buildAction(menuKey, action, id, values){
  const m = resolveMenu(menuKey);
  if(!m.actions.includes(action)) throw new MenuError("ACTION_NOT_ALLOWED");
  const vals = sanitizeValues(values);
  if(action==="add"){ if(!vals.length) throw new MenuError("VALUES_REQUIRED"); return { path:`${m.path}/add`, args:vals }; }
  if(m.noId && action==="set") { if(!vals.length) throw new MenuError("VALUES_REQUIRED"); return { path:`${m.path}/set`, args:vals }; }
  if(!ID_RE.test(String(id||""))) throw new MenuError("INVALID_ID");
  const idArg = `=.id=${id}`;
  if(action==="set"){ if(!vals.length) throw new MenuError("VALUES_REQUIRED"); return { path:`${m.path}/set`, args:[idArg,...vals] }; }
  return { path:`${m.path}/${action}`, args:[idArg] }; // remove | enable | disable
}

export function buildPrint(menuKey){
  const m = resolveMenu(menuKey);
  return { path:`${m.path}/print`, args:[], limit:m.limit||2000 };
}

// Trim an item to its useful columns (plus .id and a few flags) so payloads stay small.
export function shapeItems(menuKey, items){
  const m = resolveMenu(menuKey); const keep=new Set([".id","dynamic","invalid","comment",...m.cols]);
  let list = Array.isArray(items)?items:[];
  if(m.limit && list.length>m.limit) list = list.slice(-m.limit);
  return list.map(it=>{ const o={}; for(const k of Object.keys(it)) if(keep.has(k)) o[k]=it[k]; return o; });
}
