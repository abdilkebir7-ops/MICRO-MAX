import express from "express";
import cors from "cors";
import helmet from "helmet";
import dotenv from "dotenv";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import crypto from "crypto";
import rateLimit from "express-rate-limit";
import { OAuth2Client } from "google-auth-library";
import PDFDocument from "pdfkit";
import { RouterOS } from "./routeros.js";
import {
  assertPinStrength,
  buildWifiQr,
  generateBatch,
  sanitizePrefix,
  usernameSpace,
  usernameRegex,
  qrSecretFrom,
  buildQrUrl,
  parseQr,
  auditCards,
  scanVerdict,
  classifyRouterUser
} from "./cards.js";
import {
  binancePayConfigured,
  createBinancePayOrder,
  queryBinancePayOrder,
  verifyBinancePayNotification
} from "./binancePay.js";

dotenv.config();

const { Pool } = pg;

const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

const app = express();

const allowedOrigins = String(process.env.CORS_ORIGIN || "")
  .split(",")
  .map(x => x.trim())
  .filter(Boolean);

if (
  allowedOrigins.includes("*") &&
  process.env.NODE_ENV === "production"
) {
  throw new Error(
    "CORS_ORIGIN must be an explicit allowlist in production"
  );
}

app.set(
  "trust proxy",
  process.env.TRUST_PROXY === "true" ? 1 : false
);

app.disable("x-powered-by");

app.use(
  helmet({
    contentSecurityPolicy: false
  })
);

app.use(
  cors({
    origin(origin, callback) {
      if (
        !origin ||
        allowedOrigins.includes("*") ||
        allowedOrigins.includes(origin)
      ) {
        return callback(null, true);
      }

      return callback(new Error("CORS_ORIGIN_NOT_ALLOWED"));
    }
  })
);

app.use(
  express.json({
    limit: "2mb",
    verify: (req, _res, buf) => {
      req.rawBody = Buffer.from(buf);
    }
  })
);

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false
});

const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 600,
  standardHeaders: true,
  legacyHeaders: false,
  skip: req => req.path === "/health"
});

app.use("/api", apiLimiter);

const googleClient = new OAuth2Client(
  process.env.GOOGLE_CLIENT_ID || undefined
);

const configuredJwtSecret = String(
  process.env.JWT_SECRET || ""
);

const configuredEncryptionSecret = String(
  process.env.ROUTER_ENCRYPTION_KEY || ""
);

const devMode = ["development", "test"].includes(
  process.env.NODE_ENV
);

if (
  !devMode &&
  (
    configuredJwtSecret.length < 32 ||
    configuredEncryptionSecret.length < 32 ||
    /replace|change-me/i.test(
      configuredJwtSecret + configuredEncryptionSecret
    )
  )
) {
  throw new Error(
    "JWT_SECRET and ROUTER_ENCRYPTION_KEY must be strong production secrets"
  );
}

const ENC_KEY = crypto
  .createHash("sha256")
  .update(
    configuredEncryptionSecret ||
    configuredJwtSecret ||
    crypto.randomBytes(32)
  )
  .digest();

const QR_SECRET = qrSecretFrom(
  configuredEncryptionSecret ||
  configuredJwtSecret ||
  "dev"
);

const IV_LEN = 12;

function encrypt(text) {
  const iv = crypto.randomBytes(IV_LEN);

  const c = crypto.createCipheriv(
    "aes-256-gcm",
    ENC_KEY,
    iv
  );

  const data = Buffer.concat([
    c.update(String(text), "utf8"),
    c.final()
  ]);

  return `${iv.toString("base64")}.${c
    .getAuthTag()
    .toString("base64")}.${data.toString("base64")}`;
}

function decrypt(value) {
  const [iv, tag, data] = String(value)
    .split(".")
    .map(x => Buffer.from(x, "base64"));

  const d = crypto.createDecipheriv(
    "aes-256-gcm",
    ENC_KEY,
    iv
  );

  d.setAuthTag(tag);

  return Buffer.concat([
    d.update(data),
    d.final()
  ]).toString("utf8");
}

const runtimeJwtSecret =
  configuredJwtSecret ||
  crypto.randomBytes(32).toString("base64url");

function sign(user) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      role: user.role
    },
    runtimeJwtSecret,
    {
      expiresIn: "12h"
    }
  );
}

