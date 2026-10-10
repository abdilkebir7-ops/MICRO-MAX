// Remote-router "Agent" mode: the router connects OUT to this server (no open ports, works behind NAT).
// The server only ever emits a fixed whitelist of RouterOS commands, built from strictly validated values.
import crypto from "node:crypto";
import { USER_LETTERS, USER_DIGITS, PASS_ALPHABET, sanitizePrefix } from "./cards.js";

export const AGENT_JOB_TYPES = ["hotspot-user-add", "hotspot-user-remove", "hotspot-profile-set"];

export function newAgentToken() { return "mmag_" + crypto.randomBytes(32).toString("base64url"); }
export function hashToken(t) { return crypto.createHash("sha256").update(String(t)).digest("hex"); }
export function tokenMatches(token, hashHex) {
  const a = Buffer.from(hashToken(token), "hex"), b = Buffer.from(String(hashHex || ""), "hex");
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

/** Values are placed inside RouterOS double-quoted strings: `$ [ ] " \` must never appear. */
export function rosSafe(v, { max = 64 } = {}) {
  const s = String(v ?? "");
  if (s.length > max || !/^[\p{L}\p{N} ._\-+@:]*$/u.test(s)) throw new Error("UNSAFE_VALUE");
  return s;
}
function escRos(s) { return String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\$/g, "\\$"); }

export function normalizeBaseUrl(u) {
  const url = new URL(String(u));
  if (url.protocol !== "https:") throw new Error("BASE_URL_MUST_BE_HTTPS");
  return url.origin;
}

const FETCH_OPTS = 'mode=https check-certificate=yes-without-crl';

export function renderJobScript(jobs, { baseUrl, token, chunkSize = 25 }) {
  const base = normalizeBaseUrl(baseUrl);
  if (!/^mmag_[A-Za-z0-9_-]{20,}$/.test(token)) throw new Error("BAD_TOKEN");
  const ack = (ids, st) => `/tool fetch url="${base}/api/agent/ack?j=${[].concat(ids).join(",")}&s=${st}" http-header-field="X-Agent-Token: ${token}" keep-result=no ${FETCH_OPTS}`;
  const out = [`# MICRO-MAX jobs (${jobs.length})`];
  let chunk = [];
  const flush = () => {
    if (!chunk.length) return;
    const ids = chunk.map(c => c.id);
    const adds = chunk.map(c => `:do { ${c.body} } on-error={ :set bad 1 }`).join("; ");
    out.push(`:do { :local bad 0; ${adds}; :if ($bad = 0) do={ ${ack(ids, "ok")} } else={ ${ack(ids, "fail")} } } on-error={}`);
    chunk = [];
  };
  for (const j of jobs) {
    if (!/^[0-9a-f-]{36}$/.test(j.id)) throw new Error("BAD_JOB_ID");
    const p = j.payload || {};
    let body;
    if (j.type === "hotspot-user-add") {
      const u = rosSafe(p.username), pw = rosSafe(p.password), pr = rosSafe(p.profile), c = rosSafe(p.comment || "");
      const lu = p.limitUptime ? String(p.limitUptime) : "";
      if (lu && !/^\d{1,5}[smhdw]$/.test(lu)) throw new Error("UNSAFE_VALUE");
      if (!u || !pr) throw new Error("UNSAFE_VALUE");
      chunk.push({ id: j.id, body: `:if ([:len [/ip hotspot user find where name="${u}"]] = 0) do={ /ip hotspot user add name="${u}" password="${pw}" profile="${pr}" comment="${c}"${lu ? ` limit-uptime=${lu}` : ""} }` });
      if (chunk.length >= chunkSize) flush();
      continue;
    }
    flush();
    if (j.type === "hotspot-profile-set") {
      const n = rosSafe(p.name);
      if (!n) throw new Error("UNSAFE_VALUE");
      const parts = [];
      const su = Number(p.sharedUsers ?? 1);
      if (!Number.isInteger(su) || su < 1 || su > 1000) throw new Error("UNSAFE_VALUE");
      parts.push(`shared-users=${su}`);
      if (p.rateLimit) { if (!/^\d+[kKmMgG]?(\/\d+[kKmMgG]?)?$/.test(String(p.rateLimit))) throw new Error("UNSAFE_VALUE"); parts.push(`rate-limit=${p.rateLimit}`); }
      for (const [k, key] of [["sessionTimeout", "session-timeout"], ["idleTimeout", "idle-timeout"]]) {
        if (p[k]) { if (!/^(\d{1,5}[wdhms])+$/.test(String(p[k]))) throw new Error("UNSAFE_VALUE"); parts.push(`${key}=${p[k]}`); }
      }
      const attrs = parts.join(" ");
      body = `:if ([:len [/ip hotspot user profile find where name="${n}"]] = 0) do={ /ip hotspot user profile add name="${n}" ${attrs} } else={ /ip hotspot user profile set [find where name="${n}"] ${attrs} }`;
    } else if (j.type === "hotspot-user-remove") {
      const u = rosSafe(p.username);
      if (!u) throw new Error("UNSAFE_VALUE");
      body = `/ip hotspot user remove [find where name="${u}"]`;
    } else throw new Error("UNKNOWN_JOB_TYPE");
    out.push(`:do { ${body}; ${ack(j.id, "ok")} } on-error={ :do { ${ack(j.id, "fail")} } on-error={} }`);
  }
  flush();
  return out.join("\n") + "\n";
}

