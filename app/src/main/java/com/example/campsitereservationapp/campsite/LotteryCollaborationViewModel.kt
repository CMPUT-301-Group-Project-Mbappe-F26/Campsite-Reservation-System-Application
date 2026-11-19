package com.example.campsitereservationapp.campsite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.service.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Coordinates owner invitations and membership, keeping Firebase out of the view.
 * @property listings Accepted lotteries and pending invitations for the displayed owned campsite.
 * @property candidates Published campsites available for invitations.
 * @property loadingCandidates Whether the invitation picker is loading its choices.
 * @property loading Whether owner lottery data is being loaded.
 * @property saving Whether an owner action is being committed.
 * @property error Displayable loading or action failure.
 * @property message Confirmation of the last successful action.
 */
class LotteryCollaborationViewModel : ViewModel() {
    var listings by mutableStateOf<List<LotteryCollaborationListing>>(emptyList()); private set
    var candidates by mutableStateOf<List<Campsite>>(emptyList()); private set
    var loadingCandidates by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    private val service = LotteryCollaborationService()
    private var loadJob: Job? = null
    private var loadVersion = 0

    /** Loads the owned campsite's shared lotteries; published invitation choices are loaded lazily. */
    fun load(campsiteId: String) {
        loadJob?.cancel()
        val version = ++loadVersion
        loadJob = viewModelScope.launch {
            loading = true
            error = ""
            try {
                listings = withTimeout(20_000) { service.getForCampsite(campsiteId) }
                candidates = emptyList()
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = "Could not load lottery collaboration. Please try again."
            } finally { if (version == loadVersion) loading = false }
        }
    }

    /** Fetches published listings only when the coordinator chooses to invite another owner. */
    fun loadCandidates() {
        viewModelScope.launch {
            loadingCandidates = true
            try { candidates = CampsiteService().getPublishedCampsites() }
            catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = "Could not load campsites for invitations."
            } finally { loadingCandidates = false }
        }
    }

    /** Executes one confirmed action; the caller refreshes the displayed lottery after success. */
    fun change(lottery: Lottery, campsiteId: String, action: LotteryCollaborationAction, onChanged: () -> Unit) {
        if (saving) return
        saving = true
        error = ""
        message = ""
        viewModelScope.launch {
            try {
                service.change(lottery, campsiteId, action)
                message = when (action) {
                    LotteryCollaborationAction.INVITE -> "Invitation sent. The campsite is added only after its owner accepts."
                    LotteryCollaborationAction.ACCEPT -> "Invitation accepted. Your campsite is now in the lottery."
                    LotteryCollaborationAction.DECLINE -> "Invitation removed."
                    LotteryCollaborationAction.WITHDRAW -> "Your campsite was withdrawn. Entrants were notified."
                }
                onChanged()
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = if (exception is IllegalArgumentException || exception is IllegalStateException) exception.message.orEmpty()
                    else "Could not save this action. Check your connection and Firebase permissions."
            } finally { saving = false }
        }
    }
}
