package com.example.campsitereservationapp.service
import com.example.campsitereservationapp.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Handles [User] profiles stored at `users/{id}` in Firestore.
 *
 * Signs in a guest account on first launch, saves profile changes, and looks up other
 * users' profiles so owners and admins can see names instead of IDs.
 *
 * Outstanding issues: reading other users' profiles relies on Firestore rules allowing it.
 */
class UserService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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
