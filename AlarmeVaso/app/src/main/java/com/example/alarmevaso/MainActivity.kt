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
    private lateinit var recycler: RecyclerView
    private var musicId = SpotifyMusic.STANDARD_BELL_ID

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* usuário pode negar; alarme continua funcionando */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        musicId = prefs.getInt(AppPrefs.KEY_MUSIC_ID, SpotifyMusic.STANDARD_BELL_ID)

        txtTime = findViewById(R.id.txtTime)
        txtNext = findViewById(R.id.txtNext)
        txtStatus = findViewById(R.id.txtStatus)
        txtMusic = findViewById(R.id.txtMusicSelected)
        swAlarm = findViewById(R.id.swAlarmOn)
        swRepeat = findViewById(R.id.swRepeat)
        swMission = findViewById(R.id.swMission)
        recycler = findViewById(R.id.recyclerMusic)

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

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = MusicAdapter(SpotifyMusic.buildList(this)) { item ->
            musicId = item.id
            prefs.edit().putInt(AppPrefs.KEY_MUSIC_ID, item.id).apply()
            updateMusicLabel()
            (recycler.adapter as MusicAdapter).notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btnSamsungClock).setOnClickListener { configureSamsungAlarm() }
        findViewById<Button>(R.id.btnImportMusic).setOnClickListener { importMusicFile() }
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

    private fun configureSamsungAlarm() {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val hour = prefs.getInt(AppPrefs.KEY_HOUR, AppPrefs.DEFAULT_HOUR)
        val minute = prefs.getInt(AppPrefs.KEY_MIN, AppPrefs.DEFAULT_MIN)
        val time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Spotify às $time")
            .setMessage("O Relógio Samsung recebe esse horário no fuso do celular. Abra o alarme, vá em Som → Spotify, escolha a música e salve. A foto encerra apenas o alarme do Alarme Vaso. Se ativar os dois, cada um toca e precisa ser encerrado separadamente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Abrir Relógio") { _, _ ->
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    setPackage("com.sec.android.app.clockpackage")
                    putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                    putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Acordar com Spotify")
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                    if (prefs.getBoolean(AppPrefs.KEY_REPEAT, true)) {
                        putIntegerArrayListExtra(android.provider.AlarmClock.EXTRA_DAYS,
                            ArrayList((java.util.Calendar.SUNDAY..java.util.Calendar.SATURDAY).toList()))
                    }
                }
                try {
                    startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    android.widget.Toast.makeText(this, "O Relógio Samsung não está disponível neste aparelho.", android.widget.Toast.LENGTH_LONG).show()
                }
            }.show()
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
            .setTitle("Faixa para depois do alarme")
            .setMessage("No Spotify, abra a música e toque em Compartilhar → Copiar link. Cole aqui para abri-la depois de parar o alarme.")
            .setView(field)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Remover") { _, _ ->
                prefs.edit().remove(SpotifyMusic.SAVED_TRACK).apply()
            }.create()
        dialog.show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val link = SpotifyMusic.trackLink(field.text.toString())
            if (link == null) {
                field.error = "Cole o link de uma música de open.spotify.com."
            } else {
                prefs.edit().putString(SpotifyMusic.SAVED_TRACK, link).apply()
                android.widget.Toast.makeText(this, "Faixa salva", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun updateMusicLabel() {
        val item = SpotifyMusic.buildList(this).find { it.id == musicId }
        txtMusic.text = getString(R.string.music_selected,
            item?.title ?: getString(R.string.music_bell))
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

    private fun importMusicFile() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        importLauncher.launch(pick)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> copyUriToMusicFolder(uri) }
        }
    }

    private fun copyUriToMusicFolder(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { ins ->
                val name = queryDisplayName(uri) ?: "musica_${System.currentTimeMillis()}.mp3"
                val dir = File(getExternalFilesDir(null), "music").apply { mkdirs() }
                val dest = File(dir, name)
                dest.outputStream().use { os -> ins.copyTo(os) }
                val id = 1000 + SpotifyMusic.buildList(this).size
                SpotifyMusic.rememberFile(this, id, dest)
                val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
                musicId = id
                prefs.edit().putInt(AppPrefs.KEY_MUSIC_ID, id).apply()
                lateinit var newAdapter: MusicAdapter
                newAdapter = MusicAdapter(SpotifyMusic.buildList(this)) { item ->
                    musicId = item.id
                    prefs.edit().putInt(AppPrefs.KEY_MUSIC_ID, item.id).apply()
                    updateMusicLabel()
                    newAdapter.notifyDataSetChanged()
                }
                recycler.adapter = newAdapter
                updateMusicLabel()
                txtStatus.text = getString(R.string.status_imported, name)
            }
        } catch (e: Exception) {
            txtStatus.text = getString(R.string.status_import_error)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Dispara o alarme na hora para testar a missão da foto. */
    private fun testAlarm() {
        val prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
        val svc = Intent(this, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_START
            putExtra(AlarmSoundService.EXTRA_MUSIC_ID,
                prefs.getInt(AppPrefs.KEY_MUSIC_ID, SpotifyMusic.STANDARD_BELL_ID))
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

    /** Adapter simples da lista de músicas estilo playlist. */
    private class MusicAdapter(
        val items: List<MusicItem>,
        val onClick: (MusicItem) -> Unit
    ) : RecyclerView.Adapter<MusicAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.txtTitle)
            val subtitle: TextView = v.findViewById(R.id.txtSubtitle)
            val check: TextView = v.findViewById(R.id.txtCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_music, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            val ctx = holder.itemView.context
            val selected = ctx
                .getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
                .getInt(AppPrefs.KEY_MUSIC_ID, SpotifyMusic.STANDARD_BELL_ID) == item.id
            holder.check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            holder.itemView.setOnClickListener {
                onClick(item)
                notifyItemRangeChanged(0, items.size)
            }
        }
    }
}
