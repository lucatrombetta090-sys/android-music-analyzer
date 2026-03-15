package com.musicmood

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class MoodSectionsFragment : Fragment() {

    private val vm: SongViewModel by activityViewModels()
    private lateinit var sectionsContainer: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var btnToggle: MaterialButton

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_mood_sections, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sectionsContainer = view.findViewById(R.id.sectionsContainer)
        tvEmpty           = view.findViewById(R.id.tvSectionsEmpty)
        btnToggle         = view.findViewById(R.id.btnToggleToList)

        btnToggle.setOnClickListener {
            (activity as? MainActivity)?.switchToListView()
        }

        vm.songs.observe(viewLifecycleOwner) { buildSections() }
    }

    override fun onResume() { super.onResume(); buildSections() }

    private fun buildSections() {
        sectionsContainer.removeAllViews()
        val analyzed = vm.songs.value?.filter { it.analyzed } ?: emptyList()
        if (analyzed.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }
        tvEmpty.visibility = View.GONE

        // Raggruppa per mood effettivo mantenendo ordine MOODS
        val grouped = analyzed.groupBy { it.effectiveMood }
        SongAdapter.MOODS.forEach { mood ->
            val songs = grouped[mood] ?: return@forEach
            if (songs.isEmpty()) return@forEach
            addSection(mood, songs)
        }
    }

    private fun addSection(mood: String, songs: List<Song>) {
        val ctx = requireContext()
        val color = SongAdapter.MOOD_COLORS[mood] ?: (0xFF1DB954L).toInt()
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        // Sezione wrapper
        val section = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.setMargins(0, 0, 0, dp(8))
            layoutParams = lp
        }

        // Header row: emoji + mood name + conteggio
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        // Mood dot
        val dot = View(ctx).apply {
            val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            background = bg
            val lp = LinearLayout.LayoutParams(dp(10), dp(10))
            lp.setMargins(0, 0, dp(10), 0)
            layoutParams = lp
        }
        // Mood label
        val label = TextView(ctx).apply {
            text = mood
            textSize = 16f
            setTextColor(color)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        // Count
        val count = TextView(ctx).apply {
            text = "${songs.size} brani  ›"
            textSize = 12f
            setTextColor(0xFFB3B3B3L.toInt())
        }
        count.setOnClickListener {
            vm.setFilter(mood = mood)
            (activity as? MainActivity)?.switchToListView()
        }
        header.addView(dot)
        header.addView(label)
        header.addView(count)
        section.addView(header)

        // Horizontal RecyclerView
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            adapter = MoodCardAdapter(songs.sortedByDescending { it.energy }) { song ->
                vm.playlist = songs
                vm.playlistIndex = songs.indexOfFirst { it.path == song.path }.coerceAtLeast(0)
                vm.setCurrentSong(song)
                (activity as? MainActivity)?.goToPlayer()
            }
            setPadding(dp(8), 0, dp(8), dp(4))
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        section.addView(rv)

        // Separator
        val sep = View(ctx).apply {
            setBackgroundColor(0xFF222222L.toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        }
        section.addView(sep)
        sectionsContainer.addView(section)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ── Adapter per le card orizzontali ──────────────────────────────────────────

class MoodCardAdapter(
    private val songs: List<Song>,
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<MoodCardAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View              = v.findViewById(R.id.moodCard)
        val art: ShapeableImageView = v.findViewById(R.id.cardArt)
        val letter: TextView        = v.findViewById(R.id.cardLetter)
        val title: TextView         = v.findViewById(R.id.cardTitle)
        val artist: TextView        = v.findViewById(R.id.cardArtist)
        val dot: View               = v.findViewById(R.id.cardMoodDot)

        fun bind(song: Song) {
            val bmp = if (song.albumId > 0L) {
                try {
                    val uri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), song.albumId)
                    itemView.context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) { null }
            } else null

            if (bmp != null) {
                art.setImageBitmap(bmp)
                art.visibility    = View.VISIBLE
                letter.visibility = View.GONE
            } else {
                art.visibility    = View.INVISIBLE
                letter.visibility = View.VISIBLE
                letter.text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
            }

            title.text  = song.title.ifBlank { "Sconosciuto" }
            artist.text = song.artist.ifBlank { "Artista sconosciuto" }

            val color = SongAdapter.MOOD_COLORS[song.effectiveMood]
            if (color != null) {
                (dot.background as? GradientDrawable)?.setColor(color)
                    ?: run {
                        dot.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setColor(color) }
                    }
                dot.visibility = View.VISIBLE
            } else {
                dot.visibility = View.GONE
            }

            root.setOnClickListener { onClick(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_mood_card, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(songs[pos])
    override fun getItemCount() = songs.size
}