/** Parses the router's heartbeat body ("k=v&k=v", plain text) into a sanitized object. */
export function parseReport(text) {
  const o = {};
  for (const part of String(text || "").slice(0, 6000).split("&")) {
    const i = part.indexOf("="); if (i < 1) continue;
    const k = part.slice(0, i).trim(), raw = part.slice(i + 1).trim();
    if (k === "pf") {
      o.profiles = [...new Set(raw.split("|").map(x => x.trim()).filter(x => x && x.length <= 64 && /^[\p{L}\p{N} ._\-+@:]+$/u.test(x)))].slice(0, 100);
      continue;
    }
    const v = raw.slice(0, 80);
    if (!["v", "board", "cpu", "uptime", "active", "name"].includes(k)) continue;
    o[k] = ["cpu", "active"].includes(k) ? (Number.isFinite(Number(v)) ? Number(v) : 0) : v.replace(/[^\p{L}\p{N} ._\-+@:()]/gu, "");
  }
  return o;
}

export function agentSource({ baseUrl, token }) {
  const base = normalizeBaseUrl(baseUrl);
  if (!/^mmag_[A-Za-z0-9_-]{20,}$/.test(token)) throw new Error("BAD_TOKEN");
  return [
    `:local base "${base}";`,
    `:local tk "${token}";`,
    `:local pf "";`,
    `:do { :foreach i in=[/ip hotspot user profile find] do={ :set pf ($pf . [/ip hotspot user profile get $i name] . "|") } } on-error={};`,
    `:local d "";`,
    `:do { :set d ("v=" . [/system resource get version] . "&board=" . [/system resource get board-name] . "&cpu=" . [/system resource get cpu-load] . "&uptime=" . [/system resource get uptime] . "&active=" . [:len [/ip hotspot active find]] . "&name=" . [/system identity get name] . "&pf=" . $pf) } on-error={};`,
    `:do { /file remove [find where name="mm-jobs.rsc"] } on-error={};`,
    `/tool fetch url=($base . "/api/agent/sync") http-method=post http-data=$d http-header-field=("Content-Type: text/plain,X-Agent-Token: " . $tk) dst-path="mm-jobs.rsc" ${FETCH_OPTS};`,
    `:delay 2s;`,
    `:if ([:len [/file find where name="mm-jobs.rsc"]] > 0) do={ /import file-name=mm-jobs.rsc; /file remove [find where name="mm-jobs.rsc"] };`
  ].join("\n");
}
export const AGENT_POLICY = "read,write,test,policy,ftp,sensitive";

export function buildEnrollScript({ baseUrl, token, intervalSec = 30, includeCa = true, caUrl = "https://curl.se/ca/cacert.pem" }) {
  const base = normalizeBaseUrl(baseUrl);
  const interval = Math.min(Math.max(Math.floor(Number(intervalSec)) || 30, 10), 300);
  const src = agentSource({ baseUrl: base, token }).split("\n").map(escRos).join("\\n");
  const lines = [
    "# MICRO-MAX Agent — paste into Winbox > New Terminal (RouterOS 6.49+ / 7.x)",
    "# Requires: DNS configured on the router and correct date/time (NTP) for certificate checks.",
    "# Re-running this script is safe (it replaces the previous agent)."
  ];
  if (includeCa) lines.push(
    `/tool fetch url="${caUrl}" mode=https check-certificate=no dst-path="mm-ca.pem"`,
    ":delay 3s",
    ':do { /certificate import file-name=mm-ca.pem passphrase="" } on-error={}',
    ':do { /file remove [find where name="mm-ca.pem"] } on-error={}'
  );
  const pol = "read,write,test,policy,ftp,sensitive";
  lines.push(
    ':do { /system scheduler remove [find where name="micromax-agent"] } on-error={}',
    ':do { /system script remove [find where name="micromax-agent"] } on-error={}',
    `/system script add name=micromax-agent policy=${pol} source="${src}"`,
    `/system scheduler add name=micromax-agent interval=${interval}s start-time=startup policy=${pol} on-event="/system script run micromax-agent"`,
    '/system script run micromax-agent'
  );
  return lines.join("\n") + "\n";
}

