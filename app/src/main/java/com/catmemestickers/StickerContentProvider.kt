package com.catmemestickers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.catmemestickers.StickerContentProvider"

        // Columns WhatsApp expects for the pack metadata query
        val METADATA_COLUMNS = arrayOf(
            "sticker_pack_id",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "publisher_email",
            "publisher_website",
            "privacy_policy_website",
            "license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        )

        // Columns WhatsApp expects for the sticker list query
        val STICKER_COLUMNS = arrayOf("sticker_file_name", "sticker_emoji")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val packs = StickerPackLoader.getStickerPacks(ctx)
        val pathSegments = uri.pathSegments

        return when {
            // WhatsApp queries: /metadata  → return all packs info
            pathSegments.size == 1 && pathSegments[0] == "metadata" -> {
                val cursor = MatrixCursor(METADATA_COLUMNS)
                for (pack in packs) {
                    cursor.addRow(arrayOf(
                        pack.identifier,
                        pack.name,
                        pack.publisher,
                        pack.trayImageFile,
                        "", "", "", "", "", "",
                        "1", "0",
                        if (pack.animatedStickerPack) "1" else "0"
                    ))
                }
                cursor
            }
            // WhatsApp queries: /{packId}/stickers  → return sticker list
            pathSegments.size == 2 && pathSegments[1] == "stickers" -> {
                val packId = pathSegments[0]
                val pack = packs.find { it.identifier == packId } ?: return null
                val cursor = MatrixCursor(STICKER_COLUMNS)
                for (sticker in pack.stickers) {
                    cursor.addRow(arrayOf(
                        sticker.imageFileName,
                        sticker.emojis.joinToString("")
                    ))
                }
                cursor
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val pathSegments = uri.pathSegments
        // URI: /{packId}/{fileName}
        if (pathSegments.size < 2) return null

        val packId = pathSegments[0]
        val fileName = pathSegments[1]

        // Copy asset to cache so we can return a file descriptor
        val cacheDir = File(ctx.cacheDir, "stickers/$packId")
        cacheDir.mkdirs()
        val outFile = File(cacheDir, fileName)
        if (!outFile.exists()) {
            ctx.assets.open("contents/$fileName").use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
        return ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/webp"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
