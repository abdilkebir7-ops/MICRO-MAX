// Router troubleshooter: 20 read-only checks over data already fetched from RouterOS.
// Pure function (no I/O). Each issue may carry a SAFE auto-fix { commands, undo }.
// Fix commands are generated here, never accepted from the client.
const num=v=>{const n=Number(v);return Number.isFinite(n)?n:null;};
const yes=v=>v===true||v==="true"||v==="yes";
const arr=v=>Array.isArray(v)?v:[];
export function parseTimeoutSeconds(s){
  if(s==null||s==="")return null;
  const m=String(s).match(/^(?:(\d+)w)?(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$/);
  if(!m||!m[0])return null;
  return (+m[1]||0)*604800+(+m[2]||0)*86400+(+m[3]||0)*3600+(+m[4]||0)*60+(+m[5]||0);
}
function ipToInt(ip){const p=String(ip).split(".").map(Number);if(p.length!==4||p.some(x=>!(x>=0&&x<=255)))return null;return ((p[0]*256+p[1])*256+p[2])*256+p[3];}
function poolSize(ranges){let n=0;for(const part of String(ranges||"").split(",")){const [a,b]=part.trim().split("-");const x=ipToInt(a),y=ipToInt(b||a);if(x!=null&&y!=null&&y>=x)n+=y-x+1;}return n;}
function networkOf(addr){const [ip,p]=String(addr||"").split("/");const i=ipToInt(ip),pre=num(p);if(i==null||pre==null||pre<1||pre>32)return null;const mask=pre===32?0xFFFFFFFF:(0xFFFFFFFF<<(32-pre))>>>0;return `${(i&mask)>>>0}/${pre}`;}
const sevRank={critical:0,high:1,medium:2,low:3,info:4};

export function diagnose(d={}){
  const issues=[];let checked=0,unknown=0;
  const run=(id,needs,fn)=>{
    if(needs.some(k=>d[k]==null)){unknown++;return;}
    checked++;
    try{const r=fn();if(r)issues.push({id,...r});}catch{unknown++;checked--;}
  };
  const resource=d.resource||{};
  const hsIfaces=arr(d.hotspotServers).map(h=>h.interface).filter(Boolean);

  run("no-default-route",["routes"],()=>{
    const ok=arr(d.routes).some(r=>r["dst-address"]==="0.0.0.0/0"&&!yes(r.disabled)&&!yes(r.inactive));
    return ok?null:{severity:"critical",title:"لا يوجد مسار افتراضي (Default Route)",detail:"الراوتر لا يعرف كيف يصل للإنترنت. تحقق من WAN/DHCP client أو أضف gateway.",fix:null};
  });
  run("no-dns",["dns"],()=>{
    const has=String(d.dns.servers||"").trim()||String(d.dns["dynamic-servers"]||"").trim();
    return has?null:{severity:"high",title:"لا توجد خوادم DNS",detail:"العملاء لن يفتحوا المواقع بالأسماء.",
      fix:{commands:[["/ip/dns/set",["=servers=1.1.1.1,8.8.8.8"]]],undo:[["/ip/dns/set",["=servers="]]]}};
  });
  run("dns-remote-off",["dns"],()=>yes(d.dns["allow-remote-requests"])?null:{severity:"high",title:"DNS لا يستقبل طلبات العملاء",detail:"فعّل allow-remote-requests ليستعمل العملاء الراوتر كـ DNS.",
    fix:{commands:[["/ip/dns/set",["=allow-remote-requests=yes"]]],undo:[["/ip/dns/set",["=allow-remote-requests=no"]]]}});
  run("no-nat",["nat"],()=>arr(d.nat).some(r=>r.chain==="srcnat"&&r.action==="masquerade"&&!yes(r.disabled))?null:{severity:"critical",title:"لا توجد قاعدة NAT (masquerade)",detail:"العملاء لن يخرجوا للإنترنت. أضف masquerade على واجهة WAN (أداة التجهيز تفعل ذلك).",fix:null});
  run("hotspot-none",["hotspotServers"],()=>arr(d.hotspotServers).length?null:{severity:"high",title:"لا يوجد HotSpot server",detail:"لم يُعدّ HotSpot بعد. استخدم معالج تجهيز الشبكة.",fix:null});
  run("hotspot-disabled",["hotspotServers"],()=>{
    const off=arr(d.hotspotServers).filter(h=>yes(h.disabled)&&h[".id"]);
    return off.length?{severity:"high",title:"HotSpot server معطّل",detail:off.map(h=>h.name).join("، "),
      fix:{commands:off.map(h=>["/ip/hotspot/enable",[`=numbers=${h[".id"]}`]]),undo:off.map(h=>["/ip/hotspot/disable",[`=numbers=${h[".id"]}`]])}}:null;
  });
  run("hotspot-iface-down",["hotspotServers","interfaces"],()=>{
    const down=hsIfaces.filter(n=>{const i=arr(d.interfaces).find(x=>x.name===n);return i&&(yes(i.disabled)||(i.running!=null&&!yes(i.running)));});
    return down.length?{severity:"high",title:"واجهة HotSpot لا تعمل",detail:down.join("، ")+" — تحقق من الكبل/الواجهة.",fix:null}:null;
  });
  run("dhcp-missing",["hotspotServers","dhcpServers"],()=>{
    const miss=hsIfaces.filter(n=>!arr(d.dhcpServers).some(s=>s.interface===n));
    return miss.length?{severity:"high",title:"لا يوجد DHCP على واجهة HotSpot",detail:miss.join("، "),fix:null}:null;
  });
  run("dhcp-disabled",["dhcpServers"],()=>{
    const off=arr(d.dhcpServers).filter(s=>yes(s.disabled)&&s[".id"]);
    return off.length?{severity:"high",title:"DHCP server معطّل",detail:off.map(s=>s.name).join("، "),
      fix:{commands:off.map(s=>["/ip/dhcp-server/enable",[`=numbers=${s[".id"]}`]]),undo:off.map(s=>["/ip/dhcp-server/disable",[`=numbers=${s[".id"]}`]])}}:null;
  });
  run("pool-exhausted",["dhcpServers","pools","leases"],()=>{
    const bad=[];
    for(const s of arr(d.dhcpServers)){
      const pool=arr(d.pools).find(p=>p.name===s["address-pool"]);if(!pool)continue;
      const size=poolSize(pool.ranges);if(!size)continue;
      const used=arr(d.leases).filter(l=>l.server===s.name).length;
      if(used/size>=0.9)bad.push(`${s.name}: ${used}/${size}`);
    }
    return bad.length?{severity:"high",title:"نطاق عناوين DHCP يوشك على الامتلاء",detail:bad.join("، ")+" — وسّع الـ Pool أو قلّل مدة الإيجار.",fix:null}:null;
  });
  run("lease-short",["dhcpServers"],()=>{
    const short=arr(d.dhcpServers).filter(s=>{const t=parseTimeoutSeconds(s["lease-time"]);return t!=null&&t<600;});
    return short.length?{severity:"medium",title:"مدة إيجار DHCP قصيرة جداً",detail:short.map(s=>`${s.name} (${s["lease-time"]})`).join("، "),fix:null}:null;
  });
  run("cpu-high",["resource"],()=>{const c=num(resource["cpu-load"]);return c!=null&&c>=85?{severity:"medium",title:`حمل المعالج مرتفع (${c}%)`,detail:"راجع Queues/Firewall الثقيلة أو ارفع جهاز الراوتر.",fix:null}:null;});
  run("mem-low",["resource"],()=>{const t=num(resource["total-memory"]),f=num(resource["free-memory"]);return t&&f!=null&&f/t<0.1?{severity:"medium",title:"الذاكرة منخفضة",detail:`المتاح ${Math.round(f/t*100)}% فقط.`,fix:null}:null;});
  run("disk-low",["resource"],()=>{const t=num(resource["total-hdd-space"]),f=num(resource["free-hdd-space"]);return t&&f!=null&&f/t<0.1?{severity:"medium",title:"مساحة التخزين منخفضة",detail:`المتاح ${Math.round(f/t*100)}% فقط. احذف الملفات/النسخ القديمة.`,fix:null}:null;});
  run("clock-wrong",["clock"],()=>{
    const y=num(String(d.clock.date||"").match(/(\d{4})/)?.[1]);
    return y!=null&&y<2024?{severity:"medium",title:"ساعة الراوتر غير صحيحة",detail:`التاريخ الحالي ${d.clock.date}. يؤثر على صلاحية الكروت والتقارير.`,
      fix:{commands:[["/system/ntp/client/set",["=enabled=yes","=servers=pool.ntp.org"]]],undo:[["/system/ntp/client/set",["=enabled=no"]]]}}:null;
  });
  run("insecure-services",["services"],()=>{
    const bad=arr(d.services).filter(s=>["telnet","ftp"].includes(s.name)&&!yes(s.disabled)&&s[".id"]);
    return bad.length?{severity:"medium",title:"خدمات غير آمنة مفعّلة (Telnet/FTP)",detail:bad.map(s=>s.name).join("، "),
      fix:{commands:bad.map(s=>["/ip/service/disable",[`=numbers=${s[".id"]}`]]),undo:bad.map(s=>["/ip/service/enable",[`=numbers=${s[".id"]}`]])}}:null;
  });
  run("login-missing",["files"],()=>arr(d.files).some(f=>/(^|\/)hotspot\/login\.html$/.test(String(f.name||"")))?null:{severity:"medium",title:"صفحة login.html غير موجودة",detail:"انشر قالب login من «قوالب Login» في التطبيق.",fix:null});
  run("queue-no-target",["queues"],()=>{
    const bad=arr(d.queues).filter(q=>!String(q.target||"").trim());
    return bad.length?{severity:"low",title:"Queues بدون target",detail:bad.map(q=>q.name).join("، "),fix:null}:null;
  });
  run("iface-disabled",["interfaces"],()=>{
    const off=arr(d.interfaces).filter(i=>yes(i.disabled)&&String(i.type||"ether")==="ether");
    return off.length?{severity:"info",title:"واجهات Ethernet معطّلة",detail:off.map(i=>i.name).join("، "),fix:null}:null;
  });
  run("ip-overlap",["ipAddresses"],()=>{
    const seen=new Map(),dup=[];
    for(const a of arr(d.ipAddresses)){if(yes(a.disabled))continue;const n=networkOf(a.address);if(!n)continue;
      if(seen.has(n)&&seen.get(n)!==a.interface)dup.push(`${n} (${seen.get(n)} / ${a.interface})`);else seen.set(n,a.interface);}
    return dup.length?{severity:"high",title:"تداخل شبكات IP على واجهات مختلفة",detail:dup.join("، "),fix:null}:null;
  });
  issues.sort((a,b)=>sevRank[a.severity]-sevRank[b.severity]);
  return {checked,unknown,total:20,ok:checked-issues.length,issues};
}
