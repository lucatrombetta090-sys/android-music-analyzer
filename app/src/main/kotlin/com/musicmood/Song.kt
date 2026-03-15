package com.musicmood

data class Song(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val duration: Float,
    val albumId: Long = 0L,
    var tempo: Float = 0f,
    var energy: Float = 0f,
    var mood: String = "",
    var genreResolved: String = "",
    var analyzed: Boolean = false,
    var moodOverride: String = ""   // mood impostato manualmente dall'utente
) {
    /** Mood effettivo: override manuale ha precedenza sull'algoritmo */
    val effectiveMood: String get() = if (moodOverride.isNotBlank()) moodOverride else mood
    val hasManualMood: Boolean get() = moodOverride.isNotBlank()
}
