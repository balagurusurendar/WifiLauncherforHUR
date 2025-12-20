package com.borconi.emil.wifilauncherforhur.receivers

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.borconi.emil.wifilauncherforhur.activities.MainActivity
import com.borconi.emil.wifilauncherforhur.services.WifiService

/**
 * BroadcastReceiver to catch when a Bluetooth Device is connected or disconnected
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Permission checks are now handled in startWifiService
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val selectedBluetoothMacs =
            sharedPreferences.getStringSet("selected_bluetooth_devices", null)

        if (selectedBluetoothMacs.isNullOrEmpty() || intent.action == null) {
            return
        }

        if (device == null) {
            return
        }

        if (selectedBluetoothMacs.contains(device.address)) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(
                        "BluetoothReceiver",
                        "Device ${device.address} connected, starting service if permissions are granted."
                    )
                    startWifiService(context)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (sharedPreferences.getBoolean("ignore_bt_disconnect", false)) return

                    if (WifiService.isRunning) {
                        Log.d("BluetoothReceiver", "Device disconnected, stopping service.")
                        val stopWifiServiceIntent = Intent(context, WifiService::class.java)
                        context.stopService(stopWifiServiceIntent)
                    }
                }
            }
        }
    }

    private fun permissionMissing(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    private fun startWifiService(context: Context) {
        if (WifiService.isRunning) {
            return
        }

        val permissionsMissing  = permissionMissing(context, Manifest.permission.BLUETOOTH_CONNECT) ||
                permissionMissing(context, Manifest.permission.NEARBY_WIFI_DEVICES) ||
                permissionMissing(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                permissionMissing(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        if (permissionsMissing) {
            Log.i("BluetoothReceiver", "Permissions are missing, opening MainActivity.")
            val prefIntent = Intent(context, MainActivity::class.java)
            prefIntent.putExtra(MainActivity.FROM_BLUETOOTH_RECEIVER, true)
            prefIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(prefIntent)
            return
        }

        Log.i("BluetoothReceiver", "All necessary permissions granted, starting WifiService.")
        val startWifiServiceIntent = Intent(context, WifiService::class.java)
        context.startForegroundService(startWifiServiceIntent)
    }
}
