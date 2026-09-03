package com.pugplayzyt.printanddraw

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * File-backed developer overrides that deliberately have no in-app UI.
 *
 * The config is stored in the user-accessible Documents/PrintAndDraw folder
 * through MediaStore, so the app does not need broad storage permissions.
 */
object DeveloperConfig {
    const val DEFAULT_MAX_STEPS = 100_000L

    private const val FILE_NAME = "developer config.CFGHIJ"
    private const val MIME_TYPE = "application/x-cfghij"
    private val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/PrintAndDraw/"

    private val collection: Uri
        get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun ensureExists(context: Context): Uri? {
        val resolver = context.contentResolver
        val candidates = findCandidates(context)

        candidates.firstOrNull { it.name == FILE_NAME }?.let { canonical ->
            deleteDuplicateCandidates(context, candidates, canonical.uri)
            return canonical.uri
        }

        // Older builds declared this as text/plain. Some Android file providers
        // therefore renamed it to developer config.CFGHIJ.txt and then created
        // numbered copies on later launches. Preserve the newest valid value
        // while migrating those files to the real .CFGHIJ name.
        val preservedMaxSteps = candidates
            .sortedByDescending { it.modified }
            .mapNotNull { readMaxStepsFromUri(context, it.uri) }
            .firstOrNull()
            ?: DEFAULT_MAX_STEPS

        val uri = createCanonical(context, preservedMaxSteps) ?: run {
            // If migration cannot create the canonical file, keep using the
            // newest legacy file instead of creating even more duplicates.
            return candidates.maxByOrNull { it.modified }?.uri
        }

        deleteDuplicateCandidates(context, candidates, uri)
        return uri
    }

    fun readMaxSteps(context: Context): Long {
        val uri = ensureExists(context) ?: return DEFAULT_MAX_STEPS
        return readMaxStepsFromUri(context, uri) ?: DEFAULT_MAX_STEPS
    }

    private fun createCanonical(context: Context, maxSteps: Long): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }

        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                writer.write("# Print & Draw developer configuration\n")
                writer.write("# This file intentionally has no in-app editor.\n")
                writer.write("max_steps=$maxSteps\n")
            } ?: throw IllegalStateException("Could not open developer config for writing")
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun readMaxStepsFromUri(context: Context, uri: Uri): Long? {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return null

            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val equals = line.indexOf('=')
                    if (equals <= 0) return@mapNotNull null
                    val key = line.substring(0, equals).trim()
                    if (!key.equals("max_steps", ignoreCase = true)) return@mapNotNull null
                    line.substring(equals + 1)
                        .trim()
                        .replace("_", "")
                        .replace(",", "")
                        .toLongOrNull()
                }
                .firstOrNull { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun findCandidates(context: Context): List<Candidate> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(relativePath, "$FILE_NAME%")
        val result = mutableListOf<Candidate>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)
                if (!isKnownConfigName(name)) continue
                val id = cursor.getLong(idColumn)
                result += Candidate(
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    modified = cursor.getLong(modifiedColumn)
                )
            }
        }
        return result
    }

    private fun isKnownConfigName(name: String): Boolean {
        if (name == FILE_NAME || name == "$FILE_NAME.txt") return true
        return Regex("^${Regex.escape(FILE_NAME)} \\(\\d+\\)(?:\\.txt)?$").matches(name)
    }

    private fun deleteDuplicateCandidates(context: Context, candidates: List<Candidate>, keep: Uri) {
        candidates.forEach { candidate ->
            if (candidate.uri != keep) {
                try {
                    context.contentResolver.delete(candidate.uri, null, null)
                } catch (_: Exception) {
                    // Cleanup failure must never prevent the config from working.
                }
            }
        }
    }

    private data class Candidate(
        val uri: Uri,
        val name: String,
        val modified: Long
    )
}
