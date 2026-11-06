package com.example.model

enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AppTheme(val displayName: String) {
    NEON_CYAN("Neon Waves"),
    SOUNDWAVE("Soundwave Obsidian"),
    SUNSET("Sunset Retro"),
    CYBERPUNK("Cyberpunk Lime"),
    AMETHYST("Midnight Amethyst"),
    TURQUOISE("Turquoise Classic"),
    AMOLED("Pure AMOLED"),
    GLASS("Glassmorphism")
}

enum class BackgroundStyle(val displayName: String) {
    MUSICAL_STAVE("Musical Notes Wave"),
    SOUNDWAVE("Audio Waveform"),
    AURORA_GRADIENT("Aurora Glow"),
    MINIMAL("Minimal Flat")
}

enum class NoLyricsDisplay(val displayName: String) {
    ALBUM_ART("Album art"),
    ANIMATED_WAVE("Animated wave")
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String? = null,
    val albumArtResId: Int? = null,
    val hasSyncedLyrics: Boolean = false,
    val genre: String = "Electronic",
    val folderPath: String = "Music"
)

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsData(
    val title: String = "",
    val artist: String = "",
    val offsetMs: Long = 0L,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = true,
    val rawText: String = ""
)

data class EqPreset(
    val name: String,
    val bandLevels: List<Int>,
    val bassPercent: Int = 0,
    val trebleDb: Int = 0
)

data class EqState(
    val isEnabled: Boolean = true,
    val currentPreset: String = "Pop",
    val bandCount: Int = 5,
    val bandFrequencies: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val bandLevels: List<Int> = listOf(2, 4, -1, 3, 5),
    val bassPercent: Int = 40,
    val trebleDb: Int = 3
)
