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
    fun escposExtraFeedDoesNotDoubleAdvancePaper() {
        val text = String(TicketGenerator.generateTestTicket(), Charsets.US_ASCII)
        val marker = "\u001Bd\u0007"
        val i = text.lastIndexOf(marker)
        assertThat(i).isAtLeast(0)
        assertThat(text.substring(i + marker.length)).isEqualTo("\r\n".repeat(4))
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
        val textY = Regex("TEXT \\d+ \\d+ \\d+ (\\d+)").findAll(text)
            .map { it.groupValues[1].toInt() }
        val lineY = Regex("LINE \\d+ (\\d+)").findAll(text)
            .map { it.groupValues[1].toInt() }
        val maxY = (textY + lineY).maxOrNull()!!
        assertThat(height - maxY).isAtLeast(220)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        for (i in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[i + it] == needle[it] }) return i
        }
        return -1
    }
}
