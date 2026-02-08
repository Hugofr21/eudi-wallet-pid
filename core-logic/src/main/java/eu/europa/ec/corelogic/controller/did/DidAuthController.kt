package eu.europa.ec.corelogic.controller.did

import eu.europa.ec.corelogic.model.did.DidDocument
import kotlinx.serialization.json.JsonObject

interface DidAuthController {
    suspend fun getOrCreateDidDocument(serviceEndpoint: String): DidDocument

    suspend fun signChallenge(challenge: String): ByteArray
}

class DidAuthControllerImpl (

): DidAuthController{

    override suspend fun getOrCreateDidDocument(serviceEndpoint: String): DidDocument {
        TODO("Not yet implemented")
    }

    override suspend fun signChallenge(challenge: String): ByteArray {
        TODO("Not yet implemented")
    }

}