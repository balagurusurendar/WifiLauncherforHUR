package com.borconi.emil.wifilauncherforhur

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.borconi.emil.wifilauncherforhur.services.WifiService
import com.borconi.emil.wifilauncherforhur.services.WifiService.Companion.isRunning

/**
 * Implementation of App Widget functionality.
 */
class WiFiLauncherServiceWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("Widget", "Got action: " + intent.getAction())
        if (intent.action == null) return

        if (!intent.action.equals(WIDGET_ACTION, ignoreCase = true)) return
        try {
            if (isRunning) context.stopService(Intent(context, WifiService::class.java))
            else context.startForegroundService(Intent(context, WifiService::class.java))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.cant_start), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        Log.d("Widget", "Update widget called")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context?) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context?) {
        // Enter relevant functionality for when the last widget is disabled
    }

    companion object {
        const val WIDGET_ACTION: String = "com.borconi.emil.wifilauncherforhur.widget"
        fun updateAppWidget(
            context: Context, appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Construct the RemoteViews object
            Log.d("Widget", "Update widget called")
            val views =
                RemoteViews(context.packageName, R.layout.widget_wi_fi_launcher_service)

            val intent = Intent(context, WiFiLauncherServiceWidget::class.java)
            intent.setAction(WIDGET_ACTION)
            val pd: PendingIntent?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pd =
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            else pd = PendingIntent.getBroadcast(context, 0, intent, 0)

            if (isRunning) {
                views.setImageViewResource(R.id.appwidget_icon, R.mipmap.ic_widget_running)
                views.setTextViewText(
                    R.id.appwidget_text,
                    context.getString(R.string.app_widget_running)
                )
                views.setContentDescription(
                    R.id.appwidget_text,
                    context.getString(R.string.app_widget_running)
                )
            } else {
                views.setImageViewResource(R.id.appwidget_icon, R.mipmap.ic_widget_preview_round)
                views.setTextViewText(
                    R.id.appwidget_text,
                    context.getString(R.string.app_widget_paused)
                )
                views.setContentDescription(
                    R.id.appwidget_text,
                    context.getString(R.string.app_widget_paused)
                )
            }
            views.setOnClickPendingIntent(R.id.appwidget_container, pd)
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

