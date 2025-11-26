package eu.europa.ec.authenticationlogic.provider

import eu.europa.ec.authenticationlogic.model.did.DidDocumentIdentity

interface DidDocumentStorageProvider {
    suspend fun saveIdentity(identity: DidDocumentIdentity)

    suspend fun getIdentityByDid(did: String): DidDocumentIdentity?

    suspend fun getIdentityByAlias(alias: String): DidDocumentIdentity?

    suspend fun getDefaultIdentity(): DidDocumentIdentity?

    suspend fun getAllIdentities(): List<DidDocumentIdentity>

    suspend fun deleteIdentity(did: String)

    suspend fun setDefaultIdentity(did: String)

    suspend fun updateLastUsed(did: String)


    suspend fun hasIdentities(): Boolean

    suspend fun count(): Int
}