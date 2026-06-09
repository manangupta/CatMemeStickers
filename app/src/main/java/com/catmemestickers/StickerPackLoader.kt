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
            val packObj = packsArray.getJSONObject(i)
            val stickersArray = packObj.getJSONArray("stickers")
            val stickers = mutableListOf<Sticker>()

            for (j in 0 until stickersArray.length()) {
                val s = stickersArray.getJSONObject(j)
                val emojisArray = s.getJSONArray("emojis")
                val emojis = (0 until emojisArray.length()).map { emojisArray.getString(it) }
                stickers.add(Sticker(s.getString("image_file"), emojis))
            }

            packs.add(
                StickerPack(
                    identifier = packObj.getString("identifier"),
                    name = packObj.getString("name"),
                    publisher = packObj.getString("publisher"),
                    trayImageFile = packObj.getString("tray_image_file"),
                    stickers = stickers,
                    animatedStickerPack = packObj.optBoolean("animated_sticker_pack", false)
                )
            )
        }
        return packs
    }
}
