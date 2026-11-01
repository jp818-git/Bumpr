package com.example.lyrics

import com.example.model.LyricLine
import com.example.model.LyricsData
import java.util.regex.Pattern

object LrcParser {
    // Matches [mm:ss.xx] or [mm:ss.xxx]
    private val TIME_TAG_REGEX = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val TAG_OFFSET_REGEX = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)
    private val TAG_TITLE_REGEX = Pattern.compile("\\[ti:\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE)
    private val TAG_ARTIST_REGEX = Pattern.compile("\\[ar:\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE)

    /**
     * Parses LRC or plain text into a structured LyricsData object.
     */
    fun parse(rawContent: String): LyricsData {
        if (rawContent.isBlank()) {
            return LyricsData(rawText = "")
        }

        var title = ""
        var artist = ""
        var offsetMs = 0L
        val linesWithTimestamps = mutableListOf<LyricLine>()

        val rawLines = rawContent.lines()
        var hasValidTimeTag = false

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Parse metadata tags
            val offsetMatcher = TAG_OFFSET_REGEX.matcher(trimmed)
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                continue
            }

            val titleMatcher = TAG_TITLE_REGEX.matcher(trimmed)
            if (titleMatcher.find()) {
                title = titleMatcher.group(1)?.trim() ?: ""
                continue
            }

            val artistMatcher = TAG_ARTIST_REGEX.matcher(trimmed)
            if (artistMatcher.find()) {
                artist = artistMatcher.group(1)?.trim() ?: ""
                continue
            }

            // Extract all time tags on this line (supports multiple timestamps like [01:05.10][02:10.50]Chorus)
            val matcher = TIME_TAG_REGEX.matcher(trimmed)
            val timestampsOnLine = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val msString = matcher.group(3) ?: "0"
                val milliseconds = when (msString.length) {
                    1 -> (msString.toLongOrNull() ?: 0L) * 100
                    2 -> (msString.toLongOrNull() ?: 0L) * 10
                    else -> msString.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = (minutes * 60 + seconds) * 1000 + milliseconds
                timestampsOnLine.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestampsOnLine.isNotEmpty()) {
                hasValidTimeTag = true
                val text = trimmed.substring(lastMatchEnd).trim()
                for (time in timestampsOnLine) {
                    linesWithTimestamps.add(LyricLine(time, text))
                }
            }
        }

        return if (hasValidTimeTag) {
            // Sort lines chronologically
            val sortedLines = linesWithTimestamps.sortedBy { it.timestampMs }
            LyricsData(
                title = title,
                artist = artist,
                offsetMs = offsetMs,
                lines = sortedLines,
                isSynced = true,
                rawText = rawContent
            )
        } else {
            // Plain text (unsynced) lyrics
            val plainLines = rawLines
                .filter { it.isNotBlank() && !it.startsWith("[") }
                .mapIndexed { index, text -> LyricLine(index * 4000L, text.trim()) }
            LyricsData(
                title = title,
                artist = artist,
                offsetMs = 0L,
                lines = plainLines,
                isSynced = false,
                rawText = rawContent
            )
        }
    }

    /**
     * Efficiently locates the active lyric line using binary search.
     * Guaranteed < 50ms highlight latency per PRD.
     */
    fun findActiveLineIndex(
        lines: List<LyricLine>,
        currentPositionMs: Long,
        globalOffsetMs: Long = 0L
    ): Int {
        if (lines.isEmpty()) return -1

        val effectivePosition = currentPositionMs + globalOffsetMs
        if (effectivePosition < lines.first().timestampMs) {
            return 0
        }
        if (effectivePosition >= lines.last().timestampMs) {
            return lines.lastIndex
        }

        var low = 0
        var high = lines.lastIndex
        var bestIndex = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = lines[mid].timestampMs

            if (midTime <= effectivePosition) {
                bestIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return bestIndex
    }

    /**
     * Serializes LyricLine list back to valid .lrc string format.
     */
    fun formatToLrc(
        lines: List<LyricLine>,
        title: String = "",
        artist: String = "",
        offsetMs: Long = 0L
    ): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) sb.append("[ti:").append(title).append("]\n")
        if (artist.isNotBlank()) sb.append("[ar:").append(artist).append("]\n")
        if (offsetMs != 0L) sb.append("[offset:").append(offsetMs).append("]\n")

        for (line in lines) {
            val totalSeconds = line.timestampMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hundredths = (line.timestampMs % 1000) / 10
            val timeTag = String.format("[%02d:%02d.%02d]", minutes, seconds, hundredths)
            sb.append(timeTag).append(" ").append(line.text).append("\n")
        }

        return sb.toString()
    }
}
