package com.example.alarmevaso

import android.app.Application

class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Garante que a pasta de músicas exista já na primeira execução
        SpotifyMusic.buildList(this)
    }
}
