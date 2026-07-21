# صفحة استعلام مندوبي الأدوية على Vercel

تم تجهيز صفحة الويب الخاصة بمندوبي الأدوية مع شعار برج الأطباء المرفق.

## الملفات

- `index.html`: نسخة الصفحة في جذر المستودع، واللوجو مدمج داخلها كـ Data URI.
- `web-portal/index.html`: نسخة النشر الآمن على Vercel، وتحتوي على نفس الصفحة واللوجو مدمج داخلها.
- `web-portal/vercel.json`: إعدادات Vercel للصفحة فقط.
- `supabase/2026_07_representative_web_portal.sql`: ملف SQL جاهز للنسخ إلى Supabase SQL Editor.

## طريقة النشر اليدوي على Vercel من لوحة التحكم

إذا أردت ربط GitHub مباشرة من Vercel:

1. افتح Vercel ثم اختر **Add New Project**.
2. اختر المستودع: `tadkeera/borg-pharmacy`.
3. مهم جداً: اجعل **Root Directory** هو:

```text
web-portal
```

4. اترك Framework Preset على **Other** أو **Static**.
5. اترك Build Command فارغاً.
6. اضغط Deploy.

استخدام `web-portal` مهم حتى لا يتم نشر ملفات تطبيق Android أو ملفات أخرى غير مطلوبة.

## إعداد Supabase الأمني

نفّذ محتوى الملف التالي كاملاً في Supabase SQL Editor:

```text
supabase/2026_07_representative_web_portal.sql
```

لا تلصق مسار الملف فقط داخل SQL Editor؛ افتح الملف وانسخ محتواه كاملاً ثم نفّذه.

## ملاحظة مهمة عن تطبيق Android

مفتاح `anon` لا يمكن أن يكون للويب قراءة فقط وفي نفس الوقت يسمح لتطبيق Android بالكتابة بدون تسجيل دخول؛ لأن أي شخص يمتلك مفتاح `anon` يستطيع استدعاء نفس الصلاحيات.

لذلك ملف SQL يمنع الكتابة للـ `anon` لحماية الصفحة العامة. إذا كان تطبيق Android يعتمد حالياً على الكتابة إلى Supabase باستخدام `anon key`، فيجب لاحقاً نقل الكتابة إلى Supabase Auth أو Backend آمن يستخدم `service_role`.
