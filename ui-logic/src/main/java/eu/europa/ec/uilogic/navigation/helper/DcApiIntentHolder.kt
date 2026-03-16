package eu.europa.ec.uilogic.navigation.helper


import android.content.Intent

data class IntentAction(val intent: Intent)

private const val ACTION_GET_CREDENTIAL =
    "androidx.credentials.registry.provider.action.GET_CREDENTIAL"
private const val ACTION_GET_CREDENTIALS = "androidx.identitycredentials.action.GET_CREDENTIALS"

/**
 * Temporary storage for DCAPI intent to make it accessible across the app lifecycle
 * This is needed because the intent needs to survive context changes between caching and retrieval
 */
object DcApiIntentHolder {
    private var cachedIntent: Intent? = null

    fun cacheIntent(intent: Intent?) {
        cachedIntent = intent
    }

    fun retrieveIntent(): Intent? {
        val intent = cachedIntent
        cachedIntent = null
        return intent
    }
}

fun isDCAPIIntent(intent: Intent?): Boolean {
    return intent?.action == ACTION_GET_CREDENTIAL || intent?.action == ACTION_GET_CREDENTIALS
}
