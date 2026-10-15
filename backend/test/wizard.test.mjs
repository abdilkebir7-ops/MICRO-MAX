import test from "node:test";
import assert from "node:assert/strict";
import { validateWizard, buildSetupPlan, applyPlan } from "../src/wizard.js";

const ok = { lanInterface: "bridge", wanInterface: "ether1", lanCidr: "10.10.10.1/24" };

test("validation rejects bad input", () => {
  assert.ok(validateWizard({ ...ok, lanCidr: "8.8.8.8/24" }).errors.includes("LAN_NOT_PRIVATE"));
  assert.ok(validateWizard({ ...ok, wanInterface: "bridge" }).errors.includes("LAN_EQUALS_WAN"));
  assert.ok(validateWizard({ ...ok, wanInterface: "ether1; /system reset" }).errors.includes("WAN_INTERFACE_INVALID"));
  assert.ok(validateWizard({ ...ok, dns: "1.1.1.1,evil" }).errors.includes("DNS_INVALID"));
  assert.ok(validateWizard({ ...ok, dhcpFrom: "10.10.10.1", dhcpTo: "10.10.10.50" }).errors.includes("DHCP_RANGE_CONTAINS_GATEWAY"));
  assert.ok(validateWizard({ ...ok, dhcpFrom: "10.10.11.5", dhcpTo: "10.10.11.9" }).errors.includes("DHCP_RANGE_INVALID"));
  assert.ok(validateWizard({ ...ok, hotspotName: "a b" }).errors.includes("HOTSPOT_NAME_INVALID"));
});

test("plan defaults", () => {
  const p = buildSetupPlan(ok);
  assert.equal(p.ok, true);
  assert.equal(p.values.network, "10.10.10.0/24");
  assert.equal(p.values.from, "10.10.10.10");
  assert.equal(p.values.to, "10.10.10.254");
  assert.equal(p.steps.length, 7);
  assert.ok(p.steps.every(s => s.path.startsWith("/ip/") && s.undo.remove));
  assert.equal(buildSetupPlan({ ...ok, masquerade: false }).steps.length, 6);
});

test("failure rolls back executed steps in reverse", async () => {
  const plan = buildSetupPlan(ok);
  const log = [], store = [];
  const ros = { async command(path, args) {
    log.push(path);
    if (path === "/ip/dhcp-server/add") throw new Error("boom");
    if (path.endsWith("/print")) return [{ ".id": "*1" }];
    if (path.endsWith("/add")) store.push(path);
    return [];
  } };
  const rep = await applyPlan(ros, plan, { onBackup: async () => "b.backup" });
  assert.equal(rep.backup, "b.backup");
  assert.equal(rep.failed.step, "DHCP server");
  assert.deepEqual(rep.done, ["عنوان الشبكة (LAN)", "نطاق عناوين DHCP", "شبكة DHCP"]);
  assert.deepEqual(rep.rolledBack, ["DHCP server", "شبكة DHCP", "نطاق عناوين DHCP", "عنوان الشبكة (LAN)"]);
  assert.equal(rep.rollbackErrors.length, 0);
  assert.ok(!log.includes("/ip/hotspot/add"));   // nothing after the failure ran
});
