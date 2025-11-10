package eu.europa.ec.authenticationlogic.provider

import eu.europa.ec.authenticationlogic.model.did.DidDocumentIdentity

interface DidDocumentStorageProvider {
    /**
     * Salva uma identidade DID
     */
    suspend fun saveIdentity(identity: DidDocumentIdentity)

    /**
     * Obtém identidade por DID
     */
    suspend fun getIdentityByDid(did: String): DidDocumentIdentity?

    /**
     * Obtém identidade por alias do KeyStore
     */
    suspend fun getIdentityByAlias(alias: String): DidDocumentIdentity?

    /**
     * Obtém a identidade padrão
     */
    suspend fun getDefaultIdentity(): DidDocumentIdentity?

    /**
     * Lista todas as identidades
     */
    suspend fun getAllIdentities(): List<DidDocumentIdentity>

    /**
     * Remove uma identidade
     */
    suspend fun deleteIdentity(did: String)

    /**
     * Define identidade como padrão
     */
    suspend fun setDefaultIdentity(did: String)

    /**
     * Atualiza último uso
     */
    suspend fun updateLastUsed(did: String)

    /**
     * Verifica se existe alguma identidade
     */
    suspend fun hasIdentities(): Boolean

    /**
     * Conta quantas identidades existem
     */
    suspend fun count(): Int
}