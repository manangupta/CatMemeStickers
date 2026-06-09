package com.catmemestickers

import android.content.Context
import org.json.JSONObject

object StickerPackLoader {

    fun getStickerPacks(context: Context): List<StickerPack> {
        val json = context.assets.open("contents.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val packsArray = root.getJSONArray("sticker_packs")
        val packs = mutableListOf<StickerPack>()

        for (i in 0 until packsArray.length()) {
            val p = packsArray.getJSONObject(i)
            val stickersArray = p.getJSONArray("stickers")
            val stickers = (0 until stickersArray.length()).map { j ->
                val s = stickersArray.getJSONObject(j)
                val emojis = s.getJSONArray("emojis").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                Sticker(
                    imageFileName = s.getString("image_file"),
                    emojis = emojis,
                    accessibilityText = s.optString("accessibility_text", "")
                )
            }

            packs.add(StickerPack(
                identifier = p.getString("identifier"),
                name = p.getString("name"),
                publisher = p.getString("publisher"),
                trayImageFile = p.getString("tray_image_file"),
                stickers = stickers,
                animatedStickerPack = p.optBoolean("animated_sticker_pack", false),
                avoidCache = p.optBoolean("avoid_cache", false),
                imageDataVersion = p.optString("image_data_version", "1"),
                androidPlayStoreLink = p.optString("android_play_store_link", ""),
                iosAppStoreLink = p.optString("ios_app_store_link", ""),
                publisherEmail = p.optString("publisher_email", ""),
                publisherWebsite = p.optString("publisher_website", ""),
                privacyPolicyWebsite = p.optString("privacy_policy_website", ""),
                licenseAgreementWebsite = p.optString("license_agreement_website", "")
            ))
        }
        return packs
    }
}
