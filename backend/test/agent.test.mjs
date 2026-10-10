import test from "node:test";
import assert from "node:assert/strict";
import { newAgentToken, hashToken, tokenMatches, rosSafe, renderJobScript, parseReport, buildEnrollScript, buildDirectSetupScript, generateCredentials, normalizeBaseUrl } from "../src/agent.js";

const base = "https://example.onrender.com";
const uuid = "123e4567-e89b-12d3-a456-426614174000";

test("token generation and matching", () => {
  const t = newAgentToken();
  assert.match(t, /^mmag_[A-Za-z0-9_-]{40,}$/);
  assert.equal(tokenMatches(t, hashToken(t)), true);
  assert.equal(tokenMatches(t + "x", hashToken(t)), false);
  assert.equal(tokenMatches(t, "zz"), false);
});
test("rosSafe rejects script-injection characters", () => {
  for (const bad of ['a"b', "a$b", "a[b", "a]b", "a\\b", "a;b", "a\nb", "a`b", "x".repeat(65)]) assert.throws(() => rosSafe(bad), /UNSAFE_VALUE/, bad);
  assert.equal(rosSafe("Plan 1GB_ok-1.5@x:y"), "Plan 1GB_ok-1.5@x:y");
  assert.equal(rosSafe("باقة يومية"), "باقة يومية");
});
test("renderJobScript builds idempotent add and remove with acks", () => {
  const t = newAgentToken();
  const out = renderJobScript([
    { id: uuid, type: "hotspot-user-add", payload: { username: "MM123456", password: "ABC234", profile: "1 Day", comment: "MICRO-MAX:" + uuid } },
    { id: uuid, type: "hotspot-user-remove", payload: { username: "MM123456" } }
  ], { baseUrl: base, token: t });
  assert.match(out, /\[:len \[\/ip hotspot user find where name="MM123456"\]\] = 0/);
  assert.match(out, /\/ip hotspot user remove \[find where name="MM123456"\]/);
  assert.match(out, /api\/agent\/ack\?j=123e4567[^"]*&s=ok/);
  assert.match(out, /check-certificate=yes-without-crl/);
});
test("renderJobScript refuses unsafe values, unknown types, bad ids and http", () => {
  const t = newAgentToken();
  assert.throws(() => renderJobScript([{ id: uuid, type: "hotspot-user-add", payload: { username: 'x"; /system reset-configuration; "', password: "a", profile: "p" } }], { baseUrl: base, token: t }), /UNSAFE_VALUE/);
  assert.throws(() => renderJobScript([{ id: uuid, type: "system-reset", payload: {} }], { baseUrl: base, token: t }), /UNKNOWN_JOB_TYPE/);
  assert.throws(() => renderJobScript([{ id: "x", type: "hotspot-user-remove", payload: { username: "a" } }], { baseUrl: base, token: t }), /BAD_JOB_ID/);
  assert.throws(() => renderJobScript([], { baseUrl: "http://x.com", token: t }), /HTTPS/);
  assert.throws(() => renderJobScript([], { baseUrl: base, token: "bad" }), /BAD_TOKEN/);
});
test("renderJobScript with no jobs is a harmless comment", () => {
  assert.equal(renderJobScript([], { baseUrl: base, token: newAgentToken() }).trim(), "# MICRO-MAX jobs (0)");
});
test("parseReport keeps only known keys and sanitizes", () => {
  const r = parseReport("v=7.15 (stable)&board=hAP ax2&cpu=12&uptime=1w2d3h&active=5&name=Shop 1&evil=1&cpu2=9");
  assert.deepEqual(r, { v: "7.15 (stable)", board: "hAP ax2", cpu: 12, uptime: "1w2d3h", active: 5, name: "Shop 1" });
  assert.equal(parseReport("cpu=abc").cpu, 0);
  assert.deepEqual(parseReport(null), {});
  assert.equal(parseReport("name=a\"$[x]").name, "ax");
});
test("enroll script: structure and escaping inside source string", () => {
  const t = newAgentToken();
  const s = buildEnrollScript({ baseUrl: base, token: t, intervalSec: 5000 });
  assert.match(s, /\/system scheduler add name=micromax-agent interval=300s start-time=startup/);
  assert.match(s, /certificate import/);
  const m = s.match(/\/system script add name=micromax-agent policy=\S+ source="(.*)"\n/);
  assert.ok(m, "script add line present on a single line");
  const src = m[1];
  assert.ok(!/(^|[^\\])\$/.test(src), "every $ is escaped");
  assert.ok(!/(^|[^\\])"/.test(src), "every quote is escaped");
  assert.ok(src.includes(t) && src.includes(base) && src.includes('/api/agent/sync'));
  assert.equal(buildEnrollScript({ baseUrl: base, token: t, includeCa: false }).includes("cacert"), false);
  assert.match(buildEnrollScript({ baseUrl: base, token: t, intervalSec: 1 }), /interval=10s/);
});
test("direct (API-SSL) setup script", () => {
  const s = buildDirectSetupScript({ allowFrom: ["203.0.113.5", "198.51.100.0/24"], apiPass: "Abcdefghijkl1234" });
  assert.match(s, /\/ip service set api-ssl certificate=micromax-api port=8729 disabled=no address=203\.0\.113\.5,198\.51\.100\.0\/24/);
  assert.match(s, /\/ip service set api disabled=yes/);
  assert.match(s, /fingerprint/);
  assert.throws(() => buildDirectSetupScript({ allowFrom: ["1.1.1.1; /x"], apiPass: "Abcdefghijkl1234" }), /BAD_CIDR/);
  assert.throws(() => buildDirectSetupScript({ apiPass: "short" }), /WEAK_PASSWORD/);
  assert.throws(() => buildDirectSetupScript({ apiPass: 'Abcdefghijkl"1234' }), /UNSAFE_VALUE/);
  assert.doesNotThrow(() => buildDirectSetupScript({ apiPass: "Abcdefghijkl1234" }));
});
test("normalizeBaseUrl", () => {
  assert.equal(normalizeBaseUrl("https://a.com/path?x=1"), "https://a.com");
  assert.throws(() => normalizeBaseUrl("http://a.com"), /HTTPS/);
});
test("generateCredentials: unique, avoids taken, honors pin mode and space limits", () => {
  const c = generateCredentials({ count: 500, prefix: "MM", digits: 4, letters: 0, passLen: 8 });
  assert.equal(new Set(c.map(x => x.username)).size, 500);
  assert.ok(c.every(x => /^MM\d{4}$/.test(x.username) && x.password.length === 8));
  const pin = generateCredentials({ count: 5, prefix: "MM", digits: 8, letters: 0, pin: true });
  assert.ok(pin.every(x => x.password === x.username));
  const taken = new Set(Array.from({ length: 9990 }, (_, i) => "MM" + String(i).padStart(4, "0")));
  const rest = generateCredentials({ count: 10, prefix: "MM", digits: 4, letters: 0, taken });
  assert.ok(rest.every(x => !taken.has(x.username)));
  assert.throws(() => generateCredentials({ count: 20, prefix: "MM", digits: 1, letters: 0 }), /EXHAUSTED/);
});

test("hotspot-profile-set job: idempotent add-or-set, validated values", () => {
  const t = newAgentToken();
  const out = renderJobScript([{ id: uuid, type: "hotspot-profile-set", payload: { name: "يومي-2M", sharedUsers: 1, rateLimit: "2M/5M", sessionTimeout: "1d", idleTimeout: "10m" } }], { baseUrl: base, token: t });
  assert.match(out, /\/ip hotspot user profile find where name="يومي-2M"\]\] = 0/);
  assert.match(out, /profile add name="يومي-2M" shared-users=1 rate-limit=2M\/5M session-timeout=1d idle-timeout=10m/);
  assert.match(out, /else=\{ \/ip hotspot user profile set \[find where name="يومي-2M"\]/);
  assert.match(out, /s=ok/);
  const bad = p => () => renderJobScript([{ id: uuid, type: "hotspot-profile-set", payload: { name: "x", ...p } }], { baseUrl: base, token: t });
  assert.throws(bad({ rateLimit: "2M/5M; /system reset-configuration" }), /UNSAFE_VALUE/);
  assert.throws(bad({ sessionTimeout: "1d\"; x" }), /UNSAFE_VALUE/);
  assert.throws(bad({ sharedUsers: 0 }), /UNSAFE_VALUE/);
  assert.throws(bad({ sharedUsers: 5000 }), /UNSAFE_VALUE/);
  assert.throws(() => renderJobScript([{ id: uuid, type: "hotspot-profile-set", payload: { name: 'a"b' } }], { baseUrl: base, token: t }), /UNSAFE_VALUE/);
});

import { agentSource, AGENT_POLICY } from "../src/agent.js";
test("agentSource is the raw (unescaped) script pushed through the RouterOS API", () => {
  const t = newAgentToken();
  const src = agentSource({ baseUrl: base, token: t });
  assert.ok(src.includes('\n') && !src.includes('\\n'), "real newlines");
  assert.ok(src.includes(`:local tk "${t}";`) && src.includes(`${base}"`));
  assert.ok(src.includes('$d') && !src.includes('\\$'), "no terminal-style escaping");
  assert.equal(AGENT_POLICY, "read,write,test,policy,ftp,sensitive");
  assert.throws(() => agentSource({ baseUrl: base, token: "bad" }), /BAD_TOKEN/);
});
