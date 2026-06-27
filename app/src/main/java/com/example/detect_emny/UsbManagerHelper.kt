package com.example.detect_emeny

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "UsbManagerHelper"
private const val ACTION_USB_PERMISSION = "com.example.detect_emeny.USB_PERMISSION"

class UsbManagerHelper(
    private val context: Context,
    private val onDeviceConnected: (UsbDevice, UsbDeviceConnection, UsbEndpoint) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { openDevice(it) }
                    } else {
                        Log.d(TAG, "Permission denied for device $device")
                    }
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbPermissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister() {
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Unregister failed", e)
        }
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            openDevice(device)
            return
        }
        try {
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
            usbManager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting USB permission", e)
        }
    }

    fun openDevice(device: UsbDevice) {
        Thread {
            try {
                Log.d(TAG, "Attempting to open device: ${device.deviceName}")
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    var found = false
                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        if (connection.claimInterface(intf, true)) {
                            var outEp: UsbEndpoint? = null
                            for (j in 0 until intf.endpointCount) {
                                val ep = intf.getEndpoint(j)
                                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                                    outEp = ep
                                    break
                                }
                            }
                            if (outEp != null) {
                                // Set Baud Rate to 115200
                                val baudRate = 115200
                                val lineCoding = byteArrayOf(
                                    (baudRate and 0xFF).toByte(),
                                    (baudRate shr 8 and 0xFF).toByte(),
                                    (baudRate shr 16 and 0xFF).toByte(),
                                    (baudRate shr 24 and 0xFF).toByte(),
                                    0x00.toByte(), 0x00.toByte(), 0x08.toByte()
                                )
                                connection.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 1000)
                                connection.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 1000)
                                
                                onDeviceConnected(device, connection, outEp)
                                found = true
                                Log.d(TAG, "USB Device opened successfully")
                                break
                            } else {
                                connection.releaseInterface(intf)
                            }
                        }
                    }
                    if (!found) {
                        Log.e(TAG, "No suitable OUT endpoint found")
                        connection.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in openDevice", e)
            }
        }.start()
    }

    fun sendMsg(connection: UsbDeviceConnection?, endpoint: UsbEndpoint?, msg: String) {
        if (connection != null && endpoint != null) {
            val bytes = (msg + "\n").toByteArray()
            usbExecutor.execute {
                try {
                    val result = connection.bulkTransfer(endpoint, bytes, bytes.size, 500)
                    if (result < 0) {
                        Log.e(TAG, "USB Send '$msg' failed: $result")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in bulkTransfer", e)
                }
            }
        }
    }

    fun shutdown() {
        usbExecutor.shutdown()
    }
}
