package com.example.campsitereservationapp.model

/**
 * An owner's lottery for reservation opportunities at one or more campsites, stored at
 * `lotteries/{id}` in Firestore. Linked users and campsites are referenced by ID.
 *
 * Dates are date-only `yyyy-MM-dd` strings, matching [Campsite] and [Reservation].
 * Registration and booking start dates mean 00:00 and end dates mean 23:59 (11:59 PM),
 * interpreted in Alberta's `America/Edmonton` time zone. Stay end is an exclusive
 * checkout date, matching [Reservation]. This model stores neither
 * times nor a time zone. Registration must end before the booking start date,
 * and booking must end before the stay start date. Period endpoints cannot be shared.
 *
 * Validation belongs to the creation service, not the constructor. Defaults support
 * Firestore deserialization and incomplete drafts; they do not represent a valid lottery.
 * Participant lists default to empty and are not automatically updated by this class.
 *
 * @constructor Creates a lottery value without performing validation or saving it.
 * @property id Firestore document ID, generated once for a creation attempt and its retries.
 * @property ownerId Firebase Authentication UID of the lottery coordinator and primary campsite owner.
 * @property campsiteId Primary campsite document ID retained for existing app integrations.
 * @property numberOfWinners Positive number of reservation opportunities (the form's capacity).
 * Must be strictly below the combined current occupancy unless [useMaxCapacity] is true.
 * @property registrationStartDate First date entrants may join the lottery waiting list.
 * @property registrationEndDate Last date entrants may join, on or after registration start.
 * @property bookingStartDate First date selected entrants may reserve, after registration ends.
 * @property bookingEndDate Last date selected entrants may reserve, on or after booking start.
 * @property stayStartDate First campsite stay date offered, after booking ends.
 * @property stayEndDate Exclusive checkout end of the offered stay period, strictly after stay start.
 * Its preceding night must be within the campsite's current availability.
 * @property registered Firebase user IDs registered for the lottery waiting list.
 * @property registeredGroupSizes Party size selected by each registered user, keyed by Firebase UID.
 * Older lottery documents can omit this map; their registrations remain valid.
 * @property campsiteIds Campsites offered together. Empty preserves legacy single-campsite documents.
 * @property waitingListCapacity Maximum distinct entrants; zero means no optional limit.
 * @property pending Firebase user IDs selected and awaiting a reservation decision.
 * @property booked Reservations made through this lottery. Confirmed ones count toward
 * [numberOfWinners], and their nights cannot be booked again.
 * @property useMaxCapacity Whether the owner selected Max. When true, [numberOfWinners]
 * must equal the selected campsites' combined current maximum occupancy.
 * @property drawCompleted Whether the service has already saved the initial draw, including an empty draw.
 * @property listingRevision Owner update counter used to reject stale form submissions.
 * @property closedCampsiteId Campsite whose removal closed this listing, or empty while unaffected.
 * @property campsiteOwners Accepted campsite-to-owner grants. Missing legacy entries belong to [ownerId].
 * @property campsiteInvitations Pending campsite-to-owner invitations; these campsites are not offered to entrants.
 * @property collaborationAction Last membership operation, used by security rules to constrain writes.
 * @property collaborationCampsiteId Campsite targeted by that membership operation.
 * @property rejectedReservationId Reservation authorizing an owning contributor's last rejection.
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
    val registeredGroupSizes: Map<String, Int> = emptyMap(),
    val waitingListCapacity: Int = DEFAULT_WAITING_LIST_CAPACITY,
    val pending: List<String> = emptyList(),
    val booked: List<Reservation> = emptyList(),
    val useMaxCapacity: Boolean = false,
    val drawCompleted: Boolean = false,
    val cancelledEntrantIds: List<String> = emptyList(),
    val campsiteIds: List<String> = emptyList(),
    val listingRevision: Long = 0,
    val closedCampsiteId: String = "",
    val campsiteOwners: Map<String, String> = emptyMap(),
    val campsiteInvitations: Map<String, String> = emptyMap(),
    val collaborationAction: String = "",
    val collaborationCampsiteId: String = "",
    val rejectedReservationId: String = ""
) {
    /**
     * Randomly selects distinct user IDs from [registered], without changing this lottery.
     *
     * Returns every registered user in their existing order when there are no more
     * entrants than requested winners. Otherwise, samples the requested number
     * without replacement. Duplicate registrations count as one entrant.
     *
     * This function does not enforce the registration deadline, update [pending],
     * or save results to Firebase. Calling it again performs a new draw.
     *
     * @param amountOfWinners Maximum number of distinct users to select; zero selects nobody.
     * @return Selected user IDs, containing at most [amountOfWinners] entries.
     * @throws IllegalArgumentException If [amountOfWinners] is negative.
     */
    fun draw(amountOfWinners: Int): List<String> {
        require(amountOfWinners >= 0) { "The number of winners cannot be negative." }
        if (amountOfWinners == 0) return emptyList()
        val entrants = registered.distinct()
        return if (entrants.size <= amountOfWinners) entrants
        else entrants.shuffled().take(amountOfWinners)
    }
}

/** Default safety limit for a lottery waiting list when the owner has not configured one. */
const val DEFAULT_WAITING_LIST_CAPACITY = 1000

/** Keeps ownership validation within Firestore Security Rules' document-access budget. */
const val MAX_LOTTERY_CAMPSITES = 8

/** Resolves new multi-campsite listings and existing single-campsite documents uniformly. */
fun Lottery.offeredCampsiteIds(): List<String> = campsiteIds.ifEmpty { listOf(campsiteId) }

/** Resolves the accepting owner, retaining existing single-owner documents without migration. */
fun Lottery.campsiteOwnerId(id: String): String = campsiteOwners[id] ?: ownerId

/** True only for an offered campsite whose current owner matches its accepted ownership grant. */
fun Lottery.includesAcceptedCampsite(campsite: Campsite): Boolean =
    campsite.id in offeredCampsiteIds() && campsite.ownerId == campsiteOwnerId(campsite.id)

/** The coordinator and every owner who has accepted a campsite into this lottery. */
fun Lottery.participatingOwnerIds(): Set<String> = offeredCampsiteIds().map(::campsiteOwnerId).toSet() + ownerId