function auth(req, res, next) {
  const h = req.headers.authorization || "";

  try {
    const t = h.startsWith("Bearer ")
      ? h.slice(7)
      : "";

    req.user = jwt.verify(
      t,
      runtimeJwtSecret
    );

    next();
  } catch {
    res.status(401).json({
      error: "UNAUTHORIZED"
    });
  }
}

const decPass = v =>
  String(v || "").startsWith("enc:")
    ? decrypt(String(v).slice(4))
    : String(v || "");

const encPass = v =>
  "enc:" + encrypt(v);

function cardOut(row) {
  const password = decPass(row.password);

  const passwordMode =
    password === row.username
      ? "pin"
      : "userpass";

  const qr = row.portal_url
    ? buildQrUrl({
        portalUrl: row.portal_url,
        username: row.username,
        password:
          passwordMode === "pin"
            ? ""
            : password,
        cardId: row.id,
        secret: QR_SECRET
      })
    : (row.qr_content || null);

  return {
    ...row,
    password,
    passwordMode,
    qr_content: qr,
    qrContent: qr,
    wifiQr: buildWifiQr(row.ssid)
  };
}

async function bootstrapAdmin({
  email,
  hash = null,
  googleSub = null
}) {
  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    await client.query(
      "SELECT pg_advisory_xact_lock(7734001)"
    );

    const n =
      (
        await client.query(
          "SELECT count(*)::int n FROM users"
        )
      ).rows[0].n;

    if (n > 0) {
      await client.query("ROLLBACK");
      return null;
    }

    const id = crypto.randomUUID();

    const r = await client.query(
      `INSERT INTO users(
        id,
        email,
        password_hash,
        google_sub,
        role
      )
      VALUES($1,$2,$3,$4,'admin')
      RETURNING id,email,role`,
      [
        id,
        email,
        hash,
        googleSub
      ]
    );

    await client.query(
      `INSERT INTO app_settings(user_id)
       VALUES($1)
       ON CONFLICT DO NOTHING`,
      [id]
    );

    await client.query("COMMIT");

    return r.rows[0];
  } catch (e) {
    try {
      await client.query("ROLLBACK");
    } catch {}

    throw e;
  } finally {
    client.release();
  }
}

function role(...roles) {
  return (req, res, next) =>
    roles.includes(req.user.role)
      ? next()
      : res.status(403).json({
          error: "FORBIDDEN"
        });
}

async function audit(
  userId,
  action,
  entity,
  entityId,
  details = {}
) {
  await pool.query(
    `INSERT INTO audit_logs(
      id,
      user_id,
      action,
      entity,
      entity_id,
      details
    )
    VALUES(
      gen_random_uuid(),
      $1,
      $2,
      $3,
      $4,
      $5
    )`,
    [
      userId,
      action,
      entity,
      entityId,
      JSON.stringify(details)
    ]
  );
}

