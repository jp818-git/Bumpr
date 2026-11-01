package com.example.data

import com.example.R
import com.example.model.EqPreset
import com.example.model.Track

object DemoData {

    val demoTracks = emptyList<Track>()

    val demoLyrics = emptyMap<String, String>()

    val eqPresets = listOf(
        EqPreset("Flat", listOf(0, 0, 0, 0, 0), bassPercent = 0, trebleDb = 0),
        EqPreset("Pop", listOf(1, 3, 4, 3, 2), bassPercent = 35, trebleDb = 2),
        EqPreset("Rock", listOf(4, 2, -1, 3, 5), bassPercent = 50, trebleDb = 4),
        EqPreset("Jazz", listOf(3, 2, 0, 2, 4), bassPercent = 25, trebleDb = 3),
        EqPreset("Classical", listOf(4, 3, 1, 3, 4), bassPercent = 20, trebleDb = 2),
        EqPreset("Vocal", listOf(-2, 1, 4, 3, -1), bassPercent = 10, trebleDb = 1),
        EqPreset("Bass Boost", listOf(6, 4, 1, 0, 1), bassPercent = 80, trebleDb = 0),
        EqPreset("Custom", listOf(2, 3, 0, 2, 3), bassPercent = 40, trebleDb = 3)
    )
}
