package com.example.detect_emeny.ui.components

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.detect_emeny.config.AppConfig

@Composable
fun UsbControlView(
    usbManager: UsbManager,
    usbDevice: UsbDevice?,
    usbConnection: Any?, // Using Any? to avoid direct UsbDeviceConnection dependency here if not needed, but it's fine
    onConnectRequest: (UsbDevice) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!AppConfig.ENABLE_USB_CONTROL) return

    var showUsbDropdown by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("USB Control (ESP32)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(onClick = { showUsbDropdown = true }) {
                        val name = remember(usbDevice) {
                            try {
                                usbDevice?.productName ?: usbDevice?.deviceName ?: "Select Device"
                            } catch (e: Exception) {
                                "USB Device"
                            }
                        }
                        Text(name)
                    }
                    DropdownMenu(expanded = showUsbDropdown, onDismissRequest = { showUsbDropdown = false }) {
                        val devices = try {
                            usbManager.deviceList.values.toList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        if (devices.isEmpty()) {
                            DropdownMenuItem(text = { Text("No devices found") }, onClick = { showUsbDropdown = false })
                        } else {
                            devices.forEach { device ->
                                val displayName = try {
                                    "${device.productName ?: "Device"} (${device.vendorId}:${device.productId})"
                                } catch (e: Exception) {
                                    "Device (${device.vendorId}:${device.productId})"
                                }
                                DropdownMenuItem(
                                    text = { Text(displayName) },
                                    onClick = {
                                        showUsbDropdown = false
                                        onConnectRequest(device)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (usbConnection == null) {
                    Text("Connect to start", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSendMessage("LED_ON") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("ON")
                        }
                        Button(
                            onClick = { onSendMessage("LED_OFF") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) {
                            Text("OFF")
                        }
                    }
                }
            }
        }
    }
}
