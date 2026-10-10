import test from "node:test";
import assert from "node:assert/strict";
import tls from "node:tls";
import crypto from "node:crypto";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { RouterOS } from "../src/routeros.js";

function makeCert() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mm-"));
  try {
    execFileSync("openssl", ["req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", path.join(dir, "k.pem"), "-out", path.join(dir, "c.pem"), "-days", "2", "-subj", "/CN=router.test"], { stdio: "ignore" });
  } catch { return null; }
  const cert = fs.readFileSync(path.join(dir, "c.pem")); const key = fs.readFileSync(path.join(dir, "k.pem"));
  const fp = new crypto.X509Certificate(cert).fingerprint256;
  return { cert, key, fp };
}

const c = makeCert();
test("certificate pinning: wrong pin rejected, right pin accepted", { skip: !c && "openssl not available" }, async () => {
  const server = tls.createServer({ cert: c.cert, key: c.key }, sock => { sock.on("error", () => {}); sock.on("data", () => sock.end()); });
  await new Promise(r => server.listen(0, "127.0.0.1", r));
  const port = server.address().port;
  const mk = pin => new RouterOS({ host: "127.0.0.1", port, username: "u", password: "p", tls: true, pinSha256: pin, timeout: 1500 });
  const bad = mk("00".repeat(32));
  await assert.rejects(bad.connect(), e => e.code === "CERT_PIN_MISMATCH");
  const good = mk(c.fp);
  await assert.rejects(good.connect(), e => e.code !== "CERT_PIN_MISMATCH"); // passes TLS pin, then fails at RouterOS login (server just closes)
  const unpinned = new RouterOS({ host: "127.0.0.1", port, username: "u", password: "p", tls: true, timeout: 1500 });
  await assert.rejects(unpinned.connect(), e => /self[- ]signed|SELF_SIGNED|UNABLE_TO/i.test(String(e.code || e.message)));
  server.close();
});
