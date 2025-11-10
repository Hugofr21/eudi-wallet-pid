package eu.europa.ec.authenticationlogic.provider

import eu.europa.ec.authenticationlogic.model.did.DidIdentity

interface DidDocumentStorageProvider {
    /**
     * Salva uma identidade DID
     */
    suspend fun saveIdentity(identity: DidIdentity)

    /**
     * Obtém identidade por DID
     */
    suspend fun getIdentityByDid(did: String): DidIdentity?

    /**
     * Obtém identidade por alias do KeyStore
     */
    suspend fun getIdentityByAlias(alias: String): DidIdentity?

    /**
     * Obtém a identidade padrão
     */
    suspend fun getDefaultIdentity(): DidIdentity?

    /**
     * Lista todas as identidades
     */
    suspend fun getAllIdentities(): List<DidIdentity>

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