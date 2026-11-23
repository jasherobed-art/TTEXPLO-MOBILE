# License Vault Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bóveda de licencias por terminal, accesible desde Inventario, con export/import y ocultación de modos de prueba al instalar una licencia real.

**Architecture:** `LicenseVault` guarda entradas en `filesDir/license_vault.json`; `LicenseManager` añade un flag de licencia real instalada; una nueva pantalla Compose se abre desde el menú overflow de `InventoryScreen`; los 5 paneles de prueba consultan `isDemoModeAllowed()`.

**Tech Stack:** Kotlin 2.4.21, Compose Material3, Navigation Compose, JUnit4 + Robolectric `@Config(sdk=35)`, `org.json`, SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`).

**Spec:** `docs/superpowers/specs/2026-11-23-license-vault-design.md`

## Global Constraints

- La app NO genera licencias; solo guarda/valida/instala las de License Master Pro.
- No tocar el formato criptográfico ni exponer `MASTER_SECRET` en UI.
- La licencia temporal de fábrica (`ensureOneDayTemporaryLicenseIfNeeded`) sigue funcionando y NO marca el flag de licencia real.
- Misma firma de APK; versionName 2.5.0 / versionCode 20.
- Sin dependencias nuevas.

## Review Focus

- Licencia de otra terminal: la tarjeta debe marcar «OTRA TERMINAL» y `saveLicense` debe fallar con `DEVICE_MISMATCH`.
- Licencia inválida al agregar: debe rechazarse con `verifyToken` antes de guardarse.
- Export/import round-trip: ninguna entrada duplicada ni perdida.
- Instalar licencia real debe ocultar los 5 modos de prueba; la licencia temporal de fábrica no debe ocultarlos.
- Borrar/exportar/importar no debe alterar la licencia activa.

---

### Task 1 — Bóveda y flag de licencia real

**Files:**
- Create: `app/src/main/java/com/ttexplo/mobile/license/LicenseVault.kt`
- Modify: `app/src/main/java/com/ttexplo/mobile/license/LicenseManager.kt` (`saveLicense`, nuevos `isDemoModeAllowed` / flag)
- Test: `app/src/test/java/com/ttexplo/mobile/license/LicenseVaultTest.kt`

**Interfaces:**
- Consumes: `LicenseManager.verifyToken(context, token): Result<LicenseInfo>`.
- Produces: `LicenseVault.load(context): List<VaultEntry>`, `add(context, alias, token): Result<Unit>`, `remove(context, id)`, `toJson(entries): String`, `fromJson(json): List<VaultEntry>`, `LicenseManager.isDemoModeAllowed(context): Boolean`.

- [ ] **Step 1: test RED** — crear `LicenseVaultTest` con Robolectric `@Config(sdk=[35])`:
  - `roundTripJsonKeepsEntries`: `fromJson(toJson(listOf(entry))) == listOf(entry)`.
  - `addRejectsInvalidToken`: `add(context, "x", "basura")` es `Failure`.
  - `saveLicenseMarksRealLicenseAndHidesDemo`: con token válido generado como en `LicenseManagerTest`, `saveLicense` es Success, `isDemoModeAllowed(context) == false`.
  - `factoryTemporaryLicenseKeepsDemoVisible`: llamar `ensureOneDayTemporaryLicenseIfNeeded` (con prefs vacíos) y afirmar `isDemoModeAllowed(context) == true`.

- [ ] **Step 2: ejecutar y verificar que falla**
Run: `gradle.bat :app:testDebugUnitTest --tests "*LicenseVaultTest*" --no-daemon`
Expected: FAIL (clases/funciones no existen).

- [ ] **Step 3: implementar `LicenseVault`** — `VaultEntry(id, alias, token, createdAt)`; archivo `license_vault.json` en `context.filesDir`; `add` valida con `verifyToken` antes de guardar; `fromJson` tolera JSON vacío/corrupto devolviendo lista vacía.

- [ ] **Step 4: implementar flag en `LicenseManager`** — `KEY_REAL_LICENSE_INSTALLED`; `saveLicense` lo marca solo en éxito; `isDemoModeAllowed` devuelve `!prefs.getBoolean(KEY_REAL_LICENSE_INSTALLED, false)`. `ensureOneDayTemporaryLicenseIfNeeded` no lo marca.

- [ ] **Step 5: GREEN** — mismo comando, Expected 4/4 PASS.

- [ ] **Step 6: commit** — `git commit -m "feat(license): add per-terminal license vault and real-license flag"`

---

### Task 2 — Pantalla de bóveda y acceso desde Inventario

**Files:**
- Create: `app/src/main/java/com/ttexplo/mobile/ui/screens/license/LicenseVaultScreen.kt`
- Modify: `app/src/main/java/com/ttexplo/mobile/ui/navigation/AppNavigation.kt` (`Screen.LicenseVault`)
- Modify: `app/src/main/java/com/ttexplo/mobile/MainActivity.kt` (composable)
- Modify: `app/src/main/java/com/ttexplo/mobile/ui/screens/inventory/InventoryScreen.kt` (menú overflow + callback)

**Interfaces:**
- Consumes: `LicenseVault.*`, `LicenseManager.getFormattedDeviceId(context)`, `saveLicense`.
- Produces: `Screen.LicenseVault.route`, `InventoryScreen(onNavigateToLicenseVault: () -> Unit)`.

- [ ] **Step 1: ruta** — añadir `object LicenseVault : Screen("license_vault")` y su `composable` en `MainActivity` con `onNavigateBack = { popBackStack() }`.

- [ ] **Step 2: menú en Inventario** — `TopAppBar.actions`: `IconButton` con `Icons.Default.MoreVert` y `testTag("inventory_more_menu")`; `DropdownMenu` con item «Licencias» y `testTag("inventory_license_vault_item")`. Llamar al callback nuevo. Verificar con grep que el FAB sigue con `testTag("inventory_quick_load_button")`.

- [ ] **Step 3: pantalla** — `LicenseVaultScreen(onNavigateBack)`: cabecera con Device ID formateado, botón «Copiar ID de esta terminal», «+ Agregar licencia» (diálogo con alias + token; valida con `LicenseVault.add`), lista de tarjetas con estado derivado de `LicenseManager.verifyToken` (`esta/otra terminal`, vigente/vencida), acciones Instalar/Copiar/Compartir/Eliminar, y botones Exportar/Importar con SAF.

- [ ] **Step 4: verificar compilación**
Run: `gradle.bat :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: verificación en emulador** — abrir Inventario → ⋮ → Licencias; agregar una licencia válida de otra terminal generada en el test; confirmar tarjeta «OTRA TERMINAL»; exportar e importar el JSON. Captura en `team/license-vault.png`. Si no hay emulador, reportar limitación sin simular.

