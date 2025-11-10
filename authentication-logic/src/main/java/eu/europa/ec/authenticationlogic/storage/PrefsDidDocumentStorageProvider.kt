package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eu.europa.ec.authenticationlogic.model.did.DidIdentity
import eu.europa.ec.authenticationlogic.provider.DidDocumentStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.KeyPairController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class PrefsDidDocumentStorageProvider(
private val prefsController: PrefsController
) : DidDocumentStorageProvider {

    companion object {
        private const val KEY_PREFIX = "DidIdentity"
        private const val KEY_ALL_DID = "DidAllIdentifiers"
        private const val KEY_DEFAULT_DID = "DidDefaultIdentifier"
    }

    private val gson = Gson()

    /**
     * Salva identidade no DataStore (encriptado via Tink)
     */
    override suspend fun saveIdentity(identity: DidIdentity) = withContext(Dispatchers.IO) {
        // 1. Salvar a identidade
        val key = "$KEY_PREFIX${identity.didIdentifier}"
        val json = gson.toJson(identity)
        prefsController.setString(key, json)

        // 2. Adicionar à lista de DIDs
        val allDids = getAllDidIdentifiers().toMutableSet()
        allDids.add(identity.didIdentifier)
        prefsController.setString(KEY_ALL_DID, gson.toJson(allDids.toList()))

        // 3. Se for a primeira, definir como padrão
        if (allDids.size == 1 || identity.isDefault) {
            prefsController.setString(KEY_DEFAULT_DID, identity.didIdentifier)
        }

        println("✅ Identity saved: ${identity.displayName}")
    }

    /**
     * Obtém identidade por DID
     */
    override suspend fun getIdentityByDid(did: String): DidIdentity? =
        withContext(Dispatchers.IO) {
            val key = "$KEY_PREFIX$did"
            val json = prefsController.getString(key, "")

            if (json.isEmpty()) return@withContext null

            try {
                gson.fromJson(json, DidIdentity::class.java)
            } catch (e: Exception) {
                println("❌ Failed to parse identity: ${e.message}")
                null
            }
        }

    /**
     * Obtém identidade por alias do KeyStore
     */
    override suspend fun getIdentityByAlias(alias: String): DidIdentity? =
        withContext(Dispatchers.IO) {
            getAllIdentities().find { it.alias == alias }
        }

    /**
     * Obtém identidade padrão
     */
    override suspend fun getDefaultIdentity(): DidIdentity? =
        withContext(Dispatchers.IO) {
            val defaultDid = prefsController.getString(KEY_DEFAULT_DID, "")
            if (defaultDid.isEmpty()) return@withContext null

            getIdentityByDid(defaultDid)
        }

    /**
     * Lista todas as identidades
     */
    override suspend fun getAllIdentities(): List<DidIdentity> =
        withContext(Dispatchers.IO) {
            val dids = getAllDidIdentifiers()
            dids.mapNotNull { getIdentityByDid(it) }
                .sortedByDescending { it.lastUsedAt }
        }

    /**
     * Remove identidade
     */
    override suspend fun deleteIdentity(did: String) = withContext(Dispatchers.IO) {
        // 1. Obter identidade antes de remover
        val identity = getIdentityByDid(did)

        // 2. Remover do DataStore
        val key = "$KEY_PREFIX$did"
        prefsController.clear(key)

        // 3. Remover da lista de DIDs
        val allDids = getAllDidIdentifiers().toMutableSet()
        allDids.remove(did)
        prefsController.setString(KEY_ALL_DID, gson.toJson(allDids.toList()))

        // 4. Se era o padrão, definir outro
        if (identity?.isDefault == true && allDids.isNotEmpty()) {
            val newDefault = allDids.first()
            prefsController.setString(KEY_DEFAULT_DID, newDefault)
        } else if (allDids.isEmpty()) {
            prefsController.clear(KEY_DEFAULT_DID)
        }

        println("🗑️ Identity deleted: $did")
    }

    /**
     * Define identidade como padrão
     */
    override suspend fun setDefaultIdentity(did: String) = withContext(Dispatchers.IO) {
        // 1. Limpar flag isDefault de todas
        val allIdentities = getAllIdentities()
        allIdentities.forEach { identity ->
            if (identity.isDefault) {
                val updated = identity.copy(isDefault = false)
                saveIdentity(updated)
            }
        }

        // 2. Definir nova como padrão
        val identity = getIdentityByDid(did)
        if (identity != null) {
            val updated = identity.copy(isDefault = true)
            saveIdentity(updated)
            prefsController.setString(KEY_DEFAULT_DID, did)
            println("✅ Default identity set: $did")
        }
    }

    /**
     * Atualiza timestamp de último uso
     */
    override suspend fun updateLastUsed(did: String) = withContext(Dispatchers.IO) {
        val identity = getIdentityByDid(did) ?: return@withContext
        val updated = identity.copy(lastUsedAt = System.currentTimeMillis())
        saveIdentity(updated)
    }

    /**
     * Verifica se existe alguma identidade
     */
    override suspend fun hasIdentities(): Boolean = withContext(Dispatchers.IO) {
        getAllDidIdentifiers().isNotEmpty()
    }

    /**
     * Conta identidades
     */
    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        getAllDidIdentifiers().size
    }

    /**
     * Obtém lista de todos os DIDs guardados
     */
    private fun getAllDidIdentifiers(): List<String> {
        val json = prefsController.getString(KEY_ALL_DID, "")
        if (json.isEmpty()) return emptyList()

        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}