package com.catmemestickers

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val stickers = assets.list("contents")
            ?.filter { it.endsWith(".webp") && it != "tray_icon.webp" }
            ?: emptyList()

        if (stickers.isNotEmpty()) {
            val random = stickers.random()
            assets.open("contents/$random").use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                findViewById<ImageView>(R.id.splashSticker).setImageBitmap(bmp)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 3000)
    }
}
