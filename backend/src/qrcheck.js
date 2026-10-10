// Static analysis of a MikroTik hotspot login.html: can a scanned card QR log in without typing?
// (Deliberately static: the file comes from the router and must never be executed on the server.)
export function analyzeLoginHtml(html) {
  const h = String(html || "");
  const hasLoginForm = /<form[^>]*>/i.test(h) && /name=["']username["']/i.test(h) && /name=["']password["']/i.test(h);
  const actionOk = /action=["']\$\(link-login-only\)["']/i.test(h) || /action=["'][^"']*\/login/i.test(h);
  const readsQuery = /URLSearchParams|location\.search/.test(h);
  const readsAuto = /['"]auto['"]/.test(h);
  const hasAutoSubmit = /\.submit\s*\(|requestSubmit\s*\(/.test(h);
  const stripsCredentials = /history\.replaceState/.test(h);
  const autoLogin = hasLoginForm && actionOk && readsQuery && readsAuto && hasAutoSubmit;
  const warnings = [];
  if (!hasLoginForm) warnings.push("LOGIN_FORM_NOT_DETECTED");
  if (hasLoginForm && !actionOk) warnings.push("FORM_ACTION_NOT_LINK_LOGIN_ONLY");
  if (hasLoginForm && !autoLogin) warnings.push("LOGIN_HTML_DOES_NOT_AUTO_SUBMIT_FROM_QR");
  if (autoLogin && !stripsCredentials) warnings.push("CREDENTIALS_REMAIN_IN_BROWSER_HISTORY");
  if (h.length === 4095 || h.length === 4096) warnings.push("FILE_READ_MAY_BE_TRUNCATED");
  return { hasLoginForm, actionOk, readsQuery, readsAuto, hasAutoSubmit, stripsCredentials, autoLogin, warnings };
}
