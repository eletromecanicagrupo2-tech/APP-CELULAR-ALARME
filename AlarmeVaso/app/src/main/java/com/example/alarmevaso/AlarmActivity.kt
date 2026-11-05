package com.example.alarmevaso

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/** Tela que aparece quando o alarme toca. A música só para após a missão. */
class AlarmActivity : AppCompatActivity() {

    private var photoFile: File? = null
    private lateinit var captureLauncher: ActivityResultLauncher<Intent>
    private lateinit var missionState: TextView
    private lateinit var btnStop: Button
    private lateinit var btnPhoto: Button
    private lateinit var btnSpotify: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Liga a tela e mostra por cima da tela de bloqueio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_alarm)

        missionState = findViewById(R.id.txtMissionState)
        btnStop = findViewById(R.id.btnStop)
        btnPhoto = findViewById(R.id.btnTakePhoto)
        btnSpotify = findViewById(R.id.btnSpotify)

        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val musicId = prefs.getInt(AppPrefs.KEY_MUSIC_ID, SpotifyMusic.STANDARD_BELL_ID)
        val missionOn = prefs.getBoolean(AppPrefs.KEY_MISSION_ON, true)

        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) onPhotoTaken()
            else missionState.text = getString(R.string.mission_cancelled)
        }

        if (!missionOn) {
            // Missão desligada nas configurações: botão para direto
            btnPhoto.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
            btnStop.setOnClickListener { finishAlarm() }
        } else {
            btnStop.visibility = View.GONE
            btnPhoto.setOnClickListener { startCameraMission() }
        }

        btnSpotify.setOnClickListener { openSpotifyNow(musicId) }
    }

    /** Botão "PARAR": dispara a missão de levantar e fotografar o vaso sanitário. */
    private fun startCameraMission() {
        // O app NÃO pode abrir a câmera enquanto o alarme toca em fullscreen:
        // primeiro solta a tela, depois abre a câmera da missão.
        missionState.text = getString(R.string.mission_go)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        btnPhoto.isEnabled = false
        btnPhoto.postDelayed({ openCamera() }, 450)
    }

    private fun openCamera() {
        try {
            val f = SpotifyMusic.newPhotoFile(this)
            photoFile = f
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            captureLauncher.launch(intent)
        } catch (e: Exception) {
            missionState.text = getString(R.string.mission_camera_error)
            btnPhoto.isEnabled = true
        }
    }

    private fun onPhotoTaken() {
        missionState.text = getString(R.string.mission_done)
        // Foto tirada -> a música PARA.
        stopSoundService()
        btnPhoto.isEnabled = false
        btnPhoto.text = getString(R.string.btn_photo_done)
        btnStop.visibility = View.VISIBLE
        btnStop.text = getString(R.string.btn_ok_sleep)
        btnStop.setOnClickListener { finish() }
    }

    private fun stopSoundService() {
        try {
            startService(Intent(this, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_STOP
            })
        } catch (_: Exception) {
        }
    }

    private fun finishAlarm() {
        stopSoundService()
        finish()
    }

    /**
     * Abre o Spotify tocando a música escolhida. Para silenciar de vez, o
     * usuário pode também parar a reprodução dentro do próprio Spotify.
     */
    private fun openSpotifyNow(musicId: Int) {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val name = when (musicId) {
            SpotifyMusic.STANDARD_BELL_ID -> "música animada para acordar"
            else -> SpotifyMusic.buildList(this).find { it.id == musicId }?.title
                ?: "música animada para acordar"
        }
        val opened = SpotifyMusic.openInSpotify(this, name)
        missionState.text = if (opened)
            getString(R.string.spotify_opened)
        else
            getString(R.string.spotify_not_found)
    }

    override fun onBackPressed() {
        // Não deixa fechar sem cumprir a missão (se ela estiver ligada)
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(AppPrefs.KEY_MISSION_ON, true)) {
            missionState.text = getString(R.string.mission_required)
        } else {
            finishAlarm()
        }
    }

    override fun onResume() {
        super.onResume()
        // Quando a foto terminar e voltarmos para esta tela, a missão está cumprida
        val f = photoFile
        if (f != null && f.exists() && f.length() > 0) onPhotoTaken()
    }

    override fun onDestroy() {
        // Se a atividade for destruída sem missão cumprida, mantém o som ligado
        super.onDestroy()
    }
}
