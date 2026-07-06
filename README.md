# Borg Pharmacy — Medical Representatives Scheduling & Management

Clean Android project for **Borg Pharmacy / Borg Medical Tower**.

## Stack

- Kotlin
- Jetpack Compose + Material Design 3
- Room Database, offline-first local cache
- Supabase cloud sync adapter
- Clean Architecture-inspired package separation
- 28-day scheduling cycle with static allocation rules
- Thermal pass printing through Android Print Framework for 80mm receipts
- WhatsApp itinerary messages
- RBAC: Admin / Pharmacist
- Local automatic database backups to `BORG PHARMACY/BACKUP`

## First Launch

1. Login username: `admin`
2. Default activation code: `admin2026`
3. The app forces immediate passcode change before entering the system.

## Scheduling Logic

The app uses a fixed 28-day cycle. Working days are Saturday through Wednesday; Thursday and Friday are official weekends.

Tier mapping:

- Tier A: 3 visits per cycle
- Tier B: 2 visits per cycle
- Tier C: 1 visit per cycle
- Unrated: 0 visits

Capacity:

- Morning default: 7 companies, silent overflow up to 10
- Evening default: 8 companies, silent overflow up to 10

Static allocation:

- Upgrades add missing visits only into open slots.
- Downgrades remove only excess visits, starting from the latest visit; existing remaining slots are untouched.
- Company deletion removes only that company's visits.

## Supabase

Run `supabase/schema.sql` in the Supabase SQL editor before enabling sync.

The Android client is configured in `app/build.gradle.kts` using BuildConfig fields for the provided Supabase project URL and anon key. Ensure RLS policies match your deployment security requirements.

## Signing and Updates

The package name is static:

```text
com.borgpharmacy
```

`versionCode` starts at `1` and `versionName` at `1.0.0`. For every update, increment `versionCode` sequentially and update `versionName`.

Do **not** commit keystores. Use `keystore.properties.example` as a template. For GitHub Actions release signing, set these repository secrets:

- `BORG_KEYSTORE_BASE64`
- `BORG_KEYSTORE_PASSWORD`
- `BORG_KEY_ALIAS`
- `BORG_KEY_PASSWORD`

If secrets are not present, CI falls back to debug signing for build verification only; production devices need the same release keystore for seamless over-install updates.

## Build

```bash
./gradlew assembleRelease
```

The APK will be generated in:

```text
app/build/outputs/apk/release/
```

## GitHub Release

Push a tag such as:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The GitHub Action builds and attaches the APK to the release.

## Arabic Note

تم إنشاء المشروع من الصفر بعد إزالة ملفات المشروع السابق. التطبيق يدعم الصلاحيات الأساسية، الجداول اليومية والأسبوعية، التقييم، الاستعلامات عبر واتساب، التقارير، النسخ الاحتياطي، والتعامل بصلاحيات Admin / Pharmacist.
