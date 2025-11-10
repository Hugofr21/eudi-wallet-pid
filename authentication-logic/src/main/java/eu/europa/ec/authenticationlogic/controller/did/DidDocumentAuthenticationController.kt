package eu.europa.ec.authenticationlogic.controller.did

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.storage.DidDocumentStorageController
import eu.europa.ec.resourceslogic.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DidDocumentAuthenticationController {
    suspend fun createIdentity(
        displayName: String,
        serviceEndpoint: String
    ): DidDocumentAuthenticate

    suspend fun authenticate(did: String, challenge: String): DidDocumentAuthenticate

    suspend fun verifyAuthentication(
        did: String,
        challenge: String,
        signature: ByteArray
    ): DidDocumentAuthenticate

    fun hasIdentities(): Boolean

}




class DidDocumentAuthenticationControllerImpl(
    private val context: Context,
    private val didDocumentStorageController: DidDocumentStorageController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : DidDocumentAuthenticationController {
    override suspend fun createIdentity(
        displayName: String,
        serviceEndpoint: String
    ): DidDocumentAuthenticate {
        TODO("Not yet implemented")
    }

    override suspend fun authenticate(
        did: String,
        challenge: String
    ): DidDocumentAuthenticate {
        TODO("Not yet implemented")
    }

    override suspend fun verifyAuthentication(
        did: String,
        challenge: String,
        signature: ByteArray
    ): DidDocumentAuthenticate {
        TODO("Not yet implemented")
    }

    override fun hasIdentities(): Boolean {
        TODO("Not yet implemented")
    }


}

sealed class DidDocumentAvailability {
    object CanAuthenticate : DidDocumentAvailability()
    object NonEnrolled : DidDocumentAvailability()
    data class Failure(val errorMessage: String) : DidDocumentAvailability()
}

sealed class DidDocumentAuthenticate {
    data class Success(val did: String) : DidDocumentAuthenticate()
    data class Failure(val errorMessage: String) : DidDocumentAuthenticate()
}
