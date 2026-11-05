package com.example.alarmevaso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Depois de reiniciar o celular, religa o alarme se estava ativo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.schedule(context)
        }
    }
}
