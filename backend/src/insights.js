// Sales-velocity insights: stock runway, restock suggestions, peak hours, best plan.
export function buildInsights({ plans = [], hours = [], targetDays = 10 }) {
  const out = plans.map(p => {
    const available = Number(p.available) || 0, s7 = Number(p.sold_7d) || 0, s30 = Number(p.sold_30d) || 0;
    // Blend: recent week weighs more than the month average.
    const dailyRate = s30 === 0 && s7 === 0 ? 0 : 0.6 * (s7 / 7) + 0.4 * (s30 / 30);
    const daysLeft = dailyRate > 0 ? available / dailyRate : null;
    let status = "ok";
    if (dailyRate === 0) status = available === 0 ? "empty" : "idle";
    else if (available === 0) status = "out";
    else if (daysLeft < 2) status = "critical";
    else if (daysLeft < 5) status = "low";
    const raw = Math.max(0, Math.ceil(dailyRate * targetDays * 1.15) - available);
    const suggestedRestock = raw === 0 ? 0 : Math.ceil(raw / 10) * 10;
    return { planId: p.plan_id, name: p.name, price: Number(p.price) || 0, currency: p.currency || "", available, sold7d: s7, sold30d: s30,
      revenue30d: Number(p.revenue_30d) || 0, dailyRate: Math.round(dailyRate * 100) / 100, daysLeft: daysLeft === null ? null : Math.round(daysLeft * 10) / 10, status, suggestedRestock };
  });
  const alerts = [];
  for (const p of out) {
    if (p.status === "out") alerts.push({ level: "critical", planId: p.planId, message: `نفدت كروت باقة ${p.name}. المقترح توليد ${p.suggestedRestock} كرت.` });
    else if (p.status === "critical") alerts.push({ level: "critical", planId: p.planId, message: `كروت ${p.name} تكفي أقل من يومين (${p.available} كرت). المقترح توليد ${p.suggestedRestock}.` });
    else if (p.status === "low") alerts.push({ level: "warning", planId: p.planId, message: `مخزون ${p.name} منخفض (يكفي حوالي ${Math.floor(p.daysLeft)} أيام).` });
  }
  const hh = Array.from({ length: 24 }, (_, h) => ({ hour: h, sales: 0 }));
  for (const x of hours) { const h = Number(x.h ?? x.hour); if (Number.isInteger(h) && h >= 0 && h < 24) hh[h].sales += Number(x.n ?? x.sales) || 0; }
  const peakHours = hh.filter(x => x.sales > 0).sort((a, b) => b.sales - a.sales || a.hour - b.hour).slice(0, 3);
  const best = [...out].filter(p => p.revenue30d > 0).sort((a, b) => b.revenue30d - a.revenue30d)[0] || null;
  return { plans: out, alerts, peakHours, bestPlan: best ? { planId: best.planId, name: best.name, revenue30d: best.revenue30d } : null };
}
