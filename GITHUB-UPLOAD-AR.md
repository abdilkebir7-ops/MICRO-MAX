# MICRO-MAX — رفع النسخة الكاملة إلى GitHub

## الخيار الموصى به
يمكن رفع محتويات المشروع كاملة إلى جذر مستودع `MICRO-MAX` بحيث تظهر مباشرة:

```text
gradlew
settings.gradle.kts
app/
backend/
.github/workflows/main.yml
```

## إذا رفعت ملف ZIP فقط
Workflow في V3.0.1 يدعم أيضًا رفع ملف `MICRO-MAX-V3.0.1-FINAL.zip` وحده إلى جذر المستودع. سيقوم GitHub Actions بفك النسخة الكاملة تلقائيًا قبل إعداد Java وGradle.

## أسرار Release المطلوبة
- `MICROMAX_KEYSTORE_BASE64`
- `MICROMAX_KEYSTORE_PASSWORD`
- `MICROMAX_KEY_ALIAS`
- `MICROMAX_KEY_PASSWORD`
- `MICROMAX_API_BASE_URL`
- `MICROMAX_GOOGLE_WEB_CLIENT_ID`

## تشغيل البناء
من GitHub: Actions → MICRO-MAX Release AAB → Run workflow → فعّل `release`.

## النتيجة
سيتم رفع:
- `app-release.aab`
- `mapping.txt`

ولا يتم وضع Keystore أو كلمات المرور داخل المستودع.
