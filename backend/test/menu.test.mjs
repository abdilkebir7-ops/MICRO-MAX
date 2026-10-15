import test from "node:test"; import assert from "node:assert/strict";
import { MENUS, buildAction, buildPrint, sanitizeValues, shapeItems, MenuError, menuList } from "../src/menu.js";
const code = f => { try{ f(); }catch(e){ return e.code; } return null; };
test("print builds", () => { assert.equal(buildPrint("ip-address").path,"/ip/address/print"); assert.equal(code(()=>buildPrint("../x")),"MENU_NOT_FOUND"); });
test("add/set/remove/enable", () => {
  assert.deepEqual(buildAction("hotspot-users","add",null,{name:"u1",password:"p",profile:"default"}),{path:"/ip/hotspot/user/add",args:["=name=u1","=password=p","=profile=default"]});
  assert.deepEqual(buildAction("ip-address","disable","*1A"),{path:"/ip/address/disable",args:["=.id=*1A"]});
  assert.deepEqual(buildAction("identity","set",null,{name:"R1"}),{path:"/system/identity/set",args:["=name=R1"]});
  assert.equal(buildAction("queues","set","*5",{"max-limit":"2M/2M"}).args[0],"=.id=*5");
});
test("rejections", () => {
  assert.equal(code(()=>buildAction("log","remove","*1")),"ACTION_NOT_ALLOWED");
  assert.equal(code(()=>buildAction("arp","add",null,{a:1})),"ACTION_NOT_ALLOWED");
  assert.equal(code(()=>buildAction("queues","remove","1; /system reset")),"INVALID_ID");
  assert.equal(code(()=>buildAction("queues","remove","*")),"INVALID_ID");
  assert.equal(code(()=>buildAction("queues","set","*1",{})),"VALUES_REQUIRED");
  assert.equal(code(()=>sanitizeValues({"a=b":1})),"INVALID_KEY");
  assert.equal(code(()=>sanitizeValues({".id":"*1"})),"INVALID_KEY");
  assert.equal(code(()=>sanitizeValues({name:"a\nb"})),"INVALID_VALUE");
  assert.equal(code(()=>sanitizeValues({name:{x:1}})),"INVALID_VALUE");
  assert.equal(code(()=>sanitizeValues([1])),"INVALID_VALUE" === "x" ? "" : "INVALID_VALUES");
  assert.equal(code(()=>buildAction("nope","add",null,{a:1})),"MENU_NOT_FOUND");
});
test("every menu has path and valid actions", () => { for(const [k,m] of Object.entries(MENUS)){ assert.match(m.path,/^\/[a-z/-]+$/); for(const a of m.actions) assert.ok(["add","set","remove","enable","disable"].includes(a),k); } assert.equal(menuList().length,Object.keys(MENUS).length); });
test("shape", () => { const r=shapeItems("arp",[{".id":"*1",address:"1.1.1.1",secret:"x"}]); assert.deepEqual(r,[{".id":"*1",address:"1.1.1.1"}]); });
