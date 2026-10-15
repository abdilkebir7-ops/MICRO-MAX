// Minimal in-memory RouterOS API server (plain TCP) for integration tests.
import net from "net";
const enc = n => n<0x80?Buffer.from([n]):n<0x4000?Buffer.from([(n>>8)|0x80,n&255]):Buffer.from([(n>>16)|0xC0,(n>>8)&255,n&255]);
const sentence = ws => Buffer.concat([...ws.flatMap(w=>{const b=Buffer.from(w);return [enc(b.length),b];}),Buffer.from([0])]);
function* parse(state){ // yields complete sentences (arrays of words) from state.buf
  for(;;){ let i=0; const words=[]; const buf=state.buf;
    for(;;){ if(i>=buf.length) return; let b=buf[i]; let len,hdr=1; if(b===0){ i++; state.buf=buf.subarray(i); yield words; break; }
      if(b<0x80) len=b; else if(b<0xC0){ if(i+1>=buf.length) return; len=((b&0x3f)<<8)|buf[i+1]; hdr=2; } else { if(i+2>=buf.length) return; len=((b&0x1f)<<16)|(buf[i+1]<<8)|buf[i+2]; hdr=3; }
      if(i+hdr+len>buf.length) return; words.push(buf.subarray(i+hdr,i+hdr+len).toString()); i+=hdr+len; } }
}
export function startMockRouter({user="admin",password="pw",tables={},port=0}={}){
  const T = { ...tables }; const log=[]; let seq=100;
  const srv = net.createServer(sock=>{
    const state={buf:Buffer.alloc(0)}; sock.on("error",()=>{});
    sock.on("data",d=>{ state.buf=Buffer.concat([state.buf,d]);
      for(const ws of parse(state)){
        const [cmd,...rest]=ws; const kv={}; const q=[]; for(const w of rest){ if(w.startsWith("=")){const k=w.indexOf("=",1); kv[w.slice(1,k)]=w.slice(k+1);} else if(w.startsWith("?")) q.push(w.slice(1)); }
        if(cmd==="/login"){ if(!kv.name){ sock.write(sentence(["!done"])); continue; } if(kv.name===user&&kv.password===password) sock.write(sentence(["!done"])); else { sock.write(sentence(["!trap","=message=invalid user name or password (6)"])); sock.write(sentence(["!done"])); } continue; }
        log.push(ws);
        if(cmd==="/system/script/run"){ // emulate the card-generation script produced by cards.js
          const sc=(T["/system/script"]||[]).find(x=>x.name===kv.number||x[".id"]===kv.number); const src=sc?.source||"";
          const count=Number(/\$made < (\d+)/.exec(src)?.[1]||0), profile=/profile="([^"]*)"/.exec(src)?.[1]||"default", comment=/comment="([^"]*)"/.exec(src)?.[1]||"";
          const nameExpr=/:local u \((.*)\)\n/.exec(src)?.[1]||""; const pre=/^"([^"]*)"/.exec(nameExpr)?.[1]||""; const rs=[...nameExpr.matchAll(/length=(\d+) from="([^"]*)"/g)];
          const pin=/:local p \$u/.test(src); const pr=/:local p \[:rndstr length=(\d+) from="([^"]*)"\]/.exec(src);
          const rnd=(n,set)=>Array.from({length:n},()=>set[Math.floor(Math.random()*set.length)]).join("");
          const users=(T["/ip/hotspot/user"]??=[]);
          for(let i=0;i<count;i++){ const u=pre+rs.map(x=>rnd(Number(x[1]),x[2])).join(""); users.push({".id":"*"+(seq++).toString(16).toUpperCase(),name:u,password:pin?u:(pr?rnd(Number(pr[1]),pr[2]):"x"),profile,comment,disabled:"false"}); }
          sock.write(sentence(["!done"])); continue; }
        const m=/^(.*)\/(print|add|set|remove|enable|disable)$/.exec(cmd); if(!m){ sock.write(sentence(["!trap","=message=no such command"])); sock.write(sentence(["!done"])); continue; }
        const [,path,act]=m; const rows=(T[path]??=[]);
        const trap=msg=>{ sock.write(sentence(["!trap",`=message=${msg}`])); sock.write(sentence(["!done"])); };
        if(act==="print"){ const flt=q.map(x=>x.split("=")); for(const r of rows.filter(r=>flt.every(([k,v])=>String(r[k])===v))) sock.write(sentence(["!re",...Object.entries(r).map(([k,v])=>`=${k}=${v}`)])); sock.write(sentence(["!done"])); }
        else if(act==="add"){ const id="*"+(seq++).toString(16).toUpperCase(); rows.push({".id":id,...kv,disabled:"false"}); sock.write(sentence(["!done",`=ret=${id}`])); }
        else { const r=rows.find(x=>x[".id"]===kv[".id"]); if(!r) trap("no such item"); else { if(act==="set") Object.assign(r,Object.fromEntries(Object.entries(kv).filter(([k])=>k!==".id"))); if(act==="remove") rows.splice(rows.indexOf(r),1); if(act==="enable") r.disabled="false"; if(act==="disable") r.disabled="true"; sock.write(sentence(["!done"])); } }
      } });
  });
  return new Promise(res=>srv.listen(port,"0.0.0.0",()=>res({port:srv.address().port,tables:T,log,close:()=>srv.close()})));
}
