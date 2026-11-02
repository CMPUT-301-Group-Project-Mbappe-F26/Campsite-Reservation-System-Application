package com.example.campsitereservationapp.model

/**
 * An owner's lottery for reservation opportunities at one campsite, stored at
 * `lotteries/{id}` in Firestore. Linked users and campsites are referenced by ID.
 *
 * Dates are date-only `yyyy-MM-dd` strings, matching [Campsite] and [Reservation].
 * The form describes start dates as 00:00 and end dates as 11:59; this model stores
 * neither times nor a time zone. Registration must end before the booking start date,
 * and booking must end before the stay start date. Period endpoints cannot be shared.
 *
 * Validation belongs to the creation service, not the constructor. Defaults support
 * Firestore deserialization and incomplete drafts; they do not represent a valid lottery.
 * Participant lists default to empty and are not automatically updated by this class.
 *
 * @constructor Creates a lottery value without performing validation or saving it.
 * @property id Firestore document ID, generated once for a creation attempt and its retries.
 * @property ownerId Firebase Authentication UID of the campsite owner.
 * @property campsiteId Document ID of the campsite offered by this lottery.
 * @property numberOfWinners Positive number of reservation opportunities (the form's capacity).
 * Must be strictly below the campsite's current maximum occupancy unless [useMaxCapacity] is true.
 * @property registrationStartDate First date entrants may join the lottery waiting list.
 * @property registrationEndDate Last date entrants may join, on or after registration start.
 * @property bookingStartDate First date selected entrants may reserve, after registration ends.
 * @property bookingEndDate Last date selected entrants may reserve, on or after booking start.
 * @property stayStartDate First campsite stay date offered, after booking ends.
 * @property stayEndDate End of the offered stay period, strictly after stay start.
 * @property registered Firebase user IDs registered for the lottery waiting list.
 * @property pending Firebase user IDs selected and awaiting a reservation decision.
 * @property booked Firebase user IDs that have confirmed a reservation through this lottery.
 * @property useMaxCapacity Whether the owner selected Max. When true, [numberOfWinners]
 * must equal the campsite's current maximum occupancy.
 * @see com.example.campsitereservationapp.service.LotteryService
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
    val stayEndDate: String = "",
    val registered: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val booked: List<String> = emptyList(),
    val useMaxCapacity: Boolean = false
)
