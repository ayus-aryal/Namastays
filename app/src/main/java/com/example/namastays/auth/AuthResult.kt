package com.example.namastays.auth

/**
 * Result type specifically for the Google sign-in flow, which spans two
 * phases (on-device Credential Manager, then a network call to our
 * backend). Deliberately separate from NetworkResult — that type is
 * shared across the entire app and used by every other repository;
 * extending it with sign-in-specific cases (Cancelled, NoAccountsAvailable)
 * would force every existing call site (Trek, City, Place, Property) to
 * handle cases that can never apply to them.
 */
sealed class AuthResult {
    object Success : AuthResult()
    object Cancelled : AuthResult()
    object NoAccountsAvailable : AuthResult()
    data class Failure(val message: String) : AuthResult()
}