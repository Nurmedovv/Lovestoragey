package com.lovestory.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lovestory.app.di.appContainer
import com.lovestory.app.domain.repository.AppPrefs
import kotlinx.coroutines.tasks.await

// управление авторизацией через Google Sign-In + Firebase Auth
class AuthManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    val googleSignInClient: GoogleSignInClient by lazy {
        val webClientId = context.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // uid текущего пользователя
    fun getUserUid(): String? {
        // сначала проверяем Firebase Auth
        auth.currentUser?.uid?.let { return it }
        // запасной вариант — из SharedPreferences
        return prefs.getString(KEY_USER_UID, null)
    }

    // имя текущего пользователя
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
            ?: auth.currentUser?.displayName
    }

    fun getUserEmail(): String? {
        return auth.currentUser?.email
            ?: prefs.getString(KEY_USER_EMAIL, null)
    }

    // сохранить имя пользователя (никнейм)
    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    // авторизоваться через Google ID token
    // вызывать из onActivityResult после Google Sign-In
    suspend fun firebaseAuthWithGoogle(idToken: String): Boolean {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                prefs.edit()
                    .putString(KEY_USER_UID, user.uid)
                    .putString(KEY_USER_NAME, user.displayName)
                    .putString(KEY_USER_EMAIL, user.email)
                    .apply()
                Log.d(TAG, "Авторизация успешна: ${user.uid}")
                true
            } else {
                Log.w(TAG, "Авторизация вернула null user")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка авторизации", e)
            false
        }
    }

    // выйти из аккаунта
    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
        prefs.edit()
            .remove(KEY_USER_UID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .apply()
        context.appContainer.coupleRepository.stopListening()
        context.appContainer.coupleSessionStore.clear()
    }

    // проверить, авторизован ли пользователь
    fun isSignedIn(): Boolean = auth.currentUser != null || prefs.getString(KEY_USER_UID, null) != null

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = AppPrefs.PREFS_NAME
        private const val KEY_USER_UID = "user_uid"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }
}
