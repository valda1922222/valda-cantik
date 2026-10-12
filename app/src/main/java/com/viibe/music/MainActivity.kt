package com.viibe.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class Song(val title: String, val artist: String, val uri: Uri)

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private val songs = mutableListOf<Song>()
    private lateinit var list: LinearLayout
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: Button
    private var currentIndex = -1

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            songs.add(Song(it.lastPathSegment ?: "Musik pilihan", "File lokal", it))
            renderSongs()
            playSong(songs.lastIndex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 8, 12)
        window.navigationBarColor = Color.rgb(8, 8, 12)
        player = ExoPlayer.Builder(this).build()
        buildUI()
        requestMusicPermission()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 16); setBackgroundColor(Color.rgb(8, 8, 12)) }
        root.addView(TextView(this).apply { text = "V.I.I.B.E  ♪"; textSize = 28f; setTextColor(Color.rgb(0, 240, 190)) })
        root.addView(TextView(this).apply { text = "YOUR MUSIC. YOUR VIBE."; textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0, 4, 0, 20) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "Pilih Musik"; setOnClickListener { picker.launch(arrayOf("audio/*")) } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = "Scan Lagu"; setOnClickListener { loadSongs() } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        nowPlaying = TextView(this).apply { text = "Pilih lagu untuk mulai"; textSize = 16f; setTextColor(Color.WHITE); setPadding(0, 24, 0, 12) }
        root.addView(nowPlaying)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply { text = "⏮"; setOnClickListener { if (songs.isNotEmpty()) playSong(if (currentIndex <= 0) songs.lastIndex else currentIndex - 1) } })
        playButton = Button(this).apply { text = "▶ Play"; setOnClickListener { val p = player ?: return@setOnClickListener; if (p.isPlaying) { p.pause(); playButton.text = "▶ Play" } else if (p.mediaItemCount > 0) { p.play(); playButton.text = "Ⅱ Pause" } else if (songs.isNotEmpty()) playSong(0) } }
        controls.addView(playButton)
        controls.addView(Button(this).apply { text = "⏭"; setOnClickListener { if (songs.isNotEmpty()) playSong((currentIndex + 1) % songs.size) } })
        root.addView(controls)
        root.addView(TextView(this).apply { text = "DAFTAR LAGU"; textSize = 13f; setTextColor(Color.rgb(0, 240, 190)); setPadding(0, 22, 0, 10) })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun requestMusicPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadSongs() else requestPermissions(arrayOf(permission), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadSongs()
    }

    private fun loadSongs() {
        songs.clear()
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        try { contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) { val id = cursor.getLong(idCol); val uri = Uri.withAppendedPath(collection, id.toString()); songs.add(Song(cursor.getString(titleCol) ?: "Unknown", cursor.getString(artistCol) ?: "Unknown artist", uri)) }
        } } catch (_: Exception) { Toast.makeText(this, "Gagal membaca lagu", Toast.LENGTH_SHORT).show() }
        renderSongs()
    }

    private fun renderSongs() {
        list.removeAllViews()
        if (songs.isEmpty()) list.addView(TextView(this).apply { text = "Belum ada lagu. Tekan Pilih Musik."; setTextColor(Color.LTGRAY); setPadding(0, 16, 0, 16) })
        songs.forEachIndexed { index, song ->
            list.addView(TextView(this).apply { text = "♫  ${song.title}\n     ${song.artist}"; textSize = 15f; setTextColor(Color.WHITE); setPadding(12, 14, 12, 14); setOnClickListener { playSong(index) } })
            list.addView(View(this).apply { setBackgroundColor(Color.DKGRAY); layoutParams = LinearLayout.LayoutParams(-1, 1) })
        }
    }

    private fun playSong(index: Int) {
        if (index !in songs.indices) return
        currentIndex = index; val song = songs[index]; player?.setMediaItem(MediaItem.fromUri(song.uri)); player?.prepare(); player?.play(); nowPlaying.text = "♫  ${song.title}\n${song.artist}"; playButton.text = "Ⅱ Pause"
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
