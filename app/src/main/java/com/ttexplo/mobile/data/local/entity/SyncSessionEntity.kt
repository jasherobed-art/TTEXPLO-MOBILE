package com.ttexplo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object SyncSessionDirection {
    const val EXPORT_QR = "EXPORT_QR"
    const val EXPORT_JSON = "EXPORT_JSON"
    const val IMPORT_QR = "IMPORT_QR"
    const val IMPORT_JSON = "IMPORT_JSON"
}

/**
 * Bitácora persistente de sesiones de sincronización multi-escáner (100% offline vía QR Zebra o JSON).
 * Permite auditar cada cruce entre terminales, eventos nuevos vs duplicados omitidos y conflictos detectados.
 */
@Entity(
    tableName = "sync_sessions",
    indices = [
        Index(value = ["timestamp_epoch_ms"])
    ]
)
data class SyncSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_uuid")
    val sessionUuid: String,

    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "remote_device_id")
    val remoteDeviceId: String,

    @ColumnInfo(name = "polvorin")
    val polvorin: String,

    @ColumnInfo(name = "events_merged")
    val eventsMerged: Int,

    @ColumnInfo(name = "events_skipped_duplicate")
    val eventsSkippedDuplicate: Int,

    @ColumnInfo(name = "code_events_merged")
    val codeEventsMerged: Int,

    @ColumnInfo(name = "code_events_skipped")
    val codeEventsSkipped: Int,

    @ColumnInfo(name = "conflicts_detected")
    val conflictsDetected: Int,

    @ColumnInfo(name = "summary_note")
    val summaryNote: String,

    @ColumnInfo(name = "timestamp_iso")
    val timestampIso: String,

    @ColumnInfo(name = "timestamp_epoch_ms")
    val timestampEpochMs: Long
)