async function init() {
  await pool.query(`
    CREATE EXTENSION IF NOT EXISTS pgcrypto;

    CREATE TABLE IF NOT EXISTS users(
      id UUID PRIMARY KEY,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT,
      google_sub TEXT UNIQUE,
      role TEXT NOT NULL DEFAULT 'staff',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    ALTER TABLE users
      ADD COLUMN IF NOT EXISTS google_sub TEXT UNIQUE;

    ALTER TABLE users
      ALTER COLUMN password_hash DROP NOT NULL;

    CREATE TABLE IF NOT EXISTS routers(
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,
      name TEXT NOT NULL,
      host TEXT NOT NULL,
      port INTEGER NOT NULL DEFAULT 8728,
      username TEXT NOT NULL,
      password_enc TEXT NOT NULL,
      tls BOOLEAN NOT NULL DEFAULT false,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS cards(
      id UUID PRIMARY KEY,
      router_id UUID NOT NULL
        REFERENCES routers(id)
        ON DELETE CASCADE,
      username TEXT NOT NULL,
      password TEXT NOT NULL,
      profile TEXT NOT NULL DEFAULT 'default',
      price NUMERIC(14,2) NOT NULL DEFAULT 0,
      status TEXT NOT NULL DEFAULT 'available',
      sold_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE(router_id,username)
    );

    CREATE TABLE IF NOT EXISTS sales(
      id UUID PRIMARY KEY,
      card_id UUID
        REFERENCES cards(id)
        ON DELETE SET NULL,
      seller_id UUID
        REFERENCES users(id)
        ON DELETE SET NULL,
      amount NUMERIC(14,2) NOT NULL,
      payment_method TEXT NOT NULL,
      payment_status TEXT NOT NULL DEFAULT 'pending',
      reference TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS payments(
      id UUID PRIMARY KEY,
      sale_id UUID
        REFERENCES sales(id)
        ON DELETE CASCADE,
      provider TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending',
      external_reference TEXT,
      amount NUMERIC(14,2) NOT NULL,
      raw JSONB,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS audit_logs(
      id UUID PRIMARY KEY,
      user_id UUID
        REFERENCES users(id)
        ON DELETE SET NULL,
      action TEXT NOT NULL,
      entity TEXT NOT NULL,
      entity_id TEXT,
      details JSONB,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS notifications(
      id UUID PRIMARY KEY,
      user_id UUID
        REFERENCES users(id)
        ON DELETE CASCADE,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      level TEXT NOT NULL DEFAULT 'info',
      read_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS hotspot_themes(
      id UUID PRIMARY KEY,
      router_id UUID
        REFERENCES routers(id)
        ON DELETE CASCADE
        UNIQUE,
      name TEXT NOT NULL,
      logo_url TEXT,
      primary_color TEXT NOT NULL,
      background TEXT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS hotspot_profiles(
      id UUID PRIMARY KEY,
      router_id UUID NOT NULL
        REFERENCES routers(id)
        ON DELETE CASCADE,
      name TEXT NOT NULL,
      price NUMERIC(14,2) NOT NULL DEFAULT 0,
      duration_minutes INTEGER NOT NULL DEFAULT 0,
      rate_limit TEXT NOT NULL DEFAULT '',
      session_timeout TEXT NOT NULL DEFAULT '',
      idle_timeout TEXT NOT NULL DEFAULT '',
      shared_users INTEGER NOT NULL DEFAULT 1,
      enabled BOOLEAN NOT NULL DEFAULT true,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE(router_id,name)
    );

    CREATE TABLE IF NOT EXISTS hotspot_plans(
      id UUID PRIMARY KEY,
      router_id UUID NOT NULL
        REFERENCES routers(id)
        ON DELETE CASCADE,
      profile_name TEXT NOT NULL,
      name TEXT NOT NULL,
      price NUMERIC(14,2) NOT NULL DEFAULT 0,
      currency TEXT NOT NULL DEFAULT 'XOF',
      active BOOLEAN NOT NULL DEFAULT true,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE(router_id,name)
    );

    CREATE TABLE IF NOT EXISTS plan_price_history(
      id UUID PRIMARY KEY,
      plan_id UUID NOT NULL
        REFERENCES hotspot_plans(id)
        ON DELETE CASCADE,
      old_price NUMERIC(14,2),
      new_price NUMERIC(14,2) NOT NULL,
      changed_by UUID
        REFERENCES users(id)
        ON DELETE SET NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    ALTER TABLE cards
      ADD COLUMN IF NOT EXISTS plan_id UUID
      REFERENCES hotspot_plans(id)
      ON DELETE SET NULL;

    ALTER TABLE cards
      ADD COLUMN IF NOT EXISTS price_currency TEXT
      NOT NULL DEFAULT 'XOF';

    ALTER TABLE cards
      ADD COLUMN IF NOT EXISTS batch_id UUID;

    ALTER TABLE cards
      ADD COLUMN IF NOT EXISTS qr_content TEXT;

    ALTER TABLE cards
      ADD COLUMN IF NOT EXISTS portal_url TEXT;

    ALTER TABLE cards
      ADD COLUMN IF NOT EXISTS ssid TEXT;

    CREATE TABLE IF NOT EXISTS app_settings(
      user_id UUID PRIMARY KEY
        REFERENCES users(id)
        ON DELETE CASCADE,
      theme TEXT NOT NULL DEFAULT 'light',
      currency TEXT NOT NULL DEFAULT 'XOF'
    );

    CREATE TABLE IF NOT EXISTS store_items(
      id UUID PRIMARY KEY,
      name TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      category TEXT NOT NULL DEFAULT 'template',
      price NUMERIC(14,2) NOT NULL DEFAULT 0,
      active BOOLEAN NOT NULL DEFAULT true,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    INSERT INTO store_items(
      id,
      name,
      description,
      category,
      price,
      active
    )
    SELECT
      gen_random_uuid(),
      'MICRO-MAX Glass',
      'قالب زجاجي مجاني لواجهة HotSpot',
      'template',
      0,
      true
    WHERE NOT EXISTS(
      SELECT 1
      FROM store_items
      WHERE name='MICRO-MAX Glass'
    );

    INSERT INTO store_items(
      id,
      name,
      description,
      category,
      price,
      active
    )
    SELECT
      gen_random_uuid(),
      'VIP HotSpot',
      'قالب VIP مجاني لواجهة HotSpot',
      'template',
      0,
      true
    WHERE NOT EXISTS(
      SELECT 1
      FROM store_items
      WHERE name='VIP HotSpot'
    );

    INSERT INTO store_items(
      id,
      name,
      description,
      category,
      price,
      active
    )
    SELECT
      gen_random_uuid(),
      'QR / Barcode Pack',
      'أدوات إنشاء QR وBarcode للكروت بدون اشتراك',
      'tools',
      0,
      true
    WHERE NOT EXISTS(
      SELECT 1
      FROM store_items
      WHERE name='QR / Barcode Pack'
    );

    CREATE TABLE IF NOT EXISTS store_entitlements(
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,
      item_id UUID NOT NULL
        REFERENCES store_items(id)
        ON DELETE CASCADE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE(user_id,item_id)
    );
  `);
}

