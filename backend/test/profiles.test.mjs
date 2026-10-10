import test from "node:test";
import assert from "node:assert/strict";
import { parseRosDuration, mapRouterProfile } from "../src/profiles.js";
import { renderJobScript, parseReport, buildEnrollScript, newAgentToken } from "../src/agent.js";

const base = "https://example.onrender.com";
const mk = i => ({ id: `123e4567-e89b-12d3-a456-4266141${String(i).padStart(5, "0")}`, type: "hotspot-user-add", payload: { username: "MM" + i, password: "pw" + i, profile: "1 Day" } });

test("parseRosDuration", () => {
  assert.equal(parseRosDuration("1d"), 1440);
  assert.equal(parseRosDuration("1w2d3h4m"), 10080 + 2880 + 180 + 4);
  assert.equal(parseRosDuration("1d12:00:00"), 1440 + 720);
  assert.equal(parseRosDuration("30m"), 30);
  assert.equal(parseRosDuration("00:00:00"), 0);
  assert.equal(parseRosDuration(""), 0);
  assert.equal(parseRosDuration(undefined), 0);
});
test("mapRouterProfile", () => {
  assert.deepEqual(mapRouterProfile({ name: "Daily", "rate-limit": "2M/5M", "session-timeout": "1d", "idle-timeout": "10m", "shared-users": "2" }),
    { name: "Daily", rate_limit: "2M/5M", session_timeout: "1d", idle_timeout: "10m", shared_users: 2, duration_minutes: 1440 });
  assert.equal(mapRouterProfile({ name: "x" }).shared_users, 1);
  assert.equal(mapRouterProfile({ name: "x", "shared-users": "unlimited" }).shared_users, 1);
});
test("bulk user-add jobs are chunked: one ack per 25 cards, ids comma-separated", () => {
  const jobs = Array.from({ length: 60 }, (_, i) => mk(i));
  const out = renderJobScript(jobs, { baseUrl: base, token: newAgentToken() });
  const lines = out.trim().split("\n").slice(1);
  assert.equal(lines.length, 3);                    // 25 + 25 + 10
  assert.equal((lines[0].match(/\/ip hotspot user add/g) || []).length, 25);
  assert.equal((lines[2].match(/\/ip hotspot user add/g) || []).length, 10);
  assert.match(lines[0], /\$bad = 0\) do=\{ \/tool fetch url="[^"]*ack\?j=([0-9a-f-]{36},){24}[0-9a-f-]{36}&s=ok/);
  assert.match(lines[0], /&s=fail/);
  assert.equal((out.match(/user find where name=/g) || []).length, 60, "every add stays idempotent");
});
test("chunk is flushed before a non-add job keeps order", () => {
  const t = newAgentToken();
  const out = renderJobScript([mk(1), { id: mk(2).id, type: "hotspot-user-remove", payload: { username: "MM9" } }, mk(3)], { baseUrl: base, token: t });
  const l = out.trim().split("\n").slice(1);
  assert.equal(l.length, 3);
  assert.match(l[0], /user add name="MM1"/); assert.match(l[1], /user remove/); assert.match(l[2], /user add name="MM3"/);
});
test("heartbeat reports profile names; parseReport sanitizes them", () => {
  const r = parseReport("v=7.15&cpu=3&pf=default|1 Day|Week 2M|bad\"name|x$y|1 Day|");
  assert.deepEqual(r.profiles, ["default", "1 Day", "Week 2M"]);
  assert.equal(r.cpu, 3);
  assert.deepEqual(parseReport("cpu=1").profiles, undefined);
  const s = buildEnrollScript({ baseUrl: base, token: newAgentToken() });
  assert.match(s, /hotspot user profile find/);
  assert.match(s, /&pf=/);
  const m = s.match(/\/system script add name=micromax-agent policy=\S+ source="(.*)"\n/);
  assert.ok(m && !/(^|[^\\])"/.test(m[1]) && !/(^|[^\\])\$[^\\]/.test(m[1].replace(/\\\$/g, "")) , "source stays a single escaped line");
});
