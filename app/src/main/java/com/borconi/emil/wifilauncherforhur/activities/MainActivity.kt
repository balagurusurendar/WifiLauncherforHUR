package com.borconi.emil.wifilauncherforhur.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.borconi.emil.wifilauncherforhur.R
import com.borconi.emil.wifilauncherforhur.WiFiLauncherServiceWidget

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_WiFiLauncher)
        super.onCreate(savedInstanceState)


        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, MainPreferenceFragment()).commit()
    }

    override fun onResume() {
        super.onResume()
    }

    fun onWidgetClick(view: View?) {
        val intent = Intent(WiFiLauncherServiceWidget.WIDGET_ACTION)
        sendBroadcast(intent)
    }
}
