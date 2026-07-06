# Implementation Notes

## What is implemented

- Clean Android source tree with Kotlin/Compose/Room/Supabase modules.
- Room entities and DAOs for companies, representatives, visits, print logs, users, and settings.
- Offline-first repository: all mutations write to Room first, create a local backup, and then request Supabase sync.
- 28-day scheduling algorithm with fixed working days and silent overflow capacity.
- Static reallocation: no shuffling of existing visits during upgrades/downgrades.
- Forced first-login admin code change.
- Role-based UI gating: pharmacists can view, print, communicate, and inspect reports; only admins can mutate data.
- 80mm receipt pass rendering with large bold date/day/shift.
- WhatsApp itinerary intent.
- Manual local restore, manual backup, and Google Drive sharing via Android Sharesheet.
- GitHub Actions build and release pipeline.

## Production hardening checklist

1. Add release signing secrets to GitHub repository settings.
2. Run `supabase/schema.sql` and review RLS policies.
3. Decide whether to keep `MANAGE_EXTERNAL_STORAGE`; for Play Store distribution, prefer Storage Access Framework.
4. Test the Android Print Framework with the actual 80mm printer model. If the printer requires ESC/POS over Bluetooth/USB, replace `PassPrintManager` with the vendor SDK implementation.
5. Run an end-to-end sync test on two devices before go-live.
6. Keep the same package name and release keystore for every update.
