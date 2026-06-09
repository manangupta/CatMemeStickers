package com.catmemestickers

import android.content.Context
import android.graphics.BitmapFactory
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sticker = stickers[position]
        try {
            context.assets.open("contents/${sticker.imageFileName}").use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                holder.imageView.setImageBitmap(bmp)
            }
        } catch (e: Exception) {
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun getItemCount() = stickers.size
}
