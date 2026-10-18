package io.github.stephenjoy98.heatmap.cloud

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.FirebaseApp

/** Firebase features only work when the app was built with a google-services.json. */
object FirebaseGate {
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** The OAuth web client id the google-services plugin generates; needed for Google sign-in. */
    @SuppressLint("DiscouragedApi") // The resource only exists when Firebase is configured.
    fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }
}
