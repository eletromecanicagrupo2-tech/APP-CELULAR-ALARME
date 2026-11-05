package com.example.alarmevaso

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MusicItem(val id: Int, val title: String, val subtitle: String)

data class BundledMusic(
    val asset: String,
    val title: String,
    val playlist: String,
    val spotifyQuery: String
)

/**
 * Seleção de músicas "do Spotify".
 *
 * O Spotify não permite tocar uma faixa específica por app de terceiros
 * (a API Web Playback exige Premium + device ID próprio). Por isso o alarme
 * usa SOMENTE arquivos locais (para funcionar offline e alto na hora H), mas a
 * lista é organizada como playlists do Spotify e cada música tem um botão
 * "Abrir no Spotify" que abre a faixa real no app.
 */
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

    const val STANDARD_BELL_ID = -1

    /** Músicas embarcadas (assets/music/) + playlists estilo Spotify. */
    val bundled = listOf(
        BundledMusic("acorda_sol.wav", "Acorda, Sol!", "Manhanim Animada",
            "good morning acoustic indie uplifting"),
        BundledMusic("cafe_quente.wav", "Café Quente", "Manhã Acústica",
            "cafezinho bossa nova morning brazil"),
        BundledMusic("batida_desperta.wav", "Batida Desperta", "Despertar Elétrico",
            "uplifting electronic morning workout hits")
    )

    /** Busca no Spotify pelo nome da playlist estilo Spotify. */
    fun openPlaylistInSpotify(context: Context, playlistName: String): Boolean {
        val q = Uri.encode("playlist:$playlistName")
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            true
        } catch (e: Exception) {
            try {
                val i = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/${Uri.encode(playlistName)}"))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                true
            } catch (e2: Exception) { false }
        }
    }

    /** Busca no Spotify (deep link de busca) — abre o app instalado. */
    fun openInSpotify(context: Context, query: String): Boolean {
        return try {
            val enc = Uri.encode(query)
            // 1) tenta deep link nativo; 2) cai para link web (abre app ou navegador)
            var i = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$enc"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            true
        } catch (e: ActivityNotFoundException) {
            try {
                val i = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")
                )
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /** Lista exibida na tela principal. */
    fun buildList(context: Context): List<MusicItem> {
        val out = ArrayList<MusicItem>()
        out.add(MusicItem(STANDARD_BELL_ID, "Sininho padrão", "Toque clássico de despertador"))
        var bid = 0
        for (b in bundled) {
            out.add(MusicItem(bid++, b.title, "Som do aplicativo"))
        }
        // Músicas copiadas para a pasta do app (importadas pelo usuário)
        val dir = musicDir(context)
        var id = 1000
        dir.listFiles { f -> f.isFile && f.name.lowercase().let {
            it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".m4a") || it.endsWith(".ogg") || it.endsWith(".aac")
        } }?.sortedBy { it.name }?.forEach { f ->
            rememberFile(context, id, f)
            out.add(MusicItem(id++, f.nameWithoutExtension.replace('_', ' '),
                "Importada do celular"))
        }
        return out
    }

    fun fileFor(context: Context, id: Int): File? {
        if (id == STANDARD_BELL_ID) return null
        if (id < 1000) {
            // música embarcada -> copia do assets para cache na primeira vez
            val item = bundled.getOrNull(id) ?: return null
            val dest = File(File(context.filesDir, "alarmetones"), item.asset)
            if (!dest.exists()) {
                try {
                    context.assets.open("music/" + item.asset).use { ins ->
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { os -> ins.copyTo(os) }
                    }
                } catch (e: Exception) {
                    return null
                }
            }
            return if (dest.exists()) dest else null
        }
        // arquivo do diretório interno de músicas
        val prefs = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString("file_$id", null) ?: return null
        val f = File(musicDir(context), name)
        return if (f.exists()) f else null
    }

    fun rememberFile(context: Context, id: Int, file: File) {
        context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
            .edit().putString("file_$id", file.name).apply()
    }

    private fun musicDir(context: Context): File =
        File(context.getExternalFilesDir(null), "music").apply { mkdirs() }

    private fun hasAsset(context: Context, path: String): Boolean =
        try {
            context.assets.open(path).close()
            true
        } catch (e: Exception) {
            false
        }

    /** Cria arquivo temporário para a foto da missão (via FileProvider). */
    fun newPhotoFile(context: Context): File {
        val dir = File(context.cacheDir, "fotos_mission").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("vaso_${stamp}_", ".jpg", dir)
    }
}
