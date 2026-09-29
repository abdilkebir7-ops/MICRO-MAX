# MICRO-MAX — إعداد Release وGoogle Play

تمت إضافة إعداد Release آمن داخل `app/build.gradle.kts`. التطبيق يقبل التوقيع من `keystore.properties` محليًا أو من متغيرات البيئة في CI، ويرفض `assembleRelease` و`bundleRelease` إذا لم يجد Keystore صالحًا.

## إعداد محلي

استخدم Keystore رفع منفصلًا عن مفتاح Google Play App Signing، واحفظه خارج المستودع. أنشئه مثلًا بالأمر التالي:

```bash
keytool -genkeypair -v \
  -keystore "$HOME/.keys/micromax-upload.jks" \
  -alias micromax-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

انسخ `keystore.properties.example` إلى `keystore.properties`، ثم ضع المسار وكلمات المرور والـ alias. بعد ذلك شغّل:

```bash
./gradlew :app:signingReport
./gradlew :app:bundleRelease
```

الملف الناتج هو:

```text
app/build/outputs/bundle/release/app-release.aab
```

## GitHub Actions

فعّل `release` عند تشغيل Workflow يدويًا، وأضف الأسرار التالية:

- `MICROMAX_KEYSTORE_BASE64`
- `MICROMAX_KEYSTORE_PASSWORD`
- `MICROMAX_KEY_ALIAS`
- `MICROMAX_KEY_PASSWORD`
- `MICROMAX_API_BASE_URL`
- `MICROMAX_GOOGLE_WEB_CLIENT_ID`

يتم فك Keystore داخل مجلد مؤقت في Runner فقط، ثم رفع AAB كـ Artifact باسم `MICRO-MAX-release-aab`. Push العادي يبني Debug فقط ولا يحتاج مفاتيح التوقيع.

## ما تم اختباره

- Debug APK ما زال يُبنى بدون Keystore.
- `bundleRelease` غير الموقّع يُرفض برسالة واضحة.
- `bundleRelease` الموقّع تم اختباره باستخدام Keystore مؤقت خارج المشروع ونتج AAB بحجم تقريبي 3.7MB.
- لم يتم تسليم AAB الاختباري لأنه موقّع بمفتاح مؤقت وليس مفتاح المشروع الحقيقي.

## قبل الرفع إلى Google Play

تأكد من استخدام `applicationId` نفسه `com.micromax.app`، زيادة `versionCode` مع كل إصدار، إعداد رابط API حقيقي HTTPS، تسجيل بصمات شهادة Release في Google OAuth، استكمال Privacy Policy وData Safety، وتفعيل Google Play App Signing.