- [ ] **Step 6: commit** — `git commit -m "feat(ui): add license vault screen in inventory menu"`

---

### Task 3 — Ocultar modos de prueba

**Files:**
- Modify: `ui/screens/entradas/EntradasScanScreen.kt:391`
- Modify: `ui/screens/despacho/DespachoScanScreen.kt:401`
- Modify: `ui/screens/devolucion/DevolucionScreen.kt:605`
- Modify: `ui/screens/accesorios/AccesoriosHeaderScreen.kt` (barra de accesorios — localizar llamada a `DummyAccessoriesScannerBar`)
- Modify: `ui/screens/inventory/InventorySyncReceiveAndConflictsTabs.kt:270-313`
- Modify: `ui/screens/inventory/QuickInventoryDialog.kt:380-394`
- Test: ampliar `LicenseVaultTest` con `demoPanelsUseLicenseFlag` (test de fuente: grep de `isDemoModeAllowed` en los 6 archivos)

**Interfaces:**
- Consumes: `LicenseManager.isDemoModeAllowed(context)`.
- Produces: ninguno.

- [ ] **Step 1: test RED** — `demoPanelsUseLicenseFlag` lee los 6 archivos y afirma que contienen `isDemoModeAllowed`. Expected FAIL.
- [ ] **Step 2: envolver paneles** — en cada archivo, `val context = LocalContext.current; val demoAllowed = remember { LicenseManager.isDemoModeAllowed(context) }` y condicionar el bloque correspondiente. En `QuickInventoryDialog`, ocultar el botón «Prueba» y su `AnimatedVisibility`.
- [ ] **Step 3: GREEN** — mismo test + `assembleDebug`.
- [ ] **Step 4: verificación en emulador** — con licencia temporal de fábrica los paneles aparecen; al instalar una licencia real desaparecen. Captura en `team/demo-hidden.png`.
- [ ] **Step 5: commit** — `git commit -m "fix(ui): hide demo modes after a real license is installed"`

## Self-review

- Cobertura: bóveda, portabilidad, instalación y ocultación tienen tarea y test.
- El flag se marca en `saveLicense`, no en el aprovisionamiento temporal: distingue licencia real de prueba.
- Un único `verifyToken` decide validez y terminal destino; no se duplica lógica cripto.
