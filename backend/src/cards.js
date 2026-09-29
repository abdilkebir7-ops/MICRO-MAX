import crypto from "node:crypto";

// ---- Alphabets (no ambiguous characters: 0/O, 1/I/L) ----------------------
export const USER_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // 24 letters, no I/O
export const USER_DIGITS = "0123456789";
export const PASS_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

export function sanitizePrefix(p) {
  return String(p || "MM").replace(/[^a-zA-Z0-9]/g, "").slice(0, 12) || "MM";
}

// Profile names are placed inside a RouterOS script, so only a strict charset is allowed.
export function assertSafeProfile(name) {
  const s = String(name || "");
  if (!/^[\p{L}\p{N} ._\-+@]{1,64}$/u.test(s)) throw new Error("UNSAFE_PROFILE_NAME");
  return s;
}

export function usernameSpace(letters, digits) {
  return Math.pow(USER_LETTERS.length, letters) * Math.pow(10, digits);
}

// Code-only cards: the code IS the credential, so it must be hard to guess (>= 100M combinations).
export const PIN_MIN_SPACE = 1e8;
export function assertPinStrength(letters, digits) {
  if (usernameSpace(letters, digits) < PIN_MIN_SPACE) throw new Error("PIN_MODE_CODE_TOO_SHORT");
}

export function usernameRegex(prefix, letters, digits) {
  return new RegExp(`^${prefix}[A-HJ-NP-Z]{${letters}}[0-9]{${digits}}$`);
}

// ---- RouterOS script: MikroTik itself generates usernames/passwords --------
export function buildGenScript({ count, prefix, digits, letters, passLen, pin, profile, comment }) {
  assertSafeProfile(profile);
  if (!/^[A-Za-z0-9:_\-]+$/.test(comment)) throw new Error("UNSAFE_COMMENT");
  const parts = [`"${sanitizePrefix(prefix)}"`];
  if (letters > 0) parts.push(`[:rndstr length=${letters} from="${USER_LETTERS}"]`);
  if (digits > 0) parts.push(`[:rndstr length=${digits} from="${USER_DIGITS}"]`);
  const nameExpr = parts.join(" . ");
  const passExpr = pin ? "$u" : `[:rndstr length=${passLen} from="${PASS_ALPHABET}"]`;
  return [
    ":local made 0",
    ":local guard 0",
    `:while ($made < ${count} && $guard < ${count * 25}) do={`,
    "  :set guard ($guard + 1)",
    `  :local u (${nameExpr})`,
    "  :if ([:len [/ip hotspot user find where name=$u]] = 0) do={",
    `    :local p ${passExpr}`,
    `    /ip hotspot user add name=$u password=$p profile="${profile}" comment="${comment}"`,
    "    :set made ($made + 1)",
    "  }",
    "}"
  ].join("\n");
}

async function removeUsers(ros, users) {
  for (const u of users) if (u[".id"]) await ros.command("/ip/hotspot/user/remove", [`=.id=${u[".id"]}`]);
}

async function runGenScript(ros, source) {
  const name = `mm-gen-${crypto.randomBytes(5).toString("hex")}`;
  await ros.command("/system/script/add", [`=name=${name}`, `=source=${source}`, "=policy=read,write,test"]);
  try {
    await ros.command("/system/script/run", [`=number=${name}`]);
  } finally {
    try {
      const found = await ros.command("/system/script/print", [`?name=${name}`]);
      for (const s of found) if (s[".id"]) await ros.command("/system/script/remove", [`=.id=${s[".id"]}`]);
    } catch { /* best effort */ }
  }
}

/**
 * Generates `count` unique hotspot users ON the MikroTik, reads them back and
 * resolves duplicates against the database. Regenerates until the count is met.
 * dbHas(names) => Promise<Set<string>> of usernames already stored for this router.
 */
export async function generateBatch({ ros, count, prefix, digits, letters, passLen = 8, pin = false, profile, batchId, dbHas, maxRounds = 6, chunk = 500 }) {
  if (letters + digits === 0) throw new Error("USERNAME_FORMAT_EMPTY");
  const comment = `MICRO-MAX:${batchId}`;
  const kept = [];
  const seen = new Set();
  for (let round = 0; round < maxRounds && kept.length < count; round++) {
    const need = count - kept.length;
    for (let left = need; left > 0; left -= chunk) {
      await runGenScript(ros, buildGenScript({ count: Math.min(chunk, left), prefix, digits, letters, passLen, pin, profile, comment }));
    }
    const batchUsers = await ros.command("/ip/hotspot/user/print", [`?comment=${comment}`]);
    const names = batchUsers.map(u => String(u.name || "")).filter(n => !kept.some(k => k.username === n));
    const clash = names.length ? await dbHas(names) : new Set();
    const toRemove = [];
    for (const u of batchUsers) {
      const username = String(u.name || "");
      if (kept.some(k => k.username === username)) continue;
      const password = String(u.password ?? "");
      if (!password) throw new Error("ROUTER_POLICY_SENSITIVE_REQUIRED");
      if (clash.has(username) || seen.has(username) || kept.length >= count) { toRemove.push(u); continue; }
      seen.add(username);
      kept.push({ username, password, rosId: u[".id"] });
    }
    if (toRemove.length) await removeUsers(ros, toRemove);
  }
  if (kept.length !== count) throw new Error("USERNAME_POOL_EXHAUSTED");
  return kept;
}

