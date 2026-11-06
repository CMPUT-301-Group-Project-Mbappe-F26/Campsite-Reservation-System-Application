package com.example.campsitereservationapp.campsite

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campsitereservationapp.service.CampsiteService
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

// Gets the in-progress submission even after switching screens
class CampsiteRegistrationViewModel : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var createdId by mutableStateOf<String?>(null)
        private set
    private val service = CampsiteService()
    private var registrationId = service.newCampsiteId()

    // Clears the previous result and prepares a new registration ID.
    fun reset() {
        if (saving) return
        error = ""
        createdId = null
        registrationId = service.newCampsiteId()
    }

    /**
     * Checks the form and saves the new campsite.
     *
     * @param form The campsite information entered by the user.
     */
    // Validates and saves the campsite, then reports the result.
    fun create(form: CampsiteForm) {
        if (saving || createdId != null) return
        if ((0..3).any { form.errors(it).isNotEmpty() }) {
            error = "Please correct the required fields."
            return
        }
        saving = true
        error = ""
        viewModelScope.launch {
            try {
                createdId = service.addCampsite(form.createCampsiteData(), form.photos.map(Uri::parse), registrationId)
            } catch (_: Exception) {
                coroutineContext.ensureActive() // A cancelled screen is not a save error.
                error = "Could not create campsite. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }
}
