package com.example.campsitereservationapp.campsite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campsitereservationapp.model.Lottery
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
 * @property created Whether submission succeeded and the form should confirm and close.
 */
class LotteryCreationViewModel : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var created by mutableStateOf(false)
        private set
    private val service = LotteryService()
    private var draftId = service.newLotteryId()

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
     */
    fun submit(lottery: Lottery) {
        if (saving || created) return
        saving = true
        error = ""
        viewModelScope.launch {
            try {
                service.createLottery(lottery.copy(id = draftId))
                created = true
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = when (exception) {
                    is IllegalArgumentException, is IllegalStateException -> exception.message
                    is FirebaseFirestoreException -> when (exception.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permission denied. Check that your account can create lotteries."
                        else -> null
                    }
                    else -> null
                } ?: "Could not create the lottery. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }
}
