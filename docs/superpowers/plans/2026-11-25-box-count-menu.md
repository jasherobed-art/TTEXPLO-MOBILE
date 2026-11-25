# Box Count Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (native execution chosen by the human partner). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add a “Conteo de cajas” menu that reads a box barcode, lets the operator correct KP and the real piece count, rejects duplicates, exports the `CAJ` TXT, and installs the supplied catalog with PU mapped to unit weight.

**Architecture:** Pure domain parser/exporter with JVM tests; a Compose screen wired to the existing `ZebraScannerReceiver` and manual field; catalog import reuses `CatalogRepository.importCsv`; version bumped with the existing `VersionMetadataTest`.

**Tech Stack:** Kotlin, Jetpack Compose, ZXing, JUnit/AssertJ, Gradle 9.3.1, JDK 21, ADB on `b7411c57`.

**Spec:** `docs/superpowers/specs/2026-11-25-box-count-menu-design.md` plus the decisions recorded in conversation (PU mapping, `355 → 310`, `PU = 25 / piezas`).

## Global Constraints

- `versionName = "2.6.0"`, `versionCode = 24` at the end; APK at `D:\aistudio\apk\app2.6.0-ttexplo.apk` with `manifest.json`.
- Every change committed and pushed to `main`; branch `fix/hardening-20261122`.
- Tests with JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot`.
- TXT format: first line `CAJ`, then `codigo,KP,piezas,fechaHora`, then a blank line.
- No duplicate box codes in a session; manual piece count and KP override the range-derived values.
- Spanish (México), human tone, `IMPORTANTE` (never bare `IMPORTANT`), no new screenshots in manual.
- Do not change printing/ticket or `TTEX4` sync packets.

## Review Focus

- Operator types pieces `55` while range says `56` → warning appears and only the typed count is used.
- Same box code scanned twice → second reading rejected with `CÓDIGO REPETIDO`.
- KP cleared or changed → editable and required before confirming.
- TXT export byte-for-byte matches the supplied example, including trailing blank line.
- Catalog row `310` imports with `pesoUnitarioKg = 0.119047619047619` from PU.

---

### Task 1: Build 24

**Files:** Modify `app/src/test/java/com/ttexplo/mobile/VersionMetadataTest.kt`, `app/build.gradle.kts`.

- [ ] **Step 1:** Change assertion to `VERSION_CODE = 24`; run `VersionMetadataTest` with JDK 21; expect FAIL.
- [ ] **Step 2:** Change `versionCode = 23` → `24`; rerun; expect PASS.
- [ ] **Step 3:** Commit `chore: bump build to 24`.

### Task 2: Box count domain

**Files:** Create `domain/inventory/BoxCountRecord.kt`, `domain/inventory/BoxCountParser.kt`; test `app/src/test/java/com/ttexplo/mobile/domain/BoxCountParserTest.kt`.

- [ ] **Step 1:** Write failing tests with exact values: parse `"CAJ310071026008799008854005399631,2,55,2026-11-25 12:41:56"` into code `310071026008799008854005399631`, KP `2`, pieces `55`, timestamp; reject blank KP, pieces `0`/`501`, non-digit code; compute `rangeCount = 55` and `unitWeight = 25.0 / pieces`.
- [ ] **Step 2:** Run test; expect FAIL (unresolved references).
- [ ] **Step 3:** Implement `data class BoxCountRecord(code, kp, pieces, timestamp)` and `object BoxCountParser { fun parse(raw: String, boxWeightKg: Double = 25.0): BoxCountRecord? }`; range count = `final - inicial` (no `+1`).
- [ ] **Step 4:** Run test; expect PASS.
- [ ] **Step 5:** Commit `feat(inventory): parse box count codes`.

### Task 3: TXT exporter

**Files:** Create `domain/inventory/BoxCountTxtExporter.kt`; test `app/src/test/java/com/ttexplo/mobile/domain/BoxCountTxtExporterTest.kt`.

- [ ] **Step 1:** Write failing test asserting exact bytes `"CAJ\n310071026008799008854005399631,2,55,2026-11-25 12:41:56\n\n"` for one record.
- [ ] **Step 2:** Run test; expect FAIL.
- [ ] **Step 3:** Implement `object BoxCountTxtExporter { fun render(records: List<BoxCountRecord>): String }` and `fun fileName(date: String): String = "ttexplo$date.txt"`.
- [ ] **Step 4:** Run test; expect PASS.
- [ ] **Step 5:** Commit `feat(inventory): export box count txt`.

### Task 4: Catalog PU mapping

**Files:** Modify `data/repository/CatalogRepository.kt`; test `app/src/test/java/com/ttexplo/mobile/data/CatalogImportPuTest.kt`.

- [ ] **Step 1:** Write failing test importing a CSV row with PU `0.119047619047619` and asserting `pesoUnitarioKg` equals it; also assert ID `310` resolves.
- [ ] **Step 2:** Run test; expect FAIL.
- [ ] **Step 3:** In `importCsv`, set `resolvedUnitWeight` from PU (index 9) when positive and index 11 absent.
- [ ] **Step 4:** Run test; expect PASS; check rows with PU `25` are reported in the final summary.
- [ ] **Step 5:** Commit `fix(catalog): map PU column to unit weight`.

### Task 5: UI and navigation

**Files:** Modify `ui/screens/home/HomeScreen.kt`, `ui/navigation/AppNavigation.kt`; create `ui/screens/inventory/BoxCountScreen.kt`.

- [ ] **Step 1:** Add menu card `9. Conteo de cajas` after option 8 using `MenuActionCard` and `handleGuardedAction`.
- [ ] **Step 2:** Add route and `composable` entry following existing inventory route patterns.
- [ ] **Step 3:** Build the screen with `ZebraScannerReceiver`, `AlwaysActiveBarcodeField`, editable KP and pieces, range reference, discrepancy dialog, duplicate alert, `Volver a leer`, `Confirmar` and `Exportar TXT` buttons. Block capture while any dialog is open.
- [ ] **Step 4:** Compile with `compileDebugKotlin`; install on `b7411c57` after APK build.
- [ ] **Step 5:** Commit `feat(ui): add box count screen`.

### Task 6: Publish and verify

**Files:** Modify `docs/MANUAL_USUARIO.md`; regenerate `MANUAL_USUARIO.html/.pdf`; rebuild APK.

- [ ] **Step 1:** Add short manual section for menu 9, text only, with `IMPORTANTE` where needed.
- [ ] **Step 2:** Regenerate HTML/PDF; verify no bare `IMPORTANT`.
- [ ] **Step 3:** Run focused tests plus full suite with JDK 21; note the pre-existing QR capacity failure separately.
- [ ] **Step 4:** Push `main`; run `build_versioned.ps1`; verify `manifest.json` is `2.6.0 / 24`.
- [ ] **Step 5:** `adb push` catalog to device, import from Configuración, verify row count and ID `310`.
- [ ] **Step 6:** Physical check: scan `310071026008799008854005399631...`, change KP/pieces, confirm discrepancy, reject duplicate, export TXT and compare with supplied file.
