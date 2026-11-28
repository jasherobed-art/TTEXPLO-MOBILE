package com.ttexplo.mobile.printer

import android.content.Context
import com.ttexplo.mobile.data.repository.SettingsRepository
import com.ttexplo.mobile.license.LicenseManager
import com.ttexplo.mobile.domain.model.AccesoriosRecord
import com.ttexplo.mobile.domain.model.DespachoRecord
import com.ttexplo.mobile.domain.model.DevolucionRecord
import com.ttexplo.mobile.domain.inventory.BoxCountVale
import com.ttexplo.mobile.domain.model.EntradaRecord

/**
 * Ayudante centralizado de impresión térmica Bluetooth para todos los tipos de vale.
 * Usa la impresora del perfil especificado (o la impresora activa en SettingsRepository),
 * aplicando el protocolo y ancho de papel configurados en dicho perfil.
 */
object ValePrintHelper {

    data class PrintOutcome(
        val success: Boolean,
        val message: String,
        val printerLabel: String = ""
    )

    private fun resolveProfileAndTarget(
        context: Context,
        overrideProfile: PrinterProfile?
    ): Triple<String, String, Pair<String, PaperWidth>>? {
        val settingsRepo = SettingsRepository(context)
        val printerManager = BluetoothPrinterManager(context)

        val effectiveProfile = overrideProfile ?: settingsRepo.getActivePrinterProfile()
        val target = printerManager.resolveTargetPrinter(effectiveProfile) ?: return null
        val protocol = effectiveProfile?.protocol ?: settingsRepo.getPrinterProtocol()
        val width = effectiveProfile?.paperWidth ?: settingsRepo.getPaperWidth()
        return Triple(target.first, target.second, protocol to width)
    }

    private fun terminal(context: Context): String =
        LicenseManager.getFormattedDeviceId(context).takeUnless { it.isBlank() || it.equals("UNKNOWN", ignoreCase = true) } ?: ""

