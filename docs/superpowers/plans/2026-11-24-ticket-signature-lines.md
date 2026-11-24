# Líneas de firma adaptadas al ancho del ticket — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Imprimir solo `ENTREGO:` y `RECIBIO:` con su línea de firma, sin nombres ni leyendas, con la línea adaptada a 58/80/104 mm en los cuatro vales y los tres protocolos.

**Architecture:** Un único helper para la longitud de la línea en ESC/POS y texto plano, y uso directo de `lineEnd` en CPCL. Se eliminan las siete líneas de texto que hoy se imprimen bajo los guiones. La opción existente de listado de códigos no se toca.

**Tech Stack:** Kotlin, JUnit 4 + Truth, `ByteArray` CP437, `PaperWidth.lineLength` (32/42/56) y `PaperWidth.cpclDots` (384/576/832).

**Spec:** `docs/superpowers/specs/2026-11-24-ticket-signature-lines-design.md`

## Global Constraints

- Los cuatro vales son Entradas, Despacho, Accesorios y Devolución.
- Los tres protocolos son CPCL, ESC/POS y texto plano (`PLAIN`).
- `PaperWidth.WIDTH_2_INCH.lineLength = 32`, `WIDTH_3_INCH = 42`, `WIDTH_4_INCH = 56`.
- `PaperWidth.WIDTH_2_INCH.cpclDots = 384`, `WIDTH_3_INCH = 576`, `WIDTH_4_INCH = 832`.
- En CPCL, `lineEnd = pageWidth - 20` → 364 / 556 / 812.
- El juego de caracteres del ticket es CP437; las pruebas leen `String(bytes, Charset.forName("CP437"))`.
- Versión vigente: `2.6.0 / Build 21`. Cada cambio se commitea y se pushea a `main`.

## Review Focus

- **Papel de 58 mm:** la línea no debe pasarse del ancho imprimible; longitud exacta 28 guiones (`32 - 4`). Prueba: `signatureLineEscPosMatches58MmWidth`.
- **Papel de 80 y 104 mm:** la línea debe crecer con el papel (38 y 52 guiones; 556 y 812 dots en CPCL), no quedarse en el tope viejo de 27 / 350. Pruebas: `signatureLineGrowsWithPaperWidth` y `cpclSignatureLineUsesFullPaperWidth`.
- **Sin nombres en el bloque de firmas:** ni transportista, ni proveedor, ni empleados, ni "Firma Responsable Polvorin" bajo los guiones en ningún protocolo. Pruebas: los tres tests nuevos y `TicketGeneratorTest` existente.
- **Encabezado intacto:** `TRANSPORTE:`, `PROVEEDOR:`, `EMPRESA:` y la fecha siguen presentes. Prueba: `headerKeepsDataWhenSignatureNamesRemoved`.
- **`includeSignatures = false`:** no debe imprimir etiquetas ni líneas de firma; el resto del vale sigue igual. Prueba: `signaturesDisabledStillSkipsSignatureBlock`.

---

### Task 1: Líneas de firma en ESC/POS y texto plano

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt` (bloques de firmas en las 8 funciones ESC/POS y PLAIN; líneas 394-402, 478-486, 721-729, 804-812, 1042-1050, 1111-1113, 1308-1316, 1375-1377)
- Test: `app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt`

**Interfaces:**
- Consumes: `PaperWidth.lineLength`, `stripAccents(String)`, las funciones `generate*EscPos` y `generate*Plain` ya existentes.
- Produces: `private fun signatureLine(paperWidth: PaperWidth): String` en `TicketGenerator`, usada por los 8 bloques. Devuelve `"_".repeat((paperWidth.lineLength - 4).coerceAtLeast(1))`.

- [ ] **Step 1: Write the failing tests**

Agregar en `TicketGeneratorTest.kt`, usando los records de ejemplo ya definidos en el archivo:

```kotlin
@Test
fun signatureLineEscPosMatches58MmWidth() {
    val text = String(TicketGenerator.generateDespachoEscPos(
        record = despacho, paperWidth = PaperWidth.WIDTH_2_INCH
    ), charset)
    assertThat(text).contains("ENTREGO:\r\n${"_".repeat(28)}\r\n")
    assertThat(text).contains("RECIBIO:\r\n${"_".repeat(28)}\r\n")
}

