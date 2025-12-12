package eu.europa.ec.corelogic.controller


import com.google.gson.Gson
import eu.europa.ec.businesslogic.controller.crypto.KeyPairController
import eu.europa.ec.corelogic.model.did.AuthChallenge
import eu.europa.ec.corelogic.model.did.AuthResponse
import eu.europa.ec.corelogic.model.did.DidDocument
import eu.europa.ec.storagelogic.dao.ConnectionDao
import eu.europa.ec.storagelogic.model.DidEntity

interface WalletDidDocumentController {
    /**
     * @return All the documents from the Database.
     * */
    suspend fun saveConnection(
        peerDid: String,
        displayName: String,
        peerDidDocument: DidDocument
    )

    suspend fun getAllConnections(): List<DidEntity>

    suspend fun getConnectionByDid(peerDid: String): DidEntity?

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
    private val  keyPairController: KeyPairController,
    private val connectionDao: ConnectionDao,
    private val gson: Gson = Gson()
): WalletDidDocumentController {

    override suspend fun saveConnection(
        peerDid: String,
        displayName: String,
        peerDidDocument: DidDocument
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun getAllConnections(): List<DidEntity> {
        TODO("Not yet implemented")
    }

    override suspend fun getConnectionByDid(peerDid: String): DidEntity? {
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