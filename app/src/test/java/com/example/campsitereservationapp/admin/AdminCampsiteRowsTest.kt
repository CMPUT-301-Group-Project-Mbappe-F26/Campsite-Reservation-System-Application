package com.example.campsitereservationapp.admin

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User
import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for adminCampsiteRows, which builds the campsite list an admin browses
 * (US 03.04.01): name, location and owner for every registered campsite.
 */
class AdminCampsiteRowsTest {

    private val maria = User(id = "owner-maria", name = "Maria Lopez")
    private val devon = User(id = "owner-devon", name = "Devon Clarke")

    private val twoJack = Campsite(id = "site-two-jack", ownerId = "owner-maria", name = "Two Jack Lakeside",
        location = "Banff, AB", published = true)
    private val whistlers = Campsite(id = "site-whistlers", ownerId = "owner-devon", name = "Whistlers Campground",
        location = "Jasper, AB", published = false)

    @Test
    fun noCampsitesGivesNoRows() {
        assertTrue(adminCampsiteRows(emptyList(), listOf(maria)).isEmpty())
    }

    @Test
    fun rowShowsNameLocationAndOwner() {
        val row = adminCampsiteRows(listOf(twoJack), listOf(maria)).single()
        assertEquals("Two Jack Lakeside", row.name)
        assertEquals("Banff, AB", row.location)
        assertEquals("Maria Lopez", row.ownerName)
    }

    @Test
    fun unpublishedCampsitesAreIncluded() {
        val rows = adminCampsiteRows(listOf(twoJack, whistlers), listOf(maria, devon))
        assertEquals(2, rows.size)
        assertFalse(rows.single { it.id == "site-whistlers" }.published)
    }

    @Test
    fun rowsAreSortedByNameIgnoringCase() {
        val aspen = twoJack.copy(id = "site-aspen", name = "aspen grove")
        val rows = adminCampsiteRows(listOf(whistlers, twoJack, aspen), listOf(maria, devon))
        assertEquals(listOf("aspen grove", "Two Jack Lakeside", "Whistlers Campground"), rows.map { it.name })
    }

    @Test
    fun missingOwnerProfileIsShownAsUnknown() {
        assertEquals("Unknown owner", adminCampsiteRows(listOf(twoJack), emptyList()).single().ownerName)
    }

    @Test
    fun ownerWithoutNameIsShownAsUnnamed() {
        val row = adminCampsiteRows(listOf(twoJack), listOf(maria.copy(name = ""))).single()
        assertEquals("Unnamed owner", row.ownerName)
    }

    @Test
    fun blankNameAndLocationGetPlaceholders() {
        val row = adminCampsiteRows(listOf(twoJack.copy(name = " ", location = "")), listOf(maria)).single()
        assertEquals("Unnamed campsite", row.name)
        assertEquals("Location not provided", row.location)
    }
}
