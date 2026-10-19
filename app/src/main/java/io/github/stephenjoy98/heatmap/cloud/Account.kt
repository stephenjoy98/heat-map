package io.github.stephenjoy98.heatmap.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/** Google sign-in (through Firebase Auth) and the user's shareable friend code. */
object Account {
    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user

    private val _friendCode = MutableStateFlow<String?>(null)
    val friendCode: StateFlow<String?> = _friendCode

    private var initialized = false

    fun init(context: Context) {
        if (initialized || !FirebaseGate.isConfigured(context)) return
        initialized = true
        val auth = FirebaseAuth.getInstance()
        _user.value = auth.currentUser
        auth.addAuthStateListener {
            _user.value = it.currentUser
            if (it.currentUser == null) _friendCode.value = null
        }
    }

    suspend fun signIn(activity: Activity) {
        val clientId = FirebaseGate.webClientId(activity)
            ?: error("Firebase isn't set up in this build (missing web client id)")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential) { "Unexpected credential type" }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
            .await()
        ensureProfile()
    }

    suspend fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Creates the user's profile and a unique friend code the first time; returns the code. */
    suspend fun ensureProfile(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Not signed in")
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("users").document(user.uid)
        val existing = ref.get().await().getString("friendCode")
        if (existing != null) {
            ref.set(mapOf("name" to (user.displayName ?: "Friend")), SetOptions.merge()).await()
            _friendCode.value = existing
            return existing
        }
        repeat(10) {
            val code = randomCode()
            val codeRef = db.collection("friendCodes").document(code)
            val claimed = db.runTransaction { tx ->
                if (tx.get(codeRef).exists()) {
                    false
                } else {
                    tx.set(codeRef, mapOf("uid" to user.uid))
                    tx.set(
                        ref,
                        mapOf("name" to (user.displayName ?: "Friend"), "friendCode" to code, "createdAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge(),
                    )
                    true
                }
            }.await()
            if (claimed) {
                _friendCode.value = code
                return code
            }
        }
        error("Couldn't create a friend code; try again")
    }

    // No 0/O or 1/I, so codes are easy to read out and type.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    private fun randomCode() = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun formatCode(code: String) = if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code

    /** Accepts "ABCD-2345", "abcd2345" or a full share link. */
    fun parseCode(text: String): String? {
        val tail = text.trim().substringAfterLast("/")
        val code = tail.uppercase().filter { it in ALPHABET }
        return code.takeIf { it.length == 8 }
    }
}
