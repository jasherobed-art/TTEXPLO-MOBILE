# Validación y corrección de fecha en códigos escaneados — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detectar fechas de fabricación imposibles o fuera de 2020-2030 en los códigos escaneados y permitir al operador teclear la fecha correcta sin reescribir el código completo.

**Architecture:** El validador existente pasa a interpretar los 6 dígitos de fecha siempre como `DDMMYY` y devuelve en `ScanResult` los dígitos detectados cuando falla. Un diálogo compartido en `ui/common` toma la fecha corregida, una función pura del dominio sustituye solo esos 6 dígitos y las tres pantallas de vale vuelven a capturar el código corregido por su ruta normal.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, ViewModel/StateFlow, Gradle 9.3.1 con JDK 17.

**Spec:** `docs/superpowers/specs/2026-11-24-barcode-date-validation-design.md`

## Global Constraints

- Versión final del APK: `versionName = "2.6.0"`, `versionCode = 22`; archivo `D:\aistudio\apk\app2.6.0-ttexplo.apk`; `manifest.json` generado por `D:\aistudio\apk\build_versioned.ps1`.
- Cada tarea termina con commit; al final se pushea a `main` en la rama `fix/hardening-20261122`.
- Manual en español de México: usar `IMPORTANTE:`, nunca `IMPORTANT`.
- Los cartuchos de 17 dígitos no se validan por fecha (decisión del diseño aprobado).
- Las lecturas de muestra de Inventario quedan fuera del diálogo de corrección.
- Sin dependencias nuevas. Comandos de prueba con `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot` y `C:\gradle-dist\gradle-9.3.1\bin\gradle.bat`.
- La suite completa tiene 4 fallos preexistentes de Robolectric (SDK 36 pide Java 21); no son regressions de este plan.

## Review Focus

- Código reportado `298190896004876376`: debe rechazarse con `invalidDate = "190896"`; con corrección `190826` debe capturarse `298190826004876376`. Se fija en Task 1.
- Día inexistente y bisiesto: `310226` rechazado, `290224` aceptado, `290225` rechazado. Se fija en Task 1.
- Corrección inválida o sobre código de longitud no soportada: `correctBarcodeDate` devuelve `null` y no se captura nada. Se fija en Task 1.
- Cartucho de 17 dígitos: sigue pasando sin validación de fecha. Se fija en Task 1.
- Gatillo del lector mientras el aviso está abierto: no debe apilar capturas; y **Cancelar** no debe capturar. Sin prueba unitaria; comprobación física en Task 4 con el APK instalado.

---

### Task 1: Regla de fecha y reconstrucción del código

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/domain/scanner/BarcodeItem.kt` (`ScanResult`, línea 24)
- Modify: `app/src/main/java/com/ttexplo/mobile/domain/scanner/ScanValidator.kt` (`isValidPlausibleDate`, línea 231; rechazo de fecha, líneas 158-175)
- Test: `app/src/test/java/com/ttexplo/mobile/domain/ScanValidatorTest.kt`

**Interfaces:**
- Consumes: `ScanValidator.processScan(rawBarcode, currentSessionCodes, allowAccessories): ScanResult`, `TipoEmpaque`.
- Produces: `ScanResult.invalidDate: String?` (6 dígitos detectados, `null` en los demás casos); funciones de archivo en el paquete `com.ttexplo.mobile.domain.scanner`: `isValidManufacturingDate(dateStr: String): Boolean` y `correctBarcodeDate(rawBarcode: String, correctedDate: String): String?`, que devuelve el código con los 6 dígitos sustituidos o `null`.

- [ ] **Step 1: Write the failing tests**

Agregar a `ScanValidatorTest` (usa el `fakeCatalogRepository` existente, que reconoce prefijo `298` como agente en saco y `315` como alto explosivo):

```kotlin
@Test
fun reportedCodeWithYear96IsRejectedAndExposesDetectedDate() = runBlocking {
    val result = scanValidator.processScan("298190896004876376", emptySet())
    assertThat(result.isValid).isFalse()
    assertThat(result.invalidDate).isEqualTo("190896")
    assertThat(result.message).contains("19/08/96")
}

@Test
fun impossibleDatesAreRejectedAndLeapDayIsReal() = runBlocking {
    assertThat(scanValidator.processScan("298310226004876376", emptySet()).isValid).isFalse()
    assertThat(scanValidator.processScan("298290224004876376", emptySet()).isValid).isTrue()
    assertThat(scanValidator.processScan("298290225004876376", emptySet()).isValid).isFalse()
}

