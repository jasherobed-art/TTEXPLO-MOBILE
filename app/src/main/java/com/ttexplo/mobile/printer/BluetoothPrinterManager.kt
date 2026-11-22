package com.ttexplo.mobile.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ttexplo.mobile.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothPrinterManager(private val context: Context) {

    @Suppress("DEPRECATION")
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        if (!hasBluetoothPermission()) return emptyList()

        return try {
            val bonded = adapter.bondedDevices.toList()
            bonded.sortedByDescending { device ->
                val name = try { device.name?.lowercase() ?: "" } catch (e: Exception) { "" }
                name.contains("zebra") || name.contains("printer") || name.contains("print") ||
                    name.contains("pos") || name.contains("mpt") || name.contains("zq") ||
                    name.contains("thermal") || name.contains("spp") || name.contains("bt")
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun findPreferredPrinter(): BluetoothDevice? {
        val preferredMac = SettingsRepository(context).getPreferredPrinterMac()
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled || !hasBluetoothPermission()) return null

        val paired = getPairedPrinters()
        if (preferredMac.isNotBlank()) {
            val found = paired.find { it.address.equals(preferredMac, ignoreCase = true) }
            if (found != null) return found
            try {
                val direct = adapter.getRemoteDevice(preferredMac)
                if (direct != null) return direct
            } catch (e: Exception) {
                // Fallback
            }
        }
        return paired.firstOrNull()
    }

    suspend fun printRawBytes(device: BluetoothDevice, data: ByteArray): Boolean {
        val result = printBytes(device.address, data)
        return result.isSuccess
    }

    @SuppressLint("MissingPermission")
    suspend fun printBytes(
        deviceAddress: String,
        data: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter ?: return@withContext Result.failure(IllegalStateException("Bluetooth no disponible en este dispositivo"))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth apagado. Por favor enciéndalo."))
        }
        if (!hasBluetoothPermission()) {
            return@withContext Result.failure(SecurityException("Permiso de Bluetooth no concedido en el teléfono."))
        }

        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("Dirección Bluetooth no válida: $deviceAddress"))
        }

        // Cancel discovery immediately before connecting to free up RFCOMM bandwidth
        try {
            adapter.cancelDiscovery()
        } catch (_: Exception) {}

        var socket: BluetoothSocket? = null
        var lastException: Exception? = null

        // Attempt 1: Standard SECURE RFCOMM socket with SPP UUID
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
        } catch (e: Exception) {
            lastException = e
            try { socket?.close() } catch (_: Exception) {}
            socket = null
        }

        // Attempt 2: Insecure RFCOMM socket fallback
        if (socket == null || !socket.isConnected) {
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e: Exception) {
                lastException = e
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }

        // Attempt 3: Reflection fallback on RFCOMM Channel 1
        if (socket == null || !socket.isConnected) {
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                socket = method.invoke(device, 1) as BluetoothSocket
                socket.connect()
            } catch (e: Exception) {
                lastException = e
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }

        if (socket == null || !socket.isConnected) {
            val devName = try { device.name ?: deviceAddress } catch (_: Exception) { deviceAddress }
            val errorMsg = lastException?.localizedMessage ?: "No se pudo establecer conexión"
            return@withContext Result.failure(IOException("No se pudo conectar con $devName: $errorMsg"))
        }

        try {
            val outputStream = socket.outputStream
            outputStream.write(data)
            outputStream.flush()

            Thread.sleep(250)
            Result.success(Unit)
        } catch (e: Exception) {
            val devName = try { device.name ?: deviceAddress } catch (_: Exception) { deviceAddress }
            Result.failure(IOException("Fallo al transmitir datos a $devName: ${e.localizedMessage}", e))
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
