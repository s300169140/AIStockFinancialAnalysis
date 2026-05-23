package com.aistock.analysis.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.aistock.analysis.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuth(private val appContext: Context) {
    private val credentialManager = CredentialManager.create(appContext)

    suspend fun getIdToken(activityContext: Context): String {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        require(clientId.isNotBlank()) {
            "GOOGLE_WEB_CLIENT_ID is not set in local.properties"
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: GetCredentialException) {
            throw IllegalStateException(e.message ?: "Sign-in failed", e)
        }

        val cred = result.credential
        if (cred is androidx.credentials.CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            return google.idToken
        }
        throw IllegalStateException("Unexpected credential type: ${cred.type}")
    }
}
