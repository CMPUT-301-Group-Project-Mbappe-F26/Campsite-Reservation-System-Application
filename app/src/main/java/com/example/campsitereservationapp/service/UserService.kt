package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.reservationDateToday
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Handles [User] profiles stored at `users/{id}` in Firestore.
 *
 * Signs in a guest account on first launch, saves profile changes, and looks up other
 * users' profiles so owners and admins can see names instead of IDs.
 *
 * Outstanding issues: reading other users' profiles relies on Firestore rules allowing it.
 */
class UserService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // A missing profile should return to Welcome instead of recreating a deleted profile.
    suspend fun loadCurrentUser(): User? {
        val account = auth.currentUser ?: return null
        val profile = db.collection("users").document(account.uid).get().await().toObject(User::class.java)
        if (profile == null && auth.currentUser?.uid == account.uid) auth.signOut()
        return profile
    }
    // Loads the current profile or creates one for a guest.
    suspend fun getOrCreateUser(): User {
        val currentUser = auth.currentUser
            ?: auth.signInAnonymously().await().user
            ?: throw Exception("Sign in failed")
        val doc = db.collection("users").document(currentUser.uid)
        val user = doc.get().await().toObject(User::class.java)
        if (user != null) return user // This will return the existing user
        val newUser = User(id = currentUser.uid)
        doc.set(newUser).await() // Saves the new user
        return newUser
    }

    // Saves profile changes for the signed-in user.
    suspend fun updateUser(user: User) {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        require(user.id == uid)
        db.collection("users").document(uid).set(user).await() // Update profile
    }

    // Removes the entrant's waiting-list entries and cancels active bookings before deleting the profile.
    suspend fun deleteProfile() {
        val account = checkNotNull(auth.currentUser) { "Not signed in" }
        val uid = account.uid
        val users = db.collection("users")
        val lotteries = db.collection("lotteries")
        val reservations = db.collection("reservations")
        val today = reservationDateToday()
        val reservationRefs = reservations.whereEqualTo("entrantId", uid).get(Source.SERVER).await()
            .documents.filter {
                val reservation = it.toObject(Reservation::class.java)
                reservation != null && activeReservations(listOf(reservation), today).isNotEmpty()
            }.map { it.reference }
        val lotteryIds = listOf("registered", "pending").flatMap { field ->
            lotteries.whereArrayContains(field, uid).get(Source.SERVER).await().documents.map { it.id }
        }

        db.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            val active = reservationRefs.mapNotNull { ref ->
                transaction.get(ref).toObject(Reservation::class.java)?.copy(id = ref.id)
            }.let { activeReservations(it.filter { reservation -> reservation.entrantId == uid }, today) }
            val affectedLotteries = (lotteryIds + active.map { it.lotteryId }.filter { it.isNotBlank() })
                .distinct().mapNotNull { id ->
                    val ref = lotteries.document(id)
                    transaction.get(ref).toObject(Lottery::class.java)?.let { ref to it }
                }

            for (reservation in active) {
                transaction.update(reservations.document(reservation.id), "status", RESERVATION_CANCELLED)
            }
            val bookedLotteryIds = active.map { it.lotteryId }.toSet()
            for ((ref, lottery) in affectedLotteries) {
                val changes = mutableMapOf<String, Any>(
                    "registered" to lottery.registered.filterNot { it == uid },
                    "registeredGroupSizes" to (lottery.registeredGroupSizes - uid),
                    "pending" to lottery.pending.filterNot { it == uid }
                )
                // Release the entrant's spots without letting clients rewrite other bookings.
                if (ref.id in bookedLotteryIds && uid !in lottery.cancelledEntrantIds) {
                    changes["cancelledEntrantIds"] = lottery.cancelledEntrantIds + uid
                }
                transaction.update(ref, changes)
            }
            transaction.delete(users.document(uid))
            Unit
        }.await()

        // Firestore cleanup must run while the entrant is still authenticated.
        try {
            account.delete().await()
        } catch (exception: FirebaseAuthRecentLoginRequiredException) {
            // A guest has no credential to re-enter. The profile is already gone.
            if (!account.isAnonymous) throw exception
            if (auth.currentUser?.uid == uid) auth.signOut()
        }
    }

    /**
     * Loads the profiles for a list of user IDs.
     *
     * Duplicate and blank IDs are skipped. IDs without a saved profile are left out of
     * the result, so callers should handle missing users.
     *
     * @param ids Firebase UIDs to look up.
     * @return The profiles found, in no particular order.
     */
    suspend fun getUsers(ids: List<String>): List<User> {
        val uniqueIds = ids.filter { it.isNotBlank() && '/' !in it }.distinct()
        // Firestore limits whereIn queries to 30 values, so look the users up in groups.
        return uniqueIds.chunked(30).flatMap { group ->
            db.collection("users")
                .whereIn(FieldPath.documentId(), group)
                .get().await()
                .documents.mapNotNull { doc -> doc.toObject(User::class.java)?.copy(id = doc.id) }
        }
    }
}
