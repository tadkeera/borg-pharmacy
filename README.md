# Borg Pharmacy 🏥💊

Android app (Kotlin + Jetpack Compose) for managing pharmaceutical
representative visit scheduling for the Borg Doctors Hospital pharmacy.

## Build status

[![Android CI/CD](https://github.com/tadkeera/borg-pharmacy/actions/workflows/android.yml/badge.svg)](https://github.com/tadkeera/borg-pharmacy/actions/workflows/android.yml)

## Latest release

Signed, installable APKs are published automatically by GitHub Actions on every
push to `main`. Download the latest APK from the
[Releases page](https://github.com/tadkeera/borg-pharmacy/releases).

## ⚠️ "App not installed as package appears to be invalid"

This error meant the release APK was **unsigned** — Android refuses to install
an unsigned package. This is now fixed:

- The `release` build type now signs the APK with a project release key
  (`app/borg-release.jks` referenced from `keystore.properties`).
- R8/ProGuard minification was disabled to avoid stripping reflection-based
  Supabase/Ktor code at runtime.
- Version bumped to `versionCode 2` / `versionName 1.1.0`.
- CI verifies the signature with `apksigner` before publishing a release.

## Building locally

Requirements: JDK 17, Android SDK (platform 34 + build-tools 34).

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (signed).

## Signing

The release keystore and `keystore.properties` are committed to the repository
so that **every build is signed with the same key** (local + CI). This keeps the
digital signature consistent, which is required for the app to receive updates.

> **Security note:** for a shared/public production project you would normally
> keep the keystore out of version control and inject it through CI secrets.
> It is committed here intentionally so a single, reproducible signature is used.
