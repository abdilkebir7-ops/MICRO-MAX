import test from "node:test"; import assert from "node:assert/strict"; import vm from "node:vm";
import { renderLoginHtml, PRESETS, normalizeOptions } from "../src/templates.js";
import { readFileSync } from "node:fs";
const src = readFileSync(new URL("../src/server.js", import.meta.url),"utf8");
const m = src.match(/function validateLoginHtml\(html\)\{[\s\S]*?\n\}\n/); const validate = new Function(m[0]+"return validateLoginHtml;")();
test("all presets × layouts × modes valid", () => {
  for (const p of PRESETS) for (const layout of ["card","split","minimal"]) for (const pinMode of [false,true]) for (const lang of ["ar","en"]) {
    const html = renderLoginHtml({preset:p.id,layout,pinMode,lang,whatsapp:"+218 93 009 0401",plans:[{name:"A",note:"x"}]});
    const v = validate(html); assert.equal(v.valid,true,p.id+layout); assert.deepEqual(v.warnings,[]);
    const script = html.match(/<script>([\s\S]*)<\/script>/)[1]; new vm.Script(script);
  }
});
test("injection is neutralised", () => {
  const html = renderLoginHtml({brand:'<script>alert(1)</script>$(link-login)',subtitle:'"><img onerror=x>',plans:[{name:"<b>",note:"$(x)"}],logoUrl:'javascript:alert(1)',accent:"red;}</style>"});
  assert.ok(!/<script>alert/.test(html)); assert.ok(!/<img onerror/.test(html)); assert.ok(!/javascript:/.test(html));
  assert.ok(!/\$\(link-login\)/.test(html)); assert.ok(!/red;\}/.test(html));
});
test("autologin script works in a fake DOM", () => {
  for (const pinMode of [false,true]) {
    const html = renderLoginHtml({pinMode}); const script = html.match(/<script>([\s\S]*)<\/script>/)[1];
    let submitted=0; const f = {username:{value:""},password:{value:""},addEventListener(){},submit(){submitted++;},querySelector:()=>({textContent:""})};
    const store={}; const ctx = { URLSearchParams, Date, Number, String, document:{getElementById:()=>null,querySelectorAll:()=>[],forms:[f],querySelector:()=>({textContent:"$(error)"})}, location:{search:"?username=ab12&password=zz&auto=1",pathname:"/login.html"}, history:{replaceState(){}}, sessionStorage:{getItem:k=>store[k]||null,setItem:(k,v)=>{store[k]=v;}}, encodeURIComponent };
    vm.runInNewContext(script, ctx); assert.equal(submitted,1); assert.equal(f.username.value,"ab12");
    vm.runInNewContext(script, ctx); assert.equal(submitted,1,"loop guard");
  }
});
test("normalize", () => { const o=normalizeOptions({whatsapp:"12",preset:"zzz",plans:[{name:""}]}); assert.equal(o.whatsapp,""); assert.equal(o.preset,"aurora"); assert.equal(o.plans.length,0); });
