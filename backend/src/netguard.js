// Network guard: prevents the backend from being used to reach loopback, cloud-metadata
// or (optionally) private networks (SSRF), and validates ports. Pure helpers are unit-tested.
import net from "node:net";
import dns from "node:dns";
import http from "node:http";
import https from "node:https";

function v4ToInt(ip) {
  const p = ip.split(".").map(Number);
  return ((p[0] << 24) >>> 0) + (p[1] << 16) + (p[2] << 8) + p[3];
}
function inV4(ip, base, bits) {
  const mask = bits === 0 ? 0 : (~0 << (32 - bits)) >>> 0;
  return ((v4ToInt(ip) & mask) >>> 0) === ((v4ToInt(base) & mask) >>> 0);
}

/** Returns "blocked" (never allowed), "private" (allowed only for on-prem deployments) or "public". */
export function classifyIp(ip) {
  ip = String(ip || "").trim().toLowerCase();
  if (net.isIPv6(ip)) {
    const m = ip.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (m) return classifyIp(m[1]);
    if (ip === "::" || ip === "::1") return "blocked";
    if (/^fe[89ab]/.test(ip)) return "blocked";          // link-local fe80::/10
    if (/^ff/.test(ip)) return "blocked";                 // multicast
    if (/^f[cd]/.test(ip)) return "private";              // unique local fc00::/7
    return "public";
  }
  if (!net.isIPv4(ip)) return "blocked";
  if (inV4(ip, "0.0.0.0", 8) || inV4(ip, "127.0.0.0", 8) || inV4(ip, "169.254.0.0", 16) || inV4(ip, "224.0.0.0", 4) || inV4(ip, "240.0.0.0", 4)) return "blocked";
  if (inV4(ip, "10.0.0.0", 8) || inV4(ip, "172.16.0.0", 12) || inV4(ip, "192.168.0.0", 16) || inV4(ip, "100.64.0.0", 10)) return "private";
  return "public";
}

export function isAddressAllowed(ip, { allowPrivate = false } = {}) {
  const c = classifyIp(ip);
  return c === "public" || (c === "private" && allowPrivate);
}

export function validHostname(h) {
  h = String(h || "").trim();
  if (!h || h.length > 253) return false;
  if (net.isIP(h)) return true;
  return /^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/.test(h);
}

export function parsePorts(csv, fallback) {
  const list = String(csv || "").split(",").map(x => Number(x.trim())).filter(n => Number.isInteger(n) && n > 0 && n < 65536);
  return list.length ? list : fallback;
}

export function netConfigFromEnv(env = process.env) {
  return {
    allowPrivate: String(env.ALLOW_PRIVATE_ROUTER_HOSTS || "false").toLowerCase() === "true",
    routerPorts: parsePorts(env.ROUTER_ALLOWED_PORTS, [8728, 8729]),
    portalPorts: parsePorts(env.PORTAL_ALLOWED_PORTS, [80, 443, 8080, 8443])
  };
}

/** Syntax-level validation (no DNS). Throws Error(code). */
export function assertRouterTarget(host, port, cfg = netConfigFromEnv()) {
  host = String(host || "").trim();
  if (!validHostname(host)) throw new Error("INVALID_HOST");
  const p = Number(port);
  if (!Number.isInteger(p) || !cfg.routerPorts.includes(p)) throw new Error("PORT_NOT_ALLOWED");
  if (net.isIP(host) && !isAddressAllowed(host, cfg)) throw new Error("HOST_NOT_ALLOWED");
  return { host, port: p };
}

/** dns.lookup replacement used at connect time, so DNS-rebinding cannot bypass the guard. */
export function guardedLookup(cfg = netConfigFromEnv()) {
  return (hostname, options, callback) => {
    if (typeof options === "function") { callback = options; options = {}; }
    dns.lookup(hostname, { ...options, all: true }, (err, addrs) => {
      if (err) return callback(err);
      const ok = addrs.filter(a => isAddressAllowed(a.address, cfg));
      if (!ok.length) return callback(Object.assign(new Error("HOST_NOT_ALLOWED"), { code: "HOST_NOT_ALLOWED" }));
      if (options && options.all) return callback(null, ok);
      callback(null, ok[0].address, ok[0].family);
    });
  };
}

/** GET with SSRF protection: guarded DNS, manual redirects (max 3), size and time limits. */
export async function safeGet(urlString, { cfg = netConfigFromEnv(), timeoutMs = 5000, maxBytes = 500000, maxRedirects = 3 } = {}) {
  let url = new URL(urlString);
  for (let hop = 0; hop <= maxRedirects; hop++) {
    if (!["http:", "https:"].includes(url.protocol)) throw new Error("HOTSPOT_URL_MUST_BE_HTTP_OR_HTTPS");
    const port = Number(url.port || (url.protocol === "https:" ? 443 : 80));
    if (!cfg.portalPorts.includes(port)) throw new Error("PORT_NOT_ALLOWED");
    const bare = url.hostname.replace(/^\[|\]$/g, "");
    if (!validHostname(bare)) throw new Error("INVALID_HOST");
    // Node skips the DNS lookup hook for IP literals, so they must be checked explicitly.
    if (net.isIP(bare) && !isAddressAllowed(bare, cfg)) throw new Error("HOST_NOT_ALLOWED");
    const mod = url.protocol === "https:" ? https : http;
    const res = await new Promise((resolve, reject) => {
      const req = mod.request(url, { method: "GET", lookup: guardedLookup(cfg), timeout: timeoutMs, headers: { "User-Agent": "MICRO-MAX-HotSpot-QR-Check/1.0" } }, resolve);
      req.on("timeout", () => req.destroy(new Error("TIMEOUT")));
      req.on("error", reject);
      req.end();
    });
    if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
      res.resume();
      url = new URL(res.headers.location, url);
      continue;
    }
    const chunks = []; let size = 0;
    for await (const c of res) { size += c.length; if (size > maxBytes) { res.destroy(); break; } chunks.push(c); }
    return { status: res.statusCode, ok: res.statusCode >= 200 && res.statusCode < 300, body: Buffer.concat(chunks).toString("utf8").slice(0, maxBytes) };
  }
  throw new Error("TOO_MANY_REDIRECTS");
}
