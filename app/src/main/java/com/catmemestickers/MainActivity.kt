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

        // Test ad unit IDs — replace with real ones before release
        private const val BANNER_AD_UNIT_ID       = "ca-app-pub-3940256099942544/6300978111"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }

    private lateinit var stickerPack: StickerPack
    private lateinit var addButton: Button
    private var interstitialAd: InterstitialAd? = null

    // Pending WhatsApp action — stored while interstitial is showing
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialise AdMob (non-blocking)
        MobileAds.initialize(this)

        val packs = StickerPackLoader.getStickerPacks(this)
        stickerPack = packs.first()

        findViewById<TextView>(R.id.packName).text = stickerPack.name
        findViewById<TextView>(R.id.packPublisher).text = "by ${stickerPack.publisher}"
        findViewById<TextView>(R.id.stickerCount).text = "✦  ${stickerPack.stickers.size} STICKERS"

        // Load first sticker into pack icon and hero — off main thread to avoid jank
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

        // Load banner ad
        val adView = findViewById<AdView>(R.id.bannerAd)
        adView.loadAd(AdRequest.Builder().build())

        // Pre-load interstitial so it's ready when user taps Add
        loadInterstitial()
    }

    override fun onResume() {
        super.onResume()
        checkWhitelistAsync()
    }

    // --- AdMob ---

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
                            loadInterstitial()          // pre-load next one
                            pendingAction?.invoke()     // proceed with WhatsApp
                            pendingAction = null
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    // If ad fails, just run the pending action directly
                    pendingAction?.invoke()
                    pendingAction = null
                }
            }
        )
    }

    /** Show interstitial then run [action], or run [action] directly if no ad ready. */
    private fun showInterstitialThen(action: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            pendingAction = action
            ad.show(this)
        } else {
            action()
        }
    }

    // --- Whitelist check ---

    private fun checkWhitelistAsync() {
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

    // --- WhatsApp ---

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

    // --- Share ---

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

    // --- Utils ---

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
