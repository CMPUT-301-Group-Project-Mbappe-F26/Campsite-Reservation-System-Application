package com.example.campsitereservationapp.campsite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 02.06.01: a campsite owner views the booked dates at their campsite
 * on a month calendar. The calendar is given sample booked nights directly, so no Firebase
 * data is needed.
 */
@RunWith(AndroidJUnit4::class)
class BookedDatesIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = "2027-06-01"
    private val booked = mapOf(
        "2027-07-02" to "Sam Okafor",
        "2027-07-03" to "Sam Okafor",
        "2027-08-14" to "Priya Nair"
    )

    @Test
    fun calendarOpensOnTheMonthOfTheNextBooking() {
        composeRule.setContent { BookedDatesCalendar(booked, today) }
        composeRule.onNodeWithText("July 2027").assertIsDisplayed()
        composeRule.onNodeWithText("2 nights booked this month. Tap one to see who is staying.").assertIsDisplayed()
    }

    @Test
    fun tappingABookedNightShowsWhoIsStaying() {
        composeRule.setContent { BookedDatesCalendar(booked, today) }
        composeRule.onNodeWithTag("day-2027-07-02").performClick()
        composeRule.onNodeWithText("Jul 2, 2027: Sam Okafor").assertIsDisplayed()
    }

    @Test
    fun tappingAFreeNightShowsNothingNew() {
        composeRule.setContent { BookedDatesCalendar(booked, today) }
        composeRule.onNodeWithTag("day-2027-07-10").performClick()
        composeRule.onNodeWithText("2 nights booked this month. Tap one to see who is staying.").assertIsDisplayed()
    }

    @Test
    fun ownerMovesToTheNextMonthToSeeLaterBookings() {
        composeRule.setContent { BookedDatesCalendar(booked, today) }
        composeRule.onNodeWithContentDescription("Next month").performClick()
        composeRule.onNodeWithText("August 2027").assertIsDisplayed()
        composeRule.onNodeWithText("1 night booked this month. Tap it to see who is staying.").assertIsDisplayed()
        composeRule.onNodeWithTag("day-2027-08-14").performClick()
        composeRule.onNodeWithText("Aug 14, 2027: Priya Nair").assertIsDisplayed()
    }

    @Test
    fun ownerIsToldWhenAMonthHasNoBookings() {
        composeRule.setContent { BookedDatesCalendar(booked, today) }
        composeRule.onNodeWithContentDescription("Previous month").performClick()
        composeRule.onNodeWithText("June 2027").assertIsDisplayed()
        composeRule.onNodeWithText("No nights booked this month.").assertIsDisplayed()
    }

    @Test
    fun calendarWithNoBookingsOpensOnThisMonth() {
        composeRule.setContent { BookedDatesCalendar(emptyMap(), today) }
        composeRule.onNodeWithText("June 2027").assertIsDisplayed()
        composeRule.onNodeWithText("No nights booked this month.").assertIsDisplayed()
    }
}
