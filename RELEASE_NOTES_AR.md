# MICRO-MAX V2.16.1 — النسخة المطوّرة

## ما تم إصلاحه وتطويره

### تجربة الواجهة الداخلية
- إضافة مركز **MICRO-MAX Studio** داخل التطبيق.
- نظام بصري موحّد Black Glass / White Glass بأزرار وCards وحقول متناسقة.
- تبويب مستقل لتصميم الكروت والهوية التجارية.
- تبويب مستقل لتصميم صفحات HotSpot.
- إبقاء المزايا الأساسية داخل التطبيق: Dashboard، الراوترات، Profiles، الكروت، المتجر، المبيعات، التقارير، الأمان، الشبكة، Terminal والإعدادات.

### Card Design Studio
- قوالب جاهزة:
  - Midnight Glass
  - Orange Market
  - Clean White
  - VIP Neon
- تعديل عنوان الكرت والوصف ومعلومات الدعم.
- اختيار اللون الرئيسي بصيغة HEX.
- معاينة مباشرة للكرت مع Username وQR.
- حفظ هوية التصميم للراوتر عبر Backend.

### الطباعة
- Presets للورق:
  - A4
  - 80mm Thermal
  - Custom
- تحديد الأعمدة والصفوف وعدد النسخ.
- معاينة ورقة الطباعة قبل فتح نظام Android Print.
- طباعة QR وBarcode وبيانات الدخول.
- ما زال Android Print هو طبقة التشغيل؛ اختيار الطابعة الفعلية يتم من نافذة الطباعة في الجهاز.

### HotSpot Pages Studio
- تصميم وتعديل `login.html`.
- تصميم وتعديل `status.html`.
- معاينة واجهة الزائر داخل التطبيق.
- التحقق من الحقول والمتغيرات قبل النشر.
- نشر Admin-only.
- إنشاء Backup تلقائي قبل استبدال الملف في MikroTik.
- إضافة Endpoints موحّدة للقراءة والتحقق والنشر.

### Backup
- إكمال `/api/backup/restore` بدلًا من `501 NOT_IMPLEMENTED`.
- الاستعادة داخل Transaction.
- التحقق من ملكية الراوتر والحساب.
- عدم استعادة كلمات مرور الراوتر أو أسرار الدفع.
- إرجاع قائمة بالبيانات التي تم تجاوزها بأمان.

### الدفع وNITA
- الدفع الإلكتروني ما زال غير فعال افتراضيًا.
- `ENABLE_ONLINE_PAYMENTS=false`.
- NITA معلن كغير مفعّل حتى يتم توفير API وWebhook Secret ورابط Callback HTTPS.
- البيع النقدي، الكروت، QR، Barcode، المتجر والطباعة لا تعتمد على API الدفع.

## التحقق

- Backend syntax check: ناجح.
- RouterOS module syntax check: ناجح.
- Backend smoke tests: ناجحة.
- Android `:app:assembleDebug`: ناجح.
- APK الناتج: Debug APK، وليس Release موقّعًا للنشر.

## قبل Google Play

1. إعداد Keystore حقيقي للـ Release.
2. ربط Backend حقيقي عبر HTTPS مع Reverse Proxy.
3. إضافة Privacy Policy وTerms of Service.
4. اختبار MikroTik حقيقي وطابعة فعلية على Android 13–15.
5. عند توفر NITA API: إضافة التكامل مع توقيع Webhook واختبارات دفع قبل التفعيل.
