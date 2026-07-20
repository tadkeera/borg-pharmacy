# صفحة استعلام مندوبي الأدوية على Vercel

تمت إضافة صفحة ويب واحدة في جذر المستودع:

- `index.html`: يحتوي HTML + CSS + JavaScript بالكامل، ويستخدم Tailwind CDN وSupabase JS CDN.
- `vercel.json`: إعدادات استضافة Static على Vercel مع Headers أمنية.
- `supabase/2026_07_representative_web_portal.sql`: SQL لتجهيز View باسم `schedules` وصلاحيات قراءة فقط للـ `anon`.

## طريقة النشر على Vercel

1. افتح Vercel ثم اختر **Add New Project**.
2. اربط المستودع: `tadkeera/borg-pharmacy`.
3. اترك Framework Preset على **Other** أو **Static**.
4. اترك Build Command فارغاً.
5. اترك Output Directory فارغاً.
6. اضغط Deploy.

> إذا كان المستودع مربوطاً بالفعل بـ Vercel، فمجرد push إلى `main` سيبدأ نشر الصفحة تلقائياً.

## إعداد Supabase الأمني

نفّذ الملف التالي في Supabase SQL Editor:

```sql
supabase/2026_07_representative_web_portal.sql
```

مهم: لا تلصق مسار الملف فقط داخل SQL Editor؛ افتح الملف وانسخ محتواه كاملاً ثم نفّذه.

هذا الملف يجعل قراءة `companies` و`visits`/`schedules` متاحة للصفحة العامة، ويمنع `anon` من عمليات `insert/update/delete` على الجداول الأساسية.

## ملاحظة مهمة عن تطبيق Android

إذا كان تطبيق Android يكتب إلى Supabase حالياً باستخدام `anon key`، فإن تفعيل منع الكتابة للـ `anon` سيمنع هذه الكتابة. الحل الإنتاجي الآمن هو نقل عمليات الكتابة إلى:

- Supabase Auth مع سياسات `authenticated`، أو
- Backend آمن يستخدم `service_role` ولا يتم نشره داخل التطبيق أو صفحة الويب.