@Test
fun correctBarcodeDateReplacesOnlyDateDigits() {
    assertThat(correctBarcodeDate("298190896004876376", "190826"))
        .isEqualTo("298190826004876376")
    assertThat(correctBarcodeDate("315150826026293026360005323457", "270826"))
        .isEqualTo("315270826026293026360005323457")
    assertThat(correctBarcodeDate("0562101024112934", "220826"))
        .isEqualTo("0562208264112934")
    assertThat(correctBarcodeDate("298190896004876376", "190996")).isNull()
    assertThat(correctBarcodeDate("05611110240605501", "220826")).isNull()
}

@Test
fun correctedCodeIsAccepted() = runBlocking {
    val corrected = correctBarcodeDate("298190896004876376", "190826")!!
    val result = scanValidator.processScan(corrected, emptySet())
    assertThat(result.isValid).isTrue()
    assertThat(result.invalidDate).isNull()
}

@Test
fun seventeenDigitCartridgeKeepsSkippingDateValidation() = runBlocking {
    assertThat(scanValidator.processScan("05611110240605501", emptySet()).isValid).isTrue()
}
```

`import com.ttexplo.mobile.domain.scanner.correctBarcodeDate` al inicio del archivo de prueba.

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'; & 'C:\gradle-dist\gradle-9.3.1\bin\gradle.bat' -p 'D:\aistudio\work\tte-esc-inv' testDebugUnitTest --tests 'com.ttexplo.mobile.domain.ScanValidatorTest' --no-daemon`
Expected: FAIL, `Unresolved reference: invalidDate` y `Unresolved reference: correctBarcodeDate`.

- [ ] **Step 3: Implement the rule and the reconstruction function**

En `BarcodeItem.kt`, agregar `val invalidDate: String? = null` a `ScanResult` (al final, con valor por defecto para no tocar los demás constructores).

En `ScanValidator.kt`:
- `private fun isValidPlausibleDate(dateStr: String): Boolean` exige 6 dígitos, `mes = p2 in 1..12`, `anio = 2000 + p3` con `p3 in 20..30`, y `dia = p1` validado con `java.time.LocalDate.of(anio, mes, dia)`; devuelve `false` si `LocalDate.of` lanza `DateTimeException`. Ya no existe la rama `YYMMDD`.
- En el rechazo de fecha (donde hoy se construye el mensaje `"Fecha de fabricación inválida..."`), el mensaje debe incluir la fecha formateada `dd/MM/yy` y el resultado debe llevar `invalidDate = fechaStr`. Ejemplo para `190896`: `"Fecha de fabricación inválida en el código (19/08/96). Debe ser entre 2020 y 2030 y un día válido."`.
- Extraer la regla a `fun isValidManufacturingDate(dateStr: String): Boolean` a nivel de archivo; `isValidPlausibleDate` delega en ella.
- Función de archivo `fun correctBarcodeDate(rawBarcode: String, correctedDate: String): String?`: valida `correctedDate` con `isValidManufacturingDate`, elige el desplazamiento `3` para longitudes `18` y `30`, `4` para `16`, devuelve `null` en cualquier otro caso, y sustituye solo `substring(offset, offset + 6)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2.
Expected: PASS; verificar en la salida que `ScanValidatorTest` terminó sin fallos.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttexplo/mobile/domain/scanner/BarcodeItem.kt app/src/main/java/com/ttexplo/mobile/domain/scanner/ScanValidator.kt app/src/test/java/com/ttexplo/mobile/domain/ScanValidatorTest.kt
git commit -m "feat(scanner): strict manufacturing date and barcode date correction"
```

### Task 2: Diálogo compartido de corrección de fecha

**Files:**
- Create: `app/src/main/java/com/ttexplo/mobile/ui/common/DateCorrectionDialog.kt`

**Interfaces:**
- Consumes: `isValidManufacturingDate` (Task 1); usa los colores de `com.ttexplo.mobile.ui.theme.*` y el patrón de `ExitScanConfirmDialog.kt`.
- Produces: `@Composable fun DateCorrectionDialog(detectedDate: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit)`, donde `detectedDate` son los 6 dígitos y `onConfirm` recibe los 6 dígitos tecleados.

- [ ] **Step 1: Write the composable**

`AlertDialog` con `containerColor = MineDarkSurface`, icono `Icons.Default.Warning` en `MineErrorRedLight`, título `FECHA SOSPECHOSA EN EL CÓDIGO`, texto que muestre `Detectada: dd/MM/yy` a partir de los 6 dígitos y la frase `Debe ser entre 2020 y 2030 y un día válido.` Usar `OutlinedTextField` con `value` de `mutableStateOf("")`, `maxLines = 1`, `placeholder = "DDMMYY"` y `testTag("date_correction_input")`. Botón `Corregir` (`Button`, `testTag("date_correction_confirm")`) habilitado solo cuando `isValidManufacturingDate(value)` es `true`; botón `Cancelar` (`TextButton`, `testTag("date_correction_cancel")`) que llama a `onDismiss`. `onConfirm` envía los 6 dígitos sin formato.

