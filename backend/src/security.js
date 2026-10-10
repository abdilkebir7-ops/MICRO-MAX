// Read-only MikroTik security audit. Pure: takes data already read from RouterOS and returns findings,
// a score and a fix script. Fixes are NEVER applied automatically (a wrong firewall/service change can lock the admin out).
const T = v => v === true || ["true", "yes"].includes(String(v).toLowerCase());
const WEIGHT = { high: 20, medium: 10, low: 4, info: 0 };
const openAddr = a => { const s = String(a || "").trim(); return s === "" || s === "0.0.0.0/0"; };

export function auditSecurity(data = {}, { apiTls = false } = {}) {
  const findings = [];
  const add = (id, level, title, detail, fix = null) => findings.push({ id, level, title, detail, fix });
  const services = new Map((data.services || []).map(s => [String(s.name), s]));
  const on = n => services.has(n) && !T(services.get(n).disabled);
  const exposed = n => on(n) && openAddr(services.get(n).address);

  if (data.services) {
    if (on("telnet")) add("SVC_TELNET", "high", "خدمة Telnet مفعّلة", "Telnet ينقل كلمة المرور نصاً صريحاً.", { commands: ["/ip service disable telnet"] });
    if (on("ftp")) add("SVC_FTP", "high", "خدمة FTP مفعّلة", "FTP غير مشفّر. استخدم SFTP/WinBox بدلاً منه.", { commands: ["/ip service disable ftp"] });
    if (on("www")) add("SVC_WWW", "medium", "واجهة HTTP للإدارة مفعّلة", "WebFig عبر HTTP بلا تشفير.", { commands: ["/ip service disable www"] });
    if (on("api")) {
      if (apiTls) add("SVC_API_PLAIN", "medium", "API غير المشفر (8728) مفعّل", "التطبيق يستخدم API-SSL، يمكن تعطيل API العادي.", { commands: ["/ip service disable api"] });
      else add("SVC_API_PLAIN", "medium", "التطبيق متصل عبر API غير مشفر", "كلمة مرور الراوتر تعبر الشبكة دون تشفير. انتقل إلى API-SSL (8729) مع تثبيت البصمة، ثم عطّل API العادي. لا يُعطَّل الآن حتى لا ينقطع التطبيق.", { commands: ["/ip service set api address=<BACKEND-IP/32>"], needsInput: true, risky: true });
    }
    if (exposed("winbox")) add("SVC_WINBOX_OPEN", "medium", "WinBox مفتوح لأي عنوان", "حدّد عناوين الإدارة المسموحة.", { commands: ["/ip service set winbox address=<YOUR-ADMIN-IP/32>"], needsInput: true });
    if (exposed("ssh")) add("SVC_SSH_OPEN", "low", "SSH مفتوح لأي عنوان", "حدّد العناوين المسموحة.", { commands: ["/ip service set ssh address=<YOUR-ADMIN-IP/32>"], needsInput: true });
    if (exposed("api-ssl")) add("SVC_APISSL_OPEN", "low", "API-SSL مفتوح لأي عنوان", "حدّد عنوان السيرفر المسموح (Outbound IPs).", { commands: ["/ip service set api-ssl address=<BACKEND-IP/32>"], needsInput: true });
  }

  let hasInputDrop = null;
  if (data.firewall) {
    hasInputDrop = data.firewall.some(r => !T(r.disabled) && r.chain === "input" && r.action === "drop");
    if (!hasInputDrop) add("FW_NO_INPUT_DROP", "high", "لا توجد قاعدة drop على سلسلة input", "الراوتر نفسه (WinBox/API/DNS) قابل للوصول من الإنترنت. أضف سياسة افتراضية: السماح لـ established/related ولشبكة LAN ثم drop لما تبقى.", {
      commands: [
        '/ip firewall filter add chain=input action=accept connection-state=established,related,untracked comment="MM: allow established"',
        '/ip firewall filter add chain=input action=accept in-interface-list=LAN comment="MM: allow LAN"',
        '/ip firewall filter add chain=input action=drop in-interface-list=!LAN comment="MM: drop the rest"'
      ], needsInput: true, risky: true });
  }
  if (data.dns && T(data.dns["allow-remote-requests"]) && hasInputDrop === false)
    add("DNS_OPEN_RESOLVER", "high", "DNS يقبل طلبات خارجية بلا حماية", "قد يُستغل الراوتر في هجمات تضخيم DNS. لا تعطّل الخاصية (عملاء HotSpot يعتمدون عليها)؛ أضف drop لمنفذ 53 من WAN.", { commands: ['/ip firewall filter add chain=input action=drop protocol=udp dst-port=53 in-interface-list=WAN', '/ip firewall filter add chain=input action=drop protocol=tcp dst-port=53 in-interface-list=WAN'], needsInput: true, risky: true });

  if (data.discovery && String(data.discovery["discover-interface-list"] || "") === "all")
    add("DISCOVERY_ALL", "low", "اكتشاف الجوار على كل الواجهات", "يكشف اسم الجهاز والإصدار للشبكة الخارجية.", { commands: ["/ip neighbor discovery-settings set discover-interface-list=LAN"], needsInput: true });
  if (data.macServer && String(data.macServer["allowed-interface-list"] || "") === "all")
    add("MACSERVER_ALL", "low", "MAC-Telnet/WinBox على كل الواجهات", "يسمح بالدخول عبر الطبقة الثانية من أي واجهة.", { commands: ["/tool mac-server set allowed-interface-list=LAN"], needsInput: true });

  if (data.users && data.users.some(u => u.name === "admin" && !T(u.disabled)))
    add("ADMIN_DEFAULT_USER", "medium", "المستخدم الافتراضي admin مفعّل", "اسم معروف يسهّل التخمين. أنشئ مستخدماً باسم مختلف وكلمة مرور قوية ثم عطّل admin.", { commands: ["/user add name=<NEW-ADMIN> group=full password=<STRONG-PASSWORD>", "/user disable admin"], needsInput: true, risky: true });

  if (data.ssh && !T(data.ssh["strong-crypto"])) add("SSH_WEAK_CRYPTO", "low", "SSH بتشفير غير صارم", "فعّل strong-crypto.", { commands: ["/ip ssh set strong-crypto=yes"] });

  const major = parseInt(String(data.resource?.version || "").trim().split(".")[0], 10);
  if (Number.isInteger(major) && major < 7) add("OS_V6", "low", `الإصدار ${data.resource.version}`, "RouterOS 6 أقدم؛ فكّر بالترقية إلى 7.x بعد اختبارها (قد تتغير بعض الأوامر).");

  if (data.hotspotProfiles && data.hotspotProfiles.some(p => /http-pap/.test(String(p["login-by"] || "")) && !/https/.test(String(p["login-by"] || ""))))
    add("HOTSPOT_PAP_HTTP", "info", "دخول HotSpot عبر http-pap بدون HTTPS", "هذا مطلوب لدخول QR التلقائي، لكن كلمة مرور الكرت تعبر الواي فاي نصاً. مقبول لكروت الاستخدام المؤقت؛ استخدم كروت PIN قصيرة الصلاحية.");

  const penalty = findings.reduce((n, f) => n + WEIGHT[f.level], 0);
  const score = Math.max(0, 100 - penalty);
  const grade = score >= 90 ? "A" : score >= 75 ? "B" : score >= 60 ? "C" : score >= 40 ? "D" : "F";
  const safe = findings.filter(f => f.fix && !f.fix.needsInput && !f.fix.risky).flatMap(f => f.fix.commands);
  const counts = { high: 0, medium: 0, low: 0, info: 0 };
  for (const f of findings) counts[f.level]++;
  return { score, grade, counts, findings, fixScript: safe.length ? "# MICRO-MAX: safe fixes (review before running)\n" + safe.join("\n") + "\n" : "" };
}
