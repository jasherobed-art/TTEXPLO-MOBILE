package com.ttexplo.mobile.domain.inventory

import com.ttexplo.mobile.data.local.entity.InventoryCodeEventEntity
import com.ttexplo.mobile.data.local.entity.InventoryEventEntity
import com.ttexplo.mobile.domain.model.InventoryCategory
import com.ttexplo.mobile.domain.model.ProductType
import com.ttexplo.mobile.domain.scanner.TipoEmpaque
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater

enum class QrDensityMode(
    val label: String,
    val shortLabel: String,
    val maxChunkChars: Int,
    val useLowErrorCorrection: Boolean
) {
    HIGH_CAPACITY(
        label = "Alta capacidad (1,400)",
        shortLabel = "Alta (1,400)",
        maxChunkChars = 1400,
        useLowErrorCorrection = true
    ),
    EASY_SCAN(
        label = "Lectura fácil (600)",
        shortLabel = "Fácil (600)",
        maxChunkChars = 600,
        useLowErrorCorrection = false
    )
}

/**
 * Codificador/decodificador RFC 9285 Base45 en Kotlin puro.
 * Usa exclusivamente los 45 caracteres del modo QR Alfanumérico (`0-9A-Z $%*+-./:`),
 * permitiendo a ZXing empaquetar 11 bits por cada 2 caracteres (5.5 bits/carácter en vez de 8 bits/carácter).
 */
object Base45Codec {
    const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { idx, ch ->
            this[ch.code] = idx
        }
    }

    fun isQrAlphanumeric(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code >= 128 || DECODE_TABLE[code] == -1) return false
        }
        return true
    }

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size * 3) / 2 + 2)
        var i = 0
        while (i + 1 < bytes.size) {
            val x = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            val c = x % 45
            val rest = x / 45
            val d = rest % 45
            val e = rest / 45
            sb.append(ALPHABET[c])
            sb.append(ALPHABET[d])
            sb.append(ALPHABET[e])
            i += 2
        }
        if (i < bytes.size) {
            val x = bytes[i].toInt() and 0xFF
            val c = x % 45
            val d = x / 45
            sb.append(ALPHABET[c])
            sb.append(ALPHABET[d])
        }
        return sb.toString()
    }

    fun decode(encoded: String): ByteArray {
        if (encoded.isEmpty()) return ByteArray(0)
        require(encoded.length % 3 != 1) { "Longitud Base45 inválida (${encoded.length})." }

        val out = ByteArrayOutputStream((encoded.length * 2) / 3 + 1)
        var i = 0
        while (i < encoded.length) {
            val remaining = encoded.length - i
            if (remaining >= 3) {
                val c = charValue(encoded[i])
                val d = charValue(encoded[i + 1])
                val e = charValue(encoded[i + 2])
                val x = c + d * 45 + e * 45 * 45
                require(x in 0..0xFFFF) { "Desbordamiento de bloque Base45: $x" }
                out.write((x shr 8) and 0xFF)
                out.write(x and 0xFF)
                i += 3
            } else {
                val c = charValue(encoded[i])
                val d = charValue(encoded[i + 1])
                val x = c + d * 45
                require(x in 0..0xFF) { "Desbordamiento de byte final Base45: $x" }
                out.write(x)
                i += 2
            }
        }
        return out.toByteArray()
    }

    private fun charValue(ch: Char): Int {
        val code = ch.code
        val v = if (code in 0..127) DECODE_TABLE[code] else -1
        require(v != -1) { "Carácter Base45 no válido: '$ch'" }
        return v
    }
}

/**
 * Serializador/deserializador tabular ultra-compacto sin pérdida (`V3`) para transporte QR de alta capacidad.
 *
 * Maximiza la densidad en un solo QR (35-45 eventos combinados en < 1,400 chars Base45) mediante:
 * - Empaquetado binario puro de UUID v4 (16 bytes exactos en vez de 36 chars hex, fuera del flujo DEFLATE para
 *   no contaminar el árbol de Huffman con entropía aleatoria).
 * - Diccionario deduplicado de productos `k` y encabezado único de terminal/polvorín/epoch base `b`.
 * - Compresión DEFLATE Nivel 9 sin cabecera GZIP (`nowrap=true`) sobre la tabla posicional compacta.
 */
