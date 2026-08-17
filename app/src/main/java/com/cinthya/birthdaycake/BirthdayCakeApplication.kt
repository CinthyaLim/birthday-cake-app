package com.cinthya.birthdaycake

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder

/**
 * Supplies the app-wide Coil loader. Without the animated decoder the cat GIFs
 * would render as a frozen first frame.
 */
class BirthdayCakeApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // ImageDecoder based, so it needs API 28 - which is our minSdk.
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
