package com.ttexplo.mobile.domain.inventory

import com.example.BuildConfig
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class SyncExportScope(val label: String, val subtitle: String) {
    PENDING_ONLY("Pendientes nuevos", "Solo eventos locales aún no compartidos"),
    LAST_24_HOURS("Últimas 24h", "Movimientos recientes del turno actual y anterior"),
    FULL_HISTORY("Todo el historial", "Libro completo de cantidades y códigos SEDENA")
}

data class QrSyncChunk(
    val sessionId: String,
    val partIndex: Int, // 1-based
    val totalParts: Int,
    val expectedCrc32Hex: String,
    val payloadChunk: String,
    val rawText: String,
    val protocolVersion: String = QrSyncCodec.PROTOCOL_V3_VERSION
)

data class QrSyncPackage(
    val sessionId: String,
    val crc32Hex: String,
    val chunks: List<QrSyncChunk>,
    val rawJsonBytes: Int,
    val compressedEncodedChars: Int,
    val eventCount: Int,
    val codeEventCount: Int,
    val protocolVersion: String = QrSyncCodec.PROTOCOL_V3_VERSION,
    val useLowErrorCorrection: Boolean = true
) {
    /** Alias retrocompatible para consumidores existentes. */
    val compressedBase64Chars: Int
        get() = compressedEncodedChars
}

data class ParsedSyncBundle(
    val schemaVersion: String,
    val remoteDeviceId: String,
    val polvorin: String,
    val exportedAtIso: String,
    val events: List<InventoryEventEntity>,
    val codeEvents: List<InventoryCodeEventEntity>
)

sealed class QrChunkIngestResult {
    data class Progress(
        val sessionId: String,
        val receivedParts: Set<Int>,
        val totalParts: Int,
        val justAddedPart: Int,
        val wasDuplicatePart: Boolean
    ) : QrChunkIngestResult()

    data class Complete(
        val sessionId: String,
        val totalParts: Int,
        val json: String,
        val protocolVersion: String = QrSyncCodec.PROTOCOL_V3_VERSION
    ) : QrChunkIngestResult()

    data class Error(
        val reason: String,
        val isSessionMismatch: Boolean = false
    ) : QrChunkIngestResult()
}

