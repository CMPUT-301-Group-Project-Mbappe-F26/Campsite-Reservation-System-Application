package com.example.campsitereservationapp.service
import com.example.campsitereservationapp.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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
}
