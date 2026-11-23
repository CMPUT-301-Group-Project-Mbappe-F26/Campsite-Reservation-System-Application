package com.example.campsitereservationapp.home

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.campsitereservationapp.qr.CampsiteQr
import com.example.campsitereservationapp.service.CampsiteService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private fun scanOptions() = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan a campsite QR code")
    setBeepEnabled(false)
    setOrientationLocked(false)
}

/**
 * Returns a function that opens the camera to scan a campsite QR code.
 * Asks for the camera permission when needed, checks the campsite exists and is
 * published, then calls [onCampsiteFound] with its ID. Problems are shown as a toast.
 */
@Composable
fun rememberCampsiteScanner(onCampsiteFound: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campsiteService = remember { CampsiteService() }
    fun showError(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val qrText = result.contents ?: return@rememberLauncherForActivityResult
        val campsiteId = CampsiteQr.getCampsiteId(qrText)
        if (campsiteId == null) {
            showError("This isn't a Pinedraw campsite QR code.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val campsite = campsiteService.getCampsite(campsiteId)
                if (campsite != null && campsite.published) onCampsiteFound(campsiteId)
                else showError("Campsite not found.")
            } catch (_: Exception) {
                showError("Could not load campsite.")
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanner.launch(scanOptions()) else showError("Camera permission is required to scan QR codes.")
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) scanner.launch(scanOptions()) else cameraPermission.launch(Manifest.permission.CAMERA)
    }
}
