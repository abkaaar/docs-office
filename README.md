# Docs Office

Offline-first Android document library and viewer (Kotlin + Jetpack Compose).

Package: `com.docsoffice.app` (debug: `com.docsoffice.app.debug`)

## Phases

- **Phase 0/1:** Onboarding, scan, Home/Files/Search, trash, PDF/text/image viewers, settings, intents
- **Phase 2:** DOCX/XLSX/PPTX readers, content search, tags/bookmarks schema, text extraction worker
- **Phase 3:** Scan-to-PDF, PDF tools (merge/rotate/compress/images-to-PDF), Play Billing Pro entitlement
- **Phase 4:** Encrypted vault (biometric + PIN), ha/yo/ig locales, Crashlytics opt-in, R8, launch checklist

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:bundleStaging checkBundleSize
./gradlew testDebugUnitTest
```

Replace [`app/google-services.json`](app/google-services.json) with a real Firebase project before shipping Crashlytics to production.

## Run

```bash
./gradlew :app:installDebug
adb shell am start -n com.docsoffice.app.debug/com.docsoffice.app.MainActivity
```

## Launch checklist (Play Console)

Complete these outside the repo before staged 1.0 rollout:

1. **Store listing** — short/full description, screenshots (phone), feature graphic, app icon.
2. **Privacy policy** — host a URL; document offline-first processing, vault encryption, optional Crashlytics/analytics.
3. **Data safety form** — declare no document upload; optional crash/analytics data only when the user opts in.
4. **All-files access declaration** — justification video + form for `MANAGE_EXTERNAL_STORAGE` (library discovery).
5. **Play Billing** — create subscription product `docs_office_pro`, license testers, verify purchase on a device.
6. **Firebase** — replace placeholder `google-services.json`; enable Crashlytics; upload mapping files from staging/release.
7. **Staged rollout** — internal → closed → production staged percentages; watch crash-free sessions (≥99.5%).
8. **Locales** — have native speakers review Hausa / Yoruba / Igbo strings under `app/src/main/res/values-{ha,yo,ig}/`.
