package com.example.campsitereservationapp.campsite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.LotteryService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Coordinates lottery submission and exposes its result as Compose-observable state.
 *
 * Uses [LotteryService] in [viewModelScope] and retains the draft's document ID
 * across failures and configuration changes to avoid duplicate creation on retry.
 * Form field values are owned by [LotteryCreation], rather than this ViewModel.
 *
 * @property saving Whether a submission is in progress; the form disables editing and navigation.
 * @property error Latest displayable submission error, or an empty string when there is none.
 * @property created Whether creation, update, or deletion succeeded and the form should close.
 * @property successMessage Confirmation describing the operation that completed.
 * @property campsites Owned listings available to the form.
 * @property loadingCampsites Whether the owner's offerings are being loaded.
 */
class LotteryCreationViewModel : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var created by mutableStateOf(false)
        private set
    var successMessage by mutableStateOf("")
        private set
    private val service = LotteryService()
    private var draftId = service.newLotteryId()
    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set
    var loadingCampsites by mutableStateOf(true)
        private set

    /** Loads the owner's selectable listings without making the view access Firebase. */
    fun loadCampsites(existingLottery: Lottery? = null) {
        viewModelScope.launch {
            loadingCampsites = true
            try {
                campsites = CampsiteService().getMyCampsites()
                val missing = existingLottery?.offeredCampsiteIds().orEmpty().filterNot { id -> campsites.any { it.id == id } }
                campsites = campsites + missing.mapNotNull { CampsiteService().getCampsite(it) }
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = "Could not load your campsites. Try again."
            } finally {
                loadingCampsites = false
            }
        }
    }

    /** Clears a previous failure after an edit, unless a submission is in progress. */
    fun clearError() {
        if (!saving) error = ""
    }

    /**
     * Clears the submission result and allocates a fresh draft ID for a new lottery.
     *
     * Does nothing while saving. This does not delete a saved lottery or clear the
     * form's field values; the caller closes the form to discard those values.
     */
    fun reset() {
        if (saving) return
        error = ""
        created = false
        successMessage = ""
        draftId = service.newLotteryId()
    }

    /**
     * Starts asynchronous validation and persistence using this ViewModel's draft ID.
     *
     * Repeated calls while saving or after success are ignored. Sets [created] on
     * success or [error] on failure, then clears [saving]. Coroutine cancellation
     * propagates instead of being presented as a submission error.
     *
     * @param lottery Current form values; its ID is replaced with the stable draft ID.
     * @param original Original listing when updating; null creates a new lottery.
     */
    fun submit(lottery: Lottery, original: Lottery? = null) = perform(
        if (original == null) "Lottery created successfully" else "Lottery updated; entrants notified"
    ) {
        if (original == null) service.createLottery(lottery.copy(id = draftId))
        else service.updateLottery(original, lottery.copy(id = original.id))
    }

    /** Deletes only after the view's explicit confirmation; service checks current lifecycle and ownership. */
    fun delete(lottery: Lottery) = perform("Lottery deleted; entrants notified") {
        service.deleteLottery(lottery)
    }

    private fun perform(message: String, operation: suspend () -> Unit) {
        if (saving || created) return
        saving = true
        error = ""
        viewModelScope.launch {
            try {
                operation()
                successMessage = message
                created = true
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = when (exception) {
                    is IllegalArgumentException, is IllegalStateException -> exception.message
                    is FirebaseFirestoreException -> when (exception.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permission denied. Check Firebase access for lotteries and lottery notices."
                        else -> null
                    }
                    else -> null
                } ?: "Could not save the lottery change. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }
}
