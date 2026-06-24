package com.example.namastays.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed class GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
    object NoAccountsAvailable : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

class GoogleAuthManager {

    suspend fun signIn(context: Context): GoogleSignInResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            GoogleSignInResult.Success(credential.idToken)
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.e(TAG, "NoCredentialException: ${e.message}", e)
            GoogleSignInResult.NoAccountsAvailable
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "GoogleIdTokenParsingException: ${e.message}", e)
            GoogleSignInResult.Failure("Sign-in response was malformed. Please try again.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException type=${e.type} message=${e.message}", e)
            GoogleSignInResult.Failure("Sign-in failed. Please try again.")
        }
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
        private const val WEB_CLIENT_ID = "302581637961-4eebccb4jk879oq91hnkfq101akfsp5s.apps.googleusercontent.com"
    }
}