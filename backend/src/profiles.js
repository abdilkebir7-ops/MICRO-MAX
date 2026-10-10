// Helpers for importing REAL MikroTik hotspot user profiles.
/** RouterOS time ("1w2d3h4m5s", "1d12:00:00", "12:00:00", "30m") -> minutes (0 when empty/unknown). */
export function parseRosDuration(v) {
  const s = String(v ?? "").trim();
  if (!s || s === "0s" || s === "00:00:00") return 0;
  let secs = 0, rest = s, any = false;
  const hms = rest.match(/(\d+):(\d{1,2}):(\d{1,2})$/);
  if (hms) { secs += Number(hms[1]) * 3600 + Number(hms[2]) * 60 + Number(hms[3]); rest = rest.slice(0, hms.index); any = true; }
  const unit = { w: 604800, d: 86400, h: 3600, m: 60, s: 1 };
  const re = /(\d+)([wdhms])/g; let m;
  while ((m = re.exec(rest))) { secs += Number(m[1]) * unit[m[2]]; any = true; }
  return any ? Math.ceil(secs / 60) : 0;
}

/** One row of /ip/hotspot/user/profile/print -> local profile columns. */
export function mapRouterProfile(x) {
  const su = Number(x["shared-users"] || 1);
  const sessionTimeout = String(x["session-timeout"] || "");
  return {
    name: String(x.name || ""),
    rate_limit: String(x["rate-limit"] || ""),
    session_timeout: sessionTimeout,
    idle_timeout: String(x["idle-timeout"] || ""),
    shared_users: Number.isInteger(su) && su > 0 ? su : 1,
    duration_minutes: parseRosDuration(sessionTimeout)
  };
}