// ---- Signed QR linked to the card -----------------------------------------
export function qrSecretFrom(key) {
  return crypto.createHash("sha256").update(`micromax-qr:${key}`).digest();
}
export function signQr(secret, cardId, username) {
  return crypto.createHmac("sha256", secret).update(`${cardId}|${username}`).digest("base64url").slice(0, 16);
}
export function buildQrUrl({ portalUrl, username, password, cardId, secret }) {
  const u = new URL(portalUrl);
  u.searchParams.set("username", username);
  if (password) u.searchParams.set("password", password); // code-only cards carry no password
  u.searchParams.set("mmc", cardId);
  u.searchParams.set("mms", signQr(secret, cardId, username));
  u.searchParams.set("auto", "1");
  u.searchParams.set("dst", `${u.origin}/`);
  return u.toString();
}
export function parseQr(text) {
  try {
    const u = new URL(String(text).trim());
    const username = u.searchParams.get("username");
    if (!username) return null;
    return { username, password: u.searchParams.get("password") || "", cardId: u.searchParams.get("mmc") || "", sig: u.searchParams.get("mms") || "", origin: u.origin };
  } catch { return null; }
}
export function verifyQr(secret, parsed) {
  if (!parsed?.cardId || !parsed?.sig) return false;
  const a = Buffer.from(signQr(secret, parsed.cardId, parsed.username));
  const b = Buffer.from(parsed.sig);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

// Wi-Fi join QR (open HotSpot network). Phones connect to the SSID by scanning it, no typing.
export function buildWifiQr(ssid, { hidden = false } = {}) {
  const s = String(ssid || "").trim();
  if (!s || s.length > 32) return null;
  const esc = v => v.replace(/([\\;,:"])/g, "\\$1");
  return `WIFI:T:nopass;S:${esc(s)};${hidden ? "H:true;" : ""};`;
}

// ---- Smart detection -------------------------------------------------------
export function parseRosDuration(v) {
  const s = String(v || "");
  if (!s) return 0;
  let total = 0;
  const units = { w: 604800, d: 86400, h: 3600, m: 60, s: 1 };
  for (const [, n, u] of s.matchAll(/(\d+)([wdhms])/g)) total += Number(n) * units[u];
  return total;
}

// State of a hotspot user on the router: disabled | expired | active | used | unused
export function classifyRouterUser(u, activeNames = new Set()) {
  if (String(u.disabled) === "true") return "disabled";
  const limit = parseRosDuration(u["limit-uptime"]);
  const uptime = parseRosDuration(u.uptime);
  if (limit > 0 && uptime >= limit) return "expired";
  if (activeNames.has(String(u.name))) return "active";
  if (uptime > 0 || Number(u["bytes-in"] || 0) + Number(u["bytes-out"] || 0) > 0) return "used";
  return "unused";
}

export function auditCards({ routerUsers, activeNames = new Set(), dbCards, readPassword = c => c.password }) {
  const byName = new Map(routerUsers.map(u => [String(u.name), u]));
  const dbNames = new Set(dbCards.map(c => c.username));
  const issues = { missingOnRouter: [], orphanOnRouter: [], passwordMismatch: [], statusDrift: [] };
  const counts = { unused: 0, active: 0, used: 0, expired: 0, disabled: 0 };
  for (const c of dbCards) {
    const u = byName.get(c.username);
    if (!u) { issues.missingOnRouter.push(c.username); continue; }
    if (String(u.password ?? "") && String(u.password) !== readPassword(c)) issues.passwordMismatch.push(c.username);
    const state = classifyRouterUser(u, activeNames);
    counts[state]++;
    if (c.status === "available" && state !== "unused") issues.statusDrift.push({ username: c.username, dbStatus: c.status, routerState: state });
  }
  for (const u of routerUsers) {
    if (String(u.comment || "").startsWith("MICRO-MAX:") && !dbNames.has(String(u.name))) issues.orphanOnRouter.push(String(u.name));
  }
  const problems = Object.values(issues).reduce((n, a) => n + a.length, 0);
  return { healthy: problems === 0, totals: { dbCards: dbCards.length, routerUsers: routerUsers.length }, counts, issues };
}

export function scanVerdict({ card, routerUser, activeNames = new Set(), qrParsed, secret }) {
  if (!card) return { verdict: "NOT_FOUND", sellable: false };
  if (qrParsed && qrParsed.cardId && !verifyQr(secret, { ...qrParsed, cardId: qrParsed.cardId })) return { verdict: "QR_TAMPERED", sellable: false };
  if (qrParsed && qrParsed.cardId && qrParsed.cardId !== card.id) return { verdict: "QR_TAMPERED", sellable: false };
  if (!routerUser) return { verdict: "MISSING_ON_ROUTER", sellable: false };
  const state = classifyRouterUser(routerUser, activeNames);
  const map = { disabled: "DISABLED", expired: "EXPIRED", active: "IN_USE", used: "USED", unused: card.status === "sold" ? "SOLD_UNUSED" : "VALID_UNUSED" };
  return { verdict: map[state], routerState: state, sellable: state === "unused" && card.status === "available" };
}