data class QrMatrix(
    val size: Int,
    val modules: BooleanArray
) {
    fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/**
 * Protocolo de sincronización 100% offline por códigos QR Zebra con soporte dual:
 *
 * - **V3 Alta Capacidad (por defecto desde v2.4.1):**
 *   `TTEX3:<SESSION_ID_6>:<i/N>:<CRC32_HEX8>:<BASE45_GZIP_TABULAR>`
 *   100% caracteres del alfabeto QR Alfanumérico (5.5 bits/carácter) + GZIP nivel 9 + límite ~1,400 chars/QR.
 *
 * - **V2 Retrocompatible (v2.4.0):**
 *   `TTEXSYNC|V2|<SESSION_ID_6>|<i/N>|<CRC32_HEX8>|<BASE64URL_GZIP_JSON>`
 */
object QrSyncCodec {
    const val PROTOCOL_PREFIX = "TTEXSYNC"
    const val PROTOCOL_VERSION = "V2"
    const val PROTOCOL_V3_PREFIX = "TTEX3"
    const val PROTOCOL_V3_VERSION = "V3"
    const val PROTOCOL_V4_PREFIX = "TTEX4"
    const val PROTOCOL_V4_VERSION = "V4"
    const val DEFAULT_MAX_CHUNK_CHARS = 450
    const val DEFAULT_V3_MAX_CHUNK_CHARS = 1400

    /**
     * Codificador V3 de Alta Capacidad:
     * Comprime el payload tabular V3 con GZIP Nivel 9 (`BEST_COMPRESSION`),
     * lo codifica en Base45 (RFC 9285 · alfabeto QR Alfanumérico puro) y lo divide
     * en bloques `TTEX3:<SID>:<i/N>:<CRC32>:<BASE45>` de hasta ~1,400 caracteres.
     */
    fun encodeV3(
        tabularPayload: String,
        rawJsonBytes: Int = tabularPayload.toByteArray(Charsets.UTF_8).size,
        eventCount: Int = 0,
        codeEventCount: Int = 0,
        maxChunkChars: Int = DEFAULT_V3_MAX_CHUNK_CHARS,
        useLowErrorCorrection: Boolean = true,
        sessionIdOverride: String? = null
    ): QrSyncPackage {
        val wireBytes = tabularPayload.toByteArray(Charsets.UTF_8)
        val compressedBytes = gzipCompress(wireBytes, bestCompression = true)
        return encodeV3Binary(
            binaryPayload = compressedBytes,
            rawJsonBytes = rawJsonBytes,
            eventCount = eventCount,
            codeEventCount = codeEventCount,
            maxChunkChars = maxChunkChars,
            useLowErrorCorrection = useLowErrorCorrection,
            sessionIdOverride = sessionIdOverride
        )
    }

    /**
     * Codificador V3 de Máxima Capacidad desde sobre binario (`SyncTabularV3Codec.encodeBinary`):
     * Codifica directamente el flujo binario (UUIDs de 16 bytes + tabla DEFLATE-9) en Base45
     * (alfabeto QR Alfanumérico puro) y divide en bloques `TTEX3:<SID>:<i/N>:<CRC32>:<BASE45>`.
     */
    fun encodeV3Binary(
        binaryPayload: ByteArray,
        rawJsonBytes: Int = binaryPayload.size,
        eventCount: Int = 0,
        codeEventCount: Int = 0,
        maxChunkChars: Int = DEFAULT_V3_MAX_CHUNK_CHARS,
        useLowErrorCorrection: Boolean = true,
        sessionIdOverride: String? = null
    ): QrSyncPackage {
        val base45Payload = Base45Codec.encode(binaryPayload)
        val crcHex = computeCrc32Hex(base45Payload.toByteArray(Charsets.UTF_8))
        val sessionId = (sessionIdOverride?.trim()?.uppercase(Locale.US)?.take(6)?.ifBlank { null })
            ?: UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.US)

        val safeChunkSize = maxChunkChars.coerceAtLeast(120)
        val rawChunks = if (base45Payload.isEmpty()) listOf("") else base45Payload.chunked(safeChunkSize)
        val totalParts = rawChunks.size

        val chunks = rawChunks.mapIndexed { idx, chunkPayload ->
            val partNumber = idx + 1
            val rawText = "$PROTOCOL_V3_PREFIX:$sessionId:$partNumber/$totalParts:$crcHex:$chunkPayload"
            QrSyncChunk(
                sessionId = sessionId,
                partIndex = partNumber,
                totalParts = totalParts,
                expectedCrc32Hex = crcHex,
                payloadChunk = chunkPayload,
                rawText = rawText,
                protocolVersion = PROTOCOL_V3_VERSION
            )
        }

        return QrSyncPackage(
            sessionId = sessionId,
            crc32Hex = crcHex,
            chunks = chunks,
            rawJsonBytes = rawJsonBytes,
            compressedEncodedChars = base45Payload.length,
            eventCount = eventCount,
            codeEventCount = codeEventCount,
            protocolVersion = PROTOCOL_V3_VERSION,
            useLowErrorCorrection = useLowErrorCorrection
        )
    }

    /**
     * Codificador V4 autenticado: igual que V3 pero la firma HMAC-SHA256 sustituye al CRC32.
     * Formato: `TTEX4:<SID>:<i/N>:<HMAC_HEX64>:<BASE45>`.
     */
    fun encodeV4(
        binaryPayload: ByteArray,
        rawJsonBytes: Int = binaryPayload.size,
        eventCount: Int = 0,
        codeEventCount: Int = 0,
        maxChunkChars: Int = DEFAULT_V3_MAX_CHUNK_CHARS,
        useLowErrorCorrection: Boolean = true,
        sessionIdOverride: String? = null
    ): QrSyncPackage {
        val base45Payload = Base45Codec.encode(binaryPayload)
        val hmacHex = computeHmacSha256Hex(base45Payload)
        val sessionId = (sessionIdOverride?.trim()?.uppercase(Locale.US)?.take(6)?.ifBlank { null })
            ?: UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.US)

        val safeChunkSize = maxChunkChars.coerceAtLeast(120)
        val rawChunks = if (base45Payload.isEmpty()) listOf("") else base45Payload.chunked(safeChunkSize)
        val totalParts = rawChunks.size
        val chunks = rawChunks.mapIndexed { idx, chunkPayload ->
            val partNumber = idx + 1
            val rawText = "$PROTOCOL_V4_PREFIX:$sessionId:$partNumber/$totalParts:$hmacHex:$chunkPayload"
            QrSyncChunk(
                sessionId = sessionId,
                partIndex = partNumber,
                totalParts = totalParts,
                // Campo legacy reutilizado: en V4 contiene la firma HMAC, no un CRC32.
                expectedCrc32Hex = hmacHex,
                payloadChunk = chunkPayload,
                rawText = rawText,
                protocolVersion = PROTOCOL_V4_VERSION
            )
        }
        return QrSyncPackage(
            sessionId = sessionId,
            crc32Hex = hmacHex,
            chunks = chunks,
            rawJsonBytes = rawJsonBytes,
            compressedEncodedChars = base45Payload.length,
            eventCount = eventCount,
            codeEventCount = codeEventCount,
            protocolVersion = PROTOCOL_V4_VERSION,
            useLowErrorCorrection = useLowErrorCorrection
        )
    }

    /**
     * Codificador V2 retrocompatible (`TTEXSYNC|V2|...` con GZIP + Base64URL).
     */
    fun encode(
        json: String,
        eventCount: Int = 0,
        codeEventCount: Int = 0,
        maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
        sessionIdOverride: String? = null
    ): QrSyncPackage {
        val rawBytes = json.toByteArray(Charsets.UTF_8)
        val compressedBytes = gzipCompress(rawBytes, bestCompression = false)
        val base64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(compressedBytes)
        val crcHex = computeCrc32Hex(base64Payload.toByteArray(Charsets.UTF_8))
        val sessionId = (sessionIdOverride?.trim()?.uppercase(Locale.US)?.take(6)?.ifBlank { null })
            ?: UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.US)

        val safeChunkSize = maxChunkChars.coerceAtLeast(64)
        val rawChunks = if (base64Payload.isEmpty()) {
            listOf("")
        } else {
            base64Payload.chunked(safeChunkSize)
        }
        val totalParts = rawChunks.size

        val chunks = rawChunks.mapIndexed { idx, chunkPayload ->
            val partNumber = idx + 1
            val rawText = "$PROTOCOL_PREFIX|$PROTOCOL_VERSION|$sessionId|$partNumber/$totalParts|$crcHex|$chunkPayload"
            QrSyncChunk(
                sessionId = sessionId,
                partIndex = partNumber,
                totalParts = totalParts,
                expectedCrc32Hex = crcHex,
                payloadChunk = chunkPayload,
                rawText = rawText,
                protocolVersion = PROTOCOL_VERSION
            )
        }

        return QrSyncPackage(
            sessionId = sessionId,
            crc32Hex = crcHex,
            chunks = chunks,
            rawJsonBytes = rawBytes.size,
            compressedEncodedChars = base64Payload.length,
            eventCount = eventCount,
            codeEventCount = codeEventCount,
            protocolVersion = PROTOCOL_VERSION,
            useLowErrorCorrection = false
        )
    }

    fun isSyncQrPayload(rawScan: String): Boolean {
        val clean = rawScan.trim()
        return clean.startsWith("$PROTOCOL_V4_PREFIX:") ||
            clean.startsWith("$PROTOCOL_V3_PREFIX:") ||
            clean.startsWith("$PROTOCOL_PREFIX|$PROTOCOL_VERSION|")
    }

    fun parseChunk(rawScan: String): Result<QrSyncChunk> {
        val clean = rawScan.trim()
        return when {
            clean.startsWith("$PROTOCOL_V4_PREFIX:") -> parseV4Chunk(clean)
            clean.startsWith("$PROTOCOL_V3_PREFIX:") -> parseV3Chunk(clean)
            clean.startsWith("$PROTOCOL_PREFIX|") -> parseV2Chunk(clean)
            else -> Result.failure(IllegalArgumentException("El código escaneado no es un QR de sincronización TTExplo."))
        }
    }

    private fun parseV4Chunk(clean: String): Result<QrSyncChunk> {
        val parts = clean.split(":", limit = 5)
        if (parts.size != 5) {
            return Result.failure(IllegalArgumentException("Estructura de paquete QR V4 incompleta (${parts.size}/5 campos)."))
        }
        val sessionId = parts[1].trim().uppercase(Locale.US)
        if (sessionId.isEmpty()) {
            return Result.failure(IllegalArgumentException("ID de sesión QR V4 vacío."))
        }
        val (partIndex, totalParts) = parsePartPair(parts[2])
            ?: return Result.failure(IllegalArgumentException("Rango de parte QR V4 inválido: ${parts[2]}"))
        val hmacHex = parts[3].trim().uppercase(Locale.US)
        if (hmacHex.length != 64 || !hmacHex.all { it in '0'..'9' || it in 'A'..'F' }) {
            return Result.failure(IllegalArgumentException("Firma HMAC-SHA256 inválida: $hmacHex"))
        }
        val payload = parts[4]
        if (payload.isEmpty()) {
            return Result.failure(IllegalArgumentException("Carga útil de bloque QR V4 vacía."))
        }
        return Result.success(
            QrSyncChunk(
                sessionId = sessionId,
                partIndex = partIndex,
                totalParts = totalParts,
                expectedCrc32Hex = hmacHex,
                payloadChunk = payload,
                rawText = clean,
                protocolVersion = PROTOCOL_V4_VERSION
            )
        )
    }

    private fun parseV3Chunk(clean: String): Result<QrSyncChunk> {
        val parts = clean.split(":", limit = 5)
        if (parts.size != 5) {
            return Result.failure(IllegalArgumentException("Estructura de paquete QR V3 incompleta (${parts.size}/5 campos)."))
        }
        val sessionId = parts[1].trim().uppercase(Locale.US)
        if (sessionId.isEmpty()) {
            return Result.failure(IllegalArgumentException("ID de sesión QR V3 vacío."))
        }
        val (partIndex, totalParts) = parsePartPair(parts[2])
            ?: return Result.failure(IllegalArgumentException("Rango de parte QR V3 inválido: ${parts[2]}"))
        val crcHex = parts[3].trim().uppercase(Locale.US)
        if (!isValidCrcHex(crcHex)) {
            return Result.failure(IllegalArgumentException("Checksum CRC32 inválido: $crcHex"))
        }
        val payload = parts[4]
        if (payload.isEmpty()) {
            return Result.failure(IllegalArgumentException("Carga útil de bloque QR V3 vacía."))
        }

        return Result.success(
            QrSyncChunk(
                sessionId = sessionId,
                partIndex = partIndex,
                totalParts = totalParts,
                expectedCrc32Hex = crcHex,
                payloadChunk = payload,
                rawText = clean,
                protocolVersion = PROTOCOL_V3_VERSION
            )
        )
    }

    private fun parseV2Chunk(clean: String): Result<QrSyncChunk> {
        val parts = clean.split("|", limit = 6)
        if (parts.size != 6) {
            return Result.failure(IllegalArgumentException("Estructura de paquete QR incompleta (${parts.size}/6 campos)."))
        }
        val version = parts[1].uppercase(Locale.US)
        if (version != PROTOCOL_VERSION) {
            return Result.failure(IllegalArgumentException("Versión de protocolo QR no soportada: $version"))
        }
        val sessionId = parts[2].trim().uppercase(Locale.US)
        if (sessionId.isEmpty()) {
            return Result.failure(IllegalArgumentException("ID de sesión QR vacío."))
        }
        val (partIndex, totalParts) = parsePartPair(parts[3])
            ?: return Result.failure(IllegalArgumentException("Rango de parte QR fuera de límites: ${parts[3]}"))
        val crcHex = parts[4].trim().uppercase(Locale.US)
        if (!isValidCrcHex(crcHex)) {
            return Result.failure(IllegalArgumentException("Checksum CRC32 inválido: $crcHex"))
        }
        val payload = parts[5]
        if (payload.isEmpty()) {
            return Result.failure(IllegalArgumentException("Carga útil de bloque QR vacía."))
        }

        return Result.success(
            QrSyncChunk(
                sessionId = sessionId,
                partIndex = partIndex,
                totalParts = totalParts,
                expectedCrc32Hex = crcHex,
                payloadChunk = payload,
                rawText = clean,
                protocolVersion = PROTOCOL_VERSION
            )
        )
    }

    private fun parsePartPair(raw: String): Pair<Int, Int>? {
        val pair = raw.split("/")
        if (pair.size != 2) return null
        val partIndex = pair[0].toIntOrNull() ?: return null
        val totalParts = pair[1].toIntOrNull() ?: return null
        if (totalParts <= 0 || partIndex !in 1..totalParts) return null
        return partIndex to totalParts
    }

    private fun isValidCrcHex(crcHex: String): Boolean =
        crcHex.length == 8 && crcHex.all { it in '0'..'9' || it in 'A'..'F' }

    fun computeCrc32Hex(bytes: ByteArray): String {
        val crc = CRC32()
        crc.update(bytes)
        return String.format(Locale.US, "%08X", crc.value)
    }

    /** Firma HMAC-SHA256 sobre el payload canónico (la misma carga codificada que protegía el CRC32). */
    fun computeHmacSha256Hex(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.SYNC_HMAC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
    }

    /** Comparación en tiempo constante para no filtrar la firma esperada. */
    fun verifyHmacSha256(payload: String, expectedHex: String): Boolean {
        val actual = computeHmacSha256Hex(payload)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.US_ASCII),
            expectedHex.toByteArray(Charsets.US_ASCII)
        )
    }

    /**
     * Verifica un paquete QR firmado `TTEX4:<SID>:<i/N>:<HMAC_HEX64>:<BASE45>`.
     * Los QR legacy V2/V3 no tienen firma: se decodifican por separado durante el despliegue mixto.
     */
    fun verifyQrPacket(raw: String): Boolean {
        val clean = raw.trim()
        if (!clean.startsWith("$PROTOCOL_V4_PREFIX:")) return false
        val parts = clean.split(":", limit = 5)
        if (parts.size != 5) return false
        val hmacHex = parts[3].trim().uppercase(Locale.US)
        val payload = parts[4]
        if (payload.isEmpty() || hmacHex.length != 64 ||
            !hmacHex.all { it in '0'..'9' || it in 'A'..'F' }
        ) return false
        return verifyHmacSha256(payload, hmacHex)
    }

    fun gzipCompress(data: ByteArray, bestCompression: Boolean = true): ByteArray {
        val bos = ByteArrayOutputStream()
        val gzip = if (bestCompression) {
            object : GZIPOutputStream(bos) {
                init {
                    def.setLevel(Deflater.BEST_COMPRESSION)
                }
            }
        } else {
            GZIPOutputStream(bos)
        }
        gzip.use { it.write(data) }
        return bos.toByteArray()
    }

    fun gzipDecompress(compressed: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(compressed)
        GZIPInputStream(bis).use { gzip ->
            return gzip.readBytes()
        }
    }
}

