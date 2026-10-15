// MICRO-MAX hotspot login template generator (pure, no I/O).
// Output keeps MikroTik variables ($(link-login-only), $(link-orig), $(error)) and
// the QR auto-login script (?username=&password=&auto=1) used by MICRO-MAX cards.

export const PRESETS = [
  { id:"aurora",   name:"أورورا",     group:"داكن",  dark:true,  accent:"#38d9c4", accent2:"#5b8cff", bg1:"#155e75", bg2:"#312e81", base:"#050b16" },
  { id:"midnight", name:"منتصف الليل", group:"داكن",  dark:true,  accent:"#818cf8", accent2:"#c084fc", bg1:"#1e1b4b", bg2:"#4c1d95", base:"#0b0720" },
  { id:"carbon",   name:"كربون",       group:"داكن",  dark:true,  accent:"#f59e0b", accent2:"#ef4444", bg1:"#292524", bg2:"#44403c", base:"#0c0a09" },
  { id:"emerald",  name:"زمردي",       group:"داكن",  dark:true,  accent:"#34d399", accent2:"#22d3ee", bg1:"#064e3b", bg2:"#134e4a", base:"#021510" },
  { id:"crimson",  name:"قرمزي",       group:"داكن",  dark:true,  accent:"#fb7185", accent2:"#f97316", bg1:"#7f1d1d", bg2:"#581c87", base:"#12040a" },
  { id:"ocean",    name:"محيط",        group:"داكن",  dark:true,  accent:"#38bdf8", accent2:"#2dd4bf", bg1:"#0c4a6e", bg2:"#164e63", base:"#03111c" },
  { id:"snow",     name:"ثلجي",        group:"فاتح",  dark:false, accent:"#2563eb", accent2:"#7c3aed", bg1:"#dbeafe", bg2:"#ede9fe", base:"#f8fafc" },
  { id:"mint",     name:"نعناع",       group:"فاتح",  dark:false, accent:"#059669", accent2:"#0891b2", bg1:"#d1fae5", bg2:"#cffafe", base:"#f0fdf9" },
  { id:"sunrise",  name:"شروق",        group:"فاتح",  dark:false, accent:"#ea580c", accent2:"#db2777", bg1:"#ffedd5", bg2:"#fce7f3", base:"#fffaf5" },
  { id:"sand",     name:"رمال",        group:"فاتح",  dark:false, accent:"#b45309", accent2:"#15803d", bg1:"#fef3c7", bg2:"#ecfccb", base:"#fffdf5" },
  { id:"royal",    name:"ملكي",        group:"مميز",  dark:true,  accent:"#fbbf24", accent2:"#a78bfa", bg1:"#312e81", bg2:"#1e1b4b", base:"#07061a" },
  { id:"neon",     name:"نيون",        group:"مميز",  dark:true,  accent:"#a3e635", accent2:"#22d3ee", bg1:"#14532d", bg2:"#083344", base:"#020a06" }
];

const LAYOUTS = ["card","split","minimal"];
const hexRe = /^#[0-9a-fA-F]{6}$/;

