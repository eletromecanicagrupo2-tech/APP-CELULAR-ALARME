package com.example.alarmevaso

import java.util.TimeZone

object AppPrefs {
    const val PREFS = "alarme_vaso_prefs"

    // Chaves
    const val KEY_HOUR = "hour"
    const val KEY_MIN = "minute"
    const val KEY_ENABLED = "enabled"
    const val KEY_REPEAT = "repeat_daily"
    const val KEY_MUSIC_ID = "music_id"          // -1 = sininho padrão
    const val KEY_MISSION_ON = "mission_on"      // exigir foto do vaso
    const val KEY_LAST_TRIGGER = "last_trigger"

    const val DEFAULT_HOUR = 6
    const val DEFAULT_MIN = 30
    const val REQUEST_CODE = 101

    /** Fuso de Brasília: usa o banco de dados tzdata (cobre novo/horário de verão). */
    fun brasiliaTimeZone(): TimeZone =
        try {
            TimeZone.getTimeZone("America/Sao_Paulo")
        } catch (e: Exception) {
            TimeZone.getDefault()
        }
}
