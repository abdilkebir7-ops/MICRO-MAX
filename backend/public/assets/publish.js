/* Publish a generated login page to one of the user's routers */
window.MMPublish=(()=>{
  function modal(inner){const m=document.createElement("div");m.className="modal";m.innerHTML=`<div>${inner}</div>`;m.addEventListener("click",e=>{if(e.target===m)m.remove()});document.body.appendChild(m);return m;}
  async function open(getHtml){
    if(!MM.token){location.href="/login.html?next="+encodeURIComponent(location.pathname+location.search);return;}
    let routers;try{routers=await MM.api("/api/routers");}catch(e){return MM.fail(e);}
    routers=routers.routers||routers.items||routers; const direct=routers.filter(r=>r.mode!=="agent");
    const m=modal(`<h3>نشر القالب على راوتر</h3><p class="muted small">سيُحفظ نسخة احتياطية من الصفحة الحالية على الراوتر قبل الاستبدال، ويُنشر معها قالب صفحة الحالة الافتراضي.</p>${direct.length?`<label class="f">الراوتر</label><select id="pr">${direct.map(r=>`<option value="${r.id}">${MM.esc(r.name)} — ${MM.esc(r.host)}</option>`).join("")}</select>`:`<p style="color:var(--warn)">لا يوجد راوتر متصل مباشرة. أضف راوتراً (API) من لوحة التحكم، فالراوترات في وضع الوكيل لا تدعم نشر الملفات حالياً.</p>`}<div style="display:flex;gap:8px;margin-top:16px"><button class="btn primary" id="pgo" ${direct.length?"":"disabled"}>نشر الآن</button><button class="btn" id="pcx">إلغاء</button></div><p id="pmsg" class="small"></p>`);
    m.querySelector("#pcx").onclick=()=>m.remove();
    m.querySelector("#pgo").onclick=async()=>{const b=m.querySelector("#pgo");b.disabled=true;const msg=m.querySelector("#pmsg");msg.textContent="جارٍ النشر…";
      try{const status=await (await fetch("/api/site/status-template")).text();const id=m.querySelector("#pr").value;
        await MM.api(`/api/routers/${id}/hotspot-design/publish`,{method:"POST",body:{loginHtml:await getHtml(),statusHtml:status}});
        msg.style.color="var(--ok)";msg.textContent="✔ تم النشر بنجاح. أعد تحميل صفحة الدخول على الراوتر للتحقق.";}
      catch(e){msg.style.color="var(--danger)";msg.textContent=MM.msg(e)+(e.login?.errors?.length?" ("+e.login.errors.join(", ")+")":"");b.disabled=false;}};
  }
  return {open,modal};
})();
