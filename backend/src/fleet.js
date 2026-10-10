// Fleet overview + server configuration doctor (pure, unit-tested).
export function fleetSummary(routers = [], now = Date.now(), { onlineMs = 120000, cpuWarn = 85 } = {}) {
  const items = routers.map(r => {
    let status = "unknown";
    if (r.mode === "agent") {
      const t = r.agent_last_seen ? new Date(r.agent_last_seen).getTime() : null;
      status = t === null ? "pending" : now - t < onlineMs ? "online" : "offline";
    } else if (r.probe) status = r.probe.ok ? "online" : "offline";
    const info = r.agent_info || {};
    const cpu = Number.isFinite(Number(info.cpu)) && info.cpu !== undefined ? Number(info.cpu) : null;
    const active = Number.isFinite(Number(info.active)) && info.active !== undefined ? Number(info.active) : null;
    const issues = [];
    if (status === "offline") issues.push("OFFLINE");
    if (cpu !== null && cpu >= cpuWarn) issues.push("HIGH_CPU");
    return { id: r.id, name: r.name, mode: r.mode, status, cpu, activeUsers: active, uptime: info.uptime || null, version: info.v || null, board: info.board || null, latencyMs: r.probe?.latencyMs ?? null, issues };
  });
  const count = s => items.filter(i => i.status === s).length;
  const act = items.map(i => i.activeUsers).filter(v => v !== null);
  const cpus = items.filter(i => i.status === "online" && i.cpu !== null).map(i => i.cpu);
  return {
    total: items.length, online: count("online"), offline: count("offline"), pending: count("pending"), unknown: count("unknown"),
    activeUsers: act.reduce((a, b) => a + b, 0),
    avgCpu: cpus.length ? Math.round(cpus.reduce((a, b) => a + b, 0) / cpus.length) : null,
    attention: items.filter(i => i.issues.length), routers: items
  };
}

export function configDoctor(env = {}, { dbOk = true } = {}) {
  const checks = []; const add = (id, ok, level, message) => checks.push({ id, ok, level: ok ? "ok" : level, message });
  const jwt = String(env.JWT_SECRET || ""), enc = String(env.ROUTER_ENCRYPTION_KEY || "");
  add("DB", dbOk, "critical", dbOk ? "قاعدة البيانات تعمل" : "تعذر الاتصال بقاعدة البيانات");
  add("JWT_SECRET", jwt.length >= 32 && !/replace|change-me/i.test(jwt), "critical", "JWT_SECRET يجب أن يكون 32 حرفاً فأكثر وعشوائياً");
  add("ENC_KEY", enc.length >= 32 && !/replace|change-me/i.test(enc), "critical", "ROUTER_ENCRYPTION_KEY يجب أن يكون 32 حرفاً فأكثر وعشوائياً");
  add("SECRETS_DISTINCT", !(jwt && jwt === enc), "warning", "استخدم قيمتين مختلفتين لـ JWT_SECRET و ROUTER_ENCRYPTION_KEY");
  const cors = String(env.CORS_ORIGIN || "").split(",").map(x => x.trim()).filter(Boolean);
  add("CORS", !cors.includes("*"), "critical", "CORS_ORIGIN يجب ألا يكون *");
  let httpsBase = false; try { httpsBase = new URL(String(env.PUBLIC_BASE_URL || "")).protocol === "https:"; } catch {}
  add("PUBLIC_BASE_URL", httpsBase, "warning", "PUBLIC_BASE_URL مطلوب (https) لتعمل سكربتات الراوتر البعيد (Agent)");
  add("INSECURE_TLS", String(env.ALLOW_INSECURE_ROUTER_TLS).toLowerCase() !== "true", "warning", "ALLOW_INSECURE_ROUTER_TLS يعطل التحقق من الشهادات؛ استخدم تثبيت البصمة");
  add("REGISTRATION", String(env.ALLOW_REGISTRATION).toLowerCase() !== "true", "info", "التسجيل العام مفتوح: كل حساب جديد (owner) يرى راوتراته وبياناته فقط. اضبط ALLOW_REGISTRATION=false لإغلاقه");
  add("TRUST_PROXY", env.NODE_ENV !== "production" || String(env.TRUST_PROXY).toLowerCase() === "true", "warning", "خلف Render/Proxy اضبط TRUST_PROXY=true وإلا ستُحسب كل الطلبات من IP واحد");
  add("PRIVATE_ON_CLOUD", !(env.RENDER && String(env.ALLOW_PRIVATE_ROUTER_HOSTS).toLowerCase() === "true"), "info", "ALLOW_PRIVATE_ROUTER_HOSTS لا فائدة منه على سيرفر سحابي (لا يصل لشبكتك المحلية)");
  const pay = String(env.ENABLE_ONLINE_PAYMENTS).toLowerCase() === "true";
  add("PAYMENTS", !pay || !!(env.BINANCE_PAY_SECRET_KEY && env.BINANCE_PAY_CERTIFICATE_SN), "warning", "الدفع الإلكتروني مفعّل لكن مفاتيح Binance Pay ناقصة");
  const bad = checks.filter(c => !c.ok);
  return { healthy: !bad.some(c => c.level === "critical"), critical: bad.filter(c => c.level === "critical").length, warnings: bad.filter(c => c.level === "warning").length, checks };
}
