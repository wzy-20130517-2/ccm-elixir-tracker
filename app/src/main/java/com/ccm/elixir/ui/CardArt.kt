package com.ccm.elixir.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 卡面加载 + 缓存。
 *
 * 204 张 302x363 的 PNG-8，全部解到内存约 90MB，太多。
 * 这里只缓存真正用到的，并降采样到显示尺寸附近。
 */
object CardArt {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun load(ctx: Context, name: String?, reqW: Int): ImageBitmap? {
        if (name.isNullOrBlank()) return null
        val key = "$name@$reqW"
        cache[key]?.let { return it }

        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            ctx.assets.open("cards/$name").use { BitmapFactory.decodeStream(it, null, opts) }
            val scale = (opts.outWidth / reqW.coerceAtLeast(1)).coerceAtLeast(1)

            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = ctx.assets.open("cards/$name").use { BitmapFactory.decodeStream(it, null, opts2) }
                ?: return null
            bmp.asImageBitmap().also {
                if (cache.size > 160) cache.clear()
                cache[key] = it
            }
        }.getOrNull()
    }

    /** 加载 assets 根目录下的独立图标（如圣水球） */
    fun loadIcon(ctx: Context, name: String, reqW: Int): ImageBitmap? {
        val key = "icon:$name@$reqW"
        cache[key]?.let { return it }
        return runCatching {
            val o1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, o1) }
            val scale = (o1.outWidth / reqW.coerceAtLeast(1)).coerceAtLeast(1)
            val o2 = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            bmp.asImageBitmap().also { cache[key] = it }
        }.getOrNull()
    }

    fun clear() = cache.clear()
}