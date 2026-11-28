# Ticket con identificación de terminal — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada vale impreso muestra `TERMINAL: <deviceId>` para atribuirlo a la terminal que lo generó.

**Architecture:** El device ID ya existe y se usa en la sincronización. Se agrega un parámetro `terminalId` con default vacío a los generators de vale y `ValePrintHelper` lo resuelve desde `LicenseManager`; impresión, preview y dashboard web comparten el mismo funnel.

**Tech Stack:** Kotlin, Android, JUnit4 + Truth, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-27-terminal-id-ticket-design.md`

## Global Constraints

- Sin librerías nuevas.
- No tocar `ScanValidator.kt` ni `build.gradle.kts` salvo el versionado de la Tarea 3.
- La línea se omite si `terminalId` está vacío; los tests y llamadas existentes no cambian.
- Identificador: `LicenseManager.getFormattedDeviceId(context)`.
- Fuera de alcance: ticket de prueba, exportación `.txt`, registros y sincronización.

## Review Focus

- `terminalId` vacío: el ticket no debe mostrar `TERMINAL:` ni dejar líneas en blanco.
- Id con acentos o símbolos: se pasa por `stripAccents` como el resto del contenido.
- CPCL: la línea debe sumar su alto a `y` para no encimarse con el siguiente dato.
- Conteo en caja: no tiene cabecera de mina; la línea va antes de `FECHA`/`CAJAS`.
- Preview y dashboard deben mostrar lo mismo que la impresión (mismo generator Plain).

---

### Task 1: Línea `TERMINAL:` en los cinco vales (EscPos/Plain/CPCL)

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt`
- Test: `app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt`

**Interfaces:**
- Produces: `terminalId: String = ""` como último parámetro de
  `generateDespacho{EscPos,Plain,Cpcl}`, `generateAccesorios{...}`,
  `generateEntrada{...}`, `generateDevolucion{...}` y
  `generateBoxCount{...}`.
- Efecto: con `terminalId = "ZBR-POLV-01"` el ticket contiene
  `TERMINAL: ZBR-POLV-01` al inicio del bloque de datos; con `""` no aparece
  `TERMINAL:`.

- [ ] **Step 1: Escribir el test que falla**

```kotlin
@Test
fun ticketShowsTerminalIdWhenProvided() {
    val plain = String(TicketGenerator.generateDespachoPlain(
        record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH, terminalId = "ZBR-POLV-01"
    ), charset)
    val esc = String(TicketGenerator.generateDespachoEscPos(
        record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH, terminalId = "ZBR-POLV-01"
    ), charset)
    val cpcl = String(TicketGenerator.generateDespachoCpcl(
        despacho = despacho, paperWidth = PaperWidth.WIDTH_3_INCH, terminalId = "ZBR-POLV-01"
    ), charset)
    assertThat(plain).contains("TERMINAL: ZBR-POLV-01")
    assertThat(esc).contains("TERMINAL: ZBR-POLV-01")
    assertThat(cpcl).contains("TERMINAL: ZBR-POLV-01")
}

@Test
fun ticketOmitsTerminalLineWhenBlank() {
    val text = String(TicketGenerator.generateDespachoPlain(
        record = despacho, paperWidth = PaperWidth.WIDTH_3_INCH
    ), charset)
    assertThat(text).doesNotContain("TERMINAL:")
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `gradle :app:testDebugUnitTest --tests "*TicketGeneratorTest*"` (con `JAVA_HOME` configurado)
Expected: falla de compilación por `terminalId` inexistente.

- [ ] **Step 3: Implementar**

Agregar en `TicketGenerator` el helper privado:

```kotlin
private fun terminalLabel(terminalId: String): String? =
    terminalId.takeIf { it.isNotBlank() }?.let { "TERMINAL: ${stripAccents(it)}" }
```

En cada generator, agregar `terminalId: String = ""` como último parámetro y
escribir la línea justo después del divisor de cabecera, antes del primer dato:

- Plain: `terminalLabel(terminalId)?.let { sb.append(it).append("\r\n") }`
- EscPos: `terminalLabel(terminalId)?.let { out.write("$it\r\n".toByteArray(CHARSET)) }`
- CPCL: calcular fuente con `selectCpclFont(it, lineEnd - 20, preferLarge = false)`,
  agregar `TEXT ... 10 $y $it` y sumar `font.lineHeightDots` a `y`.

Puntos exactos (post-divisor, antes del primer dato):
- Despacho: EscPos tras `ESC_ALIGN_LEFT`; Plain tras el `divider`; CPCL tras `y += 16`.
- Accesorios: ídem, antes de `VALE: ACC…`.
- Entrada: ídem, antes de `PROVEEDOR:`.
- Devolución: ídem, antes de `ENTREGA:`.
- Conteo: EscPos antes de `FECHA`; Plain antes de `FECHA`; CPCL antes de `CAJAS:`.

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run: `gradle :app:testDebugUnitTest --tests "*TicketGeneratorTest*"`
Expected: PASS de los tests nuevos y de los existentes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt
git commit -m "feat(ticket): imprimir terminal de origen en cada vale"
```

---

### Task 2: Resolver el device ID en `ValePrintHelper`

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/printer/ValePrintHelper.kt`

**Interfaces:**
- Consumes: parámetro `terminalId` de Task 1.
- Produces: impresión, previews y dashboard con `TERMINAL: <deviceId>`.

- [ ] **Step 1: Implementar**

Importar `com.ttexplo.mobile.license.LicenseManager`. En cada `print*` y
`preview*`, calcular una vez `val terminal = LicenseManager.getFormattedDeviceId(context)`
y pasar `terminalId = terminal` a cada llamada de `TicketGenerator`
(incluye `generateBoxCount*` y `previewBoxCount`). No tocar
`printTestForProfile`.

- [ ] **Step 2: Compilar**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verificar en dispositivo**

Instalar el APK, abrir un vale (despacho) y confirmar en la vista previa la
línea `TERMINAL: <id>`. Confirmar que un vale sin device ID no muestra la línea.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ttexplo/mobile/printer/ValePrintHelper.kt
git commit -m "feat(ticket): pasar device ID a los generadores de vale"
```

---

### Task 3: Versión 2.8.1 (build 39), verificación y sincronización

**Files:**
- Modify: `app/build.gradle.kts` (versionName/versionCode), `app/src/test/java/com/ttexplo/mobile/VersionMetadataTest.kt`, `license_generator/index.html`, `CHANGELOG.md`, `docs/MANUAL_USUARIO.md`, `apk/team/MANUAL_USUARIO.md`, `apk/team/MANUAL_USUARIO.html`, `docs/UI_INVENTORY.md`.

- [ ] **Step 1: Versionar** a `2.8.1` / `39` en los archivos listados y agregar la entrada del ticket al CHANGELOG y manuales.
- [ ] **Step 2: Build + tests**

Run: `gradle :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL y tests en verde (salvo el test preexistente
`InventoryMultiScannerSyncTest...v241_highCapacity...`, ya conocido).

- [ ] **Step 3: Instalar y verificar en `b7411c57`**

`adb install -r`, abrir la app y confirmar la línea en la vista previa.

- [ ] **Step 4: Sincronizar** commit + push a `main` + `git pull` en el NAS; regenerar PDF y actualizar `entregables para Arturo` en local y NAS; comparar hashes.
