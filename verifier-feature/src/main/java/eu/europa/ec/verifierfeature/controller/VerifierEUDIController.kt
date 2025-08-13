package eu.europa.ec.verifierfeature.controller

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri


interface VerifierEUDIController {
    suspend fun launchTestVerifierAndGetResult(context: Context): VerifierResult

    suspend fun launchTestVerifierEudiAndGetResult(context: Context): VerifierResult
}

data class VerifierResult(
    val clientId: String,
    val requestUri: String
)

class VerifierEUDIControllerImpl(

) : VerifierEUDIController {
    override suspend fun launchTestVerifierAndGetResult(context: Context): VerifierResult {
        val url = "https://tester.relyingparty.eudiw.dev/"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return VerifierResult(clientId = "", requestUri = url)
    }

    override suspend fun launchTestVerifierEudiAndGetResult(context: Context): VerifierResult {
        val url = "https://verifier.eudiw.dev/home"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return VerifierResult(clientId = "", requestUri = url)
    }
}
