package com.example.campsitereservationapp.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for who an admin may remove (US 03.02.01).
 */
class AdminProfileRemovalTest {

    @Test
    fun adminCanRemoveAnotherProfile() {
        assertTrue(adminCanRemoveProfile("admin-priya", "entrant-amir"))
    }

    @Test
    fun adminCannotRemoveTheirOwnProfile() {
        assertFalse(adminCanRemoveProfile("admin-priya", "admin-priya"))
    }

    @Test
    fun missingIdsCannotBeRemoved() {
        assertFalse(adminCanRemoveProfile("", "entrant-amir"))
        assertFalse(adminCanRemoveProfile("admin-priya", ""))
    }
}
