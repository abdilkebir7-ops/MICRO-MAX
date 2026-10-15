// New-network wizard: builds an ordered RouterOS API plan (pure) + rollback selectors.
// Every created object is tagged so a failed/aborted run can be removed precisely.
export const WIZARD_TAG="micromax-wizard";
const IFACE=/^[A-Za-z0-9_.\-]{1,24}$/;
const NAME=/^[A-Za-z0-9_\-]{1,24}$/;
function ipToInt(ip){const p=String(ip).split(".").map(Number);if(p.length!==4||p.some(x=>!Number.isInteger(x)||x<0||x>255))return null;return ((p[0]*256+p[1])*256+p[2])*256+p[3];}
function intToIp(n){return [n>>>24,(n>>>16)&255,(n>>>8)&255,n&255].join(".");}
function isPrivate(i){return (i>>>24)===10||((i>>>20)===0xAC1)||((i>>>16)===0xC0A8);}
export function validateWizard(o={}){
  const errors=[];
  const lan=String(o.lanInterface||""),wan=String(o.wanInterface||"");
  if(!IFACE.test(lan))errors.push("LAN_INTERFACE_INVALID");
  if(!IFACE.test(wan))errors.push("WAN_INTERFACE_INVALID");
  if(lan&&lan===wan)errors.push("LAN_EQUALS_WAN");
  const m=String(o.lanCidr||"").match(/^(\d+\.\d+\.\d+\.\d+)\/(\d+)$/);
  let ip=null,pre=null;
  if(!m)errors.push("LAN_CIDR_INVALID");else{ip=ipToInt(m[1]);pre=+m[2];
    if(ip==null||pre<16||pre>29)errors.push("LAN_CIDR_INVALID");else if(!isPrivate(ip))errors.push("LAN_NOT_PRIVATE");}
  const hs=String(o.hotspotName||"micromax");if(!NAME.test(hs))errors.push("HOTSPOT_NAME_INVALID");
  const dns=String(o.dns||"1.1.1.1,8.8.8.8").split(",").map(s=>s.trim()).filter(Boolean);
  if(!dns.length||dns.length>4||dns.some(x=>ipToInt(x)==null))errors.push("DNS_INVALID");
  const lease=String(o.leaseTime||"1h");if(!/^\d{1,3}[smhd]$/.test(lease))errors.push("LEASE_INVALID");
  let from=null,to=null,network=null,mask=null;
  if(!errors.length||(ip!=null&&pre!=null&&!errors.includes("LAN_CIDR_INVALID"))){
    mask=pre===32?0xFFFFFFFF:(0xFFFFFFFF<<(32-pre))>>>0;network=(ip&mask)>>>0;const bcast=(network|(~mask>>>0))>>>0;
    from=o.dhcpFrom?ipToInt(o.dhcpFrom):network+10;to=o.dhcpTo?ipToInt(o.dhcpTo):bcast-1;
    if(from==null||to==null||from>to||from<=network||to>=bcast||(from&mask)>>>0!==network||(to&mask)>>>0!==network)errors.push("DHCP_RANGE_INVALID");
    else if(ip>=from&&ip<=to)errors.push("DHCP_RANGE_CONTAINS_GATEWAY");
  }
  return {errors,values:errors.length?null:{lan,wan,ip:intToIp(ip),pre,network:`${intToIp(network)}/${pre}`,from:intToIp(from),to:intToIp(to),dns:dns.join(","),lease,hs,masquerade:o.masquerade!==false}};
}
export function buildSetupPlan(o={}){
  const {errors,values:v}=validateWizard(o);
  if(errors.length)return {ok:false,errors};
  const T=WIZARD_TAG,steps=[];
  const add=(title,path,args,undo)=>steps.push({title,path,args,undo});
  add("عنوان الشبكة (LAN)","/ip/address/add",[`=address=${v.ip}/${v.pre}`,`=interface=${v.lan}`,`=comment=${T}`],{path:"/ip/address/print",key:"comment",value:T,remove:"/ip/address/remove"});
  add("نطاق عناوين DHCP","/ip/pool/add",[`=name=${T}-pool`,`=ranges=${v.from}-${v.to}`,`=comment=${T}`],{path:"/ip/pool/print",key:"name",value:`${T}-pool`,remove:"/ip/pool/remove"});
  add("شبكة DHCP","/ip/dhcp-server/network/add",[`=address=${v.network}`,`=gateway=${v.ip}`,`=dns-server=${v.dns}`,`=comment=${T}`],{path:"/ip/dhcp-server/network/print",key:"comment",value:T,remove:"/ip/dhcp-server/network/remove"});
  add("DHCP server","/ip/dhcp-server/add",[`=name=${T}-dhcp`,`=interface=${v.lan}`,`=address-pool=${T}-pool`,`=lease-time=${v.lease}`,"=disabled=no"],{path:"/ip/dhcp-server/print",key:"name",value:`${T}-dhcp`,remove:"/ip/dhcp-server/remove"});
  if(v.masquerade)add("NAT (masquerade)","/ip/firewall/nat/add",["=chain=srcnat","=action=masquerade",`=out-interface=${v.wan}`,`=comment=${T}`],{path:"/ip/firewall/nat/print",key:"comment",value:T,remove:"/ip/firewall/nat/remove"});
  add("HotSpot profile","/ip/hotspot/profile/add",[`=name=${T}-profile`,`=hotspot-address=${v.ip}`,"=login-by=http-chap,http-pap"],{path:"/ip/hotspot/profile/print",key:"name",value:`${T}-profile`,remove:"/ip/hotspot/profile/remove"});
  add("HotSpot server","/ip/hotspot/add",[`=name=${v.hs}`,`=interface=${v.lan}`,`=address-pool=${T}-pool`,`=profile=${T}-profile`,"=disabled=no"],{path:"/ip/hotspot/print",key:"name",value:v.hs,remove:"/ip/hotspot/remove"});
  return {ok:true,values:v,steps};
}
// Executes a plan with automatic rollback. `ros` needs .command(path,args). Returns a report.
export async function applyPlan(ros,plan,{onBackup}={}){
  const report={backup:null,done:[],failed:null,rolledBack:[],rollbackErrors:[]};
  if(onBackup)report.backup=await onBackup();
  const removeSel=async u=>{
    const rows=await ros.command(u.path,[`?${u.key}=${u.value}`]);
    for(const r of rows)if(r[".id"])await ros.command(u.remove,[`=.id=${r[".id"]}`]);
    return rows.length;
  };
  for(const s of plan.steps){
    try{await ros.command(s.path,s.args);report.done.push(s.title);}
    catch(e){report.failed={step:s.title,error:String(e?.message||e).slice(0,200)};break;}
  }
  if(report.failed){
    for(const s of [...plan.steps].reverse()){
      // roll back executed steps and the failed one (it may have partially applied)
      if(!report.done.includes(s.title)&&s.title!==report.failed.step)continue;
      try{await removeSel(s.undo);report.rolledBack.push(s.title);}catch(e){report.rollbackErrors.push({step:s.title,error:String(e?.message||e).slice(0,200)});}
    }
  }
  return report;
}