/* =========================================================
   ROOT + HEALTH
   ========================================================= */

app.get("/", (_req, res) => {
  res.status(200).json({
    ok: true,
    service: "MICRO-MAX API",
    name: "MICRO-MAX Hotspot Manager",
    version: "2.0.0",
    status: "online",
    endpoints: {
      health: "/health",
      api: "/api",
      me: "/api/me"
    }
  });
});

app.get("/health", (_req, res) =>
  res.json({
    ok: true,
    service: "MICRO-MAX API",
    version: "2.0.0"
  })
);

/* =========================================================
   AUTH
   ========================================================= */

app.post(
  "/api/auth/register",
  loginLimiter,
  async (req, res) => {
    const email = String(
      req.body.email || ""
    ).trim().toLowerCase();

    const password = String(
      req.body.password || ""
    );

    if (!email || password.length < 8) {
      return res.status(400).json({
        error: "INVALID_INPUT"
      });
    }

    try {
      const user = await bootstrapAdmin({
        email,
        hash: await bcrypt.hash(password, 12)
      });

      if (!user) {
        return res.status(403).json({
          error: "REGISTRATION_CLOSED"
        });
      }

      res.status(201).json({
        token: sign(user),
        user
      });
    } catch {
      res.status(409).json({
        error: "EMAIL_EXISTS"
      });
    }
  }
);

app.post(
  "/api/auth/login",
  loginLimiter,
  async (req, res) => {
    const email = String(
      req.body.email || ""
    ).trim().toLowerCase();

    const password = String(
      req.body.password || ""
    );

    const r = await pool.query(
      "SELECT * FROM users WHERE email=$1",
      [email]
    );

    if (
      !r.rowCount ||
      !r.rows[0].password_hash ||
      !(await bcrypt.compare(
        password,
        r.rows[0].password_hash
      ))
    ) {
      return res.status(401).json({
        error: "INVALID_CREDENTIALS"
      });
    }

    await audit(
      r.rows[0].id,
      "LOGIN",
      "user",
      r.rows[0].id
    );

    res.json({
      token: sign(r.rows[0]),
      user: {
        id: r.rows[0].id,
        email: r.rows[0].email,
        role: r.rows[0].role
      }
    });
  }
);

app.post(
  "/api/auth/google",
  loginLimiter,
  async (req, res) => {
    const idToken = String(
      req.body.idToken || ""
    ).trim();

    if (!idToken) {
      return res.status(400).json({
        error: "GOOGLE_ID_TOKEN_REQUIRED"
      });
    }

    if (!process.env.GOOGLE_CLIENT_ID) {
      return res.status(503).json({
        error: "GOOGLE_AUTH_NOT_CONFIGURED"
      });
    }

    try {
