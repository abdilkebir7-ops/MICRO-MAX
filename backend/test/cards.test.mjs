import assert from "node:assert/strict";
import test from "node:test";
import { buildGenScript, generateBatch, buildQrUrl, parseQr, verifyQr, qrSecretFrom, signQr, parseRosDuration, classifyRouterUser, auditCards, scanVerdict, usernameSpace, usernameRegex, buildWifiQr, assertPinStrength, assertSafeProfile } from "../src/cards.js";

// Fake RouterOS: emulates the generation script by creating random users on "the router".
function fakeRos({ forceNames = [] } = {}) {
  const users = []; const scripts = []; let id = 1; let forced = [...forceNames];
  return {
    users,
    async command(path, args = []) {
      const a = Object.fromEntries(args.filter(x => x.startsWith("=")).map(x => [x.slice(1, x.indexOf("=", 1)), x.slice(x.indexOf("=", 1) + 1)]));
      if (path === "/system/script/add") { scripts.push({ ".id": `*S${id++}`, name: a.name, source: a.source }); return []; }
      if (path === "/system/script/run") {
        const src = scripts.find(s => s.name === a.number).source;
        const n = Number(/\$made < (\d+)/.exec(src)[1]); const comment = /comment="([^"]+)"/.exec(src)[1]; const pfx = /\(\("?|\(\"([A-Za-z0-9]+)\"/.exec(src)?.[1] || "MM";
        for (let i = 0; i < n; i++) { const name = forced.length ? forced.shift() : pfx + String(Math.floor(Math.random() * 1e6)).padStart(6, "0"); if (!users.some(u => u.name === name)) users.push({ ".id": `*U${id++}`, name, password: "PW" + i + "X", comment }); }
        return [];
      }
      if (path === "/system/script/print") return scripts.filter(s => s.name === args[0].slice(6));
      if (path === "/system/script/remove") { const i = scripts.findIndex(s => s[".id"] === a[".id"]); if (i >= 0) scripts.splice(i, 1); return []; }
      if (path === "/ip/hotspot/user/print") return users.filter(u => !args[0] || u.comment === args[0].slice(9));
      if (path === "/ip/hotspot/user/remove") { const i = users.findIndex(u => u[".id"] === a[".id"]); if (i >= 0) users.splice(i, 1); return []; }
      throw new Error("unexpected " + path);
    }
  };
}

test("script is generated for RouterOS with MikroTik-side random + collision guard", () => {
  const s = buildGenScript({ count: 5, prefix: "kmx", digits: 6, letters: 0, passLen: 8, pin: false, profile: "1H", comment: "MICRO-MAX:abc" });
  assert.match(s, /:rndstr length=6 from="0123456789"/);
  assert.match(s, /:rndstr length=8 from=/);
  assert.match(s, /find where name=\$u/);
  assert.match(s, /profile="1H"/);
});

test("unsafe profile names are rejected (no script injection)", () => {
  assert.throws(() => assertSafeProfile('x"; /system reset-configuration; "'), /UNSAFE/);
  assert.throws(() => buildGenScript({ count: 1, prefix: "A", digits: 4, letters: 0, passLen: 6, profile: 'a"b', comment: "MICRO-MAX:x" }), /UNSAFE/);
});

test("duplicates against DB are removed on router and regenerated until count is met", async () => {
  const ros = fakeRos({ forceNames: ["KMX000001", "KMX000002", "KMX000003"] });
  const inDb = new Set(["KMX000001", "KMX000002"]);
  const made = await generateBatch({ ros, count: 3, prefix: "KMX", digits: 6, letters: 0, profile: "1H", batchId: "b1", dbHas: async n => new Set(n.filter(x => inDb.has(x))) });
  assert.equal(made.length, 3);
  assert.equal(new Set(made.map(m => m.username)).size, 3);
  assert.ok(made.every(m => !inDb.has(m.username)));
  assert.equal(ros.users.length, 3); // colliding users were removed from the router
});

test("exhausted pool fails loudly", async () => {
  const ros = fakeRos({ forceNames: Array(60).fill("KMX000001") });
  await assert.rejects(generateBatch({ ros, count: 2, prefix: "KMX", digits: 6, letters: 0, profile: "1H", batchId: "b2", dbHas: async n => new Set(n), maxRounds: 2 }), /USERNAME_POOL_EXHAUSTED/);
});

