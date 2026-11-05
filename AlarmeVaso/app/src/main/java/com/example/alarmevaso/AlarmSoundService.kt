package com.example.alarmevaso

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Toca a música do alarme em primeiro plano. A música NÃO para até que o
 * usuário cumpra a missão (foto do vaso sanitário) ou desative no app do Spotify
 * (botão "Tocar no Spotify" + parar no próprio Spotify).
 */
class AlarmSoundService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var looping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val musicId = intent.getIntExtra(EXTRA_MUSIC_ID, SpotifyMusic.STANDARD_BELL_ID)
                startForegroundNotif(getString(R.string.notif_playing))
                play(musicId)
                vibrateLoop(true)
            }
            ACTION_STOP -> stopEverything()
            else -> {
                if (!looping) stopEverything()
            }
        }
        return START_STICKY
    }

    private fun play(musicId: Int) {
        releasePlayer()
        player = try {
            if (musicId == SpotifyMusic.STANDARD_BELL_ID) {
                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                MediaPlayer().apply {
                    setDataSource(this@AlarmSoundService, uri)
                    setAudioAttributes(alarmAudioAttributes())
                    isLooping = true
                    prepare()
                }
            } else {
                val f = SpotifyMusic.fileFor(this, musicId)
                if (f != null && f.exists()) {
                    MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setAudioAttributes(alarmAudioAttributes())
                        isLooping = true
                        prepare()
                    }
                } else {
                    // fallback: sininho padrão
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    MediaPlayer().apply {
                        setDataSource(this@AlarmSoundService, uri)
                        setAudioAttributes(alarmAudioAttributes())
                        isLooping = true
                        prepare()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
        player?.start()
        looping = true
    }

    private fun alarmAudioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun vibrateLoop(on: Boolean) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (on) {
            val pattern = longArrayOf(0, 600, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } else {
            vibrator?.cancel()
        }
    }

    private fun startForegroundNotif(text: String) {
        createChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private fun stopEverything() {
        looping = false
        vibrateLoop(false)
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        vibrateLoop(false)
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.alarmevaso.START_SOUND"
        const val ACTION_STOP = "com.example.alarmevaso.STOP_SOUND"
        const val EXTRA_MUSIC_ID = "music_id"
        private const val CHANNEL_ID = "alarme_vaso_canal"
        private const val NOTIF_ID = 42
    }
}
