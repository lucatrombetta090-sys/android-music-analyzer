package com.musicmood

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private val vm: SongViewModel by lazy {
        ViewModelProvider(this)[SongViewModel::class.java]
    }

    private val libraryFragment  = LibraryFragment()
    private val playerFragment   = PlayerFragment()
    private val statsFragment    = StatsFragment()
    private val bubbleMapFragment = BubbleMapFragment()
    private var active: Fragment = libraryFragment

    // Mini player views
    private lateinit var miniPlayerContainer: View
    private lateinit var miniPlayer: View
    private lateinit var miniArt: ImageView
    private lateinit var miniArtLetter: TextView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniBtnPlay: MaterialButton
    private lateinit var miniBtnNext: MaterialButton
    private lateinit var miniProgress: LinearProgressIndicator

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateMiniProgress()
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContentView(R.layout.activity_main)

        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, statsFragment,    "stats").hide(statsFragment)
            .add(R.id.fragmentContainer, bubbleMapFragment, "bubbles").hide(bubbleMapFragment)
            .add(R.id.fragmentContainer, playerFragment,   "player").hide(playerFragment)
            .add(R.id.fragmentContainer, libraryFragment,  "library")
            .commit()

        // Bottom nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val frag = when (item.itemId) {
                R.id.navPlayer -> playerFragment
                R.id.navStats  -> statsFragment
                else           -> libraryFragment
            }
            showFragment(frag)
            true
        }

        // Mini player setup
        miniPlayerContainer = findViewById(R.id.miniPlayerContainer)
        miniPlayer          = findViewById(R.id.miniPlayer)
        miniArt             = findViewById(R.id.miniArt)
        miniArtLetter       = findViewById(R.id.miniArtLetter)
        miniTitle           = findViewById(R.id.miniTitle)
        miniArtist          = findViewById(R.id.miniArtist)
        miniBtnPlay         = findViewById(R.id.miniBtnPlay)
        miniBtnNext         = findViewById(R.id.miniBtnNext)
        miniProgress        = findViewById(R.id.miniProgress)

        miniPlayer.setOnClickListener { goToPlayer() }

        miniBtnPlay.setOnClickListener {
            playerFragment.togglePlayFromMini()
        }
        miniBtnNext.setOnClickListener {
            playerFragment.nextFromMini()
        }

        // Observe current song for mini player
        var miniPlayerShown = false
        vm.currentSong.observe(this) { song ->
            if (song != null) {
                if (!miniPlayerShown) {
                    miniPlayerShown = true
                    miniPlayerContainer.visibility = View.VISIBLE
                    miniPlayerContainer.startAnimation(
                        android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up))
                } else {
                    miniPlayerContainer.visibility = View.VISIBLE
                }
                miniTitle.text  = song.title.ifBlank { "Sconosciuto" }
                miniArtist.text = song.artist.ifBlank { "Artista sconosciuto" }

                // Album art
                val bmp = if (song.albumId > 0L) {
                    try {
                        val uri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), song.albumId)
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } catch (_: Exception) { null }
                } else null

                if (bmp != null) {
                    miniArt.setImageBitmap(bmp)
                    miniArt.visibility    = View.VISIBLE
                    miniArtLetter.visibility = View.GONE
                } else {
                    miniArt.visibility    = View.INVISIBLE
                    miniArtLetter.visibility = View.VISIBLE
                    miniArtLetter.text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
                }
            } else {
                miniPlayerContainer.visibility = View.GONE
            }
        }

        vm.isPlaying.observe(this) { playing ->
            miniBtnPlay.text = if (playing) "⏸" else "▶"
        }

        vm.loadCache(applicationContext)
        // Applica le correzioni mood manuali dopo il caricamento della cache
        vm.applyMoodOverrides(applicationContext)

        // Start progress updater
        progressHandler.post(progressRunnable)
    }

    private fun updateMiniProgress() {
        val frag = playerFragment
        if (!frag.isAdded) return
        val pct = frag.getProgressPercent()
        miniProgress.progress = pct
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun showFragment(f: Fragment) {
        if (f == active) return
        val order = listOf(libraryFragment, bubbleMapFragment, playerFragment, statsFragment)
        val fromIdx = order.indexOf(active)
        val toIdx   = order.indexOf(f)
        val (enter, exit) = when {
            toIdx > fromIdx -> Pair(R.anim.slide_in_right, R.anim.slide_out_left)
            toIdx < fromIdx -> Pair(R.anim.slide_in_left,  R.anim.slide_out_right)
            else            -> Pair(R.anim.fade_in,         R.anim.fade_out)
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(enter, exit)
            .hide(active)
            .show(f)
            .commit()
        active = f
    }

    fun goToPlayer() {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.navPlayer
        showFragment(playerFragment)
    }

    fun goToLibrary() {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.navLibrary
        showFragment(libraryFragment)
    }

    fun switchToBubbleMap() {
        showFragment(bubbleMapFragment)
    }

    fun switchToSectionsView() {
        showFragment(bubbleMapFragment)
    }

    fun switchToListView() {
        showFragment(libraryFragment)
    }
}