test("signed QR is bound to the card and detects tampering", () => {
  const secret = qrSecretFrom("k".repeat(40));
  const url = buildQrUrl({ portalUrl: "http://10.5.50.1/login", username: "KMX123456", password: "Ab3dEf9h", cardId: "card-1", secret });
  const p = parseQr(url);
  assert.equal(p.username, "KMX123456"); assert.equal(p.password, "Ab3dEf9h"); assert.equal(p.cardId, "card-1");
  assert.ok(verifyQr(secret, p));
  assert.ok(!verifyQr(secret, { ...p, username: "KMX999999" }));
  assert.ok(!verifyQr(qrSecretFrom("other".repeat(10)), p));
  assert.equal(signQr(secret, "card-1", "KMX123456").length, 16);
});

test("router state detection", () => {
  assert.equal(parseRosDuration("1w2d3h4m5s"), 604800 + 172800 + 10800 + 240 + 5);
  assert.equal(classifyRouterUser({ name: "a", uptime: "0s" }), "unused");
  assert.equal(classifyRouterUser({ name: "a", uptime: "5m" }), "used");
  assert.equal(classifyRouterUser({ name: "a", uptime: "1h", "limit-uptime": "1h" }), "expired");
  assert.equal(classifyRouterUser({ name: "a", disabled: "true" }), "disabled");
  assert.equal(classifyRouterUser({ name: "a", uptime: "0s" }, new Set(["a"])), "active");
});

test("audit finds missing, orphan, mismatch and drift", () => {
  const r = auditCards({
    routerUsers: [{ name: "A1", password: "p1", uptime: "0s", comment: "MICRO-MAX:x" }, { name: "A2", password: "zz", uptime: "10m" }, { name: "ORPH", comment: "MICRO-MAX:y" }],
    dbCards: [{ username: "A1", password: "p1", status: "available" }, { username: "A2", password: "p2", status: "available" }, { username: "GONE", password: "p", status: "available" }]
  });
  assert.equal(r.healthy, false);
  assert.deepEqual(r.issues.missingOnRouter, ["GONE"]);
  assert.deepEqual(r.issues.orphanOnRouter, ["ORPH"]);
  assert.deepEqual(r.issues.passwordMismatch, ["A2"]);
  assert.equal(r.issues.statusDrift[0].routerState, "used");
});

test("scan verdicts", () => {
  const secret = qrSecretFrom("s".repeat(40));
  const card = { id: "c1", username: "U1", status: "available" };
  const qr = parseQr(buildQrUrl({ portalUrl: "http://h/login", username: "U1", password: "p", cardId: "c1", secret }));
  assert.equal(scanVerdict({ card, routerUser: { name: "U1", uptime: "0s" }, qrParsed: qr, secret }).verdict, "VALID_UNUSED");
  assert.equal(scanVerdict({ card, routerUser: { name: "U1", uptime: "0s" }, qrParsed: { ...qr, sig: "bad" }, secret }).verdict, "QR_TAMPERED");
  assert.equal(scanVerdict({ card, routerUser: { name: "U1", uptime: "3m" }, secret }).verdict, "USED");
  assert.equal(scanVerdict({ card, routerUser: null, secret }).verdict, "MISSING_ON_ROUTER");
  assert.equal(scanVerdict({ card: null, secret }).verdict, "NOT_FOUND");
});

test("username space matches the real alphabet (24 letters)", () => {
  assert.equal(usernameSpace(1, 2), 2400);
  assert.ok(usernameRegex("KMX", 1, 2).test("KMXA12"));
  assert.ok(!usernameRegex("KMX", 1, 2).test("KMXI12"));
});

test("wifi join QR for the open hotspot network", () => {
  assert.equal(buildWifiQr("MICRO-MAX"), "WIFI:T:nopass;S:MICRO-MAX;;");
  assert.equal(buildWifiQr("My;Net"), "WIFI:T:nopass;S:My\\;Net;;");
  assert.equal(buildWifiQr(""), null);
});

test("code-only (pin) cards: strength rule and QR without password", () => {
  assert.throws(() => assertPinStrength(0, 6), /PIN_MODE_CODE_TOO_SHORT/);
  assert.doesNotThrow(() => assertPinStrength(0, 8));
  assert.doesNotThrow(() => assertPinStrength(4, 3));
  const secret = qrSecretFrom("p".repeat(40));
  const url = buildQrUrl({ portalUrl: "http://10.5.50.1/login", username: "KMX12345678", password: "", cardId: "c9", secret });
  assert.ok(!url.includes("password="));
  const p = parseQr(url);
  assert.equal(p.password, ""); assert.ok(verifyQr(secret, p));
  assert.match(buildGenScript({ count: 1, prefix: "K", digits: 8, letters: 0, passLen: 8, pin: true, profile: "1H", comment: "MICRO-MAX:x" }), /:local p \$u/);
});
