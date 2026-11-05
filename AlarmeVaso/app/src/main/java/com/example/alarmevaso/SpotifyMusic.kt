package com.example.alarmevaso

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SpotifyMusic {

    const val SAVED_TRACK = "spotify_after_alarm"

    fun trackLink(input: String): String? {
        val value = input.trim()
        val uri = Uri.parse(value)
        val path = uri.pathSegments
        val id = when {
            value.startsWith("spotify:track:") -> value.removePrefix("spotify:track:")
            uri.scheme == "https" && uri.host == "open.spotify.com" &&
                path.size in 2..3 && path[path.size - 2] == "track" &&
                (path.size == 2 || path.first().startsWith("intl-")) -> path.last()
            else -> return null
        }
        return if (Regex("[A-Za-z0-9]{22}").matches(id)) "https://open.spotify.com/track/$id" else null
    }

    /** Cria arquivo temporário para a foto da missão (via FileProvider). */
    fun newPhotoFile(context: Context): File {
        val dir = File(context.cacheDir, "fotos_mission").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("vaso_${stamp}_", ".jpg", dir)
    }
}
