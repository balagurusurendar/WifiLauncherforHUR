package com.borconi.emil.wifilauncherforhur.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.borconi.emil.wifilauncherforhur.activities.MainActivity
import com.borconi.emil.wifilauncherforhur.connectivity.Connector
import com.borconi.emil.wifilauncherforhur.connectivity.NDSConnector
import com.borconi.emil.wifilauncherforhur.connectivity.WiFiP2PConnector
import com.borconi.emil.wifilauncherforhur.receivers.WifiReceiver
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

class WifiService : Service() {
    private var connector : Connector? = null

    private lateinit var notificationManager: NotificationManager;


    private var pendingIntent: PendingIntent? = null
    private lateinit var notification: NotificationCompat.Builder;

    private var typeLiveData: LiveData<Int?>? = null

    private val serviceJob = SupervisorJob()
    val serviceScope = CoroutineScope(Dispatchers.Default+serviceJob+ CoroutineName("WifiService"))

    private var lastStartId = -1

    override fun onCreate() {
        super.onCreate()
        mustexit = false
        System.setProperty(
            "dexmaker.dexcache",
            cacheDir.path
        )


        val r = Random()


        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        verifyOrCreateNotificationChannels()
        notification = getNotification(this, getString(R.string.service_wifi_looking_text))
        startForeground(NOTIFICATION_ID, notification.build())

        if (!hasPermissions()) {
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )


            val notification =
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID)
                    .setContentTitle(getString(R.string.service_wifi_title))
                    .setContentText(getString(R.string.permission_missing))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(R.drawable.ic_aa_wifi_notification)
                    .setTicker(getString(R.string.service_wifi_ticker))
            notification.setContentIntent(pendingIntent)
            notificationManager.notify(PERMISSION_MISS_NOTIFY_ID, notification.build())
            stopSelf(lastStartId)
            return
        }


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val connectionmode = sharedPreferences.getString("connection_mode", "1")!!.toInt()
        connector = if (connectionmode == 2) {
            WiFiP2PConnector(notificationManager, notification, this@WifiService)
        } else {
            NDSConnector(notificationManager, notification, this@WifiService)
        }
        serviceScope.launch {
            if (!connected.get()){
                connector?.start()
            }
        }

        isRunning = true
        val carConnection = CarConnection(this)
        typeLiveData = carConnection.type
        typeLiveData!!.observeForever(AAObserver)
    }

    private fun hasPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    private val AAObserver: Observer<Int?> = Observer { newValue -> // newValue is the updated value of myLiveData
        // Do something with the new value
        val connectionState: Int = newValue!!
        Log.d("WiFiService", "Connection state: $connectionState")
        when (connectionState) {
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> {
                if (connected.get()) stopSelf(lastStartId)
                connected.set(false)
            }

            CarConnection.CONNECTION_TYPE_NATIVE -> {}
            CarConnection.CONNECTION_TYPE_PROJECTION -> {
                connected.set(true)
                notification!!.setContentText(getString(R.string.connectedtocar))
                notificationManager!!.notify(NOTIFICATION_ID, notification!!.build())
                if (connector!=null){
                    connector?.removeIntentReceivers()
                    serviceScope.launch {
                        try {
                            connector?.maintainConnectionAlone()
                        }catch (e : Exception){
                            Log.d("WifiService", "Error while stopping connector", e)
                        }
                    }
                }
            }

            else -> {}
        }
    }


    private fun verifyOrCreateNotificationChannels() {
        val notificationChannelNoVibrationDefault = notificationManager.getNotificationChannel(
            NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID
        )
        if (notificationChannelNoVibrationDefault == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID,
                getString(R.string.notification_channel_default_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = getString(R.string.notification_channel_default_description)
            channel.enableVibration(false)
            channel.setSound(null, null)
            channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }

        val notificationChannelWithVibrationImportant =
            notificationManager.getNotificationChannel(
                NOTIFICATION_CHANNEL_WITH_VIBRATION_IMPORTANT_ID
            )
        if (notificationChannelWithVibrationImportant == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_WITH_VIBRATION_IMPORTANT_ID,
                getString(R.string.notification_channel_high_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = getString(R.string.notification_channel_high_description)
            channel.enableVibration(true)
            channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNotification(
        context: Context,
        contentText: String?
    ): NotificationCompat.Builder {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID)
                .setContentTitle(getString(R.string.service_wifi_title))
                .setContentText(contentText)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_aa_wifi_notification)
                .setTicker(getString(R.string.service_wifi_ticker))

        val turnOffIntent = Intent(context, WifiReceiver::class.java)
        turnOffIntent.action = WifiReceiver.ACTION_WIFI_LAUNCHER_EXIT
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
        intent.action = WiFiLauncherServiceWidget.WIDGET_ACTION
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

        if (isStoppingService){
            Log.d("WifiService", "Stop Service Going On")
            return START_REDELIVER_INTENT
        }
        if (isRunning){
            Log.d("WifiService", "Service already Running")
            lastStartId = startId
            return START_STICKY
        }
        super.onStartCommand(intent, flags, startId)
        lastStartId = startId
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isStoppingService = true
        typeLiveData!!.removeObserver(AAObserver)
        connected.set(false)
        connector?.removeIntentReceivers()
        notification.setContentText(getString(R.string.stopping_service))
        notification.clearActions()
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        stopForeground(STOP_FOREGROUND_DETACH)
        serviceScope.launch {
            serviceJob.cancel()
            try {
                connector?.stop()
            } catch (e: Exception) {
            }

            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val pref = PreferenceManager.getDefaultSharedPreferences(this@WifiService)
            var stillconnected = false
            if (mustexit){
                isStoppingService = false
                notificationManager.cancel(NOTIFICATION_ID)
                return@launch
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                    this@WifiService,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ){
                isStoppingService = false
                notificationManager.cancel(NOTIFICATION_ID)
                return@launch
            }

            bluetoothAdapter
            val pairedDevices = bluetoothAdapter.bondedDevices
            val selectedBluetoothMacs = pref.getStringSet("selected_bluetooth_devices", null)
            if (selectedBluetoothMacs == null){
                isStoppingService = false
                notificationManager.cancel(NOTIFICATION_ID)
                return@launch
            }

            for (device in pairedDevices) {
                Log.d("WiFi Service", "Bonded device: " + device.name)
                if (isConnected(device)) {
                    val deviceAddress = device.address
                    if (selectedBluetoothMacs.contains(deviceAddress)) stillconnected = true
                }
            }
            Log.d("This", "We are still connected to the BT: " + stillconnected)
            if (stillconnected && pref.getBoolean(
                    "keep_running",
                    false
                ) && !pref.getBoolean("ignore_bt_disconnect", false)
            ) {
                startForegroundService(
                    Intent(this@WifiService, WifiService::class.java)
                )
            }
            isStoppingService = false
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        var isStoppingService = false
        const val NOTIFICATION_ID: Int = 1035

        const val PERMISSION_MISS_NOTIFY_ID = 1034
        private const val NOTIFICATION_CHANNEL_NO_VIBRATION_DEFAULT_ID =
            "wifilauncher_notification_channel_no_vibration_default"
        private const val NOTIFICATION_CHANNEL_WITH_VIBRATION_IMPORTANT_ID =
            "wifilauncher_notification_channel_with_vibration_high"

        var isRunning: Boolean = false


        var connected: AtomicBoolean = AtomicBoolean(false)
        var mustexit: Boolean = false


        fun isConnected(device: BluetoothDevice): Boolean {
            try {
                val m = device.javaClass.getMethod("isConnected")
                return m.invoke(device) as Boolean
            } catch (e: Exception) {
                throw IllegalStateException(e)
            }
        }
    }
}