- [ ] **Step 2: Verify it compiles**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'; & 'C:\gradle-dist\gradle-9.3.1\bin\gradle.bat' -p 'D:\aistudio\work\tte-esc-inv' compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ttexplo/mobile/ui/common/DateCorrectionDialog.kt
git commit -m "feat(ui): add manufacturing date correction dialog"
```

### Task 3: Cablear el diálogo en Entradas, Despacho y Devolución

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/ui/screens/entradas/EntradasScanScreen.kt` (estado ~línea 101; `anyDialogOpen` 103; `onSubmitBarcode` 408)
- Modify: `app/src/main/java/com/ttexplo/mobile/ui/screens/despacho/DespachoScanScreen.kt` (estado ~línea 104; `anyDialogOpen` 106; `AlwaysActiveBarcodeField` ~470)
- Modify: `app/src/main/java/com/ttexplo/mobile/ui/screens/devolucion/DevolucionScreen.kt` (estado ~línea 358; `anyDialogOpen` 360; `onSubmitBarcode` 654)
- Test: `app/src/test/java/com/ttexplo/mobile/domain/ScanValidatorTest.kt` (sin cambios nuevos; se usa como regresión)

**Interfaces:**
- Consumes: `ScanResult.invalidDate`, `correctBarcodeDate(rawBarcode, correctedDate)` (Task 1); `DateCorrectionDialog` (Task 2).
- Produces: `dateCorrectionTarget: Pair<String, String>?` en estado local de cada pantalla; `(rawBarcode, detectedDate)` se pasa al diálogo.

- [ ] **Step 1: Add local state and open the dialog from the scan result**

En cada una de las tres pantallas:
- `var dateCorrectionTarget by remember { mutableStateOf<Pair<String, String>?>(null) }` junto a las demás variables de diálogo.
- Incluir `dateCorrectionTarget != null` en `anyDialogOpen`.
- `LaunchedEffect(uiState.latestScanResult)` (en Devolución, `latestScanResult`) que, si `result.invalidDate != null`, asigna `dateCorrectionTarget = result.rawBarcode to result.invalidDate`. Usar el objeto `ScanResult` como clave para que cada lectura nueva pueda abrir el aviso, incluso si repite la misma fecha.
- En `AlwaysActiveBarcodeField`, envolver el envío: `onSubmitBarcode = { code -> if (dateCorrectionTarget == null) viewModel.onBarcodeScanned(code) }` (en Devolución, `viewModel.onScanned(code)`). Así el gatillo no apila lecturas mientras el aviso está abierto.

- [ ] **Step 2: Render the dialog and reprocess the corrected code**

Cerca de los otros diálogos al final del composable:

```kotlin
dateCorrectionTarget?.let { (rawCode, detectedDate) ->
    DateCorrectionDialog(
        detectedDate = detectedDate,
        onConfirm = { correctedDate ->
            val correctedCode = correctBarcodeDate(rawCode, correctedDate)
            dateCorrectionTarget = null
            if (correctedCode != null) viewModel.onBarcodeScanned(correctedCode)
        },
        onDismiss = { dateCorrectionTarget = null }
    )
}
```

En Devolución usar `viewModel.onScanned(correctedCode)`. No borrar `latestScanResult`: al capturar el código corregido se sustituye solo con la nueva lectura.

- [ ] **Step 3: Verify compilation and scanner regression tests**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'; & 'C:\gradle-dist\gradle-9.3.1\bin\gradle.bat' -p 'D:\aistudio\work\tte-esc-inv' testDebugUnitTest --tests 'com.ttexplo.mobile.domain.ScanValidatorTest' --tests 'com.ttexplo.mobile.printer.TicketGeneratorTest' --no-daemon`
Expected: BUILD SUCCESSFUL, sin fallos.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ttexplo/mobile/ui/screens/entradas/EntradasScanScreen.kt app/src/main/java/com/ttexplo/mobile/ui/screens/despacho/DespachoScanScreen.kt app/src/main/java/com/ttexplo/mobile/ui/screens/devolucion/DevolucionScreen.kt
git commit -m "feat(scanner): correct suspicious date before capturing a vale code"
```

### Task 4: Build 22, manual, publicación y comprobación física

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 21` → `22`, línea 19)
- Modify: `app/src/test/java/com/ttexplo/mobile/VersionMetadataTest.kt` (aserción de `VERSION_CODE`, línea 13)
- Modify: `docs/MANUAL_USUARIO.md` (nueva subsección después de la línea 51)
- Regenerate: `D:\aistudio\apk\team\MANUAL_USUARIO.html`, `D:\aistudio\apk\team\MANUAL_USUARIO.pdf`

