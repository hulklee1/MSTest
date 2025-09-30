package com.example.edgeaiapp

data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val message: String
)
