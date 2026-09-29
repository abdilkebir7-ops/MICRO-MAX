import crypto from "crypto";

const BASE_URL = process.env.BINANCE_PAY_BASE_URL || "https://bpay.binanceapi.com";

function credentialsReady() {
  return Boolean(process.env.BINANCE_PAY_CERTIFICATE_SN && process.env.BINANCE_PAY_SECRET_KEY);
}

function signRequest(body) {
  const timestamp = Date.now().toString();
  const nonce = crypto.randomBytes(16).toString("hex");
  const payload = `${timestamp}\n${nonce}\n${body}\n`;
  const signature = crypto.createHmac("sha512", process.env.BINANCE_PAY_SECRET_KEY)
    .update(payload, "utf8").digest("hex").toUpperCase();
  return { timestamp, nonce, signature };
}

export async function binancePayRequest(path, body) {
  if (!credentialsReady()) throw new Error("BINANCE_PAY_NOT_CONFIGURED");
  const json = JSON.stringify(body);
  const { timestamp, nonce, signature } = signRequest(json);
  const response = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "BinancePay-Timestamp": timestamp,
      "BinancePay-Nonce": nonce,
      "BinancePay-Certificate-SN": process.env.BINANCE_PAY_CERTIFICATE_SN,
      "BinancePay-Signature": signature
    },
    body: json
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error(`BINANCE_PAY_INVALID_RESPONSE_${response.status}`); }
  if (!response.ok || data.status !== "SUCCESS") {
    const e = new Error(data.errorMessage || data.code || `BINANCE_PAY_HTTP_${response.status}`);
    e.providerResponse = data;
    throw e;
  }
  return data;
}

export function binancePayConfigured() { return credentialsReady(); }

export function verifyBinancePayNotification({ body, timestamp, nonce, signature }) {
  if (!credentialsReady() || !body || !timestamp || !nonce || !signature) return false;
  const payload = `${timestamp}\n${nonce}\n${body}\n`;
  const expected = crypto.createHmac("sha512", process.env.BINANCE_PAY_SECRET_KEY)
    .update(payload, "utf8").digest("hex").toUpperCase();
  const a = Buffer.from(String(expected), "utf8");
  const b = Buffer.from(String(signature).toUpperCase(), "utf8");
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export async function createBinancePayOrder({ merchantTradeNo, amount, currency, referenceGoodsId, goodsName, goodsDetail }) {
  return binancePayRequest("/binancepay/openapi/v3/order", {
    env: { terminalType: process.env.BINANCE_PAY_TERMINAL_TYPE || "APP" },
    merchantTradeNo,
    orderAmount: Number(amount),
    currency: currency || process.env.BINANCE_PAY_CURRENCY || "USDT",
    description: goodsDetail || goodsName || "MICRO-MAX Hotspot",
    goodsDetails: [{
      goodsType: "01",
      goodsCategory: "D000",
      referenceGoodsId: String(referenceGoodsId).replace(/[^A-Za-z0-9]/g, "").slice(0, 32) || "MICROMAX",
      goodsName: String(goodsName || "MICRO-MAX Hotspot Card").slice(0, 256),
      goodsDetail: String(goodsDetail || "Hotspot access card").slice(0, 256)
    }]
  });
}

export async function queryBinancePayOrder({ merchantTradeNo, prepayId }) {
  return binancePayRequest("/binancepay/openapi/order/query", {
    merchantTradeNo: merchantTradeNo || null,
    prepayId: prepayId || null
  });
}
