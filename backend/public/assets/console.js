/* MICRO-MAX Winbox-style console */
(()=>{
if(!MM.token){location.href="/login.html?next=/console.html";return;}
const $=id=>document.getElementById(id), E=MM.esc, desk=$("desk");
let routers=[], rid=null, MENUS=[], CAN_WRITE=["admin","owner"].includes(MM.user?.role);
const cur=()=>routers.find(r=>r.id===rid);
const R=p=>`/api/routers/${rid}${p}`;

/* ---------------- window manager ---------------- */
const WM={wins:new Map(),z:10,n:0,
 open(id,{title,w=720,h=440,build,onClose}){
  if(!rid&&id!=="addrouter"){MM.toast("أضف راوتراً أو اخترْ واحداً أولاً",true);return;}
  if(this.wins.has(id)){this.focus(id);const x=this.wins.get(id);x.el.classList.remove("min");return;}
  const el=document.createElement("div");el.className="w";const k=(this.n++%8)*28;
  const mw=Math.min(w,desk.clientWidth-20),mh=Math.min(h,desk.clientHeight-20);
  el.style.cssText=`left:${Math.max(8,20+k)}px;top:${Math.max(8,16+k)}px;width:${mw}px;height:${mh}px`;
  el.innerHTML=`<div class="hd"><span>${E(title)}</span><button data-a="min" title="تصغير">–</button><button data-a="max" title="تكبير">□</button><button data-a="x" title="إغلاق">✕</button></div><div class="bd"></div>`;
  desk.appendChild(el);const win={id,el,title,bd:el.querySelector(".bd"),timers:[],onClose};this.wins.set(id,win);
  const hd=el.querySelector(".hd");let drag=null;
  hd.addEventListener("pointerdown",e=>{if(e.target.closest("button")||el.classList.contains("max"))return;drag={x:e.clientX-el.offsetLeft,y:e.clientY-el.offsetTop};hd.setPointerCapture(e.pointerId);});
  hd.addEventListener("pointermove",e=>{if(!drag)return;el.style.left=Math.min(Math.max(-el.offsetWidth+80,e.clientX-drag.x),desk.clientWidth-60)+"px";el.style.top=Math.min(Math.max(0,e.clientY-drag.y),desk.clientHeight-30)+"px";});
  hd.addEventListener("pointerup",()=>drag=null);hd.addEventListener("dblclick",()=>el.classList.toggle("max"));
  el.addEventListener("pointerdown",()=>this.focus(id));
  hd.addEventListener("click",e=>{const a=e.target.dataset.a;if(a==="x")this.close(id);if(a==="min"){el.classList.add("min");this.task();}if(a==="max")el.classList.toggle("max");});
  build(win);this.focus(id);this.task();return win;},
 focus(id){const w=this.wins.get(id);if(!w)return;this.wins.forEach(x=>x.el.classList.remove("act"));w.el.classList.add("act");w.el.style.zIndex=++this.z;this.task();},
 close(id){const w=this.wins.get(id);if(!w)return;w.timers.forEach(clearInterval);try{w.onClose&&w.onClose();}catch{}w.el.remove();this.wins.delete(id);this.task();},
 closeAll(keep){[...this.wins.keys()].forEach(i=>{if(i!==keep)this.close(i);});},
 task(){$("empty").style.display=this.wins.size?"none":"grid";$("task").innerHTML=[...this.wins.values()].map(w=>`<button data-id="${E(w.id)}" class="${w.el.classList.contains("act")&&!w.el.classList.contains("min")?"act":""}">${E(w.title)}</button>`).join("");document.querySelectorAll("#tree a").forEach(a=>a.classList.toggle("open",this.wins.has(a.dataset.id)));}};
$("task").onclick=e=>{const b=e.target.closest("button");if(!b)return;const w=WM.wins.get(b.dataset.id);if(!w)return;if(w.el.classList.contains("min")||!w.el.classList.contains("act")){w.el.classList.remove("min");WM.focus(b.dataset.id);}else{w.el.classList.add("min");WM.task();}};
const every=(win,fn,ms)=>{const t=setInterval(()=>{if(!win.el.classList.contains("min")&&document.visibilityState==="visible")fn();},ms);win.timers.push(t);return t;};

/* ---------------- generic Winbox menu window ---------------- */
const RO=new Set(["uptime","bytes-in","bytes-out","rx-byte","tx-byte","rate","status","running","active","time","topics","message","run-count","free-memory","total-memory","free-hdd-space","platform","version","board-name","cpu-load","network","dynamic","invalid"]);
const EDITABLE={interfaces:["name","comment"]};
const EXTRA_FIELDS={"hotspot-users":["password"],"ppp-secrets":["password"]};
const FIELD_HINT={"rate-limit":"مثال: 2M/5M","max-limit":"مثال: 2M/5M","limit-uptime":"مثال: 1d أو 02:00:00","session-timeout":"مثال: 1h","address":"مثال: 192.168.88.1/24","target":"مثال: 192.168.88.0/24"};
function menuWindow(m){
 WM.open("m:"+m.key,{title:m.title,w:820,h:460,build(win){
  const canAdd=CAN_WRITE&&m.actions.includes("add"),acts=CAN_WRITE?m.actions:[];let items=[],sel=null,filter="",busy=false;
  win.bd.innerHTML=`<div class="bar2">${canAdd?`<button class="btn sm primary" data-a="add">+ إضافة</button>`:""}<button class="btn sm" data-a="ref">⟳ تحديث</button><label><input type="checkbox" data-a="live"> مباشر</label><input placeholder="بحث…" data-a="f" style="margin-inline-start:auto"></div><div class="tbl"></div><div class="stat"></div>`;
  const tbl=win.bd.querySelector(".tbl"),stat=win.bd.querySelector(".stat");
  const draw=()=>{
   if(m.single){const it=items[0]||{};tbl.innerHTML=`<div class="kv">${m.cols.map(c=>`<div><small>${E(c)}</small><b style="font-size:14px;word-break:break-all">${E(fmt(c,it[c]))}</b></div>`).join("")}</div>${acts.includes("set")&&it?`<div style="padding:0 12px 12px"><button class="btn sm" data-a="edit" data-i="0">تعديل</button></div>`:""}`;stat.textContent="";return;}
   const q=filter.toLowerCase();const rows=items.filter(it=>!q||m.cols.some(c=>String(it[c]??"").toLowerCase().includes(q)));
   const hasDis=m.cols.includes("disabled");
   tbl.innerHTML=`<table class="t"><thead><tr>${acts.length?"<th></th>":""}${m.cols.map(c=>`<th>${E(c)}</th>`).join("")}</tr></thead><tbody>${rows.map(it=>{const i=items.indexOf(it);const dyn=it.dynamic==="true";return `<tr class="${it.disabled==="true"?"dis":""} ${it.invalid==="true"?"inv":""} ${dyn?"dyn":""}" data-i="${i}">${acts.length?`<td style="white-space:nowrap">${acts.includes("set")&&!dyn?`<button class="btn sm" data-a="edit" data-i="${i}" title="تعديل">✎</button> `:""}${hasDis&&acts.includes("enable")&&!dyn?`<button class="btn sm" data-a="${it.disabled==="true"?"enable":"disable"}" data-i="${i}" title="${it.disabled==="true"?"تفعيل":"تعطيل"}">${it.disabled==="true"?"▶":"⏸"}</button> `:""}${acts.includes("remove")&&!dyn?`<button class="btn sm danger" data-a="remove" data-i="${i}" title="حذف">🗑</button>`:""}</td>`:""}${m.cols.map(c=>`<td title="${E(it[c]??"")}">${E(fmt(c,it[c]))}</td>`).join("")}</tr>`;}).join("")}</tbody></table>`;
   stat.textContent=`${rows.length} عنصر${rows.length!==items.length?` من ${items.length}`:""}`;};
  const load=async()=>{if(busy)return;busy=true;try{const d=await MM.api(R(`/menu/${m.key}`));items=d.items;draw();}catch(e){tbl.innerHTML=`<p style="padding:16px;color:var(--danger)">${E(MM.msg(e))}</p>`;}finally{busy=false;}};
  const act=async(a,i)=>{const it=items[i];try{if(a==="remove"&&!confirm("حذف هذا العنصر من الراوتر؟"))return;await MM.api(R(`/menu/${m.key}/${a}`),{method:"POST",body:{id:it[".id"]}});MM.toast("تم");load();}catch(e){MM.fail(e);}};
  win.bd.addEventListener("click",e=>{const b=e.target.closest("[data-a]");if(!b)return;const a=b.dataset.a;
   if(a==="ref")load();else if(a==="add")editDialog(m,null,load);else if(a==="edit")editDialog(m,items[b.dataset.i],load);else if(["enable","disable","remove"].includes(a))act(a,b.dataset.i);});
  win.bd.querySelector('[data-a="f"]').oninput=e=>{filter=e.target.value;draw();};
  let t=null;win.bd.querySelector('[data-a="live"]').onchange=e=>{if(t){clearInterval(t);t=null;}if(e.target.checked){t=every(win,load,4000);}};
  load();}});
}
function fmt(c,v){if(v==null)return"";if(/^(bytes-in|bytes-out|rx-byte|tx-byte|free-memory|total-memory|free-hdd-space)$/.test(c))return MM.fmtBytes(v)+"";return String(v);}
function editDialog(m,item,done){
 const editable=[...(EDITABLE[m.key]||m.cols),...(EXTRA_FIELDS[m.key]||[])].filter(c=>!RO.has(c)&&c!=="disabled");
 const mo=MMPublish.modal(`<h3>${item?"تعديل":"إضافة"} — ${E(m.title)}</h3><div class="row">${editable.map(c=>`<div><label class="f">${E(c)}</label><input data-k="${E(c)}" value="${E(item?.[c]??"")}" placeholder="${E(FIELD_HINT[c]||"")}" dir="ltr"></div>`).join("")}</div><label class="f">حقول إضافية (سطر لكل حقل: key=value)</label><textarea data-x rows="2" dir="ltr" placeholder="comment=hello"></textarea><p id="em" class="small" style="color:var(--danger)"></p><div style="display:flex;gap:8px;margin-top:10px"><button class="btn primary" id="ok">${item?"حفظ":"إضافة"}</button><button class="btn" id="no">إلغاء</button></div>`);
 mo.querySelector("#no").onclick=()=>mo.remove();
 mo.querySelector("#ok").onclick=async()=>{const values={};mo.querySelectorAll("input[data-k]").forEach(i=>{const k=i.dataset.k,v=i.value.trim();if(item){if(v!==String(item[k]??""))values[k]=v;}else if(v!=="")values[k]=v;});
  for(const line of mo.querySelector("[data-x]").value.split("\n")){const s=line.trim();if(!s)continue;const j=s.indexOf("=");if(j<1)return mo.querySelector("#em").textContent="صيغة غير صحيحة: "+s;values[s.slice(0,j).trim()]=s.slice(j+1).trim();}
  if(!Object.keys(values).length)return mo.querySelector("#em").textContent=item?"لم تغيّر أي حقل":"أدخل قيمة واحدة على الأقل";
  try{await MM.api(R(`/menu/${m.key}/${item?"set":"add"}`),{method:"POST",body:{id:item?.[".id"],values}});mo.remove();MM.toast("تم الحفظ");done();}catch(e){mo.querySelector("#em").textContent=MM.msg(e)+(e.detail?": "+e.detail:"");}};
}

/* ---------------- dashboard ---------------- */
function dashWindow(){WM.open("dash",{title:"Dashboard",w:760,h:480,build(win){
 let last=null,lastT=0;
 win.bd.innerHTML=`<div class="kv" id="k"></div><div class="tbl"></div>`;const k=win.bd.querySelector("#k"),tbl=win.bd.querySelector(".tbl");
 const load=async()=>{try{const d=await MM.api(R("/dashboard"));const r=d.resource||{};const tm=Number(r["total-memory"]),fm=Number(r["free-memory"]),mp=tm?Math.round((tm-fm)/tm*100):0,cpu=Number(r["cpu-load"]||0);
  k.innerHTML=`<div><small>الجهاز</small><b>${E(r["board-name"]||"-")}</b><small>RouterOS ${E(r.version||"")}</small></div><div><small>مدة التشغيل</small><b>${E(r.uptime||"-")}</b></div><div><small>المعالج</small><b>${cpu}%</b><div class="bar-m"><i style="width:${cpu}%"></i></div></div><div><small>الذاكرة</small><b>${mp}%</b><div class="bar-m"><i style="width:${mp}%"></i></div><small>${MM.fmtBytes(fm)} حرة</small></div><div><small>المستخدمون النشطون</small><b>${d.activeUsers}</b></div>`;
  const now=Date.now(),dt=(now-lastT)/1000;const prev=last;last=Object.fromEntries(d.interfaces.map(i=>[i.name,i]));lastT=now;
  tbl.innerHTML=`<table class="t"><thead><tr><th>الواجهة</th><th>النوع</th><th>الحالة</th><th>↓ rx</th><th>↑ tx</th></tr></thead><tbody>${d.interfaces.map(i=>{const p=prev&&prev[i.name];const rate=(a,b)=>p&&dt>0?MM.fmtBytes(Math.max(0,(Number(i[a])-Number(p[a]))/dt)*8/8)+"/s":"";return `<tr class="${i.disabled==="true"?"dis":""}"><td>${E(i.name)}</td><td>${E(i.type)}</td><td>${i.running==="true"?'<span class="badge ok">يعمل</span>':'<span class="badge bad">متوقف</span>'}</td><td>${rate("rx-byte")||MM.fmtBytes(i["rx-byte"])}</td><td>${rate("tx-byte")||MM.fmtBytes(i["tx-byte"])}</td></tr>`}).join("")}</tbody></table>`;
 }catch(e){tbl.innerHTML=`<p style="padding:16px;color:var(--danger)">${E(MM.msg(e))}</p>`;}};
 load();every(win,load,5000);}});}

/* ---------------- terminal ---------------- */
function toApi(line){const toks=line.trim().split(/\s+/);if(!toks[0])return"";if(/^\/[^\s/]+\/\S/.test(toks[0]))return line.trim(); // already /a/b/print
 const path=[],args=[];for(const t of toks){if(t.startsWith("=")||t.startsWith("?"))args.push(t);else if(!args.length)path.push(t.replace(/^\//,""));else args.push("="+t);}return "/"+path.join("/")+(args.length?" "+args.join(" "):"");}
function termWindow(){WM.open("term",{title:"Terminal",w:760,h:460,build(win){
 if(!CAN_WRITE){win.bd.innerHTML=`<p style="padding:16px">الطرفية متاحة للمالك والمدير فقط.</p>`;return;}
 win.bd.innerHTML=`<div class="term"><span class="cm">MICRO-MAX terminal — اكتب أمراً مثل: /ip address print  أو  /system/resource/print\nأوامر الكتابة تُسجَّل في سجل التدقيق.\n\n</span></div><div class="tin"><span>›</span><input placeholder="/ip address print" autocomplete="off" spellcheck="false" dir="ltr"></div>`;
 const out=win.bd.querySelector(".term"),inp=win.bd.querySelector("input"),hist=[];let hi=0;
 const println=(t,c)=>{const s=document.createElement("span");if(c)s.className=c;s.textContent=t+"\n";out.appendChild(s);out.scrollTop=out.scrollHeight;};
 inp.onkeydown=async e=>{if(e.key==="ArrowUp"&&hist.length){hi=Math.max(0,hi-1);inp.value=hist[hi];e.preventDefault();}else if(e.key==="ArrowDown"){hi=Math.min(hist.length,hi+1);inp.value=hist[hi]||"";}
  else if(e.key==="Enter"&&inp.value.trim()){const line=inp.value;hist.push(line);hi=hist.length;inp.value="";const cmd=toApi(line);println("› "+cmd,"cm");
   try{const d=await MM.api(R("/terminal"),{method:"POST",body:{command:cmd}});const rows=d.result||[];if(!rows.length)println("(لا نتائج)");rows.forEach(r=>println(Object.entries(r).map(([k,v])=>`${k}=${v}`).join("  ")));}
   catch(x){println("خطأ: "+MM.msg(x)+(x.detail&&x.detail!==MM.msg(x)?" — "+x.detail:""),"er");}}};
 inp.focus();}});}

/* ---------------- cards ---------------- */
function cardsWindow(){WM.open("cards",{title:"Hotspot Cards — الكروت",w:780,h:520,build(win){
 win.bd.innerHTML=`<div class="form"><div id="planbox"></div></div><div class="bar2"><b style="font-size:13px">الدفعات</b><button class="btn sm" data-a="rb">⟳</button></div><div class="tbl" id="batches"></div>`;
 const pb=win.bd.querySelector("#planbox"),bt=win.bd.querySelector("#batches");
 async function plans(){try{const d=await MM.api(R("/plans"));const ps=(d.plans||[]).filter(p=>p.active!==false);
   if(!ps.length){let prof=[];try{prof=(await MM.api(R("/hotspot-profiles"))).profiles||[];}catch{}
    pb.innerHTML=`<p class="muted small">لا توجد باقات بعد. أنشئ باقة من أحد بروفايلات الراوتر (البروفايل يحدد السرعة والمدة على الراوتر نفسه).</p>${prof.length?`<div class="row"><div><label class="f">بروفايل الراوتر</label><select id="np">${prof.map(x=>`<option>${E(x.name)}</option>`).join("")}</select></div><div><label class="f">السعر</label><input id="npr" type="number" min="0" value="0"></div></div><button class="btn primary sm" data-a="mkplan" style="margin-top:8px">إنشاء باقة</button> `:""}<button class="btn sm" data-a="imp">استيراد/تحديث بيانات البروفايلات</button><p id="pm" class="small"></p>`;return;}
   pb.innerHTML=`<div class="row"><div><label class="f">الباقة (بروفايل الراوتر)</label><select id="plan">${ps.map(p=>`<option value="${p.id}">${E(p.name)} — ${Number(p.price)} ${E(p.currency||"")}</option>`).join("")}</select></div><div><label class="f">العدد (حتى 5000)</label><input id="cnt" type="number" min="1" max="5000" value="50"></div></div><div class="row"><div><label class="f">بادئة (اختياري)</label><input id="pfx" maxlength="6" dir="ltr"></div><div><label class="f">خانات رقمية / أحرف</label><div class="row"><input id="dg" type="number" min="0" max="20" value="6"><input id="lt" type="number" min="0" max="12" value="0"></div></div></div><label class="f">رابط البوابة للـ QR (مثال http://hotspot.local/login)</label><input id="portal" dir="ltr" placeholder="http://192.168.88.1/login"><button class="btn primary" style="margin-top:10px" data-a="gen">⚡ توليد الكروت</button><p id="gm" class="small"></p>`;
  }catch(e){pb.innerHTML=`<p style="color:var(--danger)">${E(MM.msg(e))}</p>`;}}
 async function batches(){try{const d=await MM.api("/api/card-batches");const rows=d.batches.filter(b=>!rid||true);bt.innerHTML=`<table class="t"><thead><tr><th></th><th>التاريخ</th><th>الباقة</th><th>الراوتر</th><th>الإجمالي</th><th>متاح</th><th>مباع</th><th>مستخدم</th></tr></thead><tbody>${rows.map(b=>`<tr><td style="white-space:nowrap"><button class="btn sm" data-a="pr" data-b="${b.batch_id}" data-n="${E(b.plan_name||"")}">🎨 تصميم وطباعة</button> <button class="btn sm" data-a="csv" data-b="${b.batch_id}">CSV</button></td><td>${new Date(b.created_at).toLocaleString("ar")}</td><td>${E(b.plan_name||"")}</td><td>${E(b.router_name||"")}</td><td>${b.total}</td><td>${b.available}</td><td>${b.sold}</td><td>${b.used}</td></tr>`).join("")||`<tr><td colspan=8 class="muted">لا دفعات</td></tr>`}</tbody></table>`;}catch(e){bt.innerHTML=`<p style="padding:12px;color:var(--danger)">${E(MM.msg(e))}</p>`;}}
 win.bd.addEventListener("click",async e=>{const b=e.target.closest("[data-a]");if(!b)return;const a=b.dataset.a;
  if(a==="rb")batches();
  else if(a==="mkplan"){b.disabled=true;try{const nm=win.bd.querySelector("#np").value;await MM.api(R("/hotspot-profiles/import"),{method:"POST",body:{}});await MM.api(R("/plans"),{method:"POST",body:{profileName:nm,name:nm,price:Number(win.bd.querySelector("#npr").value)||0}});MM.toast("تم إنشاء الباقة");await plans();}catch(x){MM.fail(x);b.disabled=false;}}
  else if(a==="imp"){b.disabled=true;try{await MM.api(R("/hotspot-profiles/import"),{method:"POST",body:{}});MM.toast("تم الاستيراد");await plans();}catch(x){MM.fail(x);b.disabled=false;}}
  else if(a==="gen"){const gm=win.bd.querySelector("#gm");b.disabled=true;gm.style.color="";gm.textContent="جارٍ التوليد على الراوتر…";
   try{const d=await MM.api(R("/cards/generate"),{method:"POST",body:{planId:win.bd.querySelector("#plan").value,count:Number(win.bd.querySelector("#cnt").value),prefix:win.bd.querySelector("#pfx").value,usernameDigits:Number(win.bd.querySelector("#dg").value),usernameLetters:Number(win.bd.querySelector("#lt").value),portalUrl:win.bd.querySelector("#portal").value}});
    gm.style.color="var(--ok)";gm.innerHTML=`✔ تم توليد ${d.count} كرت. <a href="#" data-a="pr" data-b="${d.batchId}" data-n="${E(d.planName||"")}">طباعة الآن</a>`;batches();}catch(x){gm.style.color="var(--danger)";gm.textContent=MM.msg(x)+(x.detail?" — "+x.detail:"");}finally{b.disabled=false;}}
  else if(a==="pr"){e.preventDefault();MMPrint.printBatch(b.dataset.b,"MICRO-MAX"+(b.dataset.n?" · "+b.dataset.n:""));}
  else if(a==="csv"){try{const r=await MM.api(`/api/card-batches/${b.dataset.b}/csv`,{raw:true});if(!r.ok)throw await r.json();const u=URL.createObjectURL(await r.blob());const l=document.createElement("a");l.href=u;l.download="cards.csv";l.click();setTimeout(()=>URL.revokeObjectURL(u),3000);}catch(x){MM.fail(x);}}});
 plans();batches();}});}

/* ---------------- login design ---------------- */
function designWindow(){WM.open("design",{title:"Login Design — قوالب الدخول",w:760,h:460,build(win){
 win.bd.innerHTML=`<div class="form"><p class="muted small">اختر قالباً من المتجر وانشره على هذا الراوتر، أو صمّم قالبك في المنشئ.</p><div class="tbl" id="tp" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(130px,1fr));gap:10px"></div><div style="display:flex;gap:8px;margin-top:10px"><a class="btn" href="/builder.html" target="_blank">✨ المنشئ</a><a class="btn" href="/store.html" target="_blank">🛍 المتجر</a></div><p id="dm" class="small"></p></div>`;
 MM.api("/api/site/templates").then(d=>{win.bd.querySelector("#tp").innerHTML=[...d.presets,...d.custom].filter(t=>!(t.price>0)).map(t=>`<div class="card" style="padding:6px"><div class="tplw"><iframe sandbox="allow-scripts" src="/api/site/templates/${t.slug}/preview"></iframe></div><div class="small" style="padding:4px 2px"><b>${E(t.name)}</b></div><button class="btn sm primary" style="width:100%" data-s="${t.slug}">نشر هنا</button></div>`).join("");MM.fitFrames(win.bd);}).catch(MM.fail);
 win.bd.addEventListener("click",async e=>{const b=e.target.closest("[data-s]");if(!b)return;const dm=win.bd.querySelector("#dm");if(!confirm("سيتم استبدال صفحة الدخول على الراوتر (مع نسخة احتياطية). متابعة؟"))return;b.disabled=true;dm.style.color="";dm.textContent="جارٍ النشر…";
  try{const [login,status]=await Promise.all([fetch(`/api/site/templates/${b.dataset.s}/download`).then(r=>r.text()),fetch("/api/site/status-template").then(r=>r.text())]);await MM.api(R("/hotspot-design/publish"),{method:"POST",body:{loginHtml:login,statusHtml:status}});dm.style.color="var(--ok)";dm.textContent="✔ تم النشر";}
  catch(x){dm.style.color="var(--danger)";dm.textContent=MM.msg(x);}finally{b.disabled=false;}});}});}

/* ---------------- insights / security / sales ---------------- */
function jsonWindow(id,title,url,render){WM.open(id,{title,w:700,h:460,build(win){win.bd.innerHTML=`<div class="bar2"><button class="btn sm" data-a="r">⟳ تحديث</button></div><div class="tbl" style="padding:12px"></div>`;const box=win.bd.querySelector(".tbl");
 const load=async()=>{box.innerHTML=`<p class="muted">جارٍ التحميل…</p>`;try{box.innerHTML=render(await MM.api(url()));}catch(e){box.innerHTML=`<p style="color:var(--danger)">${E(MM.msg(e))}</p>`;}};win.bd.querySelector('[data-a="r"]').onclick=load;load();}});}
const listOf=(o,keys)=>{for(const k of keys)if(Array.isArray(o?.[k]))return o[k];return Array.isArray(o)?o:[];};
const kvTable=rows=>`<table class="t"><tbody>${rows.map(r=>`<tr><td>${E(r[0])}</td><td style="white-space:normal">${E(r[1])}</td></tr>`).join("")}</tbody></table>`;
function securityWindow(){jsonWindow("sec","Security Audit — فحص الأمان",()=>R("/security-audit"),d=>{const f=listOf(d,["findings","issues","items"]);return `<p><b>التقييم:</b> ${E(d.score??d.grade??"")}</p>`+(f.length?kvTable(f.map(x=>[(x.severity||x.level||"")+" · "+(x.title||x.id||""),x.detail||x.message||x.fix||""])):`<pre style="white-space:pre-wrap;direction:ltr">${E(JSON.stringify(d,null,2))}</pre>`);});}
function insightsWindow(){jsonWindow("ins","Insights — رؤى ذكية",()=>"/api/insights",d=>{const f=listOf(d,["insights","items","tips"]);return f.length?kvTable(f.map(x=>[x.title||x.id||x.type||"",x.detail||x.message||x.body||JSON.stringify(x)])):`<pre style="white-space:pre-wrap;direction:ltr">${E(JSON.stringify(d,null,2))}</pre>`;});}
function salesWindow(){jsonWindow("sales","Sales — المبيعات",()=>"/api/reports/sales",d=>{const f=listOf(d,["sales","items","rows","daily"]);return f.length?`<table class="t"><thead><tr>${Object.keys(f[0]).slice(0,7).map(k=>`<th>${E(k)}</th>`).join("")}</tr></thead><tbody>${f.slice(0,300).map(r=>`<tr>${Object.keys(f[0]).slice(0,7).map(k=>`<td>${E(r[k])}</td>`).join("")}</tr>`).join("")}</tbody></table>`:`<pre style="white-space:pre-wrap;direction:ltr">${E(JSON.stringify(d,null,2))}</pre>`;});}

/* ---------------- add router ---------------- */
function addRouterWindow(){WM.open("addrouter",{title:"إضافة راوتر",w:560,h:520,build(win){
 win.bd.innerHTML=`<div class="bar2"><button class="btn sm" data-t="remote" id="t1">عن بعد (Agent) — الأسهل</button><button class="btn sm" data-t="direct" id="t2">مباشر (API)</button></div><div class="form" id="pane"></div>`;const pane=win.bd.querySelector("#pane");
 const remote=()=>{pane.innerHTML=`<p class="muted small">لا تحتاج فتح منافذ أو IP عام: الصق السكربت مرة واحدة في Terminal الراوتر (Winbox) فيتصل الراوتر بالسيرفر تلقائياً.</p><label class="f">اسم الراوتر</label><input id="an" placeholder="فرع المدينة"><button class="btn primary" id="ago" style="margin-top:10px">إنشاء السكربت</button><div id="out"></div>`;
  pane.querySelector("#ago").onclick=async()=>{const name=pane.querySelector("#an").value.trim();if(!name)return MM.toast("أدخل الاسم",true);try{const d=await MM.api("/api/routers/agent/enroll",{method:"POST",body:{name}});pane.querySelector("#out").innerHTML=`<label class="f">الصق هذا في Terminal الراوتر</label><textarea rows="9" dir="ltr" readonly style="font:11px monospace">${E(d.script)}</textarea><button class="btn sm" id="cpy" style="margin-top:6px">نسخ</button><p class="small muted">عند أول اتصال تتحول الحالة إلى متصل. لإدارة القوائم مباشرة استخدم وضع API.</p>`;pane.querySelector("#cpy").onclick=()=>navigator.clipboard.writeText(d.script).then(()=>MM.toast("تم النسخ"),()=>MM.toast("تعذر النسخ",true));await loadRouters(d.id);const box=document.createElement("div");box.id="ast";box.className="small";box.style.cssText="margin-top:10px;padding:10px;border:1px solid var(--line);border-radius:8px";box.innerHTML="⏳ بانتظار أول اتصال من الراوتر… (حتى دقيقتين بعد لصق السكربت)";pane.querySelector("#out").appendChild(box);let tries=0;const iv=setInterval(async()=>{if(!document.body.contains(box)){clearInterval(iv);return;}tries++;try{const st=await MM.api("/api/routers/"+d.id+"/agent/status");if(st.online){clearInterval(iv);box.style.borderColor="var(--ok)";box.style.color="var(--ok)";box.innerHTML="✔ الراوتر متصل"+(st.info&&st.info.identity?" — "+E(st.info.identity):"")+". افتح «الكروت» لتوليد الكروت.";await loadRouters(d.id);}else if(tries>40){clearInterval(iv);box.style.color="var(--danger)";box.innerHTML="لم يتصل الراوتر بعد. تحقق: 1) لصقت السكربت كاملاً في Terminal. 2) الراوتر يفتح الإنترنت. 3) <span class=mono>/ip dns print</span> يعمل. 4) <span class=mono>/system clock print</span> التاريخ والوقت صحيحان. ثم شغّل <span class=mono>/system script run mm-agent</span> وراجع <span class=mono>/log print where message~\"MICRO\"</span>.";}}catch{}},3000);}catch(e){MM.fail(e);}};};
 const direct=()=>{pane.innerHTML=`<p class="muted small">الراوتر يجب أن يكون قابلاً للوصول من الخادم (IP عام أو شبكة خاصة مسموحة). يُنصح بـ API-SSL (8729) مع تثبيت الشهادة.</p><label class="f">الاسم</label><input id="n"><div class="row"><div><label class="f">العنوان</label><input id="h" dir="ltr"></div><div><label class="f">المنفذ</label><input id="p" type="number" value="8729"></div></div><div class="row"><div><label class="f">المستخدم</label><input id="u" dir="ltr" autocomplete="off"></div><div><label class="f">كلمة المرور</label><input id="pw" type="password" autocomplete="new-password"></div></div><label class="f"><input type="checkbox" id="tls" checked> API-SSL</label><label class="f">بصمة الشهادة SHA-256 (اختياري)</label><input id="fp" dir="ltr"><p id="dm" class="small"></p><div id="lanw" class="small" style="display:none;color:var(--danger);border:1px solid var(--danger);border-radius:8px;padding:8px;margin:6px 0">⚠ هذا العنوان يبدو عنواناً داخلياً لشبكتك (الذي تفتح به Winbox من الواي فاي). خادم الإنترنت لا يستطيع الوصول إليه، لذلك سيفشل الاتصال المباشر. استخدم <b>عن بعد (Agent)</b> — لا يحتاج IP عاماً ولا فتح منافذ. <button class="btn sm" id="goag" type="button">التبديل إلى Agent</button></div><div style="display:flex;gap:8px"><button class="btn" id="tst">اختبار الاتصال</button><button class="btn primary" id="sv">حفظ</button></div>`;
  const body=()=>({name:pane.querySelector("#n").value,host:pane.querySelector("#h").value,port:Number(pane.querySelector("#p").value),username:pane.querySelector("#u").value,password:pane.querySelector("#pw").value,tls:pane.querySelector("#tls").checked,fingerprint:pane.querySelector("#fp").value||undefined});
  const dm=pane.querySelector("#dm");const lanw=pane.querySelector("#lanw");pane.querySelector("#goag").onclick=()=>remote();
  const isLan=h=>{h=String(h||"").trim();const m=h.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);if(!m)return /\.local$|^[^.]+$/.test(h)&&h!=="";const a=+m[1],b=+m[2];return a===10||a===127||(a===172&&b>=16&&b<=31)||(a===192&&b===168)||(a===169&&b===254)||(a===100&&b>=64&&b<=127)||/\.1$/.test(h)||/\.254$/.test(h);};
  pane.querySelector("#h").oninput=e=>{lanw.style.display=isLan(e.target.value)?"block":"none";};
  const why=e=>{const m=MM.msg(e)+(e.detail?" — "+e.detail:"");return /TIMEOUT|UNREACH|ECONN|REFUSED|ENOTFOUND|HOST|NOT_ALLOWED|PRIVATE|BLOCK/i.test(String(e.message||"")+String(e.error||"")+String(e.detail||""))?m+" — الخادم لا يصل لهذا العنوان/المنفذ. إن كان الراوتر داخل شبكتك فاستخدم Agent.":m;};
  pane.querySelector("#tst").onclick=async()=>{dm.style.color="";dm.textContent="جارٍ الاختبار…";try{const d=await MM.api("/api/routers/test",{method:"POST",body:body()});dm.style.color="var(--ok)";dm.textContent="✔ نجح الاتصال"+(d.identity?" — "+d.identity:"");}catch(e){dm.style.color="var(--danger)";dm.textContent=why(e);}};
  pane.querySelector("#sv").onclick=async()=>{try{const d=await MM.api("/api/routers",{method:"POST",body:body()});MM.toast("تمت الإضافة");await loadRouters(d.id);WM.close("addrouter");}catch(e){dm.style.color="var(--danger)";dm.textContent=why(e);}};};
 const tab=t=>{win.bd.querySelector("#t1").classList.toggle("primary",t==="remote");win.bd.querySelector("#t2").classList.toggle("primary",t==="direct");t==="remote"?remote():direct();};
 win.bd.querySelectorAll("[data-t]").forEach(b=>b.onclick=()=>tab(b.dataset.t));tab("direct");}});}

/* ---------------- tree + routers ---------------- */
const EXTRA=[["System","dash","Dashboard","📊",dashWindow],["System","term","Terminal","⌨️",termWindow],["Hotspot","cards","Cards — الكروت","🎫",cardsWindow],["Hotspot","design","Login Design","🎨",designWindow],["Tools","sales","Sales — المبيعات","💰",salesWindow],["Tools","sec","Security Audit","🛡️",securityWindow],["Tools","ins","Insights","💡",insightsWindow]];
function drawTree(){const groups={};const add=(g,id,label,icon,fn)=>{(groups[g]??=[]).push({id,label,icon,fn});};
 EXTRA.forEach(x=>{if(x[0]==="System")add(...x);});
 MENUS.forEach(m=>add(m.group,"m:"+m.key,m.title,"▸",()=>menuWindow(m)));
 EXTRA.forEach(x=>{if(x[0]!=="System")add(...x);});
 $("tree").innerHTML=Object.entries(groups).map(([g,l])=>`<div class="grp">${E(g.toUpperCase())}</div>${l.map(x=>`<a data-id="${E(x.id)}">${x.icon} ${E(x.label)}</a>`).join("")}`).join("");
 const fns=Object.fromEntries(Object.values(groups).flat().map(x=>[x.id,x.fn]));
 $("tree").onclick=e=>{const a=e.target.closest("a");if(a){fns[a.dataset.id]();$("tree").classList.remove("show");}};}
async function loadRouters(select){try{const d=await MM.api("/api/routers");routers=d.routers||[];}catch(e){MM.fail(e);routers=[];}
 $("rsel").innerHTML=routers.length?routers.map(r=>`<option value="${r.id}">${E(r.name)}${r.mode==="agent"?" (Agent)":""}</option>`).join(""):`<option value="">— لا راوترات —</option>`;
 const next=select&&routers.find(r=>r.id===select)?select:(routers.find(r=>r.id===rid)?rid:routers[0]?.id||null);setRouter(next);}
function setRouter(id){if(id!==rid){const keep=WM.wins.has("addrouter");WM.closeAll(keep?"addrouter":undefined);}rid=id;$("rsel").value=id||"";const r=cur();$("dot").className="dot "+(!r?"":(r.mode==="agent"?(r.online?"on":"off"):"on"));$("rdel").style.display=r?"":"none";
 if(r&&r.mode==="agent")MM.toast("هذا الراوتر بوضع Agent: القوائم المباشرة غير متاحة، استخدم الكروت والبروفايلات.");}
$("rsel").onchange=e=>setRouter(e.target.value||null);
$("radd").onclick=addRouterWindow;
$("rdel").onclick=async()=>{const r=cur();if(!r||!confirm(`حذف الراوتر «${r.name}» وكل بياناته من MICRO-MAX؟ (لن يتأثر الراوتر نفسه)`))return;try{await MM.api(`/api/routers/${r.id}`,{method:"DELETE"});MM.toast("تم الحذف");rid=null;loadRouters();}catch(e){MM.fail(e);}};
$("thm").onclick=MM.toggleTheme;$("lo").onclick=()=>{MM.setAuth(null,null);location.href="/index.html";};
$("who").textContent=MM.user?.email||"";$("mnu").onclick=()=>$("tree").classList.toggle("show");if(matchMedia("(max-width:800px)").matches)$("mnu").style.display="";
(async()=>{try{const me=await MM.api("/api/me");CAN_WRITE=["admin","owner"].includes(me.role);}catch{}
 try{MENUS=(await MM.api("/api/menus")).items;}catch(e){MM.fail(e);}drawTree();await loadRouters();if(!routers.length)addRouterWindow();else dashWindow();})();
})();