@Test
fun signatureLineGrowsWithPaperWidth() {
    val wide = String(TicketGenerator.generateDespachoPlain(
        record = despacho, paperWidth = PaperWidth.WIDTH_4_INCH
    ), charset)
    assertThat(wide).contains("ENTREGO:\r\n${"_".repeat(52)}\r\n")
    assertThat(wide).contains("RECIBIO:\r\n${"_".repeat(52)}\r\n")
}

@Test
fun headerKeepsDataWhenSignatureNamesRemoved() {
    val text = String(TicketGenerator.generateAccesoriosEscPos(
        accesorios, PaperWidth.WIDTH_3_INCH
    ), charset)
    assertThat(text).contains("EMPRESA: MPRG")
    assertThat(text).doesNotContain("Firma Responsable")
    assertThat(text).doesNotContain("GILBERTO MENDOZA AGUIRRE\r\n\r\n")
}

@Test
fun signaturesDisabledStillSkipsSignatureBlock() {
    val text = String(TicketGenerator.generateDevolucionPlain(
        record = devolucion, paperWidth = PaperWidth.WIDTH_3_INCH,
        includeSignatures = false
    ), charset)
    assertThat(text).doesNotContain("ENTREGO:")
    assertThat(text).doesNotContain("RECIBIO:")
}
```

Si `despacho`, `accesorios` y `devolucion` no están como campos de la clase, crearlos como `private val` con los mismos datos de los tests existentes.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot" C:\gradle-dist\gradle-9.3.1\bin\gradle.bat -p D:\aistudio\work\tte-esc-inv testDebugUnitTest --tests com.ttexplo.mobile.printer.TicketGeneratorTest --no-daemon`
Expected: FAIL en `signatureLineEscPosMatches58MmWidth` (27 guiones y texto intermedio).

- [ ] **Step 3: Add `signatureLine` and rewrite the 8 signature blocks**

En `TicketGenerator.kt`, declarar junto a los otros helpers privados:

```kotlin
private fun signatureLine(paperWidth: PaperWidth): String =
    "_".repeat((paperWidth.lineLength - 4).coerceAtLeast(1))
```

En cada bloque `if (includeSignatures)` de ESC/POS y PLAIN, reemplazar la declaración local de `sigLine` por `val sigLine = signatureLine(paperWidth)` y emitir solo:

```
ENTREGO:\r\n
sigLine\r\n
\r\n
RECIBIO:\r\n
sigLine\r\n
```

Borrar las líneas que imprimen "Firma Responsable Polvorin", `record.transportista`, `record.proveedor`, `accesorios.entrego`, `accesorios.recibio`, `record.empleadoEntrega` y `record.empleadoRecibe` dentro de esos bloques. Conservar el separador final y el salto de línea posterior.

- [ ] **Step 4: Run tests to verify they pass**

Run: el mismo comando del Step 2.
Expected: PASS de los 4 tests nuevos y de los tests existentes de `TicketGeneratorTest`.

- [ ] **Step 5: Commit**

```bash
git -C "D:\aistudio\work\tte-esc-inv" add app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt
git -C "D:\aistudio\work\tte-esc-inv" commit -m "feat(ticket): adapt ESC/POS and plain signature lines to paper width"
git -C "D:\aistudio\work\tte-esc-inv" push origin fix/hardening-20261122:main
```

---

### Task 2: Líneas de firma en CPCL

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt` (bloques `if (includeSignatures)` en las 4 funciones CPCL; líneas 608-625, 939-956, 1209-1225, 1471-1487)
- Test: `app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt`

**Interfaces:**
- Consumes: `lineEnd` local de cada generador CPCL (`pageWidth - 20`), `TEXT` y `LINE` ya usados.
- Produces: mismo resultado visual que Task 1, sin comandos `TEXT` bajo la línea.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun cpclSignatureLineUsesFullPaperWidth() {
    val cpcl = String(TicketGenerator.generateDespachoCpcl(
        despacho = despacho, paperWidth = PaperWidth.WIDTH_4_INCH
    ), charset)
    assertThat(cpcl).containsMatch("LINE 10 \\d+ 812 \\d+ 1")
    assertThat(cpcl).contains("TRANSPORTE: GILBERTO MENDOZA")
    assertThat(cpcl.indexOf("GILBERTO MENDOZA")).isEqualTo(cpcl.lastIndexOf("GILBERTO MENDOZA"))
    assertThat(cpcl).doesNotContain("Firma Responsable Polvorin")
}
```

