package com.example.campsitereservationapp.campsite

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User
import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for what an admin can see when opening a campsite from the Admin
 * Dashboard (US 03.04.01).
 */
class AdminCampsiteAccessTest {

    private val draftCampsite = Campsite(id = "site-whistlers", ownerId = "owner-devon",
        name = "Whistlers Campground", published = false)

    @Test
    fun adminCanViewAnotherOwnersDraftCampsite() {
        assertTrue(canViewCampsite(draftCampsite, "admin-priya", asAdmin = true))
    }

    @Test
    fun nonAdminStillCannotViewAnotherOwnersDraftCampsite() {
        assertFalse(canViewCampsite(draftCampsite, "admin-priya", asAdmin = false))
    }

    @Test
    fun ownerControlsAreHiddenFromAdminView() {
        // The admin view passes fromDiscover || asAdmin, so an owner who is also an admin gets no owner buttons there.
        val asAdmin = true
        assertFalse(canShowOwnerControls(draftCampsite, "owner-devon", fromDiscover = false || asAdmin))
    }

    @Test
    fun adminBackButtonReturnsToAllCampsites() {
        assertEquals("All Campsites", getCampsiteBackLabel(fromDiscover = false, asAdmin = true))
    }

    @Test
    fun usersAreNotAdminsByDefault() {
        assertFalse(User(id = "entrant-amir").isAdmin)
    }
}
