/* MICRO-MAX web — shared helpers (no dependencies) */
window.MM=(()=>{
  const S={get(k){try{return localStorage.getItem(k)}catch{return null}},set(k,v){try{localStorage.setItem(k,v)}catch{}},del(k){try{localStorage.removeItem(k)}catch{}}};
  const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  const ERR={INVALID_CREDENTIALS:"البريد أو كلمة المرور غير صحيحة",EMAIL_EXISTS:"هذا البريد مسجل مسبقاً",WEAK_PASSWORD:"كلمة المرور يجب ألا تقل عن 8 أحرف",INVALID_EMAIL:"بريد إلكتروني غير صالح",REGISTRATION_CLOSED:"التسجيل مغلق حالياً",ACCOUNT_DISABLED:"تم تعطيل هذا الحساب",FORBIDDEN:"ليست لديك صلاحية",UNAUTHORIZED:"انتهت الجلسة، سجّل الدخول من جديد",ADMIN_EMAIL_REQUIRES_GOOGLE_OR_SETUP_KEY:"هذا بريد المدير: أدخل مفتاح الإعداد أو استخدم Google",ROUTER_IS_AGENT_MODE:"هذا الراوتر في وضع الوكيل (Agent): الأوامر المباشرة غير متاحة له",ROUTER_NOT_FOUND:"الراوتر غير موجود",ROUTER_COMMAND_FAILED:"فشل تنفيذ الأمر على الراوتر",ACTION_NOT_ALLOWED:"هذا الإجراء غير مسموح لهذه القائمة",VERSION_EXISTS:"هذا الإصدار موجود مسبقاً",TEMPLATE_INVALID:"القالب غير صالح لـ MikroTik (تحقق من النموذج والمتغيرات)",INVALID_APK_URL:"رابط APK يجب أن يكون https",INVALID_VERSION:"رقم الإصدار غير صالح (مثل 11.4.0)",PAID_TEMPLATE:"هذا القالب مدفوع",RATE_LIMIT:"محاولات كثيرة، انتظر قليلاً"};
  const msg=e=>ERR[e?.error]||e?.detail||e?.error||"حدث خطأ غير متوقع";
  let token=S.get("mm_token"), user=null; try{user=JSON.parse(S.get("mm_user")||"null")}catch{}
  async function api(path,{method="GET",body,raw}={}){
    const h={}; if(body!==undefined) h["Content-Type"]="application/json"; if(token) h.Authorization="Bearer "+token;
    let r; try{ r=await fetch(path,{method,headers:h,body:body!==undefined?JSON.stringify(body):undefined}); }catch{ throw {error:"NETWORK",detail:"تعذر الاتصال بالخادم"}; }
    if(raw) return r; const t=await r.text(); let d; try{d=JSON.parse(t)}catch{d=t}
    if(r.status===401&&token){ setAuth(null,null); }
    if(!r.ok) throw (typeof d==="object"&&d?{...d,status:r.status}:{error:"HTTP_"+r.status,status:r.status});
    return d;
  }
  function setAuth(t,u){ token=t; user=u; t?S.set("mm_token",t):S.del("mm_token"); u?S.set("mm_user",JSON.stringify(u)):S.del("mm_user"); }
  function toast(m,err){ let box=document.getElementById("toasts"); if(!box){box=document.createElement("div");box.id="toasts";document.body.appendChild(box);} const d=document.createElement("div"); d.className="toast"+(err?" err":""); d.textContent=m; box.appendChild(d); setTimeout(()=>d.remove(),err?6000:3500); }
  const fail=e=>toast(msg(e),true);
  function theme(){ const t=S.get("mm_theme"); if(t) document.documentElement.dataset.theme=t; }
  function toggleTheme(){ const n=document.documentElement.dataset.theme==="light"?"dark":"light"; document.documentElement.dataset.theme=n; S.set("mm_theme",n); }
  theme();
  function header(active){
    const links=[["index.html","الرئيسية","home"],["store.html","متجر القوالب","store"],["builder.html","منشئ القوالب","builder"],["download.html","التحميل والتحديثات","download"],["console.html","لوحة التحكم","console"]];
    const el=document.getElementById("hdr"); if(!el) return;
    el.className="top"; el.innerHTML=`<div class="wrap"><a class="logo" href="/index.html"><i>M</i>MICRO-MAX</a><nav class="main">${links.map(l=>`<a href="/${l[0]}" class="${active===l[2]?"on":""}">${l[1]}</a>`).join("")}${user?.role==="admin"?`<a href="/admin.html" class="${active==="admin"?"on":""}">الإدارة</a>`:""}</nav><button class="btn sm" id="thm" title="الوضع">🌓</button>${user?`<span class="small muted">${esc(user.email)}</span><button class="btn sm" id="lo">خروج</button>`:`<a class="btn primary sm" href="/login.html">دخول</a>`}</div>`;
    el.querySelector("#thm").onclick=toggleTheme; const lo=el.querySelector("#lo"); if(lo) lo.onclick=()=>{setAuth(null,null);location.href="/index.html";};
    api("/api/site/announcements").then(d=>{ const a=d.items[0]; if(a){ const b=document.createElement("div"); b.className="banner "+a.level; b.innerHTML=`<b>${esc(a.title)}</b>${a.body?" — "+esc(a.body):""}`; el.before(b); } }).catch(()=>{});
  }
  function footer(){ const f=document.getElementById("ftr"); if(f){ f.className="foot"; f.innerHTML=`<div class="wrap">© ${new Date().getFullYear()} MICRO-MAX — نظام إدارة شبكات MikroTik · <a href="/store.html">القوالب</a> · <a href="/download.html">التحميل</a> · <a href="/console.html">لوحة التحكم</a></div>`; } }
  const fmtBytes=n=>{n=Number(n);if(!isFinite(n))return"-";const u=["B","KB","MB","GB","TB"];let i=0;while(n>=1024&&i<4){n/=1024;i++;}return n.toFixed(i?1:0)+" "+u[i];};
  // Scale thumbnails: render the page at 420x560 inside .tplw and shrink it to the wrapper width.
  function fitFrames(root=document){ root.querySelectorAll(".tplw").forEach(w=>{const f=w.firstElementChild;if(!f)return;const k=w.clientWidth/420;if(k>0){f.style.transform=`scale(${k})`;w.style.height=(560*k)+"px";}}); }
  addEventListener("resize",()=>fitFrames());
  return {fitFrames,api,S,esc,msg,toast,fail,header,footer,setAuth,fmtBytes,get token(){return token},get user(){return user},toggleTheme};
})();
