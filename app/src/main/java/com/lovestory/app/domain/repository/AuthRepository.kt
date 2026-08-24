package com.lovestory.app.domain.repository

import com.google.android.gms.auth.api.signin.GoogleSignInClient

// авторизация через Google Sign-In + Firebase Auth
interface AuthRepository {
    val googleSignInClient: GoogleSignInClient

    fun getUserUid(): String?
    fun getUserName(): String?
    fun getUserEmail(): String?
    fun saveUserName(name: String)
    suspend fun firebaseAuthWithGoogle(idToken: String): Boolean
    fun signOut()
    fun isSignedIn(): Boolean
}
