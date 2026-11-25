package com.example.campsitereservationapp.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.campsitereservationapp.service.MAX_PROFILE_IMAGE_BYTES
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_SOURCE_BYTES = 20 * 1024 * 1024

/** Reads only the selected local image; large sources are rejected before decoding. */
internal fun readProfileImage(context: Context, uri: Uri): ByteArray {
    require(uri.scheme == "content") { "Choose an image from your device." }
    return context.contentResolver.openInputStream(uri)?.use(::readProfileImageBytes)
        ?: error("This image is unavailable. Please select it again.")
}

internal fun readProfileImageBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        require(output.size() + count <= MAX_SOURCE_BYTES) { "Choose an image smaller than 20 MB." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** Bounds decoded memory and applies camera orientation before previewing or uploading. */
internal fun decodeProfileImage(bytes: ByteArray): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0) { "This file could not be read as an image." }
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight).toLong() > 1024L * options.inSampleSize) {
        options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("This file could not be read as an image.")
    val orientation = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) return decoded
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
        if (it !== decoded) decoded.recycle()
    }
}

/** Uploads use a compact JPEG without copying the source image's location metadata. */
internal fun prepareProfileImage(context: Context, uri: Uri): ByteArray {
    val bitmap = decodeProfileImage(readProfileImage(context, uri))
    try {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "Could not prepare this image." }
        return output.toByteArray().also {
            require(it.size <= MAX_PROFILE_IMAGE_BYTES) { "Choose a smaller image." }
        }
    } finally {
        bitmap.recycle()
    }
}
