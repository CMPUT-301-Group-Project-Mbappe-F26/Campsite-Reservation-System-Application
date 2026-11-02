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

class LotteryCreationViewModel : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var created by mutableStateOf(false)
        private set
    private val service = LotteryService()
    private var draftId = service.newLotteryId()

    fun reset() {
        if (saving) return
        error = ""
        created = false
        draftId = service.newLotteryId()
    }

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
