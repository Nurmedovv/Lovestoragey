package com.lovestory.app.data.auth

import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.lovestory.app.AuthManager
import com.lovestory.app.domain.repository.AuthRepository

// делегирует существующему классу AuthManager — поведение 1:1
class AuthRepositoryImpl(private val authManager: AuthManager) : AuthRepository {

    override val googleSignInClient: GoogleSignInClient
        get() = authManager.googleSignInClient

    override fun getUserUid(): String? = authManager.getUserUid()

    override fun getUserName(): String? = authManager.getUserName()

    override fun getUserEmail(): String? = authManager.getUserEmail()

    override fun saveUserName(name: String) = authManager.saveUserName(name)

    override suspend fun firebaseAuthWithGoogle(idToken: String): Boolean =
        authManager.firebaseAuthWithGoogle(idToken)

    override fun signOut() = authManager.signOut()

    override fun isSignedIn(): Boolean = authManager.isSignedIn()
}
