package com.example.alarmevaso

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.io.File

object ToiletDetector {
    fun recognizes(context: Context, file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0)
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1280) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val raw = requireNotNull(BitmapFactory.decodeFile(file.path, options))
        val rotation = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            3, 4 -> 180f
            5, 6 -> 90f
            7, 8 -> 270f
            else -> 0f
        }
        val bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
            Matrix().apply { postRotate(rotation) }, true)
        try {
            val settings = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                .setScoreThreshold(0.50f)
                .setCategoryAllowlist(listOf("toilet"))
                .build()
            ObjectDetector.createFromOptions(context, settings).use { detector ->
                val image = BitmapImageBuilder(bitmap).build()
                try {
                    return detector.detect(image).detections().any { detection ->
                        val box = detection.boundingBox()
                        box.width() * box.height() >= 0.08f * bitmap.width * bitmap.height &&
                            detection.categories().any { it.categoryName() == "toilet" }
                    }
                } finally {
                    image.close()
                }
            }
        } finally {
            bitmap.recycle()
            if (raw !== bitmap) raw.recycle()
        }
    }
}
