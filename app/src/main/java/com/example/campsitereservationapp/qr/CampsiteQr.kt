package com.example.campsitereservationapp.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

/**
 * Handles creating QR codes for campsites and reading campsite IDs from them.
 */
object CampsiteQr {
    private const val prefix = "pinedraw://campsite/"
    /**
     * Creates the text that will be stored inside a campsite's QR code.
     *
     * @param campsiteId The ID of the campsite.
     * @return The QR code text containing the campsite ID.
     */
    fun createQrText(campsiteId: String): String {
        return prefix + campsiteId
    }
    /**
     * Gets the campsite ID from a scanned QR code.
     *
     * @param qrText The text obtained from scanning a QR code.
     * @return The campsite ID, or null if the QR code is invalid.
     */
    fun getCampsiteId(qrText: String): String? {
        // Check if the QR code belongs to the app
        if (!qrText.startsWith(prefix)) {
            return null
        }
        // Remove prefix
        val campsiteId = qrText.removePrefix(prefix)
        // Make sure campsite ID is not empty
        if (campsiteId.isBlank()) {
            return null
        }
        // Checking invalid symbols
        if (campsiteId.contains("/") ||
            campsiteId.contains("?") ||
            campsiteId.contains("#") ||
            campsiteId != campsiteId.trim()
        ) {
            return null
        }

        return campsiteId
    }
    /**
     * Generates a QR code image for a campsite.
     *
     * @param campsiteId The ID of the campsite.
     * @return A bitmap containing the generated QR code.
     */
    fun generateQrCode(campsiteId: String): Bitmap {

        val qrText = createQrText(campsiteId)
        // Generate a 500x500 QR code
        val writer = MultiFormatWriter()
        val matrix = writer.encode(
            qrText,
            BarcodeFormat.QR_CODE,
            500,
            500
        )
        // Empty bitmpa
        val bitmap = Bitmap.createBitmap(
            500,
            500,
            Bitmap.Config.ARGB_8888
        )
        // Fill bitmap using QR code pattern
        for (x in 0 until 500) {
            for (y in 0 until 500) {

                if (matrix[x, y]) {
                    bitmap.setPixel(x, y, Color.BLACK)
                } else {
                    bitmap.setPixel(x, y, Color.WHITE)
                }
            }
        }

        return bitmap
    }
}