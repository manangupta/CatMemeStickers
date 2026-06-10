package com.catmemestickers

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class StickerAdapter(
    private val context: Context,
    private val stickers: List<Sticker>
) : RecyclerView.Adapter<StickerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.stickerImage)
        var loadTag: Int = -1  // tracks which position this holder is loading for
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sticker = stickers[position]
        holder.loadTag = position
        holder.imageView.setImageBitmap(null)  // clear previous image immediately

        Thread {
            try {
                context.assets.open("contents/${sticker.imageFileName}").use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    mainHandler.post {
                        // Only set if this holder hasn't been recycled to another position
                        if (holder.loadTag == position) {
                            holder.imageView.setImageBitmap(bmp)
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (holder.loadTag == position) {
                        holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
        }.start()
    }

    override fun getItemCount() = stickers.size
}
