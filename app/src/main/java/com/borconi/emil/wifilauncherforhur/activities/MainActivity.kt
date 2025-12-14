package com.borconi.emil.wifilauncherforhur.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.borconi.emil.wifilauncherforhur.R
import com.borconi.emil.wifilauncherforhur.WiFiLauncherServiceWidget

class MainActivity : AppCompatActivity() {
    private val mainPreferenceFragment = MainPreferenceFragment()
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_WiFiLauncher)
        super.onCreate(savedInstanceState)
        mainPreferenceFragment.fromBluetoothReceiver = intent.getBooleanExtra(FROM_BLUETOOTH_RECEIVER, mainPreferenceFragment.fromBluetoothReceiver)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, mainPreferenceFragment).commit()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        mainPreferenceFragment.fromBluetoothReceiver = intent?.getBooleanExtra(FROM_BLUETOOTH_RECEIVER, mainPreferenceFragment.fromBluetoothReceiver)?: mainPreferenceFragment.fromBluetoothReceiver
    }

    override fun onResume() {
        super.onResume()
    }

    fun onWidgetClick(view: View?) {
        val intent = Intent(WiFiLauncherServiceWidget.WIDGET_ACTION)
        sendBroadcast(intent)
    }

    companion object{
        val FROM_BLUETOOTH_RECEIVER = "from_bluetooth_receiver"
    }
}
