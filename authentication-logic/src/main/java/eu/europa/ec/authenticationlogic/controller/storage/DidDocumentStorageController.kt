package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.model.did.DidDocumentIdentity

interface DidDocumentStorageController {

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

class DidDocumentStorageControllerImpl(private val storageConfig: StorageConfig) : DidDocumentStorageController {
    override suspend fun saveIdentity(identity: DidDocumentIdentity) =  storageConfig.didDocumentStorageProvider.saveIdentity(identity)

    override suspend fun getIdentityByDid(did: String): DidDocumentIdentity?
    = storageConfig.didDocumentStorageProvider.getIdentityByDid(did)

    override suspend fun getIdentityByAlias(alias: String): DidDocumentIdentity? =
        storageConfig.didDocumentStorageProvider.getIdentityByDid(alias)

    override suspend fun getDefaultIdentity(): DidDocumentIdentity? =
        storageConfig.didDocumentStorageProvider.getDefaultIdentity()

    override suspend fun getAllIdentities(): List<DidDocumentIdentity> =
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