**Interfaces:**
- Consumes: cambios de Tasks 1-3; `D:\aistudio\apk\build_versioned.ps1` lee `versionName` y `versionCode` desde `app/build.gradle.kts`.
- Produces: APK `app2.6.0-ttexplo.apk` con `manifest.json` en `versionCode = 22`, instalado en el dispositivo `b7411c57`.

- [ ] **Step 1: Update the version assertion first**

Cambiar en `VersionMetadataTest.kt`: `assertThat(BuildConfig.VERSION_CODE).isEqualTo(22)`.

- [ ] **Step 2: Run it to verify it fails**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'; & 'C:\gradle-dist\gradle-9.3.1\bin\gradle.bat' -p 'D:\aistudio\work\tte-esc-inv' testDebugUnitTest --tests 'com.ttexplo.mobile.VersionMetadataTest' --no-daemon`
Expected: FAIL, esperaba 22 pero era 21.

- [ ] **Step 3: Bump the build and document the warning**

Cambiar `versionCode = 22` en `app/build.gradle.kts`. Agregar en `docs/MANUAL_USUARIO.md`, después del párrafo de "Vale en progreso recuperado" (línea 51):

```
### Si el código trae la fecha mal

Si el lector captura un código con una fecha de fabricación fuera de 2020-2030 o con un día que no existe, la app no lo da de alta. Verás **FECHA SOSPECHOSA EN EL CÓDIGO** con la fecha detectada, por ejemplo **19/08/96**.

IMPORTANTE: escribe los 6 dígitos de la fecha correcta en formato `DDMMYY` y toca **CORREGIR**. La app sustituye solo esos dígitos y captura el código corregido. Si tocas **CANCELAR**, la lectura no se agrega al vale.
```

Mantener el resto del manual intacto.

- [ ] **Step 4: Verify the version test passes**

Run: el comando del Step 2.
Expected: PASS, `appVersionIs260` sin fallos.

- [ ] **Step 5: Regenerate the manual**

Run: `python "C:\Users\des92\AppData\Local\Temp\opencode\md2html_manual_interactive.py"; & "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --disable-gpu --no-sandbox --no-pdf-header-footer --print-to-pdf="D:\aistudio\apk\team\MANUAL_USUARIO.pdf" "file:///D:/aistudio/apk/team/MANUAL_USUARIO.html"`
Expected: HTML y PDF regenerados; buscar `FECHA SOSPECHOSA EN EL CÓDIGO` en el HTML y comprobar que no aparezca `IMPORTANT` suelto (solo dentro de `IMPORTANTE:`).

- [ ] **Step 6: Commit and push**

```bash
git add app/build.gradle.kts app/src/test/java/com/ttexplo/mobile/VersionMetadataTest.kt docs/MANUAL_USUARIO.md
git commit -m "chore: bump build to 22 and document date correction"
```

Luego, con el token de GitHub CLI ya usado en esta sesión:

```powershell
$env:GIT_TERMINAL_PROMPT='0'; $gh="C:\Program Files\GitHub CLI\gh.exe"; $token=(& $gh auth token 2>$null).Trim(); $basic=[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("x-access-token:$token")); git -C "D:\aistudio\work\tte-esc-inv" -c credential.helper= -c "http.extraheader=AUTHORIZATION: basic $basic" push origin fix/hardening-20261122:main
```

Expected: `main` actualizado hasta el commit de este task.

- [ ] **Step 7: Build, verify manifest and install**

Run: `powershell -ExecutionPolicy Bypass -File "D:\aistudio\apk\build_versioned.ps1"`
Expected: `versionName=2.6.0 versionCode=22`, `sha7` igual al HEAD pusheado.

Run: `Get-Content 'D:\aistudio\apk\manifest.json'`
Expected: `versionCode: 22`, `apkPath` apuntando a `app2.6.0-ttexplo.apk`.

Run: `& 'C:\Users\des92\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s b7411c57 install -r "D:\aistudio\apk\app2.6.0-ttexplo.apk"`
Expected: `Success`.

- [ ] **Step 8: Physical check**

En la terminal, escanear o teclear `298190896004876376` en **Entradas**: debe aparecer el aviso con `19/08/96`. Teclear `190826` y tocar **Corregir**: el código capturado debe ser `298190826004876376`. Repetir con `Cancelar` para confirmar que no agrega el código. Verificar que, con el aviso abierto, apretar el gatillo no agrega lecturas. No imprimir: el ticket no cambia.
