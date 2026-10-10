import test from "node:test";
import assert from "node:assert/strict";
import { buildInsights } from "../src/insights.js";

test("runway, status and restock suggestion", () => {
  const r = buildInsights({ plans: [
    { plan_id: "a", name: "يومي", price: 5, available: 10, sold_7d: 70, sold_30d: 300, revenue_30d: 1500 },
    { plan_id: "b", name: "أسبوعي", price: 20, available: 0, sold_7d: 7, sold_30d: 20, revenue_30d: 400 },
    { plan_id: "c", name: "شهري", price: 50, available: 100, sold_7d: 1, sold_30d: 4, revenue_30d: 200 },
    { plan_id: "d", name: "خامل", price: 1, available: 5, sold_7d: 0, sold_30d: 0, revenue_30d: 0 },
    { plan_id: "e", name: "فارغ", price: 1, available: 0, sold_7d: 0, sold_30d: 0, revenue_30d: 0 }
  ], hours: [{ h: 20, n: 50 }, { h: 21, n: 80 }, { h: 9, n: 5 }, { h: 3, n: 1 }, { h: 25, n: 99 }] });
  const by = Object.fromEntries(r.plans.map(p => [p.planId, p]));
  assert.equal(by.a.dailyRate, 10);              // 0.6*10 + 0.4*10
  assert.equal(by.a.status, "critical");         // 10 cards / 10 per day = 1 day
  assert.equal(by.a.suggestedRestock, 110);      // ceil(10*10*1.15)=115 -> minus 10 = 105 -> rounded to 110
  assert.equal(by.b.status, "out");
  assert.equal(by.c.status, "ok");
  assert.equal(by.c.suggestedRestock, 0);
  assert.equal(by.d.status, "idle");
  assert.equal(by.e.status, "empty");
  assert.equal(by.d.daysLeft, null);
  assert.deepEqual(r.alerts.map(a => a.planId).sort(), ["a", "b"]);
  assert.deepEqual(r.peakHours.map(x => x.hour), [21, 20, 9]);
  assert.equal(r.bestPlan.planId, "a");
});
test("empty input is safe", () => {
  const r = buildInsights({});
  assert.deepEqual(r, { plans: [], alerts: [], peakHours: [], bestPlan: null });
});
