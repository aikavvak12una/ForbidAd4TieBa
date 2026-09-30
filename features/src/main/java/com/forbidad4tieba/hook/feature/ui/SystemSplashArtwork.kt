package com.forbidad4tieba.hook.feature.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import java.util.zip.ZipFile

/** Decode once during hook installation, before any launch callback can run. */
internal object SystemSplashArtwork {
    const val ASSET_PATH = "assets/splash/tieba_brand_dark.png"

    fun load(moduleApk: String): Bitmap = ZipFile(moduleApk).use { apk ->
        val entry = checkNotNull(apk.getEntry(ASSET_PATH)) { "Dark splash artwork missing" }
        apk.getInputStream(entry).use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })) { "Dark splash artwork cannot be decoded" }
        }.apply {
            check(width == 1080 && height == 360) { "Invalid dark splash artwork dimensions" }
            // This module artwork uses a 360 x 120 dp canvas at xxhdpi.
            density = DisplayMetrics.DENSITY_XXHIGH
        }
    }
}
