package eu.europa.ec.corelogic.controller


import eu.europa.ec.corelogic.model.did.AuthChallenge
import eu.europa.ec.corelogic.model.did.AuthResponse
import eu.europa.ec.corelogic.model.did.DidDocument
import eu.europa.ec.storagelogic.model.Connection

interface WalletDidDocumentController {
    /**
     * @return All the documents from the Database.
     * */
    suspend fun saveConnection(
        peerDid: String,
        displayName: String,
        peerDidDocument: DidDocument
    )

    suspend fun getAllConnections(): List<Connection>

    suspend fun getConnectionByDid(peerDid: String): Connection?

    suspend fun deleteConnection(peerDid: String)

    suspend fun createChallengeForPeer(peerDid: String): AuthChallenge?

    suspend fun respondToChallenge(challenge: AuthChallenge): AuthResponse

    suspend fun verifyPeerResponse(
        response: AuthResponse,
        originalChallenge: String
    ): Boolean

    suspend fun getMyDidDocument(): DidDocument?
}

class WalletDidDocumentControllerImpl (

): WalletDidDocumentController {

    override suspend fun saveConnection(
        peerDid: String,
        displayName: String,
        peerDidDocument: DidDocument
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun getAllConnections(): List<Connection> {
        TODO("Not yet implemented")
    }

    override suspend fun getConnectionByDid(peerDid: String): Connection? {
        TODO("Not yet implemented")
    }

    override suspend fun deleteConnection(peerDid: String) {
        TODO("Not yet implemented")
    }

    override suspend fun createChallengeForPeer(peerDid: String): AuthChallenge? {
        TODO("Not yet implemented")
    }

    override suspend fun respondToChallenge(challenge: AuthChallenge): AuthResponse {
        TODO("Not yet implemented")
    }

    override suspend fun verifyPeerResponse(
        response: AuthResponse,
        originalChallenge: String
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getMyDidDocument(): DidDocument? {
        TODO("Not yet implemented")
    }


}