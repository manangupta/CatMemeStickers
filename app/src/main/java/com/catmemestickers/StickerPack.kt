package com.catmemestickers

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val stickers: List<Sticker>,
    val animatedStickerPack: Boolean = false
)

data class Sticker(
    val imageFileName: String,
    val emojis: List<String>
)
