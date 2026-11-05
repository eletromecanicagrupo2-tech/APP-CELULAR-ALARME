package com.example.alarmevaso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER && intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reagenda após reiniciar o celular
            AlarmScheduler.schedule(context)
            return
        }

        val prefs = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)

        // Abre a tela de alarme tocando na tela / mostrando por cima da bloqueio
        val ui = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        try {
            context.startActivity(ui)
        } catch (e: Exception) {
            // Em alguns aparelhos sem permissão de sobreposição, seguimos só com o serviço
        }

        // Serviço em primeiro plano toca a música escolhida + vibração
        val svc = Intent(context, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }

        // Repete todo dia
        if (prefs.getBoolean(AppPrefs.KEY_REPEAT, true)) {
            AlarmScheduler.schedule(context)
        } else {
            prefs.edit().putBoolean(AppPrefs.KEY_ENABLED, false).apply()
        }
    }

    companion object {
        const val ACTION_TRIGGER = "com.example.alarmevaso.TRIGGER"
    }
}
