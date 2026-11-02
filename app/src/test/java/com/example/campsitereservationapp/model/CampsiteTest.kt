package com.example.campsitereservationapp.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteTest {
    private val campsite = Campsite(ownerId = "owner")

    @Test
    fun isOwnedBy_recognizesOwner() {
        assertTrue(campsite.isOwnedBy("owner"))
    }

    @Test
    fun isOwnedBy_rejectsOtherUser() {
        assertFalse(campsite.isOwnedBy("entrant"))
    }

    @Test
    fun isOwnedBy_rejectsMissingUser() {
        assertFalse(campsite.isOwnedBy(null))
        assertFalse(Campsite().isOwnedBy(""))
    }
}
