package eu.europa.ec.corelogic.controller

import android.content.Context
import android.credentials.GetCredentialResponse
import android.os.Build
import android.service.credentials.GetCredentialRequest
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import eu.europa.ec.resourceslogic.provider.ResourceProvider


sealed class DigitalCredentialsPartialState {
    data class Success(val documentId: String) : IssueDocumentPartialState()
    data class Failure(val errorMessage: String) : IssueDocumentPartialState()
}


interface WalletAuthController{
    fun handleSignIn(result: GetCredentialResponse)
    fun hasKey (assertionRequestJson: String): Boolean
}

class WalletAuthIControllerImpl (
    private val resourceProvider: ResourceProvider
): WalletAuthController {

    private suspend fun saveCredentials(context: Context, userId: String, password: String) {
        val credentialManager = CredentialManager.create(resourceProvider.provideContext())
        try {
            credentialManager.createCredential(
                request = CreatePasswordRequest(userId, password),
                context = context
            )
            println("Credentials saved successfully.")
        } catch (e: CreateCredentialNoCreateOptionException) {
            throw Exception("No providers available for saving credentials. ${e.message}")
        } catch (e: Exception) {
            throw Exception("Error: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
   override fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        when (credential) {
            is PublicKeyCredential -> {
                val responseJson = credential.authenticationResponseJson
                // Share responseJson i.e. a GetCredentialResponse on your server to
                // validate and  authenticate
            }

            is PasswordCredential -> {
                val username = credential.id
                val password = credential.password
                // Use id and password to send to your server to validate
                // and authenticate
            }

            is CustomCredential -> {

            }
            else -> {
                // Catch any unrecognized credential type here.
                println("Unexpected type of credential")
            }
        }
    }

    override fun hasKey(assertionRequestJson: String): Boolean {
        TODO("Not yet implemented")
    }


}