El encabezado CPCL imprime `TRANSPORTE: <nombre>`, así que el nombre debe aparecer exactamente una vez y nunca como texto debajo de la línea de firma. La coordenada Y se acepta con `\d+`; lo que se fija es el extremo X = 812.

- [ ] **Step 2: Run test to verify it fails**

Run: el comando de Task 1 Step 2.
Expected: FAIL porque hoy el extremo es 350 y aparece `Firma Responsable Polvorin`.

- [ ] **Step 3: Rewrite the 4 CPCL signature blocks**

En cada bloque: quitar `val sigEnd = (lineEnd).coerceAtMost(350)` y usar `$lineEnd` en los dos comandos `LINE` de firma. Borrar los comandos `TEXT` que imprimen la leyenda o el nombre bajo la línea. Mantener `TEXT ... ENTREGO:` / `RECIBIO:` y los incrementos de `y`.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando de Task 1 Step 2.
Expected: PASS del test nuevo y de `testGenerateDespachoCpcl_withAutoShrinkAndScannedCodesList`.

- [ ] **Step 5: Commit**

```bash
git -C "D:\aistudio\work\tte-esc-inv" add app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt
git -C "D:\aistudio\work\tte-esc-inv" commit -m "feat(ticket): adapt CPCL signature lines to paper width"
git -C "D:\aistudio\work\tte-esc-inv" push origin fix/hardening-20261122:main
```

---

### Task 3: Manual y entrega

**Files:**
- Modify: `docs/MANUAL_USUARIO.md` (línea 62 y sección de configuración de impresora)
- Modify: `D:\aistudio\apk\team\MANUAL_USUARIO.html` y `.pdf` (regenerados)

**Interfaces:**
- Consumes: script `C:\Users\des92\AppData\Local\Temp\opencode\md2html_manual_interactive.py` y Chrome headless.
- Produces: HTML/PDF con 43 páginas, 0 hojas en blanco y sin las cadenas prohibidas.

- [ ] **Step 1: Update the manual source**

En la línea 62 reemplazar "El ticket trae las dos líneas, quién entrega y quién recibe, y espacio de sobra para la firma." por texto que diga que el encabezado trae quién entrega y quién recibe, y que al final quedan solo `ENTREGO:` y `RECIBIO:` con una línea para firmar a mano, sin nombres impresos. Agregar una línea en la sección de configuración indicando que **Listado de códigos con fecha/hora** decide si el vale incluye el detalle de códigos.

- [ ] **Step 2: Regenerate HTML/PDF**

Run:

```powershell
python "C:\Users\des92\AppData\Local\Temp\opencode\md2html_manual_interactive.py"; & "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --disable-gpu --no-sandbox --no-pdf-header-footer --print-to-pdf="D:\aistudio\apk\team\MANUAL_USUARIO.pdf" "file:///D:/aistudio/apk/team/MANUAL_USUARIO.html" 2>&1 | Select-String "written" | Select-Object -Last 1
```

Expected: `bytes written to file ... MANUAL_USUARIO.pdf`.

- [ ] **Step 3: Verify the PDF**

Run:

```powershell
python -c "from pypdf import PdfReader; r=PdfReader(r'D:\aistudio\apk\team\MANUAL_USUARIO.pdf'); t='\n'.join((p.extract_text() or '') for p in r.pages); print('pages',len(r.pages)); print('blank',sum(1 for p in r.pages if len((p.extract_text() or '').strip())<40 and len(p.images)==0)); print('bad',sum(t.count(s) for s in ['IMPORTANT','Control de Explosivos en Sitio · Polvorín','códigos SEDENA']))"
```

Expected: `pages 43`, `blank 0`, `bad 0`.

- [ ] **Step 4: Commit, rebuild APK and push**

```bash
git -C "D:\aistudio\work\tte-esc-inv" add -A
git -C "D:\aistudio\work\tte-esc-inv" commit -m "docs: describe blank signature lines and code-list setting"
git -C "D:\aistudio\work\tte-esc-inv" push origin fix/hardening-20261122:main
```

Luego ejecutar `powershell -ExecutionPolicy Bypass -File "D:\aistudio\apk\build_versioned.ps1"` y confirmar que `manifest.json` queda con `sha7` igual al commit final.
