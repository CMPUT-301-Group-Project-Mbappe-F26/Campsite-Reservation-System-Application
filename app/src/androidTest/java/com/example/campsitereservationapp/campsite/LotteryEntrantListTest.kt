package com.example.campsitereservationapp.campsite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 02.09.01: a campsite owner views the entrants on their lottery's
 * waiting list. The list is given sample entrants directly, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class LotteryEntrantListTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ownerSeesEveryEntrantOnTheWaitingList() {
        composeRule.setContent {
            LotteryEntrantList(listOf(
                LotteryEntrant("entrant-amir", "Amir Haddad", "Edmonton"),
                LotteryEntrant("entrant-jen", "Jen Okafor", "Calgary")
            ))
        }
        composeRule.onNodeWithText("Waiting list").assertIsDisplayed()
        composeRule.onNodeWithText("2 entrants").assertIsDisplayed()
        composeRule.onNodeWithText("Amir Haddad").assertIsDisplayed()
        composeRule.onNodeWithText("Calgary").assertIsDisplayed()
    }

    @Test
    fun ownerIsToldWhenNobodyHasJoined() {
        composeRule.setContent { LotteryEntrantList(emptyList()) }
        composeRule.onNodeWithText("0 entrants").assertIsDisplayed()
        composeRule.onNodeWithText("No entrants have joined yet.").assertIsDisplayed()
    }
}
