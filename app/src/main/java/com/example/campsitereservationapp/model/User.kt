package com.example.campsitereservationapp.model

import com.google.firebase.firestore.PropertyName

/**
 * A Pinedraw account profile, stored at `users/{id}` in Firestore.
 *
 * Every device gets a profile on first launch (see UserService). The same account can
 * act as an entrant, a campsite owner, and an admin.
 *
 * Outstanding issues: there is no in-app way to grant [isAdmin]; it is set by hand in
 * the Firebase console, and Firestore security rules must stop users granting it to themselves.
 *
 * @property id Firebase Authentication UID, also the Firestore document ID.
 * @property name Display name shown to campsite owners and admins.
 * @property email Contact email address, which may be blank.
 * @property phone Optional contact phone number.
 * @property city Home city shown on the profile screen.
 * @property isOwner Whether the user has registered as a campsite owner.
 * @property isAdmin Whether the user can open the Admin Dashboard to browse and remove campsites.
 * Stored in Firestore as `isAdmin`.
 */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "",
    val isOwner: Boolean = false,
    // Keeps the Firestore field name as isAdmin; Kotlin would otherwise save it as "admin" and never read it back.
    @get:PropertyName("isAdmin") @field:PropertyName("isAdmin")
    val isAdmin: Boolean = false
)