export function buildDirectSetupScript({ allowFrom = [], apiUser = "micromax", apiPass, port = 8729, certCn = "micromax-api", days = 3650 }) {
  const user = rosSafe(apiUser, { max: 32 }), pass = rosSafe(apiPass, { max: 64 });
  if (!pass || pass.length < 12) throw new Error("WEAK_PASSWORD");
  const cidrs = (allowFrom || []).map(x => String(x).trim()).filter(Boolean);
  for (const c of cidrs) if (!/^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/.test(c)) throw new Error("BAD_CIDR");
  const p = Number(port); if (!Number.isInteger(p) || p < 1 || p > 65535) throw new Error("BAD_PORT");
  const cn = rosSafe(certCn, { max: 40 });
  const addr = cidrs.join(",");
  const L = [
    "# MICRO-MAX API-SSL setup — paste into Winbox > New Terminal",
    `:do { /certificate add name=micromax-api common-name="${cn}" days-valid=${days} key-usage=digital-signature,key-encipherment,tls-server } on-error={}`,
    "/certificate sign micromax-api",
    ":delay 5s",
    `/ip service set api-ssl certificate=micromax-api port=${p} disabled=no${addr ? ` address=${addr}` : ""}`,
    "/ip service set api disabled=yes",
    ':do { /user group add name=micromax policy=read,write,api,test,ftp,sensitive,policy } on-error={}',
    `:do { /user remove [find where name="${user}"] } on-error={}`,
    `/user add name="${user}" group=micromax password="${pass}"${addr ? ` address=${addr}` : ""}`
  ];
  if (addr) L.push(`/ip firewall filter add chain=input protocol=tcp dst-port=${p} src-address-list=!micromax-allow action=drop comment="MICRO-MAX: API-SSL allowlist" place-before=0`,
    ...cidrs.map(c => `/ip firewall address-list add list=micromax-allow address=${c}`));
  L.push(":put (\"Certificate fingerprint (paste into MICRO-MAX): \" . [/certificate get micromax-api fingerprint])");
  return L.join("\n") + "\n";
}

/** Server-side card credentials for agent-mode routers (no RouterOS round-trip available). */
export function generateCredentials({ count, prefix, digits, letters, passLen = 8, pin = false, taken = new Set() }) {
  const pick = (alpha, n) => Array.from({ length: n }, () => alpha[crypto.randomInt(alpha.length)]).join("");
  const out = []; const seen = new Set(taken); let guard = 0;
  const pre = sanitizePrefix(prefix);
  while (out.length < count && guard++ < count * 25 + 100) {
    const u = pre + pick(USER_LETTERS, letters) + pick(USER_DIGITS, digits);
    if (seen.has(u)) continue;
    seen.add(u);
    out.push({ username: u, password: pin ? u : pick(PASS_ALPHABET, passLen) });
  }
  if (out.length < count) {
    // Random sampling stalls near exhaustion: enumerate the (small) remaining space instead.
    const space = Math.pow(USER_LETTERS.length, letters) * Math.pow(10, digits);
    if (space <= 5e6) {
      const free = [];
      for (let i = 0; i < space; i++) {
        let n = i, d = "", l = "";
        for (let k = 0; k < digits; k++) { d = USER_DIGITS[n % 10] + d; n = Math.floor(n / 10); }
        for (let k = 0; k < letters; k++) { l = USER_LETTERS[n % USER_LETTERS.length] + l; n = Math.floor(n / USER_LETTERS.length); }
        const u = pre + l + d;
        if (!seen.has(u)) free.push(u);
      }
      while (out.length < count && free.length) {
        const u = free.splice(crypto.randomInt(free.length), 1)[0];
        out.push({ username: u, password: pin ? u : pick(PASS_ALPHABET, passLen) });
      }
    }
  }
  if (out.length < count) throw new Error("USERNAME_SPACE_EXHAUSTED");
  return out;
}
