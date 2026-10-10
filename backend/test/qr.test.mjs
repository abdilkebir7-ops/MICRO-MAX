import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { buildQrUrl, parseQr, verifyQr, qrSecretFrom, buildWifiQr } from "../src/cards.js";
import { analyzeLoginHtml } from "../src/qrcheck.js";

const secret = qrSecretFrom("test-key");
const portal = "http://192.168.88.1/login";
const cardId = "123e4567-e89b-12d3-a456-426614174000";
const tpl = f => fs.readFileSync(new URL(`../../hotspot-templates/${f}`, import.meta.url), "utf8");

// Runs the REAL shipped template script against a stubbed browser, exactly as a phone would after scanning the QR.
function scan(file, qrUrl, { errorText = "$(error)", storage = new Map(), now = 1_000_000 } = {}) {
  const html = tpl(file);
  const u = new URL(qrUrl);
  const form = { username: { value: "", addEventListener() {} }, password: { value: "" }, submitted: 0, addEventListener() {}, querySelector: () => ({ textContent: "" }), submit() { this.submitted++; } };
  const calls = { replaceState: [] };
  const sandbox = {
    location: { search: u.search, pathname: u.pathname },
    history: { replaceState: (...a) => calls.replaceState.push(a) },
    sessionStorage: { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v) },
    document: { forms: [form], getElementById: () => ({ href: "" }), querySelectorAll: () => [], querySelector: () => ({ textContent: errorText }) },
    Date: { now: () => now }, URLSearchParams, encodeURIComponent, Number, String
  };
  vm.createContext(sandbox);
  for (const m of html.matchAll(/<script>([\s\S]*?)<\/script>/g)) vm.runInContext(m[1], sandbox);
  return { form, calls, storage };
}

test("user+password QR: scanning logs in with no typing (login.html)", () => {
  const qr = buildQrUrl({ portalUrl: portal, username: "MM123456", password: "ABC234XY", cardId, secret });
  const { form, calls } = scan("login.html", qr);
  assert.equal(form.submitted, 1);
  assert.equal(form.username.value, "MM123456");
  assert.equal(form.password.value, "ABC234XY");
  assert.equal(calls.replaceState.length, 1, "credentials stripped from the address bar / history");
  assert.equal(calls.replaceState[0][2], "/login");
});
test("code-only (PIN) QR carries no password and still logs in (login.html and login-code.html)", () => {
  const qr = buildQrUrl({ portalUrl: portal, username: "MM12345678", password: "", cardId, secret });
  assert.ok(!new URL(qr).searchParams.has("password"));
  for (const f of ["login.html", "login-code.html"]) {
    const { form } = scan(f, qr);
    assert.equal(form.submitted, 1, f);
    assert.equal(form.username.value, "MM12345678", f);
    assert.equal(form.password.value, "MM12345678", f);
  }
});
test("no auto-submit after a failed login, and loop protection within 15s", () => {
  const qr = buildQrUrl({ portalUrl: portal, username: "MM1", password: "pw123456", cardId, secret });
  assert.equal(scan("login.html", qr, { errorText: "invalid username or password" }).form.submitted, 0);
  const storage = new Map();
  assert.equal(scan("login.html", qr, { storage, now: 1_000_000 }).form.submitted, 1);
  assert.equal(scan("login.html", qr, { storage, now: 1_005_000 }).form.submitted, 0);
  assert.equal(scan("login.html", qr, { storage, now: 1_020_000 }).form.submitted, 1, "a later re-scan works again");
});
test("QR without auto=1 only prefills", () => {
  const qr = buildQrUrl({ portalUrl: portal, username: "MM1", password: "pw123456", cardId, secret }).replace("&auto=1", "");
  const { form } = scan("login.html", qr);
  assert.equal(form.submitted, 0);
  assert.equal(form.password.value, "pw123456");
});
test("QR content round-trips and signature detects tampering", () => {
  const qr = buildQrUrl({ portalUrl: portal, username: "MM123456", password: "ABC234XY", cardId, secret });
  const p = parseQr(qr);
  assert.equal(p.username, "MM123456"); assert.equal(p.password, "ABC234XY"); assert.equal(p.cardId, cardId);
  assert.equal(verifyQr(secret, p), true);
  assert.equal(verifyQr(secret, { ...p, username: "MM999999" }), false);
  assert.equal(new URL(qr).searchParams.get("dst"), "http://192.168.88.1/");
});
test("special characters in credentials survive URL encoding", () => {
  const qr = buildQrUrl({ portalUrl: portal, username: "MM1", password: "a&b=c d", cardId, secret });
  assert.equal(parseQr(qr).password, "a&b=c d");
  assert.equal(scan("login.html", qr).form.password.value, "a&b=c d");
});
test("Wi-Fi QR format", () => {
  assert.equal(buildWifiQr("Cafe WiFi"), "WIFI:T:nopass;S:Cafe WiFi;;");
  assert.equal(buildWifiQr("A;B"), "WIFI:T:nopass;S:A\\;B;;");
  assert.equal(buildWifiQr(""), null);
  assert.equal(buildWifiQr("x".repeat(33)), null);
});
test("static analysis: shipped templates are auto-login capable, a plain login page is not", () => {
  for (const f of ["login.html", "login-code.html"]) {
    const a = analyzeLoginHtml(tpl(f));
    assert.equal(a.autoLogin, true, f);
    assert.equal(a.stripsCredentials, true, f);
    assert.deepEqual(a.warnings, [], f);
  }
  const plain = '<form action="$(link-login-only)" method="post"><input name="username"><input name="password"></form>';
  const b = analyzeLoginHtml(plain);
  assert.equal(b.autoLogin, false);
  assert.ok(b.warnings.includes("LOGIN_HTML_DOES_NOT_AUTO_SUBMIT_FROM_QR"));
  assert.ok(analyzeLoginHtml("<html></html>").warnings.includes("LOGIN_FORM_NOT_DETECTED"));
});
