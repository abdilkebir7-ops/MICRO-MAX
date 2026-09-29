# النشر على Vultr (Ubuntu)

## الطريقة 1: Docker Compose (الأسهل)
```
cp .env.example .env            # عدّل POSTGRES_PASSWORD (حروف وأرقام فقط)
cp backend/.env.example backend/.env
openssl rand -base64 48         # ضعه في JWT_SECRET
openssl rand -base64 48         # قيمة مختلفة في ROUTER_ENCRYPTION_KEY
docker compose up -d --build
```
`DATABASE_URL` و`NODE_ENV=production` يُضبطان تلقائياً من compose.

## الطريقة 2: PostgreSQL على السيرفر مباشرة
قاعدة `microdb` والمستخدم `micromax` الذي أنشأته يعملان. في `backend/.env`:
```
NODE_ENV=production
DATABASE_URL=postgresql://micromax:كلمة_المرور@localhost:5432/microdb
```
ثم `cd backend && npm ci --omit=dev && node src/server.js` (يفضّل تشغيله بـ systemd أو pm2).

## بعد التشغيل
1. سجّل حسابك فوراً من التطبيق: أول حساب يصبح المدير، ثم يُغلق التسجيل نهائياً.
2. ضع الـ API خلف HTTPS (Caddy أو nginx) واجعل `TRUST_PROXY=true`.
3. على MikroTik: مستخدم API بصلاحيات read,write,test,api,sensitive.
4. احفظ `ROUTER_ENCRYPTION_KEY`: إن ضاع لا يمكن فك كلمات مرور الراوترات والكروت.
