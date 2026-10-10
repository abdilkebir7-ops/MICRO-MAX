import test from "node:test";
import assert from "node:assert/strict";
import { auditSecurity } from "../src/security.js";
import { fleetSummary, configDoctor } from "../src/fleet.js";

const hardened = {
  resource: { version: "7.15.3 (stable)" },
  services: [
    { name: "telnet", disabled: "true" }, { name: "ftp", disabled: "true" }, { name: "www", disabled: "true" }, { name: "api", disabled: "true" },
    { name: "winbox", disabled: "false", address: "203.0.113.5/32" }, { name: "ssh", disabled: "false", address: "203.0.113.5/32" }, { name: "api-ssl", disabled: "false", address: "198.51.100.7/32" }
  ],
  users: [{ name: "boss", disabled: "false" }, { name: "admin", disabled: "true" }],
  firewall: [{ chain: "input", action: "accept", disabled: "false" }, { chain: "input", action: "drop", disabled: "false" }],
  dns: { "allow-remote-requests": "true" }, discovery: { "discover-interface-list": "LAN" }, macServer: { "allowed-interface-list": "LAN" },
  ssh: { "strong-crypto": "true" }, hotspotProfiles: [{ "login-by": "http-pap,https" }]
};
const bad = {
  resource: { version: "6.48.6 (long-term)" },
  services: [
    { name: "telnet", disabled: "false", address: "" }, { name: "ftp", disabled: "false" }, { name: "www", disabled: "false" }, { name: "api", disabled: "false" },
    { name: "winbox", disabled: "false", address: "" }, { name: "ssh", disabled: "false", address: "0.0.0.0/0" }, { name: "api-ssl", disabled: "false", address: "" }
  ],
  users: [{ name: "admin", disabled: "false" }],
  firewall: [{ chain: "forward", action: "accept", disabled: "false" }, { chain: "input", action: "drop", disabled: "true" }],
  dns: { "allow-remote-requests": "true" }, discovery: { "discover-interface-list": "all" }, macServer: { "allowed-interface-list": "all" },
  ssh: { "strong-crypto": "false" }, hotspotProfiles: [{ "login-by": "http-pap" }]
};

test("hardened router scores A with no findings", () => {
  const r = auditSecurity(hardened, { apiTls: true });
  assert.equal(r.score, 100); assert.equal(r.grade, "A"); assert.deepEqual(r.findings, []); assert.equal(r.fixScript, "");
});
test("bad router: all key findings, low score", () => {
  const r = auditSecurity(bad, { apiTls: false });
  const ids = r.findings.map(f => f.id);
  for (const id of ["SVC_TELNET", "SVC_FTP", "SVC_WWW", "SVC_API_PLAIN", "SVC_WINBOX_OPEN", "SVC_SSH_OPEN", "SVC_APISSL_OPEN", "FW_NO_INPUT_DROP", "DNS_OPEN_RESOLVER", "DISCOVERY_ALL", "MACSERVER_ALL", "ADMIN_DEFAULT_USER", "SSH_WEAK_CRYPTO", "OS_V6", "HOTSPOT_PAP_HTTP"]) assert.ok(ids.includes(id), id);
  assert.ok(r.score <= 20, String(r.score)); assert.equal(r.grade, "F");
  assert.equal(r.counts.high, 4);
});
test("safe fix script excludes risky/needs-input fixes and never disables the API we use", () => {
  const r = auditSecurity(bad, { apiTls: false });
  assert.match(r.fixScript, /\/ip service disable telnet/);
  assert.match(r.fixScript, /\/ip service disable ftp/);
  assert.match(r.fixScript, /strong-crypto=yes/);
  assert.ok(!/disable api\b/.test(r.fixScript), "plain API (our own connection) must not be disabled");
  assert.ok(!/<[A-Z-]+/.test(r.fixScript), "no placeholders in auto-safe script");
  assert.ok(!/firewall/.test(r.fixScript) && !/user disable/.test(r.fixScript));
  const tls = auditSecurity(bad, { apiTls: true });
  assert.match(tls.fixScript, /\/ip service disable api/);
});
test("DNS finding needs missing input-drop; missing data sections are tolerated", () => {
  const ok = auditSecurity({ ...hardened, firewall: [{ chain: "input", action: "drop", disabled: "false" }] }, { apiTls: true });
  assert.ok(!ok.findings.some(f => f.id === "DNS_OPEN_RESOLVER"));
  const partial = auditSecurity({ resource: { version: "7.1" } });
  assert.equal(partial.score, 100);
  assert.doesNotThrow(() => auditSecurity({}));
});