/**
 * Reensamblador con estado de paquetes QR (soporta V3 Base45 Alfanumérico y V2 Base64URL).
 * Acepta bloques en cualquier orden (ej. 3/3, 1/3, 2/3 o 1/1), valida coherencia de sesión
 * y verifica el CRC32 antes de descomprimir el payload.
 */
class QrSyncReassembler {
    var activeSessionId: String? = null
        private set
    var activeProtocolVersion: String? = null
        private set
    var totalParts: Int = 0
        private set
    var expectedCrc32Hex: String? = null
        private set

    private val chunksByIndex = mutableMapOf<Int, String>()

    val receivedPartIndices: Set<Int>
        get() = chunksByIndex.keys.toSet()

    fun reset() {
        activeSessionId = null
        activeProtocolVersion = null
        totalParts = 0
        expectedCrc32Hex = null
        chunksByIndex.clear()
    }

    fun acceptScan(rawScan: String): QrChunkIngestResult {
        val parsed = QrSyncCodec.parseChunk(rawScan)
        val chunk = parsed.getOrElse { err ->
            return QrChunkIngestResult.Error(err.message ?: "QR de sincronización inválido.")
        }

        if (activeSessionId == null) {
            activeSessionId = chunk.sessionId
            activeProtocolVersion = chunk.protocolVersion
            totalParts = chunk.totalParts
            expectedCrc32Hex = chunk.expectedCrc32Hex
        } else {
            if (activeSessionId != chunk.sessionId ||
                activeProtocolVersion != chunk.protocolVersion ||
                totalParts != chunk.totalParts ||
                expectedCrc32Hex != chunk.expectedCrc32Hex
            ) {
                return QrChunkIngestResult.Error(
                    reason = "Este QR pertenece a otra sesión (${chunk.sessionId}, ${chunk.partIndex}/${chunk.totalParts}). Sesión activa: $activeSessionId. Toque 'Reiniciar' para cambiar de lote.",
                    isSessionMismatch = true
                )
            }
        }

        val wasDuplicate = chunksByIndex.containsKey(chunk.partIndex)
        chunksByIndex[chunk.partIndex] = chunk.payloadChunk

        if (chunksByIndex.size < totalParts) {
            return QrChunkIngestResult.Progress(
                sessionId = activeSessionId!!,
                receivedParts = chunksByIndex.keys.toSet(),
                totalParts = totalParts,
                justAddedPart = chunk.partIndex,
                wasDuplicatePart = wasDuplicate
            )
        }

        // Todos los bloques recibidos -> reensamblar en orden 1..N y validar integridad
        val combinedPayload = buildString {
            for (i in 1..totalParts) {
                append(chunksByIndex[i].orEmpty())
            }
        }

        val version = activeProtocolVersion ?: QrSyncCodec.PROTOCOL_V3_VERSION
        if (version == QrSyncCodec.PROTOCOL_V4_VERSION) {
            if (!QrSyncCodec.verifyHmacSha256(combinedPayload, expectedCrc32Hex.orEmpty())) {
                return QrChunkIngestResult.Error(
                    reason = "Firma HMAC-SHA256 inválida. Reintente escanear el lote QR."
                )
            }
        } else {
            val actualCrc = QrSyncCodec.computeCrc32Hex(combinedPayload.toByteArray(Charsets.UTF_8))
            if (actualCrc != expectedCrc32Hex) {
                return QrChunkIngestResult.Error(
                    reason = "Error de integridad CRC32 (esperado $expectedCrc32Hex, recibido $actualCrc). Reintente escanear el lote QR."
                )
            }
        }

        return try {
            val isBase45 = version == QrSyncCodec.PROTOCOL_V3_VERSION ||
                version == QrSyncCodec.PROTOCOL_V4_VERSION
            val decodedBytes = if (isBase45) {
                Base45Codec.decode(combinedPayload)
            } else {
                Base64.getUrlDecoder().decode(combinedPayload)
            }
            val json = if (isBase45 && SyncTabularV3Codec.isBinaryEnvelope(decodedBytes)) {
                SyncTabularV3Codec.decodeBinaryToTabularJson(decodedBytes)
            } else {
                String(QrSyncCodec.gzipDecompress(decodedBytes), Charsets.UTF_8)
            }
            val completedSession = activeSessionId!!
            val completedTotal = totalParts
            reset()
            QrChunkIngestResult.Complete(
                sessionId = completedSession,
                totalParts = completedTotal,
                json = json,
                protocolVersion = version
            )
        } catch (e: Exception) {
            QrChunkIngestResult.Error("No se pudo descomprimir el paquete QR ($version): ${e.message}")
        }
    }
}

