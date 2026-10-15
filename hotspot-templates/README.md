# MICROMAX HotSpot templates

قوالب إنتاجية لـ MikroTik HotSpot:
- login.html
- status.html

يمكن تخصيصها من HotSpot Visual Studio داخل التطبيق ثم نشرها للراوتر مع Backup تلقائي.

## قوالب Login الجاهزة (Member / Voucher / QR)
`templates/login_*.html` — 6 قوالب جاهزة للرفع إلى `hotspot/login.html` (Cyan Blue, Aurora Light, Emerald, Sunset, Midnight Glass, Minimal).
تُولَّد من `app/src/main/assets/login_base.html` بواسطة `python3 hotspot-templates/tools/gen.py`
(وتتحقق منها خطوة CI بالأمر `--check`). نفس القوالب متاحة داخل التطبيق: الخدمات ← «قوالب Login» (إضافة/تعديل للأدمن فقط).
