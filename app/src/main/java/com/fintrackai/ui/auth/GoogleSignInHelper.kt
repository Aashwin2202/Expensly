package com.fintrackai.ui.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.fintrackai.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInHelper @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {

    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private val credentialManager = CredentialManager.create(appContext)

    suspend fun signIn(activityContext: Context): GoogleSignInResult {
        if (!isConfigured()) return GoogleSignInResult.Error(AuthConstants.MISSING_WEB_CLIENT_ID)

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val token = googleCredential.idToken
                if (token.isBlank()) {
                    GoogleSignInResult.Error(AuthConstants.GOOGLE_NO_ID_TOKEN)
                } else {
                    GoogleSignInResult.Success(
                        idToken = token,
                        displayName = googleCredential.displayName,
                        email = googleCredential.id.takeIf { it.isNotBlank() }
                    )
                }
            } else {
                GoogleSignInResult.Error(AuthConstants.GOOGLE_SIGN_IN_FAILED)
            }
        } catch (e: Exception) {
            GoogleSignInResult.Error(
                e.message?.takeIf { it.isNotBlank() } ?: AuthConstants.GOOGLE_SIGN_IN_FAILED
            )
        }
    }

    suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
