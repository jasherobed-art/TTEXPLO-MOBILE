package com.ttexplo.mobile

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.domain.inventory.InventorySyncJsonParser
import com.ttexplo.mobile.domain.inventory.QrChunkIngestResult
import com.ttexplo.mobile.domain.inventory.QrSyncCodec
import com.ttexplo.mobile.domain.inventory.QrSyncReassembler
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
// SDK 36 exige Java 21; el toolchain del repo es Java 17.
@Config(sdk = [35])
class HardeningSyncTest {

    /** Mutar un carácter del BASE45 debe invalidar la firma HMAC. */
    @Test
    fun mutatedV4PacketFailsSignatureVerification() {
        val pkg = QrSyncCodec.encodeV4(
            binaryPayload = "payload-autentico-QR".toByteArray(Charsets.UTF_8),
            sessionIdOverride = "ABC123"
        )
        val raw = pkg.chunks.single().rawText
        assertThat(raw).startsWith("TTEX4:")
        assertThat(QrSyncCodec.verifyQrPacket(raw)).isTrue()

        val fields = raw.split(":", limit = 5)
        val mutatedPayload = fields[4].dropLast(1) + if (fields[4].last() == 'A') 'B' else 'A'
        val mutated = listOf(fields[0], fields[1], fields[2], fields[3], mutatedPayload)
            .joinToString(":")

        assertThat(QrSyncCodec.verifyQrPacket(mutated)).isFalse()
    }

    /** Durante el despliegue mixto, los QR V2 y V3 legítimos siguen decodificando. */
    @Test
    fun legacyV2AndV3PacketsStillDecode() {
        val json = """{"schema_version":"ttexplo-inventory-events-v2","device_id":"T1","events":[{"event_uuid":"u1","product_id":"315","timestamp_iso":"2026-11-22T10:00:00Z","timestamp_epoch_ms":1000}]}"""

        val v2 = QrSyncCodec.encode(json, maxChunkChars = 120, sessionIdOverride = "V2AAAA")
        val v2Json = reassemble(v2.chunks.map { it.rawText })
        assertThat(v2Json).isEqualTo(json)

        val v3 = QrSyncCodec.encodeV3(json, maxChunkChars = 120, sessionIdOverride = "V3AAAA")
        val v3Json = reassemble(v3.chunks.map { it.rawText })
        assertThat(v3Json).isEqualTo(json)
    }

    /** El mismo payload produce idéntico epoch y orden bajo cualquier zona horaria. */
    @Test
    fun decodingIsDeterministicAcrossTimezones() {
        val previous = TimeZone.getDefault()
        val json = """{"schema_version":"ttexplo-inventory-events-v2","events":[{"event_uuid":"evt-b","product_id":"315","timestamp_iso":"2026-11-22T10:15:30Z","notes":"B"},{"event_uuid":"evt-a","product_id":"315","timestamp_iso":"2026-11-22T08:00:00Z","notes":"A"}]}"""
        val expectedEpochs = listOf(
            Instant.parse("2026-11-22T10:15:30Z").toEpochMilli(),
            Instant.parse("2026-11-22T08:00:00Z").toEpochMilli()
        )
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val utc = InventorySyncJsonParser.parse(json).getOrThrow()

            TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
            val mexico = InventorySyncJsonParser.parse(json).getOrThrow()

            assertThat(utc.events.map { it.timestampEpochMs }).isEqualTo(expectedEpochs)
            assertThat(mexico.events.map { it.timestampEpochMs }).isEqualTo(expectedEpochs)
            assertThat(mexico.events.map { it.timestampEpochMs })
                .isEqualTo(utc.events.map { it.timestampEpochMs })
            assertThat(mexico.events.map { it.eventUuid }).isEqualTo(listOf("evt-b", "evt-a"))

            // fail-closed: paquete sin marca de tiempo se rechaza, no usa reloj local.
            val noTimestamp = """{"schema_version":"ttexplo-inventory-events-v2","events":[{"event_uuid":"u","product_id":"315"}]}"""
            assertThat(InventorySyncJsonParser.parse(noTimestamp).isFailure).isTrue()
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun reassemble(rawChunks: List<String>): String {
        val reassembler = QrSyncReassembler()
        var result = ""
        for (raw in rawChunks) {
            val res = reassembler.acceptScan(raw)
            if (res is QrChunkIngestResult.Complete) result = res.json
        }
        return result
    }
}
