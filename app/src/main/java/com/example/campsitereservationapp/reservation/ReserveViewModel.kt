package com.example.campsitereservationapp.reservation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campsitereservationapp.model.LotteryReservationSelection
import com.example.campsitereservationapp.service.ReservationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Coordinates winner eligibility, campsite availability loading, and reservation actions.
 *
 * @property selection Loaded offerings, including their disabled reasons.
 * @property loading Whether eligibility and availability are being refreshed.
 * @property error A displayable loading failure; empty after a successful load.
 */
class ReserveViewModel : ViewModel() {
    var selection by mutableStateOf<LotteryReservationSelection?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf("")
        private set
    private val service = ReservationService()
    private var loadJob: Job? = null
    private var loadVersion = 0

    /** Refreshes the available offerings, cancelling obsolete requests without showing cancellation errors. */
    fun load(lotteryId: String) {
        loadJob?.cancel()
        val version = ++loadVersion
        loadJob = viewModelScope.launch {
            loading = true
            error = ""
            selection = null
            try {
                selection = withTimeout(20_000) { service.getReservationSelection(lotteryId) }
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = if (exception is TimeoutCancellationException) "Loading took too long. Please try again."
                else if (exception is IllegalStateException || exception is IllegalArgumentException)
                    exception.message.orEmpty() else "Could not load this lottery. Check your connection and try again."
            } finally {
                if (version == loadVersion) loading = false
            }
        }
    }

    /** The service rechecks the selected campsite and occupied nights before committing. */
    suspend fun reserve(campsiteId: String, checkIn: String, checkOut: String, rulesAccepted: Boolean): String? {
        val lotteryId = selection?.lottery?.id ?: return "Please reload this lottery."
        return service.reserve(lotteryId, checkIn, checkOut, rulesAccepted, campsiteId)
    }

    /** Releases this winner's opportunity through the existing decline transaction. */
    suspend fun decline(): String? {
        val lotteryId = selection?.lottery?.id ?: return "Please reload this lottery."
        return service.decline(lotteryId)
    }
}
