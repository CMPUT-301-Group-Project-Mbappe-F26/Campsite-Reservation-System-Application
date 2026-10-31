package com.example.campsitereservationapp.model

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "",
    val isOwner: Boolean = false
)