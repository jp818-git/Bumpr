package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.model.Track
import java.io.File

object MediaStoreScanner {

    /**
     * Discovers all unique folder names containing audio on the device without loading full track metadata.
     */
    fun getDiscoveredAudioFolders(context: Context): List<String> {
        val folders = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    if (dataCol >= 0) {
                        val path = cursor.getString(dataCol)
                        if (!path.isNullOrBlank()) {
                            val name = File(path).parentFile?.name
                            if (!name.isNullOrBlank()) {
                                folders.add(name)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return folders.sorted()
    }

    /**
     * Scans local audio strictly within the user's selected folders.
     * If [selectedFolders] is empty, returns empty list.
     */
    fun scanLocalAudio(context: Context, selectedFolders: Set<String>): List<Track> {
        if (selectedFolders.isEmpty()) return emptyList()

        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val dataPath = cursor.getString(dataColumn) ?: ""
                    val folderName = if (dataPath.isNotBlank()) {
                        File(dataPath).parentFile?.name ?: "Music"
                    } else "Music"

                    // ONLY scan and accept tracks that belong to selected folders
                    if (!selectedFolders.contains(folderName)) {
                        continue
                    }

                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    // Check for sidecar .lrc file next to the audio file
                    var hasSidecarLrc = false
                    if (dataPath.isNotBlank()) {
                        val audioFile = File(dataPath)
                        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                        hasSidecarLrc = lrcFile.exists() && lrcFile.canRead()
                    }

                    tracks.add(
                        Track(
                            id = "device_$id",
                            title = title,
                            artist = if (artist.contains("<unknown>")) "Unknown Artist" else artist,
                            album = if (album.contains("<unknown>")) "Unknown Album" else album,
                            durationMs = duration,
                            contentUri = contentUri,
                            hasSyncedLyrics = hasSidecarLrc,
                            folderPath = folderName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tracks
    }

    /**
     * Attempts to read a sidecar LRC file if the track originated from local storage.
     */
    fun readSidecarLrc(context: Context, track: Track): String? {
        if (!track.id.startsWith("device_")) return null
        val rawId = track.id.removePrefix("device_").toLongOrNull() ?: return null

        try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rawId)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    if (!path.isNullOrBlank()) {
                        val audioFile = File(path)
                        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                        if (lrcFile.exists() && lrcFile.canRead()) {
                            return lrcFile.readText(Charsets.UTF_8)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Scans audio files from a user-selected folder Uri obtained via OpenDocumentTree.
     */
    fun scanFolderUri(context: Context, treeUri: Uri, folderName: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol)
                    val displayName = cursor.getString(nameCol) ?: "Audio"
                    val mime = cursor.getString(mimeCol) ?: ""

                    val isAudio = mime.startsWith("audio/") ||
                            displayName.endsWith(".mp3", true) ||
                            displayName.endsWith(".flac", true) ||
                            displayName.endsWith(".wav", true) ||
                            displayName.endsWith(".m4a", true) ||
                            displayName.endsWith(".ogg", true)

                    if (isAudio) {
                        val fileDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val title = displayName.substringBeforeLast('.')
                        tracks.add(
                            Track(
                                id = "saf_$childDocId",
                                title = title,
                                artist = "Folder Import",
                                album = folderName,
                                durationMs = 180000L,
                                contentUri = fileDocUri.toString(),
                                folderPath = folderName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }
}
