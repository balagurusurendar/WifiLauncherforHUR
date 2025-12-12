package com.borconi.emil.wifilauncherforhur.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.car.app.connection.CarConnection
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.borconi.emil.wifilauncherforhur.R
import com.borconi.emil.wifilauncherforhur.WiFiLauncherServiceWidget
import com.borconi.emil.wifilauncherforhur.connectivity.Connector
import com.borconi.emil.wifilauncherforhur.connectivity.NDSConnector
import com.borconi.emil.wifilauncherforhur.connectivity.WiFiP2PConnector
import com.borconi.emil.wifilauncherforhur.receivers.WifiReceiver
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

class WifiService : Service() {
    private var connector: Connector? = null

    private var notificationManager: NotificationManager? = null


    private var pendingIntent: PendingIntent? = null
    private var notification: NotificationCompat.Builder? = null

    private var typeLiveData: LiveData<Int?>? = null
    override fun onCreate() {
        super.onCreate()
        mustexit = false
        System.setProperty(
            "dexmaker.dexcache",
            getCacheDir().getPath()
        )


        val r = Random()


        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        verifyOrCreateNotificationChannels()
        notification = getNotification(this, getString(R.string.service_wifi_looking_text))
        startForeground(NOTIFICATION_ID, notification!!.build())

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val connectionmode = sharedPreferences.getString("connection_mode", "1")!!.toInt()
        if (connectionmode == 2) {
            connector = WiFiP2PConnector(notificationManager!!, notification!!, this)
        } else {
            connector = NDSConnector(notificationManager!!, notification!!, this)
        }

        isRunning = true
        val carConnection = CarConnection(this)
        typeLiveData = carConnection.type
        typeLiveData!!.observeForever(AAObserver)
    }

    private val AAObserver: Observer<Int?> = object : Observer<Int?> {
        override fun onChanged(newValue: Int?) {
            // newValue is the updated value of myLiveData
            // Do something with the new value
            val connectionState: Int = newValue!!
            Log.d("WiFiService", "Connection state: $connectionState")
            when (connectionState) {
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> {
                    if (connected.get()) stopSelf()
                    connected.set(false)
                }

                CarConnection.CONNECTION_TYPE_NATIVE -> {}
                CarConnection.CONNECTION_TYPE_PROJECTION -> {
                    connected.set(true)
                    notification!!.setContentText(getString(R.string.connectedtocar))
                    notificationManager!!.notify(NOTIFICATION_ID, notification!!.build())
                }

                else -> {}
            }
        }
    }


    private fun verifyOrCreateNotificationChannels() {
        val notificationChannelNoVibrationDefault = notificationManager!!.getNotificationChannel(
            NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID
        )
        if (notificationChannelNoVibrationDefault == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID,
                getString(R.string.notification_channel_default_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.setDescription(getString(R.string.notification_channel_default_description))
            channel.enableVibration(false)
            channel.setSound(null, null)
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            notificationManager!!.createNotificationChannel(channel)
        }

        val notificationChannelWithVibrationImportant =
            notificationManager!!.getNotificationChannel(
                NOTIFICATION_CHANNEL_WITH_VIBRATION_IMPORTANT_ID
            )
        if (notificationChannelWithVibrationImportant == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_WITH_VIBRATION_IMPORTANT_ID,
                getString(R.string.notification_channel_high_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.setDescription(getString(R.string.notification_channel_high_description))
            channel.enableVibration(true)
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun getNotification(
        context: Context,
        contentText: String?
    ): NotificationCompat.Builder {
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID)
                .setContentTitle(getString(R.string.service_wifi_title))
                .setContentText(contentText)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_aa_wifi_notification)
                .setTicker(getString(R.string.service_wifi_ticker))

        val turnOffIntent = Intent(context, WifiReceiver::class.java)
        turnOffIntent.setAction(WifiReceiver.Companion.ACTION_WIFI_LAUNCHER_EXIT)
        turnOffIntent.putExtra(NotificationCompat.EXTRA_NOTIFICATION_ID, 0)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pendingIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                turnOffIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        else pendingIntent =
            PendingIntent.getBroadcast(this, 0, turnOffIntent, PendingIntent.FLAG_UPDATE_CURRENT)


        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_power_settings_new_24,
                getString(R.string.turn_off),
                pendingIntent
            ).build()
        )


        return builder
    }


    private fun updateWidget(id: Int) {
        val intent = Intent(this, WiFiLauncherServiceWidget::class.java)
        intent.setAction(WiFiLauncherServiceWidget.WIDGET_ACTION)
        val pd = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)


        Log.d("WifiService", "We should update our widget")
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val remoteViews = RemoteViews(packageName, R.layout.widget_wi_fi_launcher_service)
        val thisWidget = ComponentName(this, WiFiLauncherServiceWidget::class.java)
        remoteViews.setTextViewText(R.id.appwidget_text, getString(id))
        remoteViews.setOnClickPendingIntent(R.id.appwidget_container, pd)
        if (id == R.string.app_widget_running) remoteViews.setImageViewResource(
            R.id.appwidget_icon,
            R.mipmap.ic_widget_running
        )
        else remoteViews.setImageViewResource(R.id.appwidget_icon, R.mipmap.ic_widget_preview_round)

        remoteViews.setOnClickPendingIntent(R.id.appwidget_icon, pd)
        appWidgetManager.updateAppWidget(thisWidget, remoteViews)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) if (ACTION_FOREGROUND_STOP == intent.action) {
            Log.d("WifiService", "Stop service")
            mustexit = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            if (isRunning) return START_STICKY
            Log.d("WifiService", "Start service")
            super.onStartCommand(intent, flags, startId)
        }
        else {
            if (isRunning) return START_STICKY
            super.onStartCommand(intent, flags, startId)
        }



        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            connector!!.stop()
        } catch (e: Exception) {
        }

        connected.set(false)
        isRunning = false
        typeLiveData!!.removeObserver(AAObserver)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        var stillconnected = false
        if (mustexit) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        bluetoothAdapter
        val pairedDevices = bluetoothAdapter.getBondedDevices()
        val selectedBluetoothMacs = pref.getStringSet("selected_bluetooth_devices", null)
        if (selectedBluetoothMacs == null) return

        for (device in pairedDevices) {
            Log.d("WiFi Service", "Bonded device: " + device.getName())
            if (isConnected(device)) {
                val deviceAddress = device.getAddress()
                if (selectedBluetoothMacs.contains(deviceAddress)) stillconnected = true
            }
        }
        Log.d("This", "We are still connected to the BT: " + stillconnected)
        if (stillconnected && pref.getBoolean(
                "keep_running",
                false
            ) && !pref.getBoolean("ignore_bt_disconnect", false)
        ) startForegroundService(
            Intent(this, WifiService::class.java)
        )
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val NOTIFICATION_ID: Int = 1035
        private const val NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID =
            "wifilauncher_notification_channel_no_vibration_default"
        private const val NOTIFICATION_CHANNEL_WITH_VIBRATION_IMPORTANT_ID =
            "wifilauncher_notification_channel_with_vibration_high"

        var isRunning: Boolean = false
        const val ACTION_FOREGROUND_STOP: String = "actionWifiServiceForegroundStop"


        var connected: AtomicBoolean = AtomicBoolean(false)
        var mustexit: Boolean = false


        fun isConnected(device: BluetoothDevice): Boolean {
            try {
                val m = device.javaClass.getMethod("isConnected")
                val connected = m.invoke(device) as Boolean
                return connected
            } catch (e: Exception) {
                throw IllegalStateException(e)
            }
        }
    }
}
