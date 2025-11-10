package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.model.did.DidIdentity

interface DidDocumentStorageController {

    suspend fun saveIdentity(identity: DidIdentity)


    suspend fun getIdentityByDid(did: String): DidIdentity?


    suspend fun getIdentityByAlias(alias: String): DidIdentity?


    suspend fun getDefaultIdentity(): DidIdentity?


    suspend fun getAllIdentities(): List<DidIdentity>

    suspend fun deleteIdentity(did: String)


    suspend fun setDefaultIdentity(did: String)

    suspend fun updateLastUsed(did: String)

    suspend fun hasIdentities(): Boolean

    suspend fun count(): Int

}

class DidDocumentStorageControllerImpl(private val storageConfig: StorageConfig) : DidDocumentStorageController {
    override suspend fun saveIdentity(identity: DidIdentity) =  storageConfig.didDocumentStorageProvider.saveIdentity(identity)

    override suspend fun getIdentityByDid(did: String): DidIdentity?
    = storageConfig.didDocumentStorageProvider.getIdentityByDid(did)

    override suspend fun getIdentityByAlias(alias: String): DidIdentity? =
        storageConfig.didDocumentStorageProvider.getIdentityByDid(alias)

    override suspend fun getDefaultIdentity(): DidIdentity? =
        storageConfig.didDocumentStorageProvider.getDefaultIdentity()

    override suspend fun getAllIdentities(): List<DidIdentity> =
        storageConfig.didDocumentStorageProvider.getAllIdentities()

    override suspend fun deleteIdentity(did: String) =
        storageConfig.didDocumentStorageProvider.deleteIdentity(did)

    override suspend fun setDefaultIdentity(did: String)  =
        storageConfig.didDocumentStorageProvider.setDefaultIdentity(did)


    override suspend fun updateLastUsed(did: String)  =
        storageConfig.didDocumentStorageProvider.updateLastUsed(did)


    override suspend fun hasIdentities(): Boolean  =
        storageConfig.didDocumentStorageProvider.hasIdentities()


    override suspend fun count(): Int =
    storageConfig.didDocumentStorageProvider.count()


}