/**
 * Generador puro JVM de matrices QR ISO/IEC 18004 usando ZXing Core.
 * Soporta nivel de corrección `L` (Alta Capacidad en pantalla) y `M` (Lectura Fácil).
 * Cuando el texto usa el alfabeto Base45 (`TTEX3:...`), ZXing activa automáticamente el modo QR Alfanumérico.
 */
object QrMatrixGenerator {
    fun encode(
        content: String,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M
    ): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to errorCorrection,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val size = maxOf(width, height)
        val modules = BooleanArray(size * size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                modules[y * size + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrix(size = size, modules = modules)
    }
}

/**
 * Analizador unificado de paquetes de sincronización:
 * - Formato tabular ultra-compacto `V3` (`{"v":3,...}`)
 * - Formato JSON estándar `ttexplo-inventory-events-v2` y retrocompatible `v1`
 */
object InventorySyncJsonParser {

    /**
     * Época determinista: se usa el campo expuesto por el paquete; si no está, se deriva del ISO-8601
     * con `Instant.parse` (siempre UTC, independiente de la zona horaria del dispositivo).
     * Sin ninguna de las dos, se lanza y `parse` devuelve failure (fail-closed): nunca se usa el reloj local.
     */
    private fun resolveEpochMs(obj: JSONObject, timestampIso: String): Long {
        if (obj.has("timestamp_epoch_ms") && !obj.isNull("timestamp_epoch_ms")) {
            return obj.getLong("timestamp_epoch_ms")
        }
        if (timestampIso.isNotBlank()) {
            return Instant.parse(timestampIso).toEpochMilli()
        }
        throw IllegalArgumentException("Paquete rechazado: falta timestamp_epoch_ms y timestamp_iso no es ISO-8601 válido.")
    }