export function esc(s){ return String(s??"").replace(/\$\(/g,"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c])); }
const clean = (s,max) => String(s??"").replace(/[\u0000-\u001f\u007f]/g," ").trim().slice(0,max);

export function normalizeOptions(o={}){
  const p = PRESETS.find(x=>x.id===o.preset) || PRESETS[0];
  const col = (v,d) => hexRe.test(String(v||"")) ? String(v) : d;
  let wa = String(o.whatsapp||"").replace(/[^0-9]/g,"").slice(0,15);
  if (wa.length<7) wa="";
  const plans = (Array.isArray(o.plans)?o.plans:[]).slice(0,4).map(x=>({name:clean(x?.name,24),note:clean(x?.note,24)})).filter(x=>x.name);
  let logo = String(o.logoUrl||"").trim();
  if (!/^https:\/\/[^\s"'<>]+$/i.test(logo) || logo.length>400) logo="";
  return {
    preset:p.id, p,
    brand: clean(o.brand,40) || "بوابة الإنترنت",
    subtitle: clean(o.subtitle,90) || "اتصال سريع وآمن — أدخل بيانات الكرت للمتابعة",
    accent: col(o.accent,p.accent), accent2: col(o.accent2,p.accent2),
    layout: LAYOUTS.includes(o.layout)?o.layout:"card",
    lang: o.lang==="en"?"en":"ar",
    whatsapp: wa, plans, logo,
    footer: clean(o.footer,100),
    pinMode: !!o.pinMode,
    showQrHint: o.showQrHint!==false
  };
}

const T = {
  ar:{user:"اسم المستخدم",pass:"كلمة المرور",pin:"رقم الكرت (PIN)",go:"دخول إلى الإنترنت",going:"جارٍ تسجيل الدخول…",plans:"اختر الباقة المناسبة",wa:"استفسار وشراء باقة عبر واتساب",qr:"لديك كرت QR؟ امسحه بكاميرا الهاتف للدخول تلقائياً",waMsg:"مرحباً، أريد الاستفسار عن باقة "},
  en:{user:"Username",pass:"Password",pin:"Card PIN",go:"Connect to the Internet",going:"Signing in…",plans:"Choose your plan",wa:"Buy a plan on WhatsApp",qr:"Have a QR card? Scan it with your phone camera to connect automatically",waMsg:"Hello, I would like to ask about the plan "}
};

export function renderLoginHtml(opts={}){
  const o = normalizeOptions(opts); const p=o.p; const t=T[o.lang]; const dir=o.lang==="ar"?"rtl":"ltr";
  const ink = p.dark?"#f8fafc":"#0f172a", muted = p.dark?"#9fb0c7":"#475569";
  const line = p.dark?"rgba(255,255,255,.14)":"rgba(15,23,42,.14)";
  const panel = p.dark?"rgba(10,22,41,.78)":"rgba(255,255,255,.86)";
  const inputBg = p.dark?"#020817aa":"#ffffffd9";
  const btnInk = p.dark?"#06111f":"#ffffff";
  const split = o.layout==="split", minimal=o.layout==="minimal";
  const logo = o.logo
    ? `<img class="logo" src="${esc(o.logo)}" alt="" style="object-fit:contain;background:#fff">`
    : `<span class="logo"><svg viewBox="0 0 24 24"><path d="M12 2a9 9 0 0 0-9 9v3a3 3 0 0 0 3 3h2v-7H5a7 7 0 0 1 14 0h-3v7h2a3 3 0 0 0 3-3v-3a9 9 0 0 0-9-9Zm-1 17h2v3h-2v-3Z"/></svg></span>`;
  const plans = o.plans.length ? `<div class="plans"><h2>${esc(t.plans)}</h2><div class="plan-grid">${o.plans.map(x=>`<button type="button" class="plan" data-plan="${esc(x.name)}"><b>${esc(x.name)}</b>${esc(x.note)}</button>`).join("")}</div></div>` : "";
  const wa = o.whatsapp ? `<a class="wa" id="wa" href="https://wa.me/${o.whatsapp}" target="_blank" rel="noopener">${esc(t.wa)} — +${o.whatsapp}</a>` : "";
  const qr = o.showQrHint ? `<p class="qrhint">${esc(t.qr)}</p>` : "";
  const footer = o.footer ? `<div class="foot">${esc(o.footer)}</div>` : "";
  const userPh = o.pinMode ? t.pin : t.user;
  const passField = o.pinMode ? "" : `<label class="field"><input type="password" name="password" autocomplete="current-password" placeholder="${esc(t.pass)}" required><svg viewBox="0 0 24 24"><path d="M17 8h-1V6a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2Zm-5 9a2 2 0 1 1 0-4 2 2 0 0 1 0 4Zm2-9h-4V6a2 2 0 1 1 4 0v2Z"/></svg></label>`;
  const pinHidden = o.pinMode ? `<input type="hidden" name="password" value="">` : "";
  const side = split ? `<aside class="side"><div class="sidein">${logo}<h1>${esc(o.brand)}</h1><p>${esc(o.subtitle)}</p></div></aside>` : "";
  const head = split ? "" : `<header class="brand">${logo}<strong>${esc(o.brand)}</strong></header><p class="sub">${esc(o.subtitle)}</p>`;
  const bgCss = minimal ? `background:${p.base}` : `background:radial-gradient(circle at 15% 10%,${p.bg1} 0,transparent 36%),radial-gradient(circle at 90% 90%,${p.bg2} 0,transparent 38%),${p.base}`;
  const css = `:root{--ink:${ink};--muted:${muted};--line:${line};--panel:${panel};--accent:${o.accent};--accent2:${o.accent2};--danger:#e11d48}*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:18px;display:grid;place-items:center;${bgCss};color:var(--ink);font-family:Tahoma,Arial,sans-serif}.shell{width:min(${split?"880px":"460px"},100%);${split?"display:grid;grid-template-columns:1fr 1fr;border-radius:30px;overflow:hidden;border:1px solid var(--line);box-shadow:0 30px 100px #0004":""}}.card{${split?"padding:30px;background:var(--panel)":"border:1px solid var(--line);border-radius:30px;padding:26px;background:var(--panel);backdrop-filter:blur(22px);box-shadow:0 30px 100px #0004"}}.side{display:grid;place-items:center;padding:30px;background:linear-gradient(135deg,var(--accent),var(--accent2));color:${btnInk}}.sidein{text-align:center}.sidein h1{margin:14px 0 6px;font-size:26px}.sidein p{margin:0;opacity:.9;font-size:14px}.brand{display:flex;align-items:center;gap:12px;margin-bottom:6px}.logo{width:48px;height:48px;border-radius:16px;display:inline-grid;place-items:center;background:linear-gradient(135deg,var(--accent),var(--accent2))}.logo svg{width:26px;fill:${btnInk}}.brand strong{font-size:24px}.sub{color:var(--muted);font-size:13px;margin:0 0 18px}.status{min-height:22px;color:var(--danger);font-size:13px;margin:8px 0}.field{position:relative;display:block;margin:11px 0}.field svg{position:absolute;${dir==="rtl"?"right":"left"}:15px;top:15px;width:20px;fill:var(--muted);transition:.2s}.field input{width:100%;height:52px;padding:0 ${dir==="rtl"?"48px 0 14px":"14px 0 48px"};border:1px solid var(--line);border-radius:16px;background:${inputBg};color:var(--ink);font-size:15px;outline:0;transition:.22s}.field input:focus{border-color:var(--accent);box-shadow:0 0 0 4px ${o.accent}33}.field input:focus+svg{fill:var(--accent)}button,.wa{width:100%;border:0;border-radius:16px;min-height:52px;font-weight:800;font-size:15px;cursor:pointer;transition:.18s;font-family:inherit}.primary{margin-top:8px;background:linear-gradient(135deg,var(--accent),var(--accent2));color:${btnInk};box-shadow:0 12px 28px ${o.accent}44}.primary:active,.plan:active,.wa:active{transform:scale(.98)}.plans{margin-top:20px}.plans h2{font-size:15px;margin:0 0 10px}.plan-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(90px,1fr));gap:8px}.plan{border:1px solid var(--line);border-radius:15px;background:transparent;color:var(--ink);padding:11px 6px;font-size:12px;min-height:0}.plan.selected{border-color:var(--accent);background:${o.accent}22}.plan b{display:block;font-size:14px;margin-bottom:4px}.wa{display:flex;align-items:center;justify-content:center;margin-top:13px;text-decoration:none;background:#25d366;color:#052512;font-size:13px;padding:0 10px;text-align:center}.qrhint{color:var(--muted);font-size:12px;text-align:center;margin:14px 0 0}.foot{text-align:center;color:var(--muted);font-size:11px;margin-top:14px}@media(max-width:700px){.shell{grid-template-columns:1fr}.side{padding:20px}}`;
  const js = `(function(){var T=${JSON.stringify({going:t.going,waMsg:t.waMsg})};var wa=document.getElementById('wa');document.querySelectorAll('.plan').forEach(function(b){b.addEventListener('click',function(){document.querySelectorAll('.plan').forEach(function(x){x.classList.remove('selected')});b.classList.add('selected');if(wa)wa.href=wa.href.split('?')[0]+'?text='+encodeURIComponent(T.waMsg+b.dataset.plan);});});try{var q=new URLSearchParams(location.search),u=q.get('username'),p=q.get('password'),f=document.forms[0];if(u)f.username.value=u;if(p)f.password.value=p;else if(u&&!q.has('password'))f.password.value=u;${o.pinMode?"var pn=f.username;f.addEventListener('submit',function(){f.password.value=pn.value;});":""}if(u&&history.replaceState)history.replaceState(null,'',location.pathname);var st=document.querySelector('.status')||{},msg=String(st.textContent||'').trim(),failed=msg!==''&&msg.indexOf('$(')!==0,key='mmauto:'+u,now=Date.now(),last=Number(sessionStorage.getItem(key)||0);if(q.get('auto')==='1'&&u&&f.password.value&&!failed&&now-last>15000){sessionStorage.setItem(key,String(now));var b=f.querySelector('button[type=submit]');if(b)b.textContent=T.going;f.submit();}}catch(e){}})();`;
  return `<!doctype html>
<html lang="${o.lang}" dir="${dir}">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>${esc(o.brand)}</title>
<style>${css}</style></head><body><main class="shell">${side}<section class="card">${head}<div class="status">$(error)</div><form action="$(link-login-only)" method="post" autocomplete="on"><input type="hidden" name="dst" value="$(link-orig)">${pinHidden}<label class="field"><input name="username" autocomplete="username" placeholder="${esc(userPh)}" required><svg viewBox="0 0 24 24"><path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-5 0-9 2.5-9 5.5V22h18v-2.5c0-3-4-5.5-9-5.5Z"/></svg></label>${passField}<button class="primary" type="submit">${esc(t.go)}</button></form>${plans}${wa}${qr}${footer}</section></main><script>${js}</script></body></html>`;
}
