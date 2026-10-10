import test from "node:test";
import assert from "node:assert/strict";
import { classifyIp, isAddressAllowed, assertRouterTarget, validHostname, parsePorts, guardedLookup, safeGet } from "../src/netguard.js";

const cfgPublic = { allowPrivate: false, routerPorts: [8728, 8729], portalPorts: [80, 443] };
const cfgLan = { ...cfgPublic, allowPrivate: true };

test("classifyIp", () => {
  for (const ip of ["127.0.0.1", "0.0.0.0", "169.254.169.254", "::1", "fe80::1", "224.0.0.1", "::ffff:127.0.0.1", "not-an-ip"]) assert.equal(classifyIp(ip), "blocked", ip);
  for (const ip of ["10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.88.1", "100.64.0.1", "fd00::1"]) assert.equal(classifyIp(ip), "private", ip);
  for (const ip of ["8.8.8.8", "172.32.0.1", "172.15.0.1", "1.1.1.1", "2606:4700::1111"]) assert.equal(classifyIp(ip), "public", ip);
});
test("private only allowed for local deployments, blocked never", () => {
  assert.equal(isAddressAllowed("192.168.88.1", cfgPublic), false);
  assert.equal(isAddressAllowed("192.168.88.1", cfgLan), true);
  assert.equal(isAddressAllowed("169.254.169.254", cfgLan), false);
  assert.equal(isAddressAllowed("127.0.0.1", cfgLan), false);
});
test("assertRouterTarget", () => {
  assert.deepEqual(assertRouterTarget("8.8.8.8", 8729, cfgPublic), { host: "8.8.8.8", port: 8729 });
  assert.throws(() => assertRouterTarget("8.8.8.8", 22, cfgPublic), /PORT_NOT_ALLOWED/);
  assert.throws(() => assertRouterTarget("127.0.0.1", 8728, cfgPublic), /HOST_NOT_ALLOWED/);
  assert.throws(() => assertRouterTarget("192.168.1.1", 8728, cfgPublic), /HOST_NOT_ALLOWED/);
  assert.doesNotThrow(() => assertRouterTarget("192.168.1.1", 8728, cfgLan));
  assert.throws(() => assertRouterTarget("bad host!", 8728, cfgPublic), /INVALID_HOST/);
  assert.doesNotThrow(() => assertRouterTarget("router.example.com", 8728, cfgPublic));
});
test("validHostname / parsePorts", () => {
  assert.equal(validHostname("a.b-c.example.com"), true);
  assert.equal(validHostname("-bad.com"), false);
  assert.equal(validHostname("a b"), false);
  assert.deepEqual(parsePorts("8728, 8729,x,99999", [1]), [8728, 8729]);
  assert.deepEqual(parsePorts("", [1]), [1]);
});
test("guardedLookup blocks localhost at connect time", async () => {
  const lookup = guardedLookup(cfgPublic);
  const err = await new Promise(r => lookup("localhost", {}, e => r(e)));
  assert.ok(err); assert.equal(err.code, "HOST_NOT_ALLOWED");
});
test("safeGet rejects loopback, bad ports and metadata", async () => {
  await assert.rejects(safeGet("http://127.0.0.1:80/", { cfg: cfgPublic }), /HOST_NOT_ALLOWED/);
  await assert.rejects(safeGet("http://example.com:22/", { cfg: cfgPublic }), /PORT_NOT_ALLOWED/);
  await assert.rejects(safeGet("ftp://example.com/", { cfg: cfgPublic }), /HTTP_OR_HTTPS/);
  await assert.rejects(safeGet("http://169.254.169.254/latest/meta-data", { cfg: cfgLan }), /HOST_NOT_ALLOWED/);
});