object SyncTabularV3Codec {
    const val WIRE_VERSION = 3
    private const val BINARY_MAGIC_0: Byte = 0x54 // 'T'
    private const val BINARY_MAGIC_1: Byte = 0x03 // V3 Binary Envelope

    private fun formatEpochIso(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return fmt.format(Date(epochMs))
    }

    private fun parseCanonicalUuidOrNull(raw: String): UUID? {
        if (raw.length != 36) return null
        return try {
            val parsed = UUID.fromString(raw)
            if (parsed.toString() == raw) parsed else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun encodeEventType(raw: String): String = when (raw) {
        InventoryEventType.ENTRADA.name -> "E"
        InventoryEventType.DESPACHO.name -> "D"
        InventoryEventType.DEVOLUCION.name -> "V"
        InventoryEventType.INITIAL_STOCK.name -> "I"
        InventoryEventType.PHYSICAL_ADJUSTMENT.name -> "A"
        InventoryEventType.SALIDA_ACCESORIOS.name -> "S"
        InventoryEventType.DEVOLUCION_ACCESORIOS.name -> "W"
        InventoryEventType.REVERSAL.name -> "R"
        else -> raw
    }

    private fun decodeEventType(code: String): String = when (code) {
        "E" -> InventoryEventType.ENTRADA.name
        "D" -> InventoryEventType.DESPACHO.name
        "V" -> InventoryEventType.DEVOLUCION.name
        "I" -> InventoryEventType.INITIAL_STOCK.name
        "A" -> InventoryEventType.PHYSICAL_ADJUSTMENT.name
        "S" -> InventoryEventType.SALIDA_ACCESORIOS.name
        "W" -> InventoryEventType.DEVOLUCION_ACCESORIOS.name
        "R" -> InventoryEventType.REVERSAL.name
        else -> code.ifBlank { InventoryEventType.PHYSICAL_ADJUSTMENT.name }
    }

    private fun encodeRefType(raw: String): String = when (raw) {
        InventoryReferenceType.MANUAL -> "M"
        InventoryReferenceType.ENTRADA -> "E"
        InventoryReferenceType.DESPACHO -> "D"
        InventoryReferenceType.DEVOLUCION -> "V"
        InventoryReferenceType.ACCESORIO_VALE -> "A"
        else -> raw
    }

    private fun decodeRefType(code: String): String = when (code) {
        "M" -> InventoryReferenceType.MANUAL
        "E" -> InventoryReferenceType.ENTRADA
        "D" -> InventoryReferenceType.DESPACHO
        "V" -> InventoryReferenceType.DEVOLUCION
        "A" -> InventoryReferenceType.ACCESORIO_VALE
        else -> code.ifBlank { InventoryReferenceType.MANUAL }
    }

    private fun encodeUnit(raw: String): String = when (raw.uppercase(Locale.US)) {
        "KG" -> "K"
        "PZA" -> "P"
        "MTS" -> "M"
        else -> raw.uppercase(Locale.US)
    }

    private fun decodeUnit(code: String, fallback: String): String = when (code) {
        "K" -> "KG"
        "P" -> "PZA"
        "M" -> "MTS"
        "" -> fallback
        else -> code.uppercase(Locale.US)
    }

    private fun encodeCodeStatus(raw: String): String = when (raw) {
        InventoryCodeStatus.EN_POLVORIN.name -> "P"
        InventoryCodeStatus.DESPACHADO.name -> "D"
        InventoryEventType.REVERSAL.name -> "R"
        else -> raw
    }

    private fun decodeCodeStatus(code: String): String = when (code) {
        "P" -> InventoryCodeStatus.EN_POLVORIN.name
        "D" -> InventoryCodeStatus.DESPACHADO.name
        "R" -> InventoryEventType.REVERSAL.name
        else -> code.ifBlank { InventoryCodeStatus.EN_POLVORIN.name }
    }

    private fun encodeEmpaque(raw: String): String = when (raw) {
        TipoEmpaque.CAJA.name -> "C"
        TipoEmpaque.SACO.name -> "S"
        TipoEmpaque.PIEZA.name -> "P"
        TipoEmpaque.ACCESORIO.name -> "A"
        else -> "X"
    }

    private fun decodeEmpaque(code: String): String = when (code) {
        "C" -> TipoEmpaque.CAJA.name
        "S" -> TipoEmpaque.SACO.name
        "P" -> TipoEmpaque.PIEZA.name
        "A" -> TipoEmpaque.ACCESORIO.name
        else -> TipoEmpaque.DESCONOCIDO.name
    }

    private fun encodeCategory(raw: String): Int =
        InventoryCategory.fromStringOrNull(raw)?.ordinal ?: InventoryCategory.ALTO_EXPLOSIVO.ordinal

    private fun decodeCategory(idx: Int): InventoryCategory =
        InventoryCategory.entries.getOrElse(idx) { InventoryCategory.ALTO_EXPLOSIVO }

    fun encode(
        deviceId: String,
        polvorin: String,
        exportedAtIso: String,
        events: List<InventoryEventEntity>,
        codeEvents: List<InventoryCodeEventEntity>
    ): String {
        val (root, _) = buildTabularRoot(
            deviceId = deviceId,
            polvorin = polvorin,
            exportedAtIso = exportedAtIso,
            events = events,
            codeEvents = codeEvents,
            extractBinaryUuids = false
        )
        return root.toString()
    }

    /**
     * Genera el sobre binario V3 de máxima compactación para transporte QR Base45:
     * `[0x54, 0x03][uuidCount:uint16][uuidCount * 16B raw UUIDs][DEFLATE-9(nowrap) tabular JSON]`.
     */
    fun encodeBinary(
        deviceId: String,
        polvorin: String,
        exportedAtIso: String,
        events: List<InventoryEventEntity>,
        codeEvents: List<InventoryCodeEventEntity>
    ): ByteArray {
        val (root, binaryUuids) = buildTabularRoot(
            deviceId = deviceId,
            polvorin = polvorin,
            exportedAtIso = exportedAtIso,
            events = events,
            codeEvents = codeEvents,
            extractBinaryUuids = true
        )
        val compressedTable = rawDeflateBest(root.toString().toByteArray(Charsets.UTF_8))

        val totalSize = 4 + binaryUuids.size * 16 + compressedTable.size
        val buf = ByteBuffer.allocate(totalSize)
        buf.put(BINARY_MAGIC_0)
        buf.put(BINARY_MAGIC_1)
        buf.putShort(binaryUuids.size.toShort())
        for (uuid in binaryUuids) {
            buf.putLong(uuid.mostSignificantBits)
            buf.putLong(uuid.leastSignificantBits)
        }
        buf.put(compressedTable)
        return buf.array()
    }

    fun isBinaryEnvelope(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == BINARY_MAGIC_0 && bytes[1] == BINARY_MAGIC_1

    /**
     * Desempaqueta el sobre binario V3 (`0x54 0x03`) reconstruyendo la tabla `"u"` de UUIDs
     * dentro del JSON tabular V3 listo para `InventorySyncJsonParser.parse(...)`.
     */
    fun decodeBinaryToTabularJson(bytes: ByteArray): String {
        require(isBinaryEnvelope(bytes)) { "Cabecera binaria V3 inválida." }
        val buf = ByteBuffer.wrap(bytes)
        buf.position(2)
        val uuidCount = buf.short.toInt() and 0xFFFF
        val uuidsOffsetEnd = 4 + uuidCount * 16
        require(bytes.size >= uuidsOffsetEnd) { "Sobre binario V3 truncado (UUIDs=$uuidCount)." }

        val uuidArr = JSONArray()
        for (i in 0 until uuidCount) {
            val msb = buf.long
            val lsb = buf.long
            uuidArr.put(UUID(msb, lsb).toString())
        }

        val deflateSlice = bytes.copyOfRange(uuidsOffsetEnd, bytes.size)
        val inflatedJson = String(rawInflate(deflateSlice), Charsets.UTF_8)
        val root = JSONObject(inflatedJson)
        if (uuidCount > 0) {
            root.put("u", uuidArr)
        }
        return root.toString()
    }

    private fun commonPrefixOf(strings: List<String>): String {
        if (strings.size < 2) return ""
        var prefix = strings.first()
        for (i in 1 until strings.size) {
            val s = strings[i]
            val maxLen = minOf(prefix.length, s.length)
            var j = 0
            while (j < maxLen && prefix[j] == s[j]) j++
            prefix = prefix.substring(0, j)
            if (prefix.isEmpty()) return ""
        }
        return prefix
    }

    private data class CodeBatchKey(
        val action: String,
        val status: String,
        val pIdx: Int,
        val cat: Int,
        val empaque: String,
        val weightKg: Double,
        val refType: String,
        val refId: Long,
        val refFolio: String,
        val devOverride: String,
        val polvOverride: String,
        val tsIsoOverride: String
    )

    private fun buildTabularRoot(
        deviceId: String,
        polvorin: String,
        exportedAtIso: String,
        events: List<InventoryEventEntity>,
        codeEvents: List<InventoryCodeEventEntity>,
        extractBinaryUuids: Boolean
    ): Pair<JSONObject, List<UUID>> {
        val root = JSONObject()
        root.put("v", WIRE_VERSION)
        root.put("d", deviceId)
        root.put("p", polvorin)
        root.put("t", exportedAtIso)

        val minEpoch = (events.asSequence().map { it.timestampEpochMs } +
            codeEvents.asSequence().map { it.timestampEpochMs }).minOrNull() ?: 0L
        root.put("b", minEpoch)

        val uuidList = mutableListOf<UUID>()
        fun appendSequentialUuid(raw: String): String {
            if (!extractBinaryUuids || raw.isEmpty()) return raw
            val parsed = parseCanonicalUuidOrNull(raw) ?: return raw
            if (uuidList.size >= 65_000) return raw
            uuidList.add(parsed)
            return "" // Vacío indica tomar el siguiente UUID secuencial de la tabla binaria
        }

        // Diccionario compacto de productos para no repetir nombre/presentación en cada movimiento
        val productDict = LinkedHashMap<String, Pair<String, String>>()
        for (e in events) {
            val pid = e.productId.trim().uppercase(Locale.US)
            if (pid.isNotEmpty() && !productDict.containsKey(pid)) {
                productDict[pid] = e.productName to e.presentation
            }
        }
        for (c in codeEvents) {
            val pid = c.productId.trim().uppercase(Locale.US)
            if (pid.isNotEmpty() && !productDict.containsKey(pid)) {
                productDict[pid] = c.productName to c.presentation
            }
        }
        val prodIndexMap = HashMap<String, Int>(productDict.size)
        val dictArr = JSONArray()
        var idxCounter = 0
        for ((pid, meta) in productDict) {
            prodIndexMap[pid] = idxCounter++
            dictArr.put(JSONArray().apply {
                put(pid)
                put(meta.first)
                if (meta.second.isNotEmpty()) put(meta.second)
            })
        }
        root.put("k", dictArr)

        // Arreglo tabular de eventos de inventario
        val evArr = JSONArray()
        for (e in events) {
            val pid = e.productId.trim().uppercase(Locale.US)
            val pIdx = prodIndexMap[pid] ?: -1
            val defaultIso = formatEpochIso(e.timestampEpochMs)
            val tsIsoOverride = if (e.timestampIso == defaultIso) "" else e.timestampIso
            val devOverride = if (e.deviceId == deviceId) "" else e.deviceId
            val polvOverride = if (e.polvorin == polvorin) "" else e.polvorin

            evArr.put(JSONArray().apply {
                put(appendSequentialUuid(e.eventUuid)) // 0
                put(encodeEventType(e.eventType))      // 1
                put(pIdx)                              // 2
                put(encodeCategory(e.inventoryCategory)) // 3
                put(encodeUnit(e.unit))                // 4
                put(e.deltaQuantity)                   // 5
                put(e.cajasDelta)                      // 6
                put(e.sacosDelta)                      // 7
                put(e.cartuchosDelta)                  // 8
                put(e.unitWeightKgUsed)                // 9
                put(e.boxWeightKgUsed)                 // 10
                put(encodeRefType(e.referenceType))    // 11
                put(e.referenceId)                     // 12
                put(e.referenceFolio)                  // 13
                put(e.notes)                           // 14
                put(e.timestampEpochMs - minEpoch)     // 15 (delta respecto a b)
                if (devOverride.isNotEmpty() || polvOverride.isNotEmpty() || tsIsoOverride.isNotEmpty()) {
                    put(devOverride)                   // 16
                    if (polvOverride.isNotEmpty() || tsIsoOverride.isNotEmpty()) {
                        put(polvOverride)              // 17
                        if (tsIsoOverride.isNotEmpty()) {
                            put(tsIsoOverride)         // 18
                        }
                    }
                }
            })
        }
        if (evArr.length() > 0) root.put("e", evArr)

        // Agrupar códigos SEDENA homogéneos (mismo folio/producto/acción) en modo binario para evitar repetir metadatos
        val unbatchedCodes = mutableListOf<InventoryCodeEventEntity>()
        val batchedGroups = mutableListOf<Pair<CodeBatchKey, List<InventoryCodeEventEntity>>>()

        if (extractBinaryUuids && codeEvents.size >= 2) {
            val groupedByKey = LinkedHashMap<CodeBatchKey, MutableList<InventoryCodeEventEntity>>()
            for (c in codeEvents) {
                val pid = c.productId.trim().uppercase(Locale.US)
                val pIdx = prodIndexMap[pid] ?: -1
                val canBatch = c.reversesEventUuid.isEmpty() && parseCanonicalUuidOrNull(c.eventUuid) != null
                if (!canBatch) {
                    unbatchedCodes.add(c)
                    continue
                }
                val defaultIso = formatEpochIso(c.timestampEpochMs)
                val tsIsoOverride = if (c.timestampIso == defaultIso) "" else c.timestampIso
                val devOverride = if (c.deviceId == deviceId) "" else c.deviceId
                val polvOverride = if (c.polvorin == polvorin) "" else c.polvorin
                val key = CodeBatchKey(
                    action = encodeEventType(c.actionType),
                    status = encodeCodeStatus(c.status),
                    pIdx = pIdx,
                    cat = encodeCategory(c.inventoryCategory),
                    empaque = encodeEmpaque(c.empaque),
                    weightKg = c.weightKg,
                    refType = encodeRefType(c.referenceType),
                    refId = c.referenceId,
                    refFolio = c.referenceFolio,
                    devOverride = devOverride,
                    polvOverride = polvOverride,
                    tsIsoOverride = tsIsoOverride
                )
                groupedByKey.getOrPut(key) { mutableListOf() }.add(c)
            }
            for ((key, list) in groupedByKey) {
                if (list.size >= 2) {
                    batchedGroups.add(key to list)
                } else {
                    unbatchedCodes.addAll(list)
                }
            }
        } else {
            unbatchedCodes.addAll(codeEvents)
        }

        // Arreglo tabular de códigos SEDENA individuales (o reversiones)
        val codeArr = JSONArray()
        for (c in unbatchedCodes) {
            val pid = c.productId.trim().uppercase(Locale.US)
            val pIdx = prodIndexMap[pid] ?: -1
            val defaultIso = formatEpochIso(c.timestampEpochMs)
            val tsIsoOverride = if (c.timestampIso == defaultIso) "" else c.timestampIso
            val devOverride = if (c.deviceId == deviceId) "" else c.deviceId
            val polvOverride = if (c.polvorin == polvorin) "" else c.polvorin

            codeArr.put(JSONArray().apply {
                put(appendSequentialUuid(c.eventUuid))    // 0
                put(c.reversesEventUuid)                  // 1
                put(encodeEventType(c.actionType))        // 2
                put(encodeCodeStatus(c.status))           // 3
                put(c.barcode)                            // 4
                put(pIdx)                                 // 5
                put(encodeCategory(c.inventoryCategory))  // 6
                put(encodeEmpaque(c.empaque))             // 7
                put(c.weightKg)                           // 8
                put(encodeRefType(c.referenceType))       // 9
                put(c.referenceId)                        // 10
                put(c.referenceFolio)                     // 11
                put(c.timestampEpochMs - minEpoch)        // 12 (delta respecto a b)
                if (devOverride.isNotEmpty() || polvOverride.isNotEmpty() || tsIsoOverride.isNotEmpty()) {
                    put(devOverride)                      // 13
                    if (polvOverride.isNotEmpty() || tsIsoOverride.isNotEmpty()) {
                        put(polvOverride)                 // 14
                        if (tsIsoOverride.isNotEmpty()) {
                            put(tsIsoOverride)            // 15
                        }
                    }
                }
            })
        }
        if (codeArr.length() > 0) root.put("c", codeArr)

        // Arreglo compacto de lotes homogéneos de códigos SEDENA (`g`) con prefijo común de código de barras
        if (batchedGroups.isNotEmpty()) {
            val groupArr = JSONArray()
            for ((key, list) in batchedGroups) {
                val barcodes = list.map { it.barcode }
                val prefix = commonPrefixOf(barcodes)
                val suffixes = JSONArray()
                for (item in list) {
                    appendSequentialUuid(item.eventUuid)
                    suffixes.put(item.barcode.substring(prefix.length))
                }
                val firstDelta = list.first().timestampEpochMs - minEpoch
                val steps = (1 until list.size).map { i ->
                    list[i].timestampEpochMs - list[i - 1].timestampEpochMs
                }
                val uniformStep = steps.firstOrNull()?.takeIf { s -> steps.all { it == s } }

                groupArr.put(JSONArray().apply {
                    put(key.action)       // 0
                    put(key.status)       // 1
                    put(key.pIdx)         // 2
                    put(key.cat)          // 3
                    put(key.empaque)      // 4
                    put(key.weightKg)     // 5
                    put(key.refType)      // 6
                    put(key.refId)        // 7
                    put(key.refFolio)     // 8
                    put(key.devOverride)  // 9
                    put(key.polvOverride) // 10
                    put(key.tsIsoOverride)// 11
                    put(prefix)           // 12
                    put(suffixes)         // 13
                    put(firstDelta)       // 14
                    if (uniformStep != null) {
                        put(uniformStep)  // 15 (paso uniforme, p. ej. 1 ms)
                    } else {
                        put(JSONArray().apply { steps.forEach { put(it) } }) // 15 (pasos individuales)
                    }
                })
            }
            root.put("g", groupArr)
        }

        return root to uuidList
    }

    private fun rawDeflateBest(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 32)
            val buf = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count > 0) out.write(buf, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun rawInflate(compressed: ByteArray): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream(compressed.size * 3 + 64)
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count > 0) {
                    out.write(buf, 0, count)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    fun isTabularV3Json(root: JSONObject): Boolean {
        return root.optInt("v", 0) == WIRE_VERSION && (root.has("e") || root.has("c") || root.has("g"))
    }

    fun decode(root: JSONObject): Result<ParsedSyncBundle> {
        return try {
            val remoteDeviceId = root.optString("d", "TERMINAL-REMOTA").ifBlank { "TERMINAL-REMOTA" }
            val polvorin = root.optString("p", "POLVORÍN PRINCIPAL").ifBlank { "POLVORÍN PRINCIPAL" }
            val exportedAtIso = root.optString("t", "")
            val hasBaseEpoch = root.has("b")
            val baseEpoch = if (hasBaseEpoch) root.optLong("b", 0L) else 0L

            val uuidTable = mutableListOf<String>()
            val uuidArr = root.optJSONArray("u")
            if (uuidArr != null) {
                for (i in 0 until uuidArr.length()) {
                    uuidTable.add(uuidArr.optString(i, ""))
                }
            }
            var uuidCursor = 0

            fun resolvePrimaryUuid(row: JSONArray, idx: Int): String {
                val raw = row.opt(idx)
                return when (raw) {
                    is Number -> uuidTable.getOrNull(raw.toInt()).orEmpty()
                    is String -> {
                        val trimmed = raw.trim()
                        if (trimmed.isEmpty() && uuidCursor < uuidTable.size) {
                            uuidTable[uuidCursor++]
                        } else {
                            trimmed
                        }
                    }
                    else -> if (uuidCursor < uuidTable.size) uuidTable[uuidCursor++] else ""
                }
            }

            fun resolveOptionalUuid(row: JSONArray, idx: Int): String {
                val raw = row.opt(idx)
                return when (raw) {
                    is Number -> uuidTable.getOrNull(raw.toInt()).orEmpty()
                    is String -> raw.trim()
                    else -> ""
                }
            }

            val dict = mutableListOf<Triple<String, String, String>>()
            val dictArr = root.optJSONArray("k")
            if (dictArr != null) {
                for (i in 0 until dictArr.length()) {
                    val row = dictArr.optJSONArray(i) ?: continue
                    dict.add(
                        Triple(
                            row.optString(0, "").trim().uppercase(Locale.US),
                            row.optString(1, ""),
                            row.optString(2, "")
                        )
                    )
                }
            }

            val events = mutableListOf<InventoryEventEntity>()
            val evArr = root.optJSONArray("e")
            if (evArr != null) {
                for (i in 0 until evArr.length()) {
                    val row = evArr.optJSONArray(i) ?: continue
                    val uuid = resolvePrimaryUuid(row, 0)
                    val pIdx = row.optInt(2, -1)
                    val meta = dict.getOrNull(pIdx)
                    val productId = meta?.first.orEmpty()
                    if (uuid.isEmpty() || productId.isEmpty()) continue

                    val cat = decodeCategory(row.optInt(3, 0))
                    val unit = decodeUnit(row.optString(4, ""), cat.defaultUnit)
                    val rawTs = row.optLong(15, 0L)
                    val tsEpoch = if (hasBaseEpoch) baseEpoch + rawTs else if (rawTs > 0L) rawTs else System.currentTimeMillis()
                    val dev = row.optString(16, "").ifBlank { remoteDeviceId }
                    val polv = row.optString(17, "").ifBlank { polvorin }
                    val tsIso = row.optString(18, "").ifBlank { formatEpochIso(tsEpoch) }

                    events.add(
                        InventoryEventEntity(
                            eventUuid = uuid,
                            deviceId = dev,
                            polvorin = polv,
                            eventType = decodeEventType(row.optString(1, "")),
                            productId = productId,
                            productName = meta?.second?.ifBlank { productId } ?: productId,
                            presentation = meta?.third.orEmpty(),
                            productType = cat.toProductType().name,
                            inventoryCategory = cat.name,
                            unit = unit,
                            deltaQuantity = row.optDouble(5, 0.0),
                            cajasDelta = row.optInt(6, 0),
                            sacosDelta = row.optInt(7, 0),
                            cartuchosDelta = row.optInt(8, 0),
                            unitWeightKgUsed = row.optDouble(9, 0.0),
                            boxWeightKgUsed = row.optDouble(10, 0.0),
                            referenceType = decodeRefType(row.optString(11, "")),
                            referenceId = row.optLong(12, 0L),
                            referenceFolio = row.optString(13, ""),
                            notes = row.optString(14, ""),
                            timestampIso = tsIso,
                            timestampEpochMs = tsEpoch,
                            syncStatus = InventorySyncStatus.SYNCED,
                            isVoided = false
                        )
                    )
                }
            }

            val codeEvents = mutableListOf<InventoryCodeEventEntity>()
            val codeArr = root.optJSONArray("c")
            if (codeArr != null) {
                for (i in 0 until codeArr.length()) {
                    val row = codeArr.optJSONArray(i) ?: continue
                    val uuid = resolvePrimaryUuid(row, 0)
                    val barcode = row.optString(4, "").trim()
                    val pIdx = row.optInt(5, -1)
                    val meta = dict.getOrNull(pIdx)
                    val productId = meta?.first.orEmpty()
                    if (uuid.isEmpty() || barcode.isEmpty() || productId.isEmpty()) continue

                    val cat = decodeCategory(row.optInt(6, 0))
                    val rawTs = row.optLong(12, 0L)
                    val tsEpoch = if (hasBaseEpoch) baseEpoch + rawTs else if (rawTs > 0L) rawTs else System.currentTimeMillis()
                    val dev = row.optString(13, "").ifBlank { remoteDeviceId }
                    val polv = row.optString(14, "").ifBlank { polvorin }
                    val tsIso = row.optString(15, "").ifBlank { formatEpochIso(tsEpoch) }

                    codeEvents.add(
                        InventoryCodeEventEntity(
                            eventUuid = uuid,
                            reversesEventUuid = resolveOptionalUuid(row, 1),
                            deviceId = dev,
                            polvorin = polv,
                            actionType = decodeEventType(row.optString(2, "")),
                            status = decodeCodeStatus(row.optString(3, "")),
                            barcode = barcode,
                            productId = productId,
                            productName = meta?.second?.ifBlank { productId } ?: productId,
                            presentation = meta?.third.orEmpty(),
                            inventoryCategory = cat.name,
                            empaque = decodeEmpaque(row.optString(7, "")),
                            weightKg = row.optDouble(8, 0.0),
                            referenceType = decodeRefType(row.optString(9, "")),
                            referenceId = row.optLong(10, 0L),
                            referenceFolio = row.optString(11, ""),
                            timestampIso = tsIso,
                            timestampEpochMs = tsEpoch,
                            syncStatus = InventorySyncStatus.SYNCED,
                            isVoided = false
                        )
                    )
                }
            }

            // Reconstruir lotes homogéneos `g`
            val groupArr = root.optJSONArray("g")
            if (groupArr != null) {
                for (gIdx in 0 until groupArr.length()) {
                    val grp = groupArr.optJSONArray(gIdx) ?: continue
                    val action = decodeEventType(grp.optString(0, ""))
                    val status = decodeCodeStatus(grp.optString(1, ""))
                    val pIdx = grp.optInt(2, -1)
                    val meta = dict.getOrNull(pIdx)
                    val productId = meta?.first.orEmpty()
                    val cat = decodeCategory(grp.optInt(3, 0))
                    val empaque = decodeEmpaque(grp.optString(4, ""))
                    val weightKg = grp.optDouble(5, 0.0)
                    val refType = decodeRefType(grp.optString(6, ""))
                    val refId = grp.optLong(7, 0L)
                    val refFolio = grp.optString(8, "")
                    val dev = grp.optString(9, "").ifBlank { remoteDeviceId }
                    val polv = grp.optString(10, "").ifBlank { polvorin }
                    val tsIsoOverride = grp.optString(11, "")
                    val prefix = grp.optString(12, "")
                    val suffixes = grp.optJSONArray(13) ?: continue
                    val firstDelta = grp.optLong(14, 0L)
                    val stepSpec = grp.opt(15)

                    var currentTs = baseEpoch + firstDelta
                    for (i in 0 until suffixes.length()) {
                        if (i > 0) {
                            currentTs += when (stepSpec) {
                                is Number -> stepSpec.toLong()
                                is JSONArray -> stepSpec.optLong(i - 1, 0L)
                                else -> 0L
                            }
                        }
                        val uuid = if (uuidCursor < uuidTable.size) uuidTable[uuidCursor++] else ""
                        val barcode = (prefix + suffixes.optString(i, "")).trim()
                        if (uuid.isEmpty() || barcode.isEmpty() || productId.isEmpty()) continue
                        val tsIso = tsIsoOverride.ifBlank { formatEpochIso(currentTs) }

                        codeEvents.add(
                            InventoryCodeEventEntity(
                                eventUuid = uuid,
                                reversesEventUuid = "",
                                deviceId = dev,
                                polvorin = polv,
                                actionType = action,
                                status = status,
                                barcode = barcode,
                                productId = productId,
                                productName = meta?.second?.ifBlank { productId } ?: productId,
                                presentation = meta?.third.orEmpty(),
                                inventoryCategory = cat.name,
                                empaque = empaque,
                                weightKg = weightKg,
                                referenceType = refType,
                                referenceId = refId,
                                referenceFolio = refFolio,
                                timestampIso = tsIso,
                                timestampEpochMs = currentTs,
                                syncStatus = InventorySyncStatus.SYNCED,
                                isVoided = false
                            )
                        )
                    }
                }
            }

            Result.success(
                ParsedSyncBundle(
                    schemaVersion = "ttexplo-inventory-events-v3",
                    remoteDeviceId = remoteDeviceId,
                    polvorin = polvorin,
                    exportedAtIso = exportedAtIso,
                    events = events,
                    codeEvents = codeEvents
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Error al decodificar paquete tabular V3: ${e.message}"))
        }
    }
}
