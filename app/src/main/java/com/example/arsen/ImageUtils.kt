package com.example.arsen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

object ImageUtils {

    /**
     * Создает временный файл для съемки фото на камеру и возвращает его Uri через FileProvider
     */
    fun createTempPictureUri(context: Context): Uri {
        val storageDir = File(context.cacheDir, "test_photos")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val file = File(storageDir, "test_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Загружает изображение по Uri, корректирует ориентацию (Exif) и оптимизирует размер для четкого распознавания текста
     */
    fun loadAndCorrectBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Получаем размеры изображения без полной загрузки в память
            var inputStream: InputStream? = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Рассчитываем inSampleSize для масштабирования
            var sampleSize = 1
            val maxSide = max(origWidth, origHeight)
            while ((maxSide / (sampleSize * 2)) >= maxDimension) {
                sampleSize *= 2
            }

            // 3. Загружаем масштабированный битмап
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = contentResolver.openInputStream(uri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (decodedBitmap == null) return null

            // 4. Проверяем и исправляем поворот по Exif
            val orientation = getExifOrientation(context, uri)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (!matrix.isIdentity) {
                Bitmap.createBitmap(
                    decodedBitmap,
                    0,
                    0,
                    decodedBitmap.width,
                    decodedBitmap.height,
                    matrix,
                    true
                )
            } else {
                decodedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Сжимает Bitmap в JPEG и конвертирует в Base64 строку для передачи в AI API
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
