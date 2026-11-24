package com.example.campsitereservationapp.profile

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.User
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises photo management with picker/save stand-ins, without writing to Firebase. */
@RunWith(AndroidJUnit4::class)
class ProfileImageIntentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selection = Uri.parse("content://profile-image-test/photo")
    private var pickerResult: Uri? = selection
    private val user = mutableStateOf(User(id = "entrant", name = "Amir"))
    private var calls = 0
    private var savedUri: Uri? = null
    private var failSave = false
    private var gate: CompletableDeferred<Unit>? = null

    private fun showEditor(withPhoto: Boolean = false) {
        if (withPhoto) user.value = user.value.copy(profileImagePath = "profile-images/entrant/original.jpg")
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    dispatchResult(requestCode, pickerResult)
                }
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ProfileImageEditor(user.value, enabled = true) { uri ->
                    calls++
                    savedUri = uri
                    gate?.await()
                    if (failSave) error("Simulated save failure")
                    user.value = user.value.copy(
                        profileImagePath = if (uri == null) "" else "profile-images/entrant/replacement.jpg"
                    )
                }
            }
        }
    }

    @Test
    fun selectingAPhotoNeedsAnExplicitSave() {
        showEditor()
        composeRule.onNodeWithText("Add photo").performClick()
        composeRule.onNodeWithText("Save photo").assertIsDisplayed()
        assertEquals(0, calls)
        composeRule.onNodeWithText("Save photo").performClick()
        composeRule.waitUntil { calls == 1 && user.value.profileImagePath.endsWith("replacement.jpg") }
        assertEquals(selection, savedUri)
        composeRule.onNodeWithText("Save photo").assertDoesNotExist()
    }

    @Test
    fun cancelKeepsTheOriginalPhotoWithoutSaving() {
        showEditor(withPhoto = true)
        composeRule.onNodeWithText("Add photo").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, calls)
        assertEquals("profile-images/entrant/original.jpg", user.value.profileImagePath)
        composeRule.onNodeWithText("Save photo").assertDoesNotExist()
    }

    @Test
    fun dismissingThePickerDoesNotCreateAnImageChange() {
        pickerResult = null
        showEditor()
        composeRule.onNodeWithText("Add photo").performClick()
        composeRule.onNodeWithText("Save photo").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test
    fun removalIsSavedAndRestoresTheDefaultAvatar() {
        showEditor(withPhoto = true)
        composeRule.onNodeWithText("Remove photo").performClick()
        assertEquals(0, calls)
        composeRule.onNodeWithText("Save photo").performClick()
        composeRule.waitUntil { calls == 1 && user.value.profileImagePath.isEmpty() }
        assertNull(savedUri)
        composeRule.onNodeWithContentDescription("Default profile avatar").assertIsDisplayed()
        composeRule.onNodeWithText("Remove photo").assertDoesNotExist()
    }

    @Test
    fun failureKeepsTheDraftAndAllowsRetry() {
        failSave = true
        showEditor(withPhoto = true)
        composeRule.onNodeWithText("Add photo").performClick()
        composeRule.onNodeWithText("Save photo").performClick()
        composeRule.onNodeWithText(
            "Could not save your photo. Check your connection and try again, or choose another image."
        ).assertIsDisplayed()
        assertEquals("profile-images/entrant/original.jpg", user.value.profileImagePath)
        failSave = false
        composeRule.onNodeWithText("Save photo").performClick()
        composeRule.waitUntil { calls == 2 && user.value.profileImagePath.endsWith("replacement.jpg") }
        assertEquals(selection, savedUri)
    }

    @Test
    fun savingDisablesDuplicateSavesAndDraftChanges() {
        gate = CompletableDeferred()
        showEditor()
        composeRule.onNodeWithText("Add photo").performClick()
        composeRule.onNodeWithText("Save photo").performClick()
        composeRule.onNodeWithText("Saving photo...").assertIsNotEnabled()
        composeRule.onNodeWithText("Change photo").assertIsNotEnabled()
        composeRule.onNodeWithText("Remove photo").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        assertEquals(1, calls)
        gate!!.complete(Unit)
        composeRule.waitUntil { user.value.profileImagePath.endsWith("replacement.jpg") }
    }
}
