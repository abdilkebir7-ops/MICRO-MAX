import test from "node:test";
import assert from "node:assert/strict";
import { diagnose, parseTimeoutSeconds } from "../src/troubleshoot.js";

test("parseTimeoutSeconds", () => {
  assert.equal(parseTimeoutSeconds("1h"), 3600);
  assert.equal(parseTimeoutSeconds("1d2h30m"), 95400);
  assert.equal(parseTimeoutSeconds("10m"), 600);
  assert.equal(parseTimeoutSeconds("abc"), null);
});

test("healthy router has no issues", () => {
  const r = diagnose({
    resource: { "cpu-load": "5", "total-memory": "1000", "free-memory": "600", "total-hdd-space": "100", "free-hdd-space": "50" },
    routes: [{ "dst-address": "0.0.0.0/0" }], dns: { servers: "1.1.1.1", "allow-remote-requests": "true" },
    nat: [{ chain: "srcnat", action: "masquerade" }], hotspotServers: [{ name: "hs", interface: "br", ".id": "*1" }],
    interfaces: [{ name: "br", running: "true", type: "bridge" }], dhcpServers: [{ name: "d", interface: "br", "address-pool": "p", "lease-time": "1h" }],
    pools: [{ name: "p", ranges: "10.0.0.10-10.0.0.200" }], leases: [], clock: { date: "2026-10-12" }, services: [{ name: "api", ".id": "*2" }],
    files: [{ name: "hotspot/login.html" }], queues: [{ name: "q", target: "10.0.0.0/24" }], ipAddresses: [{ address: "10.0.0.1/24", interface: "br" }]
  });
  assert.equal(r.issues.length, 0);
  assert.equal(r.checked, 20);
});

test("detects problems, builds safe fixes only from known data", () => {
  const r = diagnose({
    routes: [], dns: { servers: "", "allow-remote-requests": "false" }, nat: [],
    hotspotServers: [{ name: "hs", interface: "br", disabled: "true", ".id": "*1" }],
    interfaces: [{ name: "br", running: "false" }], dhcpServers: [{ name: "d", interface: "br", disabled: "true", ".id": "*3", "address-pool": "p", "lease-time": "2m" }],
    pools: [{ name: "p", ranges: "10.0.0.10-10.0.0.19" }], leases: Array.from({ length: 10 }, () => ({ server: "d" })),
    clock: { date: "1970-01-02" }, services: [{ name: "telnet", ".id": "*9", disabled: "false" }, { name: "api", ".id": "*8" }],
    ipAddresses: [{ address: "10.0.0.1/24", interface: "br" }, { address: "10.0.0.2/24", interface: "ether2" }]
  });
  const ids = r.issues.map(i => i.id);
  for (const id of ["no-default-route", "no-dns", "dns-remote-off", "no-nat", "hotspot-disabled", "hotspot-iface-down", "dhcp-disabled", "pool-exhausted", "lease-short", "clock-wrong", "insecure-services", "ip-overlap"]) assert.ok(ids.includes(id), id);
  assert.equal(r.issues[0].severity, "critical");
  const tel = r.issues.find(i => i.id === "insecure-services");
  assert.deepEqual(tel.fix.commands, [["/ip/service/disable", ["=numbers=*9"]]]);   // api service is never touched
  assert.deepEqual(tel.fix.undo, [["/ip/service/enable", ["=numbers=*9"]]]);
  assert.equal(r.issues.find(i => i.id === "no-nat").fix, null);
  assert.ok(r.unknown > 0);   // files/queues unreadable -> skipped, not reported
});
