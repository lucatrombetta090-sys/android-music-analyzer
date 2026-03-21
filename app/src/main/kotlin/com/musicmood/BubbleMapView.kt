package com.musicmood

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.view.GestureDetectorCompat
import kotlin.math.*

class BubbleMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Data ─────────────────────────────────────────────────────────────────
    data class Bubble(
        val song: Song,
        var x: Float, var y: Float,
        val radius: Float,
        val color: Int,
        val alphaDim: Int
    )

    private val bubbles = mutableListOf<Bubble>()
    private var selected: Bubble? = null
    var onBubbleTap: ((Song) -> Unit)? = null
    var onSongTapped: ((Song) -> Unit)?  // alias
        get() = onBubbleTap
        set(v) { onBubbleTap = v }
    var activeMood: String? = null
        set(v) { field = v; invalidate() }

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.WHITE }
    private val zonePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 34f; isFakeBoldText = true }
    private val panelBg     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0xFF1E1E1E.toInt() }
    private val titlePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f }
    private val subPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB3B3B3.toInt(); textSize = 26f }
    private val metaPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }
    private val playBg      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0xFF1DB954.toInt() }
    private val playFg      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.BLACK }

    // ── Cluster positions (fraction of canvas w/h) ───────────────────────────
    private val ZONES = mapOf(
        "Festivo"        to Pair(0.50f, 0.12f),   // top center
        "Energico"       to Pair(0.80f, 0.22f),   // top right
        "Positivo"       to Pair(0.22f, 0.22f),   // top left
        "Rilassato"      to Pair(0.15f, 0.48f),   // mid left
        "Concentrazione" to Pair(0.78f, 0.48f),   // mid right
        "Nostalgico"     to Pair(0.50f, 0.50f),   // center
        "Malinconico"    to Pair(0.18f, 0.78f),   // bottom left
        "Romantico"      to Pair(0.52f, 0.82f),   // bottom center
        "Aggressivo"     to Pair(0.84f, 0.75f)    // bottom right
    )

    // ── Viewport ──────────────────────────────────────────────────────────────
    private val matrix = Matrix()
    private val inv    = Matrix()
    private var scale  = 1f
    private var transX = 0f
    private var transY = 0f
    private val MIN_SCALE = 0.35f
    private val MAX_SCALE = 4f

    // ── Gestures ──────────────────────────────────────────────────────────────
    private val scaleGD = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                rebuildMatrix(); invalidate(); return true
            }
        })

    private val tapGD = GestureDetectorCompat(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                transX -= dX; transY -= dY; rebuildMatrix(); invalidate(); return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x, e.y); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom(); return true
            }
        })

    // ── Public API ────────────────────────────────────────────────────────────
    fun setSongs(songs: List<Song>) {
        bubbles.clear(); selected = null
        if (songs.isEmpty() || width == 0) { post { setSongs(songs) }; return }
        val w = width.toFloat(); val h = height.toFloat()
        val rng = java.util.Random(42L)

        songs.groupBy { it.effectiveMood }.forEach { (mood, mSongs) ->
            val pos    = ZONES[mood] ?: Pair(0.5f, 0.5f)
            val cx     = pos.first * w
            val cy     = pos.second * h
            val spread = minOf(w, h) * 0.10f
            val color  = SongAdapter.MOOD_COLORS[mood] ?: 0xFF1DB954.toInt()
            val dim    = (color and 0x00FFFFFF) or 0xBB000000.toInt()

            mSongs.take(150).forEach { song ->
                val en = song.energyNorm.coerceIn(0f, 1f)
                val r  = 14f + en * 24f
                var bx: Float; var by: Float
                var att = 0
                do {
                    val angle = rng.nextFloat() * 2 * PI.toFloat()
                    val dist  = sqrt(rng.nextFloat()) * spread
                    bx = (cx + cos(angle) * dist).coerceIn(r + 4, w - r - 4)
                    by = (cy + sin(angle) * dist).coerceIn(r + 4, h - r - 4)
                    att++
                } while (att < 10 && overlaps(bx, by, r))
                bubbles += Bubble(song, bx, by, r, color, dim)
            }
        }
        resetZoom()
    }

    private val Song.energyNorm: Float get() {
        val e = energy; if (e <= 0f) return 0.3f
        return (ln(1f + e * 20f) / ln(1f + 7f)).coerceIn(0f, 1f)
    }

    private fun overlaps(x: Float, y: Float, r: Float) =
        bubbles.any { b -> hypot(b.x - x, b.y - y) < b.radius + r + 3f }

    fun resetZoom() { scale = 1f; transX = 0f; transY = 0f; rebuildMatrix(); invalidate() }

    private fun rebuildMatrix() {
        matrix.reset()
        matrix.postScale(scale, scale, width / 2f, height / 2f)
        matrix.postTranslate(transX, transY)
        matrix.invert(inv)
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.concat(matrix)

        // Zone labels
        ZONES.forEach { (mood, pos) ->
            val col = SongAdapter.MOOD_COLORS[mood] ?: 0xFF1DB954.toInt()
            zonePaint.color = (col and 0x00FFFFFF) or 0x25000000
            canvas.drawText(mood, pos.first * width, pos.second * height - 55f, zonePaint)
        }

        // Bubbles
        val am = activeMood
        bubbles.forEach { b ->
            val active = am == null || b.song.effectiveMood == am
            fillPaint.color = when {
                b === selected  -> b.color
                active          -> b.alphaDim
                else            -> (b.color and 0x00FFFFFF) or 0x28000000
            }
            canvas.drawCircle(b.x, b.y, b.radius, fillPaint)
            if (b === selected)
                canvas.drawCircle(b.x, b.y, b.radius + 4f, ringPaint)
        }
        canvas.restore()

        selected?.let { drawPanel(canvas, it) }
    }

    private fun drawPanel(canvas: Canvas, b: Bubble) {
        val pH = 170f; val pY = height - pH
        val rr = RectF(0f, pY - 12f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rr, 20f, 20f, panelBg)

        // Mood accent bar
        fillPaint.color = b.color
        canvas.drawRoundRect(RectF(0f, pY - 12f, 5f, height.toFloat()), 0f, 0f, fillPaint)

        val lx = 22f
        var ly = pY + 36f

        // Title
        titlePaint.textSize = 34f
        val title = b.song.title.let { if (it.length > 34) it.take(34) + "…" else it }
        canvas.drawText(title, lx, ly, titlePaint); ly += 38f

        // Artist
        val artist = b.song.artist.ifBlank { "Artista sconosciuto" }.let {
            if (it.length > 30) it.take(30) + "…" else it
        }
        val yr = if (b.song.year.isNotBlank()) "  ·  ${b.song.year}" else ""
        canvas.drawText("$artist$yr", lx, ly, subPaint); ly += 34f

        // Meta row
        val meta = buildString {
            append(b.song.effectiveMood)
            if (b.song.tempo > 0) append("  ·  ${b.song.tempo.toInt()} BPM")
            if (b.song.genreResolved.isNotBlank()) append("  ·  ${b.song.genreResolved}")
        }
        metaPaint.color = b.color
        canvas.drawText(meta, lx, ly, metaPaint)

        // Play circle
        val bR = 38f; val bX = width - bR - 20f; val bY = pY + pH / 2f - 5f
        canvas.drawCircle(bX, bY, bR, playBg)
        val path = Path()
        val ts = 14f
        path.moveTo(bX - ts * 0.5f, bY - ts * 0.85f)
        path.lineTo(bX + ts, bY)
        path.lineTo(bX - ts * 0.5f, bY + ts * 0.85f)
        path.close()
        canvas.drawPath(path, playFg)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleGD.onTouchEvent(e); tapGD.onTouchEvent(e); return true
    }

    private fun handleTap(sx: Float, sy: Float) {
        // Tap on play button?
        selected?.let { b ->
            val pH = 170f; val pY = height - pH
            val bR = 38f; val bX = width - bR - 20f; val bY = pY + pH / 2f - 5f
            if (hypot(sx - bX, sy - bY) <= bR + 14f) {
                onBubbleTap?.invoke(b.song); return
            }
            if (sy < pY - 12f) { selected = null; invalidate(); return }
        }

        // Map screen → canvas coordinates
        val pt = floatArrayOf(sx, sy); inv.mapPoints(pt)
        val hit = bubbles.asReversed().firstOrNull { b ->
            hypot(b.x - pt[0], b.y - pt[1]) <= b.radius + 12f
        }
        selected = hit; invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh); rebuildMatrix()
    }
}
