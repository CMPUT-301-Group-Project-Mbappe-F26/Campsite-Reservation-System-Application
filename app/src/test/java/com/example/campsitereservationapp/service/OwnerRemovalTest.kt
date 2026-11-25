package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for removing a campsite owner who violates app policy (US 03.07.01): who can be
 * removed, which reservations are cancelled, how lotteries change, and the messages shown.
 */
class OwnerRemovalTest {

    private val today = "2026-11-24"
    private val maria = User(id = "owner-maria", name = "Maria Lopez", isOwner = true)
    private val ownerSites = setOf("site-two-jack", "site-lake-louise")

    private fun stay(id: String, site: String, checkIn: String, checkOut: String, entrant: String = "entrant-amir") =
        Reservation(
            id = id, entrantId = entrant, campsiteId = site, lotteryId = "lottery-summer",
            campsiteName = "Two Jack Lakeside", checkInDate = checkIn, checkOutDate = checkOut
        )

    @Test
    fun anyoneWhoListedACampsiteIsAnOwner() {
        val users = listOf(User(id = "owner-maria", name = "Maria Lopez"), User(id = "entrant-amir"))
        val campsites = listOf(Campsite(id = "site-two-jack", ownerId = "owner-maria"))
        assertEquals(listOf(true, false), withOwnerRoles(users, campsites).map { it.isOwner })
    }

    @Test
    fun ownerWhoseCampsitesWereRemovedIsAnEntrantAgain() {
        assertFalse(withOwnerRoles(listOf(User(id = "owner-maria")), emptyList()).single().isOwner)
    }

    @Test
    fun ownerRolesChangeNothingElseAboutTheProfile() {
        val maria = User(id = "owner-maria", name = "Maria Lopez", email = "maria@example.com", isAdmin = true)
        val marked = withOwnerRoles(listOf(maria), listOf(Campsite(id = "site-two-jack", ownerId = "owner-maria"))).single()
        assertEquals(maria.copy(isOwner = true), marked)
    }

    @Test
    fun adminCanRemoveAnotherOwner() {
        assertTrue(adminCanRemoveOwner("admin-priya", maria))
    }

    @Test
    fun entrantsHaveNoOwnerRoleToRemove() {
        assertFalse(adminCanRemoveOwner("admin-priya", User(id = "entrant-amir")))
    }

    @Test
    fun adminCannotRemoveThemselvesAsAnOwner() {
        assertFalse(adminCanRemoveOwner("owner-maria", maria))
    }

    @Test
    fun upcomingAndCurrentStaysAtTheOwnersCampsitesAreCancelled() {
        val upcoming = stay("res-upcoming", "site-two-jack", "2026-12-05", "2026-12-07")
        val current = stay("res-current", "site-lake-louise", "2026-11-23", today)
        val cancelled = reservationsCancelledByOwnerRemoval(listOf(upcoming, current), ownerSites, today)
        assertEquals(listOf("res-current", "res-upcoming"), cancelled.map { it.id })
    }

    @Test
    fun pastStaysAndOtherCampsitesAreKept() {
        val past = stay("res-past", "site-two-jack", "2026-08-14", "2026-08-16")
        val elsewhere = stay("res-elsewhere", "site-kananaskis", "2026-12-05", "2026-12-07")
        assertTrue(reservationsCancelledByOwnerRemoval(listOf(past, elsewhere), ownerSites, today).isEmpty())
    }

    @Test
    fun alreadyCancelledOrRejectedStaysAreNotCancelledAgain() {
        val stays = listOf(RESERVATION_CANCELLED, RESERVATION_REJECTED).map { status ->
            stay("res-$status", "site-two-jack", "2026-12-05", "2026-12-07").copy(status = status)
        }
        assertTrue(reservationsCancelledByOwnerRemoval(stays, ownerSites, today).isEmpty())
    }

    @Test
    fun activeLotteryForARemovedCampsiteIsClosed() {
        val lottery = Lottery(
            id = "lottery-summer", campsiteId = "site-two-jack",
            registered = listOf("entrant-sam"), registeredGroupSizes = mapOf("entrant-sam" to 2), pending = listOf("entrant-lee")
        )
        val changes = lotteryChangesForOwnerRemoval(lottery, ownerSites, emptyList())
        assertEquals(emptyList<String>(), changes["registered"])
        assertEquals(emptyList<String>(), changes["pending"])
        assertEquals(true, changes["drawCompleted"])
        assertEquals("site-two-jack", changes["closedCampsiteId"])
    }

    @Test
    fun sharedLotteryRecordsWhichRemovedCampsiteClosedIt() {
        val shared = Lottery(campsiteId = "site-kananaskis", campsiteIds = listOf("site-kananaskis", "site-lake-louise"))
        assertEquals("site-lake-louise", lotteryChangesForOwnerRemoval(shared, ownerSites, emptyList())["closedCampsiteId"])
    }

    @Test
    fun cancelledEntrantsNightsAreFreed() {
        val drawn = Lottery(campsiteId = "site-two-jack", drawCompleted = true, cancelledEntrantIds = listOf("entrant-sam"))
        val cancelled = listOf(
            stay("res-1", "site-two-jack", "2026-12-05", "2026-12-07"),
            stay("res-2", "site-two-jack", "2026-12-10", "2026-12-12", entrant = "entrant-sam")
        )
        val changes = lotteryChangesForOwnerRemoval(drawn, ownerSites, cancelled)
        assertEquals(listOf("entrant-sam", "entrant-amir"), changes["cancelledEntrantIds"])
        assertFalse("A finished draw is not closed again", changes.containsKey("drawCompleted"))
    }

    @Test
    fun unrelatedLotteryIsUnchanged() {
        val other = Lottery(campsiteId = "site-kananaskis", registered = listOf("entrant-sam"))
        assertTrue(lotteryChangesForOwnerRemoval(other, ownerSites, emptyList()).isEmpty())
    }

    @Test
    fun entrantIsToldWhyTheirReservationWasCancelled() {
        val message = ownerRemovalNoticeMessage(stay("res-1", "site-two-jack", "2026-12-05", "2026-12-07"))
        assertTrue(message.startsWith("Two Jack Lakeside · "))
        assertTrue(message.endsWith("Cancelled because this campsite was removed for violating app policy."))
    }

    @Test
    fun adminSeesWhatTheRemovalChanged() {
        assertEquals(
            "Maria Lopez is no longer a campsite owner. 2 campsites removed, 1 reservation cancelled.",
            ownerRemovalSummary("Maria Lopez", OwnerRemovalResult(campsitesRemoved = 2, reservationsCancelled = 1))
        )
    }

    @Test
    fun ownerWithNoBookingsStillGetsAClearSummary() {
        assertEquals(
            "Maria Lopez is no longer a campsite owner. 1 campsite removed, 0 reservations cancelled.",
            ownerRemovalSummary("Maria Lopez", OwnerRemovalResult(campsitesRemoved = 1, reservationsCancelled = 0))
        )
    }
}
