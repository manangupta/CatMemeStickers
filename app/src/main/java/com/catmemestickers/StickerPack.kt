package com.catmemestickers

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val stickers: List<Sticker>,
    val animatedStickerPack: Boolean = false,
    val avoidCache: Boolean = false,
    val imageDataVersion: String = "1",
    val androidPlayStoreLink: String = "",
    val iosAppStoreLink: String = "",
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = ""
)

data class Sticker(
    val imageFileName: String,
    val emojis: List<String>,
    val accessibilityText: String = ""
)
