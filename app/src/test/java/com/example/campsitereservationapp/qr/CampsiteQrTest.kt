package com.example.campsitereservationapp.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the functions used to create and read campsite QR codes.
 */
class CampsiteQrTest {

    /**
     * Checks that the QR text contains the correct campsite ID.
     */
    @Test
    fun testCreateQrText() {

        val campsiteId = "123"

        val result = CampsiteQr.createQrText(campsiteId)

        assertEquals("pinedraw://campsite/123", result)
    }

    /**
     * Checks that a valid QR code returns the correct campsite ID.
     */
    @Test
    fun testValidQrCode() {

        val qrText = "pinedraw://campsite/123"

        val result = CampsiteQr.getCampsiteId(qrText)

        assertEquals("123", result)
    }

    /**
     * Checks that QR codes from other websites are rejected.
     */
    @Test
    fun testInvalidQrCode() {

        val qrText = "https://google.com"

        val result = CampsiteQr.getCampsiteId(qrText)

        assertNull(result)
    }

    /**
     * Checks that a QR code without a campsite ID is rejected.
     */
    @Test
    fun testEmptyCampsiteId() {

        val qrText = "pinedraw://campsite/"

        val result = CampsiteQr.getCampsiteId(qrText)

        assertNull(result)
    }

    /**
     * Checks that QR codes with an invalid path are rejected.
     */
    @Test
    fun testInvalidCampsitePath() {

        val qrText = "pinedraw://campsite/123/456"

        val result = CampsiteQr.getCampsiteId(qrText)

        assertNull(result)
    }

    /**
     * Checks that QR codes with extra URL information are rejected.
     */
    @Test
    fun testInvalidQrParameters() {

        val qrText = "pinedraw://campsite/123?test=true"

        val result = CampsiteQr.getCampsiteId(qrText)

        assertNull(result)
    }
}