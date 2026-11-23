package com.ttexplo.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ttexplo.mobile.data.local.dao.*
import com.ttexplo.mobile.data.local.entity.*

@Database(
    entities = [
        ProductoEntity::class,
        EntradaEntity::class,
        EntradaItemEntity::class,
        DespachoEntity::class,
        DespachoItemEntity::class,
        DevolucionEntity::class,
        DevolucionItemEntity::class,
        AccesorioValeEntity::class,
        AccesorioItemEntity::class,
        InventoryEventEntity::class,
        InventoryCodeEventEntity::class,
        SyncSessionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productoDao(): ProductoDao
    abstract fun entradaDao(): EntradaDao
    abstract fun despachoDao(): DespachoDao
    abstract fun devolucionDao(): DevolucionDao
    abstract fun accesorioDao(): AccesorioDao
    abstract fun inventoryEventDao(): InventoryEventDao
    abstract fun inventoryCodeEventDao(): InventoryCodeEventDao
    abstract fun syncSessionDao(): SyncSessionDao
    abstract fun valeDeletionDao(): ValeDeletionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE productos ADD COLUMN peso_unitario_kg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE productos ADD COLUMN cartuchos_por_caja INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inventory_events` (
                        `event_uuid` TEXT NOT NULL,
                        `device_id` TEXT NOT NULL,
                        `polvorin` TEXT NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `product_id` TEXT NOT NULL,
                        `product_name` TEXT NOT NULL,
                        `presentation` TEXT NOT NULL,
                        `product_type` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `delta_quantity` REAL NOT NULL,
                        `cajas_delta` INTEGER NOT NULL,
                        `sacos_delta` INTEGER NOT NULL,
                        `cartuchos_delta` INTEGER NOT NULL,
                        `unit_weight_kg_used` REAL NOT NULL,
                        `box_weight_kg_used` REAL NOT NULL,
                        `reference_type` TEXT NOT NULL,
                        `reference_id` INTEGER NOT NULL,
                        `reference_folio` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `timestamp_iso` TEXT NOT NULL,
                        `timestamp_epoch_ms` INTEGER NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `is_voided` INTEGER NOT NULL,
                        PRIMARY KEY(`event_uuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_events_product_id` ON `inventory_events` (`product_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_events_reference_type_reference_id` ON `inventory_events` (`reference_type`, `reference_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_events_timestamp_epoch_ms` ON `inventory_events` (`timestamp_epoch_ms`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE productos ADD COLUMN categoria_inventario TEXT NOT NULL DEFAULT 'ALTO_EXPLOSIVO'"
                )
                db.execSQL(
                    """
                    UPDATE productos SET categoria_inventario = CASE
                        WHEN tipo = 'AGENTE' THEN 'AGENTE_EXPLOSIVO'
                        WHEN tipo = 'ACCESORIO' OR UPPER(id_producto) LIKE 'X%' THEN
                            CASE
                                WHEN UPPER(id_producto) LIKE '%XCOR%' OR UPPER(producto) LIKE '%CORDON%' OR UPPER(producto) LIKE '%CORDÓN%' OR UPPER(producto) LIKE '%PENT%' OR UPPER(unidad) = 'MTS' THEN 'CORDON_DETONANTE'
                                WHEN UPPER(id_producto) LIKE '%XCAN%' OR UPPER(id_producto) LIKE '%XMECH%' OR UPPER(producto) LIKE '%CANUELA%' OR UPPER(producto) LIKE '%CAÑUELA%' OR UPPER(producto) LIKE '%MECHA%' OR UPPER(producto) LIKE '%GUIA%' THEN 'CANUELAS'
                                ELSE 'DETONADORES'
                            END
                        ELSE 'ALTO_EXPLOSIVO'
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    "ALTER TABLE inventory_events ADD COLUMN inventory_category TEXT NOT NULL DEFAULT 'ALTO_EXPLOSIVO'"
                )
                db.execSQL(
                    """
                    UPDATE inventory_events SET inventory_category = CASE
                        WHEN product_type = 'AGENTE' THEN 'AGENTE_EXPLOSIVO'
                        WHEN product_type = 'ACCESORIO' OR UPPER(product_id) LIKE 'X%' THEN
                            CASE
                                WHEN UPPER(product_id) LIKE '%XCOR%' OR UPPER(product_name) LIKE '%CORDON%' OR UPPER(product_name) LIKE '%CORDÓN%' OR UPPER(product_name) LIKE '%PENT%' OR UPPER(unit) = 'MTS' THEN 'CORDON_DETONANTE'
                                WHEN UPPER(product_id) LIKE '%XCAN%' OR UPPER(product_id) LIKE '%XMECH%' OR UPPER(product_name) LIKE '%CANUELA%' OR UPPER(product_name) LIKE '%CAÑUELA%' OR UPPER(product_name) LIKE '%MECHA%' OR UPPER(product_name) LIKE '%GUIA%' THEN 'CANUELAS'
                                ELSE 'DETONADORES'
                            END
                        ELSE 'ALTO_EXPLOSIVO'
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inventory_code_events` (
                        `event_uuid` TEXT NOT NULL,
                        `reverses_event_uuid` TEXT NOT NULL DEFAULT '',
                        `device_id` TEXT NOT NULL,
                        `polvorin` TEXT NOT NULL,
                        `action_type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `product_id` TEXT NOT NULL,
                        `product_name` TEXT NOT NULL,
                        `presentation` TEXT NOT NULL,
                        `inventory_category` TEXT NOT NULL,
                        `empaque` TEXT NOT NULL,
                        `weight_kg` REAL NOT NULL,
                        `reference_type` TEXT NOT NULL,
                        `reference_id` INTEGER NOT NULL,
                        `reference_folio` TEXT NOT NULL,
                        `timestamp_iso` TEXT NOT NULL,
                        `timestamp_epoch_ms` INTEGER NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `is_voided` INTEGER NOT NULL,
                        PRIMARY KEY(`event_uuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_code_events_barcode` ON `inventory_code_events` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_code_events_product_id` ON `inventory_code_events` (`product_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_code_events_reference_type_reference_id` ON `inventory_code_events` (`reference_type`, `reference_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_code_events_timestamp_epoch_ms` ON `inventory_code_events` (`timestamp_epoch_ms`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_sessions` (
                        `session_uuid` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `remote_device_id` TEXT NOT NULL,
                        `polvorin` TEXT NOT NULL,
                        `events_merged` INTEGER NOT NULL,
                        `events_skipped_duplicate` INTEGER NOT NULL,
                        `code_events_merged` INTEGER NOT NULL,
                        `code_events_skipped` INTEGER NOT NULL,
                        `conflicts_detected` INTEGER NOT NULL,
                        `summary_note` TEXT NOT NULL,
                        `timestamp_iso` TEXT NOT NULL,
                        `timestamp_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`session_uuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_sessions_timestamp_epoch_ms` ON `sync_sessions` (`timestamp_epoch_ms`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ttexplo_database.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
