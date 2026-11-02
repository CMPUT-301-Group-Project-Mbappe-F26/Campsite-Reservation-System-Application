package com.example.campsitereservationapp.model

/**
 * An owner's lottery for reservation opportunities at one campsite.
 * Dates use yyyy-MM-dd, matching the campsite and reservation models.
 * Registration dates define when entrants can join; booking dates define when
 * winners can reserve; stay dates define the campsite dates offered by the lottery.
 * Required-field, capacity, and period-conflict validation belongs in the creation flow.
 */
data class Lottery(
    val id: String = "",
    val ownerId: String = "",
    val campsiteId: String = "",
    val numberOfWinners: Int = 0,
    val registrationStartDate: String = "",
    val registrationEndDate: String = "",
    val bookingStartDate: String = "",
    val bookingEndDate: String = "",
    val stayStartDate: String = "",
    val stayEndDate: String = ""
)
