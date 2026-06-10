package com.catmemestickers

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ADD_PACK_INTENT        = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        private const val ADD_PACK_INTENT_WA_BIZ = "com.whatsapp.w4b.intent.action.ENABLE_STICKER_PACK"
        private const val REQUEST_CODE_ADD_PACK  = 200

        private const val WA_PACKAGE     = "com.whatsapp"
        private const val WA_BIZ_PACKAGE = "com.whatsapp.w4b"

        private const val WHITELIST_AUTHORITY     = "com.whatsapp.provider.sticker_whitelist_check"
        private const val WHITELIST_AUTHORITY_BIZ = "com.whatsapp.w4b.provider.sticker_whitelist_check"

        // TODO: swap back to real IDs once AdMob account is approved (24-48hrs)
        // Real banner:       ca-app-pub-6109655326397368/4467762577
        // Real interstitial: ca-app-pub-6109655326397368/8255798490
        private const val BANNER_AD_UNIT_ID       = "ca-app-pub-3940256099942544/6300978111"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        // Hidden dev menu: tap pack name this many times rapidly
        private const val DEV_TAP_COUNT = 5
        private const val DEV_TAP_WINDOW_MS = 2000L
    }

    private lateinit var stickerPack: StickerPack
    private lateinit var addButton: Button
    private var interstitialAd: InterstitialAd? = null
    private var pendingAction: (() -> Unit)? = null

    // Dev mode: bypass whitelist check so Add button always shows
    private var devModeForceAdd = false

    // Hidden tap counter
    private var devTapCount = 0
    private var devTapFirstTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register physical test device so ads show during development
        MobileAds.setRequestConfiguration(
            com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("3A94C020335D047171743DED3457EC78"))
                .build()
        )
        MobileAds.initialize(this) {
            // SDK ready — load ads now (adUnitId already set in XML)
            findViewById<AdView>(R.id.bannerAd).loadAd(AdRequest.Builder().build())
            loadInterstitial()
        }

        val packs = StickerPackLoader.getStickerPacks(this)
        stickerPack = packs.first()

        findViewById<TextView>(R.id.packName).text = stickerPack.name
        findViewById<TextView>(R.id.packPublisher).text = "by ${stickerPack.publisher}"
        findViewById<TextView>(R.id.stickerCount).text = "✦  ${stickerPack.stickers.size} STICKERS"

        // 5 rapid taps on the pack name → hidden dev menu
        findViewById<TextView>(R.id.packName).setOnClickListener { onDevTap() }

        // Load first sticker into pack icon and hero — off main thread
        stickerPack.stickers.firstOrNull()?.let { first ->
            Thread {
                try {
                    assets.open("contents/${first.imageFileName}").use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        Handler(Looper.getMainLooper()).post {
                            if (!isDestroyed) {
                                findViewById<ImageView>(R.id.packIconImage).setImageBitmap(bmp)
                                findViewById<ImageView>(R.id.heroSticker).setImageBitmap(bmp)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.start()
        }

        val recycler = findViewById<RecyclerView>(R.id.stickerGrid)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = StickerAdapter(this, stickerPack.stickers)

        addButton = findViewById(R.id.addToWhatsApp)
        addButton.setOnClickListener { addStickerPackToWhatsApp() }

        findViewById<Button>(R.id.shareApk).setOnClickListener { shareApk() }

    }

    override fun onResume() {
        super.onResume()
        checkWhitelistAsync()
    }

    // -------------------------------------------------------------------------
    // Hidden dev menu — 5 taps on pack name within 2 seconds
    // -------------------------------------------------------------------------

    private fun onDevTap() {
        val now = System.currentTimeMillis()
        if (now - devTapFirstTime > DEV_TAP_WINDOW_MS) {
            // Window expired — reset
            devTapCount = 1
            devTapFirstTime = now
        } else {
            devTapCount++
        }

        if (devTapCount >= DEV_TAP_COUNT) {
            devTapCount = 0
            showDevMenu()
        }
    }

    private fun showDevMenu() {
        val forceLabel = if (devModeForceAdd) "✅ Force Add: ON  (tap to disable)"
                         else                 "Force Add: OFF (tap to enable)"

        AlertDialog.Builder(this)
            .setTitle("🛠  Dev Options")
            .setMessage(
                "Force Add mode bypasses the WhatsApp whitelist check so " +
                "the Add button always appears — useful for testing ads.\n\n" +
                "To actually remove the pack, open WhatsApp → Stickers → " +
                "Cat Stickers → Remove."
            )
            .setPositiveButton(forceLabel) { _, _ ->
                devModeForceAdd = !devModeForceAdd
                val status = if (devModeForceAdd) "ON" else "OFF"
                Toast.makeText(this, "Force Add: $status", Toast.LENGTH_SHORT).show()
                checkWhitelistAsync()   // refresh button state immediately
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // AdMob
    // -------------------------------------------------------------------------

    private fun loadInterstitial() {
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial()
                            pendingAction?.invoke()
                            pendingAction = null
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    pendingAction?.invoke()
                    pendingAction = null
                }
            }
        )
    }

    private fun showInterstitialThen(action: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            pendingAction = action
            ad.show(this)
        } else {
            action()
        }
    }

    // -------------------------------------------------------------------------
    // Whitelist check
    // -------------------------------------------------------------------------

    private fun checkWhitelistAsync() {
        if (devModeForceAdd) {
            // Dev mode: always show Add button regardless of whitelist
            addButton.text = "➕  Add to WhatsApp"
            addButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF25D366.toInt())
            addButton.isEnabled = true
            return
        }

        Thread {
            val waInstalled  = isAppInstalled(WA_PACKAGE)
            val bizInstalled = isAppInstalled(WA_BIZ_PACKAGE)
            val waAdded  = if (waInstalled)  isPackWhitelisted(WHITELIST_AUTHORITY)     else true
            val bizAdded = if (bizInstalled) isPackWhitelisted(WHITELIST_AUTHORITY_BIZ) else true
            val added = waAdded && bizAdded
            Handler(Looper.getMainLooper()).post {
                if (added) {
                    addButton.text = "✅  Added to WhatsApp"
                    addButton.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
                    addButton.isEnabled = false
                } else {
                    addButton.text = "➕  Add to WhatsApp"
                    addButton.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(0xFF25D366.toInt())
                    addButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun isPackWhitelisted(whitelistAuthority: String): Boolean {
        return try {
            val uri = android.net.Uri.Builder()
                .scheme("content").authority(whitelistAuthority)
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", StickerContentProvider.AUTHORITY)
                .appendQueryParameter("identifier", stickerPack.identifier)
                .build()
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use { it.moveToFirst() && it.getInt(0) == 1 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // WhatsApp
    // -------------------------------------------------------------------------

    private fun addStickerPackToWhatsApp() {
        val waInstalled  = isAppInstalled(WA_PACKAGE)
        val bizInstalled = isAppInstalled(WA_BIZ_PACKAGE)

        when {
            !waInstalled && !bizInstalled -> {
                Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_LONG).show()
            }
            waInstalled && bizInstalled -> {
                AlertDialog.Builder(this)
                    .setTitle("Add to WhatsApp")
                    .setItems(arrayOf("WhatsApp", "WhatsApp Business")) { _, which ->
                        val action = if (which == 0)
                            { -> sendAddIntent(ADD_PACK_INTENT, WA_PACKAGE) }
                        else
                            { -> sendAddIntent(ADD_PACK_INTENT_WA_BIZ, WA_BIZ_PACKAGE) }
                        showInterstitialThen(action)
                    }
                    .show()
            }
            waInstalled  -> showInterstitialThen { sendAddIntent(ADD_PACK_INTENT, WA_PACKAGE) }
            else         -> showInterstitialThen { sendAddIntent(ADD_PACK_INTENT_WA_BIZ, WA_BIZ_PACKAGE) }
        }
    }

    private fun sendAddIntent(action: String, pkg: String) {
        val intent = Intent(action).apply {
            setPackage(pkg)
            putExtra("sticker_pack_id", stickerPack.identifier)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", stickerPack.name)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_ADD_PACK)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open WhatsApp", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    private fun shareApk() {
        val apkFile = File(applicationInfo.sourceDir)
        val uri = FileProvider.getUriForFile(this, "com.catmemestickers.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Cat Stickers for WA 🐱")
            putExtra(Intent.EXTRA_TEXT, "Hey! Check out this fun cat sticker pack for WhatsApp 🐾")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share App via"))
    }

    // -------------------------------------------------------------------------
    // Utils
    // -------------------------------------------------------------------------

    private fun isAppInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE_ADD_PACK) return
        when (resultCode) {
            RESULT_OK -> {
                Toast.makeText(this, "🎉 Sticker pack added to WhatsApp!", Toast.LENGTH_LONG).show()
                devModeForceAdd = false   // turn off dev mode after successful add
                checkWhitelistAsync()
            }
            RESULT_CANCELED -> {
                val error = data?.getStringExtra("validation_error")
                val message = when {
                    error.isNullOrEmpty() -> "Sticker pack was not added."
                    error.contains("size", ignoreCase = true) ->
                        "A sticker file is too large (max 100KB each)."
                    error.contains("count", ignoreCase = true) ->
                        "Pack must have 3–30 stickers."
                    error.contains("whitelist", ignoreCase = true) ->
                        "Pack is already added to WhatsApp."
                    else -> "Error: $error"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
