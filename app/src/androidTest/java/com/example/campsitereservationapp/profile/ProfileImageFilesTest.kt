package com.example.campsitereservationapp.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileImageFilesTest {
    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun largePhotosAreDecodedWithinTheAvatarMemoryLimit() {
        val bitmap = decodeProfileImage(jpeg(4096, 2048))
        try {
            assertTrue(bitmap.width <= 1024)
            assertTrue(bitmap.height <= 1024)
            assertEquals(2, bitmap.width / bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun cameraOrientationIsApplied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("profile-orientation", ".jpg", context.cacheDir)
        try {
            file.writeBytes(jpeg(80, 40))
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val bitmap = decodeProfileImage(file.readBytes())
            try {
                assertEquals(40, bitmap.width)
                assertEquals(80, bitmap.height)
            } finally {
                bitmap.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun nonImagesAndOversizedSourcesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { decodeProfileImage("not an image".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            readProfileImageBytes(ByteArrayInputStream(ByteArray(20 * 1024 * 1024 + 1)))
        }
    }

    @Test
    fun validSourcesCanBeReadAndDecoded() {
        val bytes = jpeg(64, 64)
        val read = readProfileImageBytes(ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, read)
        val image = BitmapFactory.decodeByteArray(read, 0, read.size)
        assertNotNull(image)
        image.recycle()
    }
}
