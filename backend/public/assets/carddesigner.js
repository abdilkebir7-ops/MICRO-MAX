/* MICRO-MAX card designer: full control over card look, auto page layout, live multi-card preview, QR. Needs MMQR (qr.js). */
window.MMCards=(()=>{
const E=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const PAPER={A4:[210,297],A5:[148,210],A3:[297,420],Letter:[216,279]};
const FONTS={"Tajawal":"Tajawal","Cairo":"Cairo","Almarai":"Almarai","Noto Kufi Arabic":"Noto Kufi Arabic","IBM Plex Sans Arabic":"IBM Plex Sans Arabic","IBM Plex Mono":"IBM Plex Mono","Roboto Mono":"Roboto Mono","Tahoma (النظام)":"Tahoma"};
const DEF={paper:"A4",orient:"portrait",margin:8,gap:3,cols:0,cw:85,ch:54,
 shape:"rounded",radius:3,bg1:"#ffffff",bg2:"#e6fffb",grad:false,angle:135,ink:"#0f172a",muted:"#64748b",accent:"#0d9488",
 border:"dashed",bw:0.3,bc:"#64748b",header:"top",hbg:"#0d9488",hink:"#ffffff",
 font:"Tajawal",codeFont:"IBM Plex Mono",fsU:12,fsP:12,fsB:10,fsPlan:9,weight:700,ls:0.4,dir:"rtl",
 qr:true,qrPos:"end",qrSize:22,qrFg:"#000000",qrBg:"#ffffff",qrType:"login",ssid:"",wifiPass:"",
 sBrand:true,sPlan:true,sPrice:true,sSerial:false,sHint:true,sLogo:true,sLabels:true,
 brand:"MICRO-MAX",hint:"امسح الرمز للاتصال تلقائياً",footer:"",logo:""};
const PRESETS={
 "كلاسيك":{},
 "تذكرة":{shape:"ticket",border:"none",bg1:"#fff7e6",bg2:"#ffe8b8",grad:true,hbg:"#b45309",accent:"#b45309",radius:2},
 "داكن":{bg1:"#0b1b2b",bg2:"#12324a",grad:true,ink:"#eafcff",muted:"#8fb3c7",accent:"#38d9c4",hbg:"#38d9c4",hink:"#04201c",border:"solid",bc:"#38d9c4",qrBg:"#ffffff"},
 "شريحة أفقية":{cw:190,ch:32,header:"side",qrPos:"end",qrSize:24,gap:2,fsU:14,fsP:14},
 "مربع QR":{cw:50,ch:60,header:"none",qrPos:"top",qrSize:34,fsU:9,fsP:9,sHint:false,shape:"rounded"},
 "صغير اقتصادي":{cw:62,ch:36,qrSize:16,fsU:9.5,fsP:9.5,fsB:8,fsPlan:7,gap:2,margin:6},
 "بسيط":{header:"none",border:"solid",bw:0.2,bg1:"#ffffff",radius:1.5,accent:"#111827",hbg:"#111827"}};
const FIELDS=[
 ["الصفحة",[["paper","الورق","sel",Object.keys(PAPER)],["orient","الاتجاه","sel",[["portrait","طولي"],["landscape","عرضي"]]],["margin","الهامش (مم)","num",0,25,1],["gap","المسافة بين البطاقات (مم)","num",0,15,0.5],["cols","عدد الأعمدة (0 = تلقائي)","num",0,8,1]]],
 ["حجم وشكل البطاقة",[["cw","العرض (مم)","num",30,200,1],["ch","الارتفاع (مم)","num",20,120,1],["shape","الشكل","sel",[["rounded","حواف دائرية"],["rect","مربع الحواف"],["pill","كبسولة"],["ticket","تذكرة"]]],["radius","استدارة الحواف","num",0,12,0.5],["border","الإطار","sel",[["dashed","متقطع (للقص)"],["solid","متصل"],["dotted","منقط"],["none","بدون"]]],["bw","سمك الإطار","num",0.1,1.5,0.1],["bc","لون الإطار","color"]]],
 ["الألوان",[["bg1","لون الخلفية","color"],["grad","تدرج لوني","chk"],["bg2","لون التدرج","color"],["angle","زاوية التدرج","num",0,360,15],["ink","لون النص","color"],["muted","لون النص الثانوي","color"],["accent","لون التمييز","color"],["hbg","لون الشريط","color"],["hink","لون نص الشريط","color"]]],
 ["الخطوط",[["font","خط النصوص","sel",Object.keys(FONTS)],["codeFont","خط الأرقام","sel",Object.keys(FONTS)],["fsU","حجم اليوزر","num",6,24,0.5],["fsP","حجم كلمة المرور","num",6,24,0.5],["fsB","حجم الاسم","num",6,20,0.5],["fsPlan","حجم الباقة","num",5,16,0.5],["weight","سماكة الخط","sel",[["400","عادي"],["500","متوسط"],["700","عريض"],["800","ثقيل"]]],["ls","تباعد الأحرف","num",0,3,0.1],["dir","اتجاه البطاقة","sel",[["rtl","من اليمين"],["ltr","من اليسار"]]]]],
 ["المحتوى",[["header","الشريط","sel",[["top","علوي"],["side","جانبي"],["none","بدون"]]],["brand","اسم الشبكة","text"],["sBrand","إظهار الاسم","chk"],["sLogo","إظهار الشعار","chk"],["sPlan","إظهار الباقة","chk"],["sPrice","إظهار السعر/المدة","chk"],["sSerial","رقم تسلسلي","chk"],["sLabels","عناوين الحقول","chk"],["sHint","نص أسفل الرمز","chk"],["hint","النص","text"],["footer","سطر ختامي (واتساب...)","text"]]],
 ["رمز QR",[["qr","إظهار QR","chk"],["qrType","نوع الرمز","sel",[["login","دخول تلقائي بالرابط"],["wifi","اتصال Wi-Fi"]]],["ssid","اسم الشبكة SSID (للـ Wi-Fi)","text"],["wifiPass","كلمة مرور Wi-Fi (اتركها فارغة للشبكة المفتوحة)","text"],["qrPos","مكان الرمز","sel",[["end","نهاية البطاقة"],["start","بداية البطاقة"],["top","أعلى"],["bottom","أسفل"]]],["qrSize","حجم الرمز (مم)","num",10,60,1],["qrFg","لون الرمز","color"],["qrBg","خلفية الرمز","color"]]]];
const wifiEsc=s=>String(s).replace(/([\;,:"])/g,"\\$1");
function qrContent(card,cfg){ if(cfg.qrType==="wifi"){ if(!cfg.ssid) return ""; return cfg.wifiPass?`WIFI:T:WPA;S:${wifiEsc(cfg.ssid)};P:${wifiEsc(cfg.wifiPass)};;`:`WIFI:T:nopass;S:${wifiEsc(cfg.ssid)};;`; } return card.qr||""; }
function geometry(cfg){ let [pw,ph]=PAPER[cfg.paper]||PAPER.A4; if(cfg.orient==="landscape")[pw,ph]=[ph,pw]; const m=+cfg.margin,g=+cfg.gap;
 const cols=cfg.cols>0?+cfg.cols:Math.max(1,Math.floor((pw-2*m+g)/(+cfg.cw+g))), rows=Math.max(1,Math.floor((ph-2*m+g)/(+cfg.ch+g)));
 const fits=cols*cfg.cw+(cols-1)*g<=pw-2*m+0.01; return {pw,ph,cols,rows,per:cols*rows,fits}; }
function cardHtml(c,cfg,i){
 const qc=cfg.qr?qrContent(c,cfg):""; let qr=""; try{ if(qc) qr=MMQR.toSvg(qc,{margin:1,fg:cfg.qrFg,bg:cfg.qrBg}); }catch(e){}
 const pin=c.pin; const L=(t)=>cfg.sLabels?`<small>${t}</small>`:"";
 const logo=cfg.sLogo&&cfg.logo?`<img class="lg" src="${E(cfg.logo)}" alt="">`:"";
 const brand=cfg.sBrand?`<b class="br">${E(cfg.brand)}</b>`:"";
 const plan=cfg.sPlan&&c.plan?`<span class="pl">${E(c.plan)}</span>`:"";
 const hdr=cfg.header==="top"?`<div class="hd">${logo}${brand}<span class="sp"></span>${plan}</div>`:cfg.header==="side"?`<div class="sd">${logo}${brand?`<span class="vb">${E(cfg.brand)}</span>`:""}</div>`:"";
 const meta=[cfg.sPrice&&c.price,cfg.sPrice&&c.duration].filter(Boolean).map(E).join(" · ");
 const txt=`<div class="tx">${cfg.header!=="top"&&plan?plan:""}${pin?`${L("رقم الكرت (PIN)")}<code class="u">${E(c.user)}</code>`:`${L("اسم المستخدم")}<code class="u">${E(c.user)}</code>${L("كلمة المرور")}<code class="p">${E(c.pass)}</code>`}${meta?`<div class="mt"><bdi>${meta}</bdi></div>`:""}${cfg.sSerial?`<div class="sn">#${String(i+1).padStart(4,"0")}</div>`:""}</div>`;
 const q=qr?`<div class="qr"><div class="qi">${qr}</div>${cfg.sHint&&cfg.hint?`<div class="qh">${E(cfg.hint)}</div>`:""}</div>`:"";
 const vert=cfg.qrPos==="top"||cfg.qrPos==="bottom"; const first=(cfg.qrPos==="start"||cfg.qrPos==="top");
 const body=`<div class="bd ${vert?"v":""}">${first?q+txt:txt+q}</div>`;
 const ft=cfg.footer?`<div class="ft">${E(cfg.footer)}</div>`:"";
 return `<div class="c ${cfg.header==="side"?"hs":""}">${cfg.header==="side"?hdr+`<div class="mn">${body}${ft}</div>`:hdr+body+ft}</div>`; }
function css(cfg){ const g=geometry(cfg); const f=n=>`'${FONTS[n]||n}'`; const r=cfg.shape==="rect"?0:cfg.shape==="pill"?Math.min(cfg.ch/2,12):cfg.radius;
 const bg=cfg.grad?`linear-gradient(${cfg.angle}deg,${cfg.bg1},${cfg.bg2})`:cfg.bg1;
 const ticket=cfg.shape==="ticket"?`-webkit-mask:radial-gradient(circle at 0 50%,#0000 2.2mm,#000 2.3mm) left/51% 100% no-repeat,radial-gradient(circle at 100% 50%,#0000 2.2mm,#000 2.3mm) right/51% 100% no-repeat;mask:radial-gradient(circle at 0 50%,#0000 2.2mm,#000 2.3mm) left/51% 100% no-repeat,radial-gradient(circle at 100% 50%,#0000 2.2mm,#000 2.3mm) right/51% 100% no-repeat;`:"";
 const qrW=cfg.qrSize; const vert=cfg.qrPos==="top"||cfg.qrPos==="bottom";
 return `@page{size:${g.pw}mm ${g.ph}mm;margin:0}*{box-sizing:border-box;margin:0;padding:0}html,body{background:#fff}body{font-family:${f(cfg.font)},Tahoma,Arial,sans-serif;color:${cfg.ink};-webkit-print-color-adjust:exact;print-color-adjust:exact}
.pg{width:${g.pw}mm;height:${g.ph}mm;padding:${cfg.margin}mm;display:grid;grid-template-columns:repeat(${g.cols},${cfg.cw}mm);grid-auto-rows:${cfg.ch}mm;gap:${cfg.gap}mm;align-content:start;justify-content:start;page-break-after:always;break-after:page;overflow:hidden;background:#fff;direction:ltr}
.pg:last-child{page-break-after:auto;break-after:auto}
.c{width:${cfg.cw}mm;height:${cfg.ch}mm;direction:${cfg.dir};background:${bg};border:${cfg.border==="none"?"none":cfg.bw+"mm "+cfg.border+" "+cfg.bc};border-radius:${r}mm;overflow:hidden;display:flex;flex-direction:column;${ticket}}
.c.hs{flex-direction:row}.mn{flex:1;display:flex;flex-direction:column;min-width:0}
.hd{display:flex;align-items:center;gap:1.5mm;background:${cfg.hbg};color:${cfg.hink};padding:1mm 2.5mm;font-size:${cfg.fsB}pt;flex:none}.hd .sp{flex:1}.hd .pl{font-size:${cfg.fsPlan}pt;font-weight:700;opacity:.95}.lg{height:${Math.min(8,cfg.ch/5)}mm;max-width:16mm;object-fit:contain;background:#fff;border-radius:1mm;padding:.3mm}
.sd{width:${Math.max(8,cfg.fsB*1.1)}mm;background:${cfg.hbg};color:${cfg.hink};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2mm;flex:none;padding:1.5mm 0}.vb{writing-mode:vertical-rl;transform:rotate(180deg);font-weight:800;font-size:${cfg.fsB}pt;letter-spacing:.5mm;white-space:nowrap}.sd .lg{max-width:6mm}
.bd{flex:1;display:flex;align-items:center;gap:2.5mm;padding:1.5mm 2.5mm;min-height:0}.bd.v{flex-direction:column;justify-content:center;text-align:center}
.tx{flex:1;min-width:0;display:flex;flex-direction:column;justify-content:center;gap:.2mm}.tx small{font-size:${Math.max(5,cfg.fsPlan-1.5)}pt;color:${cfg.muted};line-height:1.1;margin-top:.8mm}
.tx .pl{font-size:${cfg.fsPlan}pt;color:${cfg.accent};font-weight:800}
code{font-family:${f(cfg.codeFont)},ui-monospace,Consolas,monospace;direction:ltr;unicode-bidi:isolate;font-weight:${cfg.weight};letter-spacing:${cfg.ls}pt;display:block;word-break:break-all;text-align:${cfg.dir==="rtl"?"right":"left"}}
.u{font-size:${cfg.fsU}pt}.p{font-size:${cfg.fsP}pt}.mt{font-size:${cfg.fsPlan}pt;color:${cfg.muted};margin-top:.6mm}.sn{font-size:5.5pt;color:${cfg.muted};font-family:${f(cfg.codeFont)},monospace}
.qr{flex:none;display:flex;flex-direction:column;align-items:center;gap:.5mm;width:${qrW}mm}.qi{width:${qrW}mm;height:${qrW}mm;border-radius:1mm;overflow:hidden;background:${cfg.qrBg}}.qi svg{width:100%;height:100%;display:block}.qh{font-size:${Math.max(5,cfg.fsPlan-2)}pt;color:${cfg.muted};text-align:center;line-height:1.15}
.ft{text-align:center;font-size:${Math.max(5,cfg.fsPlan-1.5)}pt;color:${cfg.muted};padding:.4mm 2mm 1mm;border-top:.15mm solid ${cfg.muted}33;flex:none}`; }
function buildSheet(cards,cfg,opt={}){ const g=geometry(cfg); const pages=Math.max(1,Math.ceil(cards.length/g.per)); const from=opt.page!=null?opt.page:0, to=opt.page!=null?opt.page+1:pages; let body="";
 for(let p=from;p<to;p++){ const sl=cards.slice(p*g.per,(p+1)*g.per); body+=`<div class="pg">${sl.map((c,k)=>cardHtml(c,cfg,p*g.per+k)).join("")}</div>`; }
 const fonts=[...new Set([cfg.font,cfg.codeFont])].filter(n=>FONTS[n]&&n!=="Tahoma (النظام)").map(n=>"family="+encodeURIComponent(FONTS[n]).replace(/%20/g,"+")+":wght@400;500;700;800").join("&");
 return `<!doctype html><html lang="ar"><head><meta charset="utf-8"><title>كروت MICRO-MAX</title>${fonts?`<link rel="stylesheet" href="https://fonts.googleapis.com/css2?${fonts}&display=swap">`:""}<style>${css(cfg)}${opt.screen?`body{background:#cfd8de}.pg{margin:0 auto;box-shadow:0 2px 10px #0004}`:""}</style></head><body>${body}</body></html>`; }
function demoCards(n,cfg){ const out=[]; for(let i=0;i<n;i++){ const u="MM"+String(100000+Math.floor(Math.random()*899999)); out.push({user:u,pass:Math.random().toString(36).slice(2,8).toUpperCase(),pin:false,plan:"يومية 1GB",price:"5 $",duration:"24 ساعة",qr:"http://192.168.88.1/login?username="+u+"&password=x&auto=1"}); } return out; }
/* ---------- UI ---------- */
function mount(host,{cards,print=null,onCopy=null,title="MICRO-MAX"}={}){
 let cfg={...DEF}; let page=0; let timer=null; cards=cards&&cards.length?cards:demoCards(12);
 try{ const s=localStorage.getItem("mm_card_cfg"); if(s) cfg={...cfg,...JSON.parse(s)}; }catch(e){}
 const id="cd"+Math.random().toString(36).slice(2,6);
 host.innerHTML=`<div class="cd"><div class="cd-ctl" id="${id}c"></div><div class="cd-pv"><div class="cd-bar"><b id="${id}i"></b><span class="cd-sp"></span><button type="button" data-a="prev">‹</button><span id="${id}p"></span><button type="button" data-a="next">›</button><button type="button" data-a="print" class="pri">${print?"🖨 طباعة مباشرة":"📋 نسخ ورقة الطباعة"}</button></div><div class="cd-fr" id="${id}f"><iframe sandbox="allow-same-origin" title="معاينة"></iframe></div><div class="cd-warn" id="${id}w"></div></div></div>`;
 const ctl=host.querySelector("#"+id+"c"), fr=host.querySelector("#"+id+"f iframe");
 let h=`<div class="cd-pre"><label>تصاميم جاهزة</label><div class="cd-chips">${Object.keys(PRESETS).map(n=>`<button type="button" data-pre="${E(n)}">${E(n)}</button>`).join("")}</div></div>`;
 for(const [g,fs] of FIELDS){ h+=`<details${g==="الصفحة"||g==="رمز QR"?" open":""}><summary>${g}</summary><div class="cd-grid">`; for(const f of fs){ const [k,l,t,a,b,s]=f; const v=cfg[k]; let inp="";
  if(t==="num") inp=`<input data-k="${k}" type="number" min="${a}" max="${b}" step="${s}" value="${v}">`; else if(t==="color") inp=`<input data-k="${k}" type="color" value="${v}">`; else if(t==="chk") inp=`<input data-k="${k}" type="checkbox" ${v?"checked":""}>`; else if(t==="text") inp=`<input data-k="${k}" type="text" value="${E(v)}">`;
  else inp=`<select data-k="${k}">${a.map(o=>Array.isArray(o)?`<option value="${o[0]}" ${String(v)===o[0]?"selected":""}>${o[1]}</option>`:`<option ${String(v)===o?"selected":""}>${o}</option>`).join("")}</select>`;
  h+=`<label class="${t==="chk"?"chk":""}"><span>${l}</span>${inp}</label>`; } h+=`</div></details>`; }
 h+=`<details><summary>الشعار</summary><div class="cd-grid"><label><span>ارفع صورة (حتى 300KB)</span><input type="file" id="${id}l" accept="image/*"></label><button type="button" data-a="nologo">إزالة الشعار</button></div></details><div class="cd-row"><button type="button" data-a="save">حفظ تصميمي</button><button type="button" data-a="reset">إعادة الضبط</button></div>`;
 ctl.innerHTML=h;
 function sync(){ ctl.querySelectorAll("[data-k]").forEach(el=>{ const v=cfg[el.dataset.k]; if(el.type==="checkbox")el.checked=!!v; else el.value=v; }); }
 function render(){ const g=geometry(cfg); const pages=Math.ceil(cards.length/g.per)||1; if(page>=pages)page=pages-1; if(page<0)page=0;
  host.querySelector("#"+id+"i").textContent=`${g.cols} × ${g.rows} = ${g.per} بطاقة في الصفحة · ${cards.length} كرت = ${pages} صفحة`;
  host.querySelector("#"+id+"p").textContent=`${page+1} / ${pages}`;
  const w=host.querySelector("#"+id+"w"); const bad=[]; if(!g.fits) bad.push("الأعمدة المختارة أعرض من الورقة؛ قلّل العدد أو العرض."); if(g.pw-2*cfg.margin<cfg.cw) bad.push("البطاقة أعرض من مساحة الطباعة."); if(cfg.qr&&cfg.qrType==="wifi"&&!cfg.ssid) bad.push("أدخل اسم الشبكة SSID لرمز Wi-Fi."); if(cfg.qr&&cfg.qrType==="login"&&!cards.some(c=>c.qr)) bad.push("لا يوجد رابط بوابة في الكروت: أضفه في خطوة التوليد ليعمل رمز الدخول التلقائي."); const qh=Math.min(cfg.qrSize,cfg.ch-4); if(cfg.qr&&(cfg.qrPos==="top"||cfg.qrPos==="bottom")&&cfg.qrSize+14>cfg.ch) bad.push("رمز QR كبير على الارتفاع الحالي."); w.textContent=bad.join(" ");
  fr.srcdoc=buildSheet(cards,cfg,{page,screen:true}); fit(); }
 function fit(){ const box=host.querySelector("#"+id+"f"); if(!box)return; const g=geometry(cfg); const px=g.pw*3.7795; const k=Math.min(1,(box.clientWidth-4)/px); fr.style.width=px+"px"; fr.style.height=(g.ph*3.7795)+"px"; fr.style.transform=`scale(${k})`; fr.style.transformOrigin="0 0"; box.style.height=(g.ph*3.7795*k+4)+"px"; }
 const later=()=>{ clearTimeout(timer); timer=setTimeout(render,120); };
 ctl.addEventListener("input",e=>{ const el=e.target; const k=el.dataset.k; if(!k)return; cfg[k]=el.type==="checkbox"?el.checked:el.type==="number"?(el.value===""?0:+el.value):el.value; if(k==="weight")cfg[k]=String(el.value); later(); });
 ctl.addEventListener("click",e=>{ const b=e.target.closest("button"); if(!b)return; if(b.dataset.pre){ cfg={...DEF,...PRESETS[b.dataset.pre]}; sync(); render(); } const a=b.dataset.a; if(a==="reset"){ cfg={...DEF}; try{localStorage.removeItem("mm_card_cfg");}catch(_){} sync(); render(); } if(a==="save"){ try{ localStorage.setItem("mm_card_cfg",JSON.stringify(cfg)); b.textContent="تم الحفظ ✓"; setTimeout(()=>b.textContent="حفظ تصميمي",1500);}catch(_){ b.textContent="تعذر الحفظ"; } } if(a==="nologo"){ cfg.logo=""; render(); } });
 ctl.querySelector("#"+id+"l").onchange=e=>{ const f=e.target.files[0]; if(!f)return; if(f.size>300000){ alert&&0; host.querySelector("#"+id+"w").textContent="الصورة أكبر من 300KB."; return; } const r=new FileReader(); r.onload=()=>{ cfg.logo=r.result; render(); }; r.readAsDataURL(f); };
 host.querySelector(".cd-bar").addEventListener("click",async e=>{ const b=e.target.closest("button"); if(!b)return; const a=b.dataset.a; if(a==="prev"){page--;render();} if(a==="next"){page++;render();} if(a==="print"){ const html=buildSheet(cards,cfg,{}); if(print) print(html); else { try{ await navigator.clipboard.writeText(html); b.textContent="تم النسخ ✓"; }catch(_){ b.textContent="تعذر النسخ"; } setTimeout(()=>b.textContent="📋 نسخ ورقة الطباعة",1800); if(onCopy)onCopy(html); } } });
 addEventListener("resize",fit); render();
 return {setCards(c){cards=c;page=0;render();},getConfig:()=>cfg,html:()=>buildSheet(cards,cfg,{})};
}
const CSS=`.cd{display:grid;grid-template-columns:minmax(0,320px) minmax(0,1fr);gap:14px;align-items:start}.cd-ctl{max-height:78vh;overflow:auto;border:1px solid var(--line,#ccd);border-radius:10px;padding:8px;background:var(--panel2,#f4f7f9)}.cd-ctl details{border-bottom:1px solid var(--line,#ccd);padding:4px 0}.cd-ctl summary{cursor:pointer;font-weight:700;padding:6px 4px}.cd-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px 8px;padding:4px 2px 8px}.cd-grid label{display:flex;flex-direction:column;gap:2px;font-size:12px;color:var(--muted,#567);min-width:0}.cd-grid label.chk{flex-direction:row;align-items:center;gap:6px;grid-column:1/-1;color:var(--ink,#123)}.cd-grid input,.cd-grid select{width:100%;font:inherit;font-size:13px;padding:5px 7px;border:1px solid var(--line,#ccd);border-radius:6px;background:var(--bg,#fff);color:var(--ink,#123);min-width:0}.cd-grid input[type=checkbox]{width:auto}.cd-grid input[type=color]{padding:1px;height:30px}.cd-pre{padding:4px 2px 8px}.cd-pre>label{font-weight:700;font-size:13px}.cd-chips{display:flex;flex-wrap:wrap;gap:5px;margin-top:5px}.cd button{font:inherit;font-size:13px;border:1px solid var(--line,#ccd);background:var(--panel,#fff);color:var(--ink,#123);border-radius:7px;padding:4px 10px;cursor:pointer}.cd button:hover{border-color:var(--accent,#0a8)}.cd button.pri{background:var(--accent,#0a8);color:var(--accent-ink,#fff);border-color:transparent;font-weight:700}.cd-row{display:flex;gap:6px;padding:8px 2px}.cd-bar{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:8px;font-size:13px}.cd-sp{flex:1}.cd-fr{direction:ltr;overflow:hidden;border:1px solid var(--line,#ccd);border-radius:8px;background:#cfd8de}.cd-fr iframe{border:0;display:block}.cd-warn{color:var(--danger,#c33);font-size:13px;min-height:18px;margin-top:6px}@media(max-width:860px){.cd{grid-template-columns:1fr}.cd-ctl{max-height:none}}`;
return {mount,buildSheet,geometry,demoCards,DEF,PRESETS,CSS};
})();
