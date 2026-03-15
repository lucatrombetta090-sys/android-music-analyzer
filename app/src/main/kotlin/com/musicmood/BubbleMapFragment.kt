package com.musicmood

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class BubbleMapFragment : Fragment() {

    private val vm: SongViewModel by activityViewModels()
    private lateinit var bubbleView: BubbleMapView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvInfo: TextView
    private lateinit var tvEmpty: TextView

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_bubble_map, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bubbleView = view.findViewById(R.id.bubbleMapView)
        chipGroup  = view.findViewById(R.id.chipsMood)
        tvInfo     = view.findViewById(R.id.tvBubbleInfo)
        tvEmpty    = view.findViewById(R.id.tvBubbleEmpty)

        view.findViewById<MaterialButton>(R.id.btnBubbleToList)
            .setOnClickListener { (activity as? MainActivity)?.switchToListView() }

        // Build mood chips
        val allChip = Chip(requireContext()).apply {
            text = "Tutti"; isCheckable = true; isChecked = true
            chipBackgroundColor = resources.getColorStateList(R.color.chip_bg_selector, null)
            setTextColor(resources.getColorStateList(R.color.chip_text_selector, null))
            setOnClickListener { bubbleView.activeMood = null; refreshInfo(null) }
        }
        chipGroup.addView(allChip)

        SongAdapter.MOODS.forEach { mood ->
            val color = SongAdapter.MOOD_COLORS[mood] ?: 0xFF1DB954L.toInt()
            chipGroup.addView(Chip(requireContext()).apply {
                this.text = mood; isCheckable = true
                chipBackgroundColor = resources.getColorStateList(R.color.chip_bg_selector, null)
                setTextColor(resources.getColorStateList(R.color.chip_text_selector, null))
                setOnClickListener {
                    bubbleView.activeMood = mood
                    refreshInfo(mood)
                }
            })
        }

        bubbleView.onSongTapped = { song ->
            vm.playlist      = vm.songs.value?.filter { it.analyzed } ?: emptyList()
            vm.playlistIndex = vm.playlist.indexOfFirst { it.path == song.path }.coerceAtLeast(0)
            vm.setCurrentSong(song)
            (activity as? MainActivity)?.goToPlayer()
        }

        vm.songs.observe(viewLifecycleOwner) { songs ->
            val analyzed = songs.filter { it.analyzed }
            if (analyzed.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                bubbleView.visibility = View.INVISIBLE
            } else {
                tvEmpty.visibility = View.GONE
                bubbleView.visibility = View.VISIBLE
                bubbleView.post { bubbleView.setSongs(analyzed) }
            }
            refreshInfo(bubbleView.activeMood)
        }
    }

    private fun buildLegend(songs: List<Song>) = refreshInfo(null)

    private fun refreshInfo(mood: String?) {
        val songs = vm.songs.value?.filter { it.analyzed } ?: emptyList()
        val n = if (mood != null) songs.count { it.effectiveMood == mood } else songs.size
        tvInfo.text = if (mood != null) "$n brani · $mood  ·  doppio tap per reset zoom"
                      else "$n brani analizzati  ·  doppio tap per reset zoom"
    }

    override fun onResume() {
        super.onResume()
        vm.songs.value?.let { songs ->
            val analyzed = songs.filter { it.analyzed }
            if (analyzed.isNotEmpty()) bubbleView.post { bubbleView.setSongs(analyzed) }
        }
    }
}