    fun parse(jsonText: String): Result<ParsedSyncBundle> {
        val clean = jsonText.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("El archivo o paquete JSON está vacío."))
        }
        return try {
            val root = JSONObject(clean)

            // Auto-detección de formato tabular V3
            if (SyncTabularV3Codec.isTabularV3Json(root)) {
                return SyncTabularV3Codec.decode(root)
            }

            val schemaVersion = root.optString("schema_version", "ttexplo-inventory-events-v1")
            if (!schemaVersion.startsWith("ttexplo-inventory-events", ignoreCase = true) &&
                !root.has("events") && !root.has("code_events")
            ) {
                return Result.failure(IllegalArgumentException("El archivo JSON no corresponde a una bitácora de inventario TTExplo."))
            }

            val remoteDeviceId = root.optString("device_id", "TERMINAL-REMOTA").ifBlank { "TERMINAL-REMOTA" }
            val polvorin = root.optString("polvorin", "POLVORÍN PRINCIPAL").ifBlank { "POLVORÍN PRINCIPAL" }
            val exportedAtIso = root.optString("exported_at", "")

            val events = mutableListOf<InventoryEventEntity>()
            val eventsArr = root.optJSONArray("events")
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    val obj = eventsArr.optJSONObject(i) ?: continue
                    val uuid = obj.optString("event_uuid", "").trim()
                    val productId = obj.optString("product_id", "").trim().uppercase(Locale.US)
                    if (uuid.isEmpty() || productId.isEmpty()) continue

                    val productName = obj.optString("product_name", productId).ifBlank { productId }
                    val rawProductType = obj.optString("product_type", "")
                    val parsedType = try {
                        if (rawProductType.isNotBlank()) ProductType.valueOf(rawProductType)
                        else if (productId.startsWith("X")) ProductType.ACCESORIO else ProductType.ALTO_EXPLOSIVO
                    } catch (_: Exception) {
                        if (productId.startsWith("X")) ProductType.ACCESORIO else ProductType.ALTO_EXPLOSIVO
                    }
                    val unit = obj.optString("unit", "").trim().uppercase(Locale.US)
                    val category = InventoryCategory.fromStringOrNull(obj.optString("inventory_category", ""))
                        ?: InventoryCategory.inferCategory(
                            productType = parsedType,
                            codeOrId = productId,
                            productName = productName,
                            unit = unit
                        )
                    val finalUnit = unit.ifBlank { category.defaultUnit }
                    val timestampIso = obj.optString("timestamp_iso", "")

                    events.add(
                        InventoryEventEntity(
                            eventUuid = uuid,
                            deviceId = obj.optString("device_id", remoteDeviceId).ifBlank { remoteDeviceId },
                            polvorin = obj.optString("polvorin", polvorin).ifBlank { polvorin },
                            eventType = obj.optString("event_type", InventoryEventType.PHYSICAL_ADJUSTMENT.name),
                            productId = productId,
                            productName = productName,
                            presentation = obj.optString("presentation", ""),
                            productType = category.toProductType().name,
                            inventoryCategory = category.name,
                            unit = finalUnit,
                            deltaQuantity = obj.optDouble("delta_quantity", 0.0),
                            cajasDelta = obj.optInt("cajas_delta", 0),
                            sacosDelta = obj.optInt("sacos_delta", 0),
                            cartuchosDelta = obj.optInt("cartuchos_delta", 0),
                            unitWeightKgUsed = obj.optDouble("unit_weight_kg_used", 0.0),
                            boxWeightKgUsed = obj.optDouble("box_weight_kg_used", 0.0),
                            referenceType = obj.optString("reference_type", InventoryReferenceType.MANUAL),
                            referenceId = obj.optLong("reference_id", 0L),
                            referenceFolio = obj.optString("reference_folio", ""),
                            notes = obj.optString("notes", ""),
                            timestampIso = timestampIso,
                            timestampEpochMs = resolveEpochMs(obj, timestampIso),
                            syncStatus = InventorySyncStatus.SYNCED,
                            isVoided = false
                        )
                    )
                }
            }

            val codeEvents = mutableListOf<InventoryCodeEventEntity>()
            val codesArr = root.optJSONArray("code_events")
            if (codesArr != null) {
                for (i in 0 until codesArr.length()) {
                    val obj = codesArr.optJSONObject(i) ?: continue
                    val uuid = obj.optString("event_uuid", "").trim()
                    val barcode = obj.optString("barcode", "").trim()
                    val productId = obj.optString("product_id", "").trim().uppercase(Locale.US)
                    if (uuid.isEmpty() || barcode.isEmpty() || productId.isEmpty()) continue

                    val productName = obj.optString("product_name", productId).ifBlank { productId }
                    val category = InventoryCategory.fromStringOrNull(obj.optString("inventory_category", ""))
                        ?: if (barcode.length == 18) InventoryCategory.AGENTE_EXPLOSIVO else InventoryCategory.ALTO_EXPLOSIVO
                    val timestampIso = obj.optString("timestamp_iso", "")

                    codeEvents.add(
                        InventoryCodeEventEntity(
                            eventUuid = uuid,
                            reversesEventUuid = obj.optString("reverses_event_uuid", ""),
                            deviceId = obj.optString("device_id", remoteDeviceId).ifBlank { remoteDeviceId },
                            polvorin = obj.optString("polvorin", polvorin).ifBlank { polvorin },
                            actionType = obj.optString("action_type", InventoryEventType.ENTRADA.name),
                            status = obj.optString("status", InventoryCodeStatus.EN_POLVORIN.name),
                            barcode = barcode,
                            productId = productId,
                            productName = productName,
                            presentation = obj.optString("presentation", ""),
                            inventoryCategory = category.name,
                            empaque = obj.optString("empaque", "CAJA"),
                            weightKg = obj.optDouble("weight_kg", 0.0),
                            referenceType = obj.optString("reference_type", InventoryReferenceType.ENTRADA),
                            referenceId = obj.optLong("reference_id", 0L),
                            referenceFolio = obj.optString("reference_folio", ""),
                            timestampIso = timestampIso,
                            timestampEpochMs = resolveEpochMs(obj, timestampIso),
                            syncStatus = InventorySyncStatus.SYNCED,
                            isVoided = false
                        )
                    )
                }
            }

            Result.success(
                ParsedSyncBundle(
                    schemaVersion = schemaVersion,
                    remoteDeviceId = remoteDeviceId,
                    polvorin = polvorin,
                    exportedAtIso = exportedAtIso,
                    events = events,
                    codeEvents = codeEvents
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Error al leer paquete JSON de sincronización: ${e.message}"))
        }
    }
}
