package com.catmemestickers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        // WhatsApp intent action for adding sticker packs
        private const val ADD_PACK_INTENT = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        private const val WHATSAPP_BUSINESS_ADD_PACK_INTENT =
            "com.whatsapp.w4b.intent.action.ENABLE_STICKER_PACK"
        private const val REQUEST_CODE_ADD_PACK = 200
    }

    private lateinit var stickerPack: StickerPack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val packs = StickerPackLoader.getStickerPacks(this)
        stickerPack = packs.first()

        // Pack info
        findViewById<TextView>(R.id.packName).text = stickerPack.name
        findViewById<TextView>(R.id.packPublisher).text = "by ${stickerPack.publisher}"
        findViewById<TextView>(R.id.stickerCount).text = "${stickerPack.stickers.size} stickers"

        // Sticker grid
        val recycler = findViewById<RecyclerView>(R.id.stickerGrid)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = StickerAdapter(this, stickerPack.stickers)

        // Add to WhatsApp button
        findViewById<Button>(R.id.addToWhatsApp).setOnClickListener {
            addStickerPackToWhatsApp()
        }
    }

    private fun addStickerPackToWhatsApp() {
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent().apply {
            action = ADD_PACK_INTENT
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

    private fun isWhatsAppInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.whatsapp", 0)
            true
        } catch (e: Exception) {
            try {
                packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ADD_PACK) {
            when (resultCode) {
                RESULT_OK -> Toast.makeText(
                    this, "✅ Sticker pack added to WhatsApp!", Toast.LENGTH_LONG
                ).show()
                RESULT_CANCELED -> {
                    val error = data?.getStringExtra("validation_error")
                    Toast.makeText(
                        this,
                        if (error != null) "Error: $error" else "Sticker pack not added",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
