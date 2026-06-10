package com.catmemestickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.catmemestickers.StickerContentProvider"
        private const val TAG = "StickerCP"

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

        // These column names MUST match the official WhatsApp sticker sample exactly
        val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",  // NOT "avoid_cache"
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

        val matchCode = URI_MATCHER.match(uri)
        Log.d(TAG, "query() uri=$uri matchCode=$matchCode caller=${callingPackage}")

        return when (matchCode) {
            METADATA_CODE -> {
                val cursor = MatrixCursor(METADATA_COLUMNS)
                packs.forEach { cursor.addRow(it.toMetadataRow()) }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                Log.d(TAG, "query() METADATA returning ${cursor.count} packs")
                cursor
            }
            METADATA_SINGLE_CODE -> {
                val id = uri.lastPathSegment ?: return null
                val pack = packs.find { it.identifier == id } ?: run {
                    Log.e(TAG, "query() METADATA_SINGLE: pack '$id' not found")
                    return null
                }
                val cursor = MatrixCursor(METADATA_COLUMNS)
                cursor.addRow(pack.toMetadataRow())
                cursor.setNotificationUri(ctx.contentResolver, uri)
                Log.d(TAG, "query() METADATA_SINGLE returning pack '$id' name='${pack.name}' publisher='${pack.publisher}' tray='${pack.trayImageFile}' version='${pack.imageDataVersion}' avoidCache='${pack.avoidCache}' stickers=${pack.stickers.size}")
                cursor
            }
            STICKERS_CODE -> {
                val id = uri.lastPathSegment ?: return null
                val pack = packs.find { it.identifier == id } ?: run {
                    Log.e(TAG, "query() STICKERS: pack '$id' not found")
                    return null
                }
                val cursor = MatrixCursor(STICKER_COLUMNS)
                pack.stickers.forEach { sticker ->
                    cursor.addRow(arrayOf(
                        sticker.imageFileName,
                        sticker.emojis.joinToString(""),
                        sticker.accessibilityText
                    ))
                }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                Log.d(TAG, "query() STICKERS returning ${cursor.count} stickers for '$id'")
                cursor
            }
            else -> {
                Log.e(TAG, "query() NO MATCH for uri=$uri")
                null
            }
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val ctx = context ?: return null
        val segments = uri.pathSegments
        val fileName = segments.lastOrNull() ?: return null
        Log.d(TAG, "openAssetFile() uri=$uri segments=$segments fileName=$fileName")
        return try {
            val afd = ctx.assets.openFd("contents/$fileName")
            Log.d(TAG, "openAssetFile() SUCCESS: $fileName length=${afd.length}")
            afd
        } catch (e: Exception) {
            Log.e(TAG, "openAssetFile() FAILED for $fileName: ${e.message}")
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

    override fun getType(uri: Uri): String {
        val type = when (URI_MATCHER.match(uri)) {
            METADATA_CODE         -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            METADATA_SINGLE_CODE  -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
            STICKERS_CODE         -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            STICKERS_ASSET_CODE   -> "image/webp"
            else                  -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        Log.d(TAG, "getType() uri=$uri → $type")
        return type
    }
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