const NOW = Date.parse("2026-10-08T12:00:00Z");
test("fleetSummary statuses and attention list", () => {
  const f = fleetSummary([
    { id: "1", name: "A", mode: "agent", agent_last_seen: "2026-10-08T11:59:30Z", agent_info: { cpu: 10, active: 7, uptime: "1d", v: "7.15", board: "hAP" } },
    { id: "2", name: "B", mode: "agent", agent_last_seen: "2026-10-08T11:50:00Z", agent_info: { cpu: 5, active: 3 } },
    { id: "3", name: "C", mode: "agent", agent_last_seen: null },
    { id: "4", name: "D", mode: "direct", probe: { ok: true, latencyMs: 40 } },
    { id: "5", name: "E", mode: "direct", probe: { ok: false } },
    { id: "6", name: "F", mode: "direct" },
    { id: "7", name: "G", mode: "agent", agent_last_seen: "2026-10-08T11:59:50Z", agent_info: { cpu: 93, active: 10 } }
  ], NOW);
  assert.deepEqual([f.total, f.online, f.offline, f.pending, f.unknown], [7, 3, 2, 1, 1]);
  assert.equal(f.activeUsers, 20);
  assert.equal(f.avgCpu, 52);                       // (10 + 93) / 2 online agents with cpu
  assert.deepEqual(f.attention.map(i => i.id).sort(), ["2", "5", "7"]);
  assert.deepEqual(f.attention.find(i => i.id === "7").issues, ["HIGH_CPU"]);
  assert.deepEqual(fleetSummary([], NOW).routers, []);
});
test("configDoctor", () => {
  const good = configDoctor({ NODE_ENV: "production", JWT_SECRET: "a".repeat(40), ROUTER_ENCRYPTION_KEY: "b".repeat(40), CORS_ORIGIN: "https://x.com", PUBLIC_BASE_URL: "https://x.onrender.com", TRUST_PROXY: "true", ALLOW_REGISTRATION: "false" });
  assert.equal(good.healthy, true); assert.equal(good.warnings, 0);
  const bad2 = configDoctor({ NODE_ENV: "production", JWT_SECRET: "short", ROUTER_ENCRYPTION_KEY: "short", CORS_ORIGIN: "*", ALLOW_INSECURE_ROUTER_TLS: "true", ALLOW_REGISTRATION: "true", RENDER: "true", ALLOW_PRIVATE_ROUTER_HOSTS: "true", ENABLE_ONLINE_PAYMENTS: "true" }, { dbOk: false });
  assert.equal(bad2.healthy, false);
  const failing = bad2.checks.filter(c => !c.ok).map(c => c.id);
  for (const id of ["DB", "JWT_SECRET", "ENC_KEY", "CORS", "PUBLIC_BASE_URL", "INSECURE_TLS", "REGISTRATION", "TRUST_PROXY", "PRIVATE_ON_CLOUD", "PAYMENTS"]) assert.ok(failing.includes(id), id);
  assert.equal(configDoctor({ JWT_SECRET: "x".repeat(40), ROUTER_ENCRYPTION_KEY: "x".repeat(40) }).checks.find(c => c.id === "SECRETS_DISTINCT").ok, false);
});
