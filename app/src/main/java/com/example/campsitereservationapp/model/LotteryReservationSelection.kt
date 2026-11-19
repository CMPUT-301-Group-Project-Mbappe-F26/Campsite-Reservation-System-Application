package com.example.campsitereservationapp.model

/**
 * A listed campsite and its availability snapshot, ready for the winner's selection screen.
 *
 * @property campsiteId Offered ID, retained even if its campsite has been removed.
 * @property campsite Current listing; null represents a removed campsite.
 * @property blockedNights Confirmed occupied nights across related lotteries.
 * @property firstAvailableNight First selectable night, or null when none remain.
 * @property unavailableReason Displayable reason to disable this choice, or null when available.
 */
data class ReservationCampsiteChoice(
    val campsiteId: String,
    val campsite: Campsite?,
    val blockedNights: Set<String>,
    val firstAvailableNight: String?,
    val unavailableReason: String?
) {
    /** True only when the entrant can choose this campsite and at least one night is open. */
    val selectable: Boolean get() = unavailableReason == null && firstAvailableNight != null
}

/** The winning lottery and every offering, including disabled or removed campsites. */
data class LotteryReservationSelection(val lottery: Lottery, val choices: List<ReservationCampsiteChoice>)

/**
 * Collects occupied nights across lotteries without treating cancelled/rejected bookings as occupied.
 * Legacy embedded reservations with no campsite ID belong to their lottery's primary campsite.
 */
fun occupiedCampsiteNights(campsiteId: String, lotteries: List<Lottery>): Set<String> =
    lotteries.flatMap { lottery ->
        confirmedReservations(lottery).filter { it.campsiteId.ifBlank { lottery.campsiteId } == campsiteId }
            .flatMap { reservationNights(it.checkInDate, it.checkOutDate) }
    }.toSet()

/** Shared eligibility checks for selection and the transaction that confirms a reservation. */
fun reservationCampsiteError(lottery: Lottery, campsite: Campsite?, entrantId: String): String? = when {
    entrantId !in lottery.pending -> "You do not have a spot to reserve in this lottery."
    campsite == null -> "This campsite is no longer available."
    campsite.id !in lottery.offeredCampsiteIds() -> "Choose a campsite listed in this lottery."
    !lottery.includesAcceptedCampsite(campsite) || !campsite.published -> "This campsite is no longer available."
    (campsite.maxOccupancy ?: 0) < (lottery.registeredGroupSizes[entrantId] ?: 1) ->
        "This campsite cannot accommodate your party."
    else -> null
}

/**
 * Finds at least one reservable night before enabling a campsite. Checkout is exclusive,
 * and may fall the day after the last available night. Uses the same night rules as the picker
 * and submission. [today] is supplied explicitly so date-boundary behavior can be tested.
 */
fun reservationCampsiteChoice(
    campsiteId: String,
    campsite: Campsite?,
    lottery: Lottery,
    entrantId: String,
    blockedNights: Set<String>,
    today: String
): ReservationCampsiteChoice {
    val eligibilityError = reservationCampsiteError(lottery, campsite, entrantId)
        ?: if (confirmedReservations(lottery).size >= lottery.numberOfWinners) "This lottery is fully booked." else null
    if (eligibilityError != null) return ReservationCampsiteChoice(campsiteId, campsite, blockedNights, null, eligibilityError)
    val site = requireNotNull(campsite)
    val start = maxOf(lottery.stayStartDate, today, site.availableFrom)
    val end = reservationDateToUtcMillis(lottery.stayEndDate)
    var night = reservationDateToUtcMillis(start)
    var first: String? = null
    while (night != null && end != null && night < end) {
        val date = reservationDateFromUtcMillis(night)
        if (site.availableUntil.isNotBlank() && date > site.availableUntil) break
        if (isNightOpen(date, lottery, site, blockedNights)) {
            first = date
            break
        }
        night += 86_400_000L
    }
    return ReservationCampsiteChoice(campsiteId, site, blockedNights, first,
        if (first == null) "No available dates in this lottery." else null)
}
