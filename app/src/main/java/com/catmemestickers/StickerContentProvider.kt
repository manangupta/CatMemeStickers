package com.catmemestickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.catmemestickers.StickerContentProvider"

        private const val METADATA_CODE        = 1
        private const val METADATA_SINGLE_CODE = 2
        private const val STICKERS_CODE        = 3
        private const val STICKERS_ASSET_CODE  = 4

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata",           METADATA_CODE)
            addURI(AUTHORITY, "metadata/*",         METADATA_SINGLE_CODE)
            addURI(AUTHORITY, "stickers/*",         STICKERS_CODE)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET_CODE)
        }

        val METADATA_COLUMNS = arrayOf(
            "sticker_pack_id", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link",
            "ios_app_download_link",  // correct column name per WhatsApp spec
            "publisher_email", "publisher_website", "privacy_policy_website",
            "license_agreement_website", "image_data_version", "avoid_cache",
            "animated_sticker_pack"
        )

        val STICKER_COLUMNS = arrayOf(
            "sticker_file_name", "sticker_emoji", "sticker_accessibility_text"
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val packs = StickerPackLoader.getStickerPacks(ctx)

        return when (URI_MATCHER.match(uri)) {
            METADATA_CODE -> {
                val cursor = MatrixCursor(METADATA_COLUMNS)
                packs.forEach { cursor.addRow(it.toMetadataRow()) }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }
            METADATA_SINGLE_CODE -> {
                val id = uri.lastPathSegment ?: return null
                val pack = packs.find { it.identifier == id } ?: return null
                val cursor = MatrixCursor(METADATA_COLUMNS)
                cursor.addRow(pack.toMetadataRow())
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }
            STICKERS_CODE -> {
                val id = uri.lastPathSegment ?: return null
                val pack = packs.find { it.identifier == id } ?: return null
                val cursor = MatrixCursor(STICKER_COLUMNS)
                pack.stickers.forEach { sticker ->
                    cursor.addRow(arrayOf(
                        sticker.imageFileName,
                        sticker.emojis.joinToString(""),
                        sticker.accessibilityText
                    ))
                }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val ctx = context ?: return null
        val fileName = uri.pathSegments.lastOrNull() ?: return null
        return try {
            ctx.assets.openFd("contents/$fileName")
        } catch (e: Exception) {
            null
        }
    }

    private fun StickerPack.toMetadataRow() = arrayOf(
        identifier, name, publisher, trayImageFile,
        androidPlayStoreLink, iosAppStoreLink,
        publisherEmail, publisherWebsite,
        privacyPolicyWebsite, licenseAgreementWebsite,
        imageDataVersion,
        if (avoidCache) "1" else "0",
        if (animatedStickerPack) "1" else "0"
    )

    override fun getType(uri: Uri): String = "image/webp"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
