package com.example.alarmevaso

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import java.io.File
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var txtTime: TextView
    private lateinit var txtNext: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtMusic: TextView
    private lateinit var swAlarm: MaterialSwitch
    private lateinit var swRepeat: MaterialSwitch
    private lateinit var swMission: MaterialSwitch

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* usuário pode negar; alarme continua funcionando */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)

        txtTime = findViewById(R.id.txtTime)
        txtNext = findViewById(R.id.txtNext)
        txtStatus = findViewById(R.id.txtStatus)
        txtMusic = findViewById(R.id.txtMusicSelected)
        swAlarm = findViewById(R.id.swAlarmOn)
        swRepeat = findViewById(R.id.swRepeat)
        swMission = findViewById(R.id.swMission)

        // Hora de Brasília agora
        findViewById<Button>(R.id.btnPickTime).setOnClickListener { showTimePicker() }
        findViewById<TextView>(R.id.txtBrasiliaNow).text = brasiliaNowString()

        swAlarm.isChecked = prefs.getBoolean(AppPrefs.KEY_ENABLED, false)
        swRepeat.isChecked = prefs.getBoolean(AppPrefs.KEY_REPEAT, true)
        swMission.isChecked = prefs.getBoolean(AppPrefs.KEY_MISSION_ON, true)

        swAlarm.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(AppPrefs.KEY_ENABLED, on).apply()
            if (on) {
                requestPermissionsIfNeeded()
                AlarmScheduler.schedule(this)
            } else {
                AlarmScheduler.cancel(this)
            }
            refresh()
        }
        swRepeat.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(AppPrefs.KEY_REPEAT, on).apply()
        }
        swMission.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(AppPrefs.KEY_MISSION_ON, on).apply()
        }

        findViewById<Button>(R.id.btnOpenSpotify).setOnClickListener {
            chooseSpotifyTrack()
        }
        findViewById<Button>(R.id.btnTestAlarm).setOnClickListener { testAlarm() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.txtBrasiliaNow).text = brasiliaNowString()
        refresh()
    }

    private fun brasiliaNowString(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        fmt.timeZone = AppPrefs.brasiliaTimeZone()
        return getString(R.string.title_brasilia, fmt.format(Date()))
    }

    private fun refresh() {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val h = prefs.getInt(AppPrefs.KEY_HOUR, AppPrefs.DEFAULT_HOUR)
        val m = prefs.getInt(AppPrefs.KEY_MIN, AppPrefs.DEFAULT_MIN)
        txtTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
        val enabled = prefs.getBoolean(AppPrefs.KEY_ENABLED, false)
        txtStatus.text = getString(if (enabled) R.string.status_on else R.string.status_off)
        val next = AlarmScheduler.nextTriggerMillis(this)
        if (enabled && next > 0) {
            val sameDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).also {
                it.timeZone = AppPrefs.brasiliaTimeZone()
            }.format(Date(next)) == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).also {
                it.timeZone = AppPrefs.brasiliaTimeZone()
            }.format(Date())
            txtNext.text = if (sameDay)
                getString(R.string.next_today, SimpleDateFormat("HH:mm", Locale.getDefault())
                    .also { it.timeZone = AppPrefs.brasiliaTimeZone() }.format(Date(next)))
            else
                getString(R.string.next_tomorrow, SimpleDateFormat("HH:mm", Locale.getDefault())
                    .also { it.timeZone = AppPrefs.brasiliaTimeZone() }.format(Date(next)))
        } else {
            txtNext.text = getString(R.string.next_none)
        }
        updateMusicLabel()
    }

    private fun chooseSpotifyTrack() {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val field = android.widget.EditText(this).apply {
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://open.spotify.com/track/…"
            setText(prefs.getString(SpotifyMusic.SAVED_TRACK, ""))
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Escolher faixa do Spotify")
            .setMessage("No Spotify, abra a música e toque em Compartilhar → Copiar link. Cole aqui para salvar a faixa.")
            .setView(field)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Remover") { _, _ ->
                prefs.edit().remove(SpotifyMusic.SAVED_TRACK).apply()
                updateMusicLabel()
            }.create()
        dialog.show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val link = SpotifyMusic.trackLink(field.text.toString())
            if (link == null) {
                field.error = "Cole o link de uma música de open.spotify.com."
            } else {
                prefs.edit().putString(SpotifyMusic.SAVED_TRACK, link).apply()
                updateMusicLabel()
                android.widget.Toast.makeText(this, "Faixa salva", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun updateMusicLabel() {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        txtMusic.text = prefs.getString(SpotifyMusic.SAVED_TRACK, null) ?: "Escolha uma faixa do Spotify"

    }

    private fun showTimePicker() {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(prefs.getInt(AppPrefs.KEY_HOUR, AppPrefs.DEFAULT_HOUR))
            .setMinute(prefs.getInt(AppPrefs.KEY_MIN, AppPrefs.DEFAULT_MIN))
            .setTitleText(getString(R.string.pick_time_title))
            .build()
        picker.addOnPositiveButtonClickListener {
            prefs.edit()
                .putInt(AppPrefs.KEY_HOUR, picker.hour)
                .putInt(AppPrefs.KEY_MIN, picker.minute)
                .apply()
            AlarmScheduler.schedule(this)
            refresh()
        }
        picker.show(supportFragmentManager, "time")
    }

    /** Dispara o alarme na hora para testar a missão da foto. */
    private fun testAlarm() {
        val svc = Intent(this, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
        startActivity(Intent(this, AlarmActivity::class.java))
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName")))
            } catch (_: Exception) {
            }
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName")))
            } catch (_: Exception) {
            }
        }
    }

}
