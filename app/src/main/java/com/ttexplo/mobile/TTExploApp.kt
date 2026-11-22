package com.ttexplo.mobile

import android.app.Application
import com.ttexplo.mobile.data.local.AppDatabase
import com.ttexplo.mobile.data.repository.InventoryRepository
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.license.LicenseManager

class TTExploApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val inventoryRepository: InventoryRepository by lazy {
        val settings = SettingsRepository(this)
        InventoryRepository(
            eventDao = database.inventoryEventDao(),
            codeEventDao = database.inventoryCodeEventDao(),
            productoDao = database.productoDao(),
            deviceIdProvider = { LicenseManager.getFormattedDeviceId(this) },
            polvorinProvider = { settings.getTicketHeader().ifBlank { "POLVORÍN PRINCIPAL" } },
            syncSessionDao = database.syncSessionDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TTExploApp
            private set
    }
}
