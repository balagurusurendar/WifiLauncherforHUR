package com.borconi.emil.wifilauncherforhur.receivers

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import com.borconi.emil.wifilauncherforhur.services.WifiService

/**
 * BroadcastReceiver to catch when a Bluetooth Device is connected or disconnected
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val selectedBluetoothMacs =
            sharedPreferences.getStringSet("selected_bluetooth_devices", null)

        if (selectedBluetoothMacs == null || intent.action == null) {
            return
        }


        if (device == null) {
            return
        }

        if (selectedBluetoothMacs.contains(device.getAddress())) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(
                        "BluetoothReceiver",
                        intent.action + " want: " + selectedBluetoothMacs + ", got: " + device.getAddress()
                    )

                    startWifiService(context)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (sharedPreferences.getBoolean("ignore_bt_disconnect", false)) return

                    if (WifiService.isRunning) {
                        Log.d("BluetoothReceiver", "We should exit wifi service")
                        val stopWifiServiceIntent = Intent(context, WifiService::class.java)
                        context.stopService(stopWifiServiceIntent)
                    }
                }
            }
        }
    }

    protected fun startWifiService(context: Context) {
        if (WifiService.isRunning) return
        val startWifiServiceIntent = Intent(context, WifiService::class.java)
        context.startForegroundService(startWifiServiceIntent)
    }
}
