package com.example.campsitereservationapp.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 03.06.01 (admin browses uploaded images). The grid is given sample
 * images directly, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class AdminImagesIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val images = listOf(
        AdminImage("campsite-site-two-jack-0", "", AdminImageSource.CAMPSITE, "site-two-jack", "Two Jack Lakeside"),
        AdminImage("profile-user-maria", "", AdminImageSource.PROFILE, "user-maria", "Maria Lopez")
    )

    @Test
    fun adminOpensImagesFromTheDashboard() {
        var opened = false
        composeRule.setContent { AdminDashboardScreen(onBack = {}, onCampsitesClick = {}, onImagesClick = { opened = true }) }
        composeRule.onNodeWithText("Images").performClick()
        assertTrue(opened)
    }

    @Test
    fun everyImageShowsWhatItBelongsTo() {
        composeRule.setContent { AdminImageGrid(images) }
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Maria Lopez").assertIsDisplayed()
        composeRule.onNodeWithText("2 uploaded · 1 campsite · 1 profile").assertIsDisplayed()
    }

    @Test
    fun tabsFilterCampsiteAndProfilePhotos() {
        composeRule.setContent { AdminImageGrid(images) }
        composeRule.onNodeWithText("Profile photos").performClick()
        composeRule.onNodeWithText("Maria Lopez").assertIsDisplayed()
        composeRule.onNodeWithText("Two Jack Lakeside").assertDoesNotExist()
        composeRule.onNodeWithText("Campsite promos").performClick()
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Maria Lopez").assertDoesNotExist()
    }

    @Test
    fun tappingAnImageOpensALargerPreview() {
        composeRule.setContent { AdminImageGrid(images) }
        composeRule.onNodeWithTag("admin-image-profile-user-maria").performClick()
        composeRule.onNodeWithText("Profile photo").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Profile photo").assertDoesNotExist()
    }

    @Test
    fun noImagesShowsAMessage() {
        composeRule.setContent { AdminImageGrid(emptyList()) }
        composeRule.onNodeWithText("No images found").assertIsDisplayed()
    }
}
