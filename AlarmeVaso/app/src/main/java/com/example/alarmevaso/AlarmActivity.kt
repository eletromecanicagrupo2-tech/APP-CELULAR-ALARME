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

    private var missionOn = true
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
        missionOn = savedInstanceState?.getBoolean("missionOn") ?: prefs.getBoolean(AppPrefs.KEY_MISSION_ON, true)
        photoFile = savedInstanceState?.getString("photoPath")?.let(::File)
        val track = prefs.getString(SpotifyMusic.SAVED_TRACK, null)
        btnSpotify.visibility = View.GONE
        btnSpotify.setOnClickListener {
            if (track != null) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track)))
                } catch (_: android.content.ActivityNotFoundException) {
                    missionState.text = "Nenhum aplicativo disponível para abrir a faixa."
                }
            }
        }

        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) validatePhoto()
            else retryPhoto(getString(R.string.mission_cancelled))
        }

        if (!missionOn) {
            // Missão desligada nas configurações: botão para direto
            btnPhoto.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
            btnStop.setOnClickListener { completeMission() }
        } else {
            btnStop.visibility = View.GONE
            btnPhoto.setOnClickListener { startCameraMission() }
        }

        when {
            savedInstanceState?.getBoolean("completed") == true -> showCompleted()
            savedInstanceState?.getBoolean("checking") == true -> validatePhoto()
        }
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
            retryPhoto(getString(R.string.mission_camera_error))
        }
    }

    private var checking = false
    private var completed = false

    private fun retryPhoto(message: String) {
        photoFile?.delete()
        photoFile = null
        btnPhoto.isEnabled = true
        missionState.text = message
    }

    private fun validatePhoto() {
        if (checking || completed) return
        val file = photoFile ?: return retryPhoto("Foto indisponível. Tire outra foto.")
        checking = true
        btnPhoto.isEnabled = false
        missionState.text = "Verificando se há um vaso sanitário na foto…"
        Thread {
            val accepted = try {
                ToiletDetector.recognizes(applicationContext, file)
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                checking = false
                when (accepted) {
                    true -> completeMission()
                    false -> retryPhoto("Vaso não reconhecido. Enquadre a bacia inteira com boa iluminação e tente novamente.")
                    null -> retryPhoto("Não foi possível analisar a foto. Tente novamente.")
                }
            }
        }.start()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("photoPath", photoFile?.path)
        out.putBoolean("missionOn", missionOn)
        out.putBoolean("checking", checking)
        out.putBoolean("completed", completed)
    }

    private fun completeMission() {
        stopSoundService()
        photoFile?.delete()
        photoFile = null
        showCompleted()
    }

    private fun showCompleted() {
        completed = true
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        btnSpotify.visibility = if (prefs.contains(SpotifyMusic.SAVED_TRACK)) View.VISIBLE else View.GONE
        missionState.text = getString(R.string.mission_done)
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

    override fun onBackPressed() {
        // Não deixa fechar sem cumprir a missão (se ela estiver ligada)
        if (!completed && missionOn) {
            missionState.text = getString(R.string.mission_required)
        } else {
            finishAlarm()
        }
    }

}
