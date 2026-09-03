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
    private const val MIME_TYPE = "text/plain"
    private val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/PrintAndDraw/"

    private val collection: Uri
        get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun ensureExists(context: Context): Uri? {
        val resolver = context.contentResolver
        findExisting(context)?.let { return it }

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
                writer.write("max_steps=$DEFAULT_MAX_STEPS\n")
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun readMaxSteps(context: Context): Long {
        val uri = ensureExists(context) ?: return DEFAULT_MAX_STEPS
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return DEFAULT_MAX_STEPS

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
                ?: DEFAULT_MAX_STEPS
        } catch (_: Exception) {
            DEFAULT_MAX_STEPS
        }
    }

    private fun findExisting(context: Context): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(FILE_NAME, relativePath)

        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }
}
