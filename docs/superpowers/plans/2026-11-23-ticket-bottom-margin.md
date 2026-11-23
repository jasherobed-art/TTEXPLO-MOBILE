# Ticket Bottom Margin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir ~3 cm de margen inferior en blanco a todos los tickets térmicos, sin cambiar su contenido.

**Architecture:** Un único par de constantes/helpers en `TicketGenerator.kt` y sustituir las 15 colas existentes (5 ESC/POS, 5 texto plano, 5 CPCL). El margen se añade después de la firma y antes del corte/fin de página.

**Tech Stack:** Kotlin 2.4.21, JUnit4, Gradle 9.3.1, JDK 17, ESC/POS + CPCL + texto plano, impresora térmica MUNBYN (80 mm).

**Spec:** `docs/superpowers/specs/2026-11-23-ticket-bottom-margin-design.md`

## Global Constraints

- `PaperWidth.WIDTH_3_INCH` = 80 mm; margen ≈7 líneas ESC/POS, 7 saltos CRLF o 220 dots CPCL.
- Mantener `ESC_FEED_6_LINES` (6 líneas) existente; el nuevo margen se **añade**, no lo sustituye.
- No cambiar textos de firma, comandos de corte, licencias, QR ni sincronización.
- No tocar `license/**`, `LicenseManager.kt`, `license_generator/**` ni `ZebraScannerReceiver.kt`.
- Tests con JUnit4 puro (sin Robolectric).

## Review Focus

- Un ticket ESC/POS debe llevar `ESC d 6` y, después, `ESC d 7` — test `escposTicketHasExtraFeedAfterCurrentOne`.
- Un ticket en texto plano debe terminar con 13 saltos de línea seguidos — test `plainTicketEndsWithExtraBlankLines`.
- Un ticket CPCL debe declarar al menos 220 dots de papel en blanco tras el último elemento — test `cpclTicketReservesBottomMarginDots`.
- Los tickets de prueba (no solo los 4 vales) también deben llevar el margen — los 3 tests usan los generadores de prueba.

---

### Task 1: margen inferior en los 15 tickets

**Files:**
- Modify: `app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt:50,392-394,475-476,612,717-719,799-800,941,1036-1038,1097-1098,1208,1300-1302,1359-1360,1468,1519-1521,1541-1542,1592`
- Test: `app/src/test/java/com/ttexplo/mobile/printer/TicketBottomMarginTest.kt` (nuevo)

**Interfaces:**
- Consumes: `TicketGenerator.generateTestTicket()`, `generatePlainTestTicket()`, `generateCpclTestTicket()`.
- Produces: constantes `BOTTOM_MARGIN_ESC_LINES = 7`, `BOTTOM_MARGIN_CPCL_DOTS = 220` y helpers internos que todos los generadores usan al final.

- [ ] **Step 1: test que falla** — crear `TicketBottomMarginTest.kt`:

```kotlin
package com.ttexplo.mobile.printer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TicketBottomMarginTest {
    @Test
    fun escposTicketHasExtraFeedAfterCurrentOne() {
        val bytes = TicketGenerator.generateTestTicket()
        val feed6 = byteArrayOf(0x1B, 0x64, 0x06)
        val feed7 = byteArrayOf(0x1B, 0x64, 0x07)
        val i6 = indexOf(bytes, feed6)
        val i7 = indexOf(bytes, feed7)
        assertThat(i6).isAtLeast(0)
        assertThat(i7).isGreaterThan(i6)
    }

    @Test
    fun plainTicketEndsWithExtraBlankLines() {
        val text = String(TicketGenerator.generatePlainTestTicket(), Charsets.US_ASCII)
        assertThat(text.endsWith("\r\n".repeat(13))).isTrue()
    }

    @Test
    fun cpclTicketReservesBottomMarginDots() {
        val text = String(TicketGenerator.generateCpclTestTicket(), Charsets.US_ASCII)
        val height = Regex("! 0 200 200 (\\d+) 1").find(text)!!.groupValues[1].toInt()
        val maxY = Regex("(?:TEXT|LINE) \\d+(?: \\d+)* (\\d+)").findAll(text)
            .map { it.groupValues[1].toInt() }.maxOrNull()!!
        assertThat(height - maxY).isAtLeast(220)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        for (i in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[i + it] == needle[it] }) return i
        }
        return -1
    }
}
```

- [ ] **Step 2: verificar que falla**
Run: `C:\gradle-dist\gradle-9.3.1\bin\gradle.bat :app:testDebugUnitTest --tests "*TicketBottomMarginTest*" --no-daemon`
Expected: FAIL (los 3 asertos: falta `ESC d 7`, el texto termina con 6 saltos, y `height - maxY` es 20).

- [ ] **Step 3: constantes y helpers** — en `TicketGenerator.kt`:
  - `const val BOTTOM_MARGIN_ESC_LINES = 7`, `const val BOTTOM_MARGIN_CPCL_DOTS = 220`.
  - `ESC_FEED_EXTRA = byteArrayOf(0x1B, 0x64, BOTTOM_MARGIN_ESC_LINES.toByte())`.
  - `private fun writeEscPosBottomMargin(out: ByteArrayOutputStream)` → escribe `ESC_FEED_EXTRA` y 7 `\r\n`.
  - `private fun appendPlainBottomMargin(sb: StringBuilder)` → añade 7 `\r\n`.
  - CPCL: añadir una línea en blanco y sumar `BOTTOM_MARGIN_CPCL_DOTS` a `totalHeight`.

- [ ] **Step 4: aplicar a las 15 colas** — sustituir cada una de las 5 colas ESC/POS, 5 colas de texto plano y 5 `totalHeight` por los helpers/constantes. No tocar el resto del archivo.

- [ ] **Step 5: verificar que pasa**
Run: el comando del paso 2.
Expected: PASS (3/3).

- [ ] **Step 6: regresión de tickets**
Run: `gradle.bat :app:testDebugUnitTest --tests "*TicketGeneratorTest*" --tests "*PrinterProfilesAndTicketInverseTest*" --tests "*TicketBottomMarginTest*" assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: commit**
`git commit -m "fix(printer): add ~3cm bottom margin to all thermal tickets"`

- [ ] **Step 8: verificación en hardware (humano)** — recompilar `app2.5.0-ttexplo.apk` con `build_versioned.ps1`, reinstalar en el MUNBYN con `adb -s b7411c57 install -r ...` (misma firma, sin borrar datos) y pedir al humano que imprima un ticket de prueba. Si queda corto/largo, ajustar solo `BOTTOM_MARGIN_ESC_LINES` / `BOTTOM_MARGIN_CPCL_DOTS`.

## Self-review

- Cobertura: los 3 formatos y los tickets de prueba están cubiertos por tests; los 4 vales comparten los mismos helpers.
- Tipos: los helpers reciben los tipos ya usados (`ByteArrayOutputStream`, `StringBuilder`) y las constantes son `Int`/`ByteArray`.
- Proporción: el plan decide valores y puntos exactos; el cuerpo de los helpers es trivial y queda al implementador.
