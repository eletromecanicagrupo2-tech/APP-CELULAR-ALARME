package com.example.alarmevaso

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.TimeZone

/** Agenda/desagenda o alarme usando SEMPRE o fuso de Brasília (America/Sao_Paulo). */
object AlarmScheduler {

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(AppPrefs.KEY_ENABLED, false)) return

        val hour = prefs.getInt(AppPrefs.KEY_HOUR, AppPrefs.DEFAULT_HOUR)
        val min = prefs.getInt(AppPrefs.KEY_MIN, AppPrefs.DEFAULT_MIN)

        val tz = AppPrefs.brasiliaTimeZone()
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, min)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Se já passou hoje em Brasília, agenda para amanhã
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, AppPrefs.REQUEST_CODE, intent, flags)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } catch (e: SecurityException) {
                am.setWindow(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 60_000, pi)
            }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, AppPrefs.REQUEST_CODE, intent, flags)
        am.cancel(pi)
    }

    /** Próximo disparo em millis (para exibir na tela). */
    fun nextTriggerMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(AppPrefs.KEY_ENABLED, false)) return -1L
        val hour = prefs.getInt(AppPrefs.KEY_HOUR, AppPrefs.DEFAULT_HOUR)
        val min = prefs.getInt(AppPrefs.KEY_MIN, AppPrefs.DEFAULT_MIN)
        val tz = AppPrefs.brasiliaTimeZone()
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, min)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
