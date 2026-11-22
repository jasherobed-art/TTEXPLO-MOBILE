package com.ttexplo.mobile.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

class ZebraScannerReceiver(
    private val onBarcodeScanned: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        // Extract scanned string from various OEM / Zebra SE4710 DataWedge Intent extras
        val barcode = intent.getStringExtra("com.symbol.datawedge.data_string")
            ?: intent.getStringExtra("data")
            ?: intent.getStringExtra("scannerdata")
            ?: intent.getStringExtra("barcode_string")
            ?: intent.getStringExtra("scan_result")

        if (!barcode.isNullOrBlank()) {
            onBarcodeScanned(barcode.trim())
        }
    }

    companion object {
        val INTENT_ACTIONS = listOf(
            "com.ttexplo.mobile.SCAN_EVENT",
            "com.symbol.datawedge.data_string",
            "android.intent.ACTION_DECODE_DATA",
            "com.xcheng.scanner.action.BARCODE_DECODING_BROADCAST",
            "com.android.server.scannerservice.broadcast"
        )

        fun register(context: Context, receiver: ZebraScannerReceiver) {
            val filter = IntentFilter()
            for (action in INTENT_ACTIONS) {
                filter.addAction(action)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, filter)
            }
        }

        fun unregister(context: Context, receiver: ZebraScannerReceiver) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Already unregistered or not registered
            }
        }
    }
}
