# حل اتصال MICRO-MAX مع MikroTik

## الفكرة الأساسية

الهاتف لا يتصل بالراوتر مباشرة. التطبيق يتصل بالـBackend، والـBackend هو الذي يتصل بخدمة RouterOS API. لذلك يجب أن يستطيع **الخادم الذي يشغل Backend** الوصول إلى عنوان الراوتر والمنفذ، وليس الهاتف فقط.

## زر التشخيص داخل التطبيق

في شاشة إضافة الراوتر، اذهب إلى خطوة الاتصال واضغط **تشخيص الشبكة من Backend**. سيختبر النظام بالترتيب:

1. حل اسم المضيف DNS.
2. فتح TCP على المنفذ 8728 أو 8729.
3. تسجيل الدخول إلى RouterOS.
4. قراءة `/system/resource/print`.

إذا فشل TCP فالمشكلة في الشبكة أو VPN أو Firewall. إذا نجح TCP وفشل RouterOS فالمشكلة غالبًا في اسم المستخدم أو كلمة المرور أو صلاحيات `api`.

## إعداد MikroTik المحلي

من Terminal الراوتر:

```routeros
/ip service enable api
/ip service set api disabled=no port=8728
/ip service print
```

أنشئ مستخدمًا خاصًا للتطبيق بدل استخدام `admin`:

```routeros
/user group add name=micromax policy=read,write,test,api,sensitive
/user add name=micromax group=micromax password="ضع-كلمة-قوية-جديدة"
```

لإنشاء الكروت وقراءة كلمات المرور يحتاج المستخدم إلى `read,write,test,api,sensitive`. لا تستخدم `full` في الإنتاج إلا مؤقتًا للتشخيص.

## Backend على Vultr أو Render

إذا كان الراوتر داخل المنزل بعنوان محلي مثل `192.168.88.1`، فلن يراه Render أو Vultr عبر الإنترنت. استخدم أحد الحلول التالية:

### الحل المفضل: WireGuard أو Tailscale

أنشئ شبكة VPN بين VPS والراوتر، ثم أدخل عنوان VPN للراوتر داخل MICRO-MAX، مثل `10.10.0.2`. لا تستخدم عنوان LAN المحلي من خارج الشبكة.

بعد إنشاء VPN اختبر من VPS:

```bash
nc -vz 10.10.0.2 8728
curl -fsS https://api.example.com/health
```

### حل مؤقت: Port Forward مقيد

إذا اضطررت لاستخدام عنوان عام، وجّه منفذًا خارجيًا إلى 8728، ثم اسمح فقط بعنوان VPS في MikroTik. لا تفتح API للعالم كله:

```routeros
/ip firewall filter add chain=input protocol=tcp dst-port=8728 src-address=VPS_PUBLIC_IP action=accept comment="MICRO-MAX API"
/ip firewall filter add chain=input protocol=tcp dst-port=8728 action=drop comment="Block public RouterOS API"
```

استبدل `VPS_PUBLIC_IP` بعنوان Vultr الحقيقي، ويفضل استخدام VPN بدل ذلك.

## API-SSL

للاتصال المشفر:

```routeros
/ip service enable api-ssl
/ip service set api-ssl disabled=no port=8729
/ip service print
```

لا تستخدم API-SSL بشهادة ذاتية غير موثوقة في الإنتاج إلا بعد تثبيت CA صحيح على الخادم. للتشخيص المحلي استخدم API على 8728 أولًا.

## أوامر فحص من VPS

```bash
getent hosts ROUTER_HOST
nc -vz -w 5 ROUTER_HOST 8728
timeout 5 bash -c '</dev/tcp/ROUTER_HOST/8728' && echo OPEN || echo CLOSED
```

إذا كانت النتيجة `CLOSED` أو `timed out`، فلن تنجح كلمة المرور لأن الاتصال لم يصل إلى MikroTik.

## Docker

حاوية API في `docker-compose.yml` مربوطة على `127.0.0.1:8080` لحمايتها خلف Nginx أو Caddy. هذا لا يمنعها من الاتصال بالراوتر؛ المهم أن يكون مسار الشبكة من جهاز VPS إلى عنوان الراوتر مفتوحًا عبر VPN أو Route صحيح.

## ترتيب التشخيص الصحيح

1. شغّل Backend.
2. تأكد من أن `/health` يعمل.
3. نفّذ `nc` من نفس جهاز Backend إلى 8728.
4. فعّل API في MikroTik.
5. اختبر المستخدم والصلاحيات.
6. استخدم زر **تشخيص الشبكة من Backend**.
7. بعد نجاح التشخيص احفظ الراوتر ثم جرّب Dashboard وTerminal.