    suspend fun printDespacho(
        context: Context,
        record: DespachoRecord,
        profile: PrinterProfile? = null
    ): PrintOutcome {
        val settingsRepo = SettingsRepository(context)
        val printerManager = BluetoothPrinterManager(context)
        val resolved = resolveProfileAndTarget(context, profile)
            ?: return PrintOutcome(false, "Sin impresora Bluetooth vinculada. Agregue una en Ajustes.")

        val (mac, label, spec) = resolved
        val (protocol, paperWidth) = spec
        val bytes = when (protocol.uppercase()) {
            "CPCL" -> TicketGenerator.generateDespachoCpcl(
                terminalId = terminal(context),
                despacho = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            "PLAIN" -> TicketGenerator.generateDespachoPlain(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            else -> TicketGenerator.generateDespachoEscPos(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
        }
        return transmit(printerManager, mac, label, bytes)
    }

    suspend fun printAccesorios(
        context: Context,
        record: AccesoriosRecord,
        profile: PrinterProfile? = null
    ): PrintOutcome {
        val settingsRepo = SettingsRepository(context)
        val printerManager = BluetoothPrinterManager(context)
        val resolved = resolveProfileAndTarget(context, profile)
            ?: return PrintOutcome(false, "Sin impresora Bluetooth vinculada. Agregue una en Ajustes.")

        val (mac, label, spec) = resolved
        val (protocol, paperWidth) = spec
        val bytes = when (protocol.uppercase()) {
            "CPCL" -> TicketGenerator.generateAccesoriosCpcl(
                terminalId = terminal(context),
                accesorios = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            "PLAIN" -> TicketGenerator.generateAccesoriosPlain(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            else -> TicketGenerator.generateAccesoriosEscPos(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
        }
        return transmit(printerManager, mac, label, bytes)
    }

    suspend fun printEntrada(
        context: Context,
        record: EntradaRecord,
        profile: PrinterProfile? = null
    ): PrintOutcome {
        val settingsRepo = SettingsRepository(context)
        val printerManager = BluetoothPrinterManager(context)
        val resolved = resolveProfileAndTarget(context, profile)
            ?: return PrintOutcome(false, "Sin impresora Bluetooth vinculada. Agregue una en Ajustes.")

        val (mac, label, spec) = resolved
        val (protocol, paperWidth) = spec
        val bytes = when (protocol.uppercase()) {
            "CPCL" -> TicketGenerator.generateEntradaCpcl(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            "PLAIN" -> TicketGenerator.generateEntradaPlain(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            else -> TicketGenerator.generateEntradaEscPos(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
        }
        return transmit(printerManager, mac, label, bytes)
    }

    suspend fun printDevolucion(
        context: Context,
        record: DevolucionRecord,
        profile: PrinterProfile? = null
    ): PrintOutcome {
        val settingsRepo = SettingsRepository(context)
        val printerManager = BluetoothPrinterManager(context)
        val resolved = resolveProfileAndTarget(context, profile)
            ?: return PrintOutcome(false, "Sin impresora Bluetooth vinculada. Agregue una en Ajustes.")

        val (mac, label, spec) = resolved
        val (protocol, paperWidth) = spec
        val bytes = when (protocol.uppercase()) {
            "CPCL" -> TicketGenerator.generateDevolucionCpcl(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            "PLAIN" -> TicketGenerator.generateDevolucionPlain(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
            else -> TicketGenerator.generateDevolucionEscPos(
                terminalId = terminal(context),
                record = record,
                paperWidth = paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber(),
                headerNote = settingsRepo.getTicketHeader(),
                includeSignatures = settingsRepo.getPrintSignatures(),
                includeBreakdown = settingsRepo.getPrintBreakdown(),
                includeCodeDetails = settingsRepo.getPrintCodeDetails()
            )
        }
        return transmit(printerManager, mac, label, bytes)
    }

    suspend fun printBoxCount(
        context: Context,
        vale: BoxCountVale,
        profile: PrinterProfile? = null
    ): PrintOutcome {
        val resolved = resolveProfileAndTarget(context, profile)
            ?: return PrintOutcome(false, "Sin impresora Bluetooth vinculada. Agregue una en Ajustes.")
        val (mac, label, spec) = resolved
        val bytes = when (spec.first.uppercase()) {
            "CPCL" -> TicketGenerator.generateBoxCountCpcl(vale, spec.second, terminalId = terminal(context))
            "PLAIN" -> TicketGenerator.generateBoxCountPlain(vale, spec.second, terminalId = terminal(context))
            else -> TicketGenerator.generateBoxCountEscPos(vale, spec.second, terminalId = terminal(context))
        }
        return transmit(BluetoothPrinterManager(context), mac, label, bytes)
    }

    suspend fun printTestForProfile(
        context: Context,
        profile: PrinterProfile,
        overrideProtocol: String? = null
    ): PrintOutcome {
        val settingsRepo = SettingsRepository(context)
        val printerManager = BluetoothPrinterManager(context)
        val protocol = (overrideProtocol ?: profile.protocol).uppercase()
        val bytes = when (protocol) {
            "CPCL" -> TicketGenerator.generateCpclTestTicket(
                paperWidth = profile.paperWidth,
                mineName = settingsRepo.getMineName()
            )
            "PLAIN" -> TicketGenerator.generatePlainTestTicket(
                paperWidth = profile.paperWidth,
                mineName = settingsRepo.getMineName()
            )
            else -> TicketGenerator.generateTestTicket(
                paperWidth = profile.paperWidth,
                mineName = settingsRepo.getMineName(),
                permitNumber = settingsRepo.getPermitNumber()
            )
        }
        return transmit(printerManager, profile.macAddress, profile.displayTitle(), bytes)
    }

    // --- Previsualización (texto PLAIN del ticket, sin enviar a la impresora) ---

    private fun previewWidth(context: Context, profile: PrinterProfile?): PaperWidth {
        val s = SettingsRepository(context)
        return (profile ?: s.getActivePrinterProfile())?.paperWidth ?: s.getPaperWidth()
    }

    fun previewDespacho(context: Context, record: DespachoRecord, profile: PrinterProfile? = null): String {
        val s = SettingsRepository(context)
        return TicketGenerator.generateDespachoPlain(
            terminalId = terminal(context),
            record = record,
            paperWidth = previewWidth(context, profile),
            mineName = s.getMineName(),
            permitNumber = s.getPermitNumber(),
            headerNote = s.getTicketHeader(),
            includeSignatures = s.getPrintSignatures(),
            includeBreakdown = s.getPrintBreakdown(),
            includeCodeDetails = false
        ).toString(Charsets.UTF_8)
    }

    fun previewEntrada(context: Context, record: EntradaRecord, profile: PrinterProfile? = null): String {
        val s = SettingsRepository(context)
        return TicketGenerator.generateEntradaPlain(
            terminalId = terminal(context),
            record = record,
            paperWidth = previewWidth(context, profile),
            mineName = s.getMineName(),
            permitNumber = s.getPermitNumber(),
            headerNote = s.getTicketHeader(),
            includeSignatures = s.getPrintSignatures(),
            includeBreakdown = s.getPrintBreakdown(),
            includeCodeDetails = false
        ).toString(Charsets.UTF_8)
    }

    fun previewAccesorios(context: Context, record: AccesoriosRecord, profile: PrinterProfile? = null): String {
        val s = SettingsRepository(context)
        return TicketGenerator.generateAccesoriosPlain(
            terminalId = terminal(context),
            record = record,
            paperWidth = previewWidth(context, profile),
            mineName = s.getMineName(),
            permitNumber = s.getPermitNumber(),
            includeSignatures = s.getPrintSignatures(),
            includeCodeDetails = false
        ).toString(Charsets.UTF_8)
    }

    fun previewDevolucion(context: Context, record: DevolucionRecord, profile: PrinterProfile? = null): String {
        val s = SettingsRepository(context)
        return TicketGenerator.generateDevolucionPlain(
            terminalId = terminal(context),
            record = record,
            paperWidth = previewWidth(context, profile),
            mineName = s.getMineName(),
            permitNumber = s.getPermitNumber(),
            headerNote = s.getTicketHeader(),
            includeSignatures = s.getPrintSignatures(),
            includeBreakdown = s.getPrintBreakdown(),
            includeCodeDetails = false
        ).toString(Charsets.UTF_8)
    }

    fun previewBoxCount(context: Context, vale: BoxCountVale, profile: PrinterProfile? = null): String =
        TicketGenerator.generateBoxCountPlain(vale, previewWidth(context, profile), terminalId = terminal(context)).toString(Charsets.UTF_8)

    private suspend fun transmit(
        printerManager: BluetoothPrinterManager,
        macAddress: String,
        label: String,
        bytes: ByteArray
    ): PrintOutcome {
        val result = printerManager.printBytes(macAddress, bytes)
        return if (result.isSuccess) {
            PrintOutcome(true, "Ticket enviado a $label", label)
        } else {
            val err = result.exceptionOrNull()?.localizedMessage ?: "Error de conexión Bluetooth"
            PrintOutcome(false, "Error al imprimir en $label: $err", label)
        }
    }
}
