package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eu.europa.ec.authenticationlogic.model.did.DidDocumentIdentity
import eu.europa.ec.authenticationlogic.provider.DidDocumentStorageProvider
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class PrefsDidDocumentStorageProvider(
private val prefsController: PrefsController
) : DidDocumentStorageProvider {

    companion object {
        private const val KEY_PREFIX = "DidDocumentIdentity"
        private const val KEY_ALL_DID = "DidAllIdentifiers"
        private const val KEY_DEFAULT_DID = "DidDefaultIdentifier"
    }

    private val gson = Gson()

    /**
     * Saves identity in the DataStore (encrypted via Tink)
     */
    override suspend fun saveIdentity(identity: DidDocumentIdentity) = withContext(Dispatchers.IO) {
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

        println("Identity saved: ${identity.displayName}")
    }

    /**
     * Obtains identity through DID
     */
    override suspend fun getIdentityByDid(did: String): DidDocumentIdentity? =
        withContext(Dispatchers.IO) {
            val key = "$KEY_PREFIX$did"
            val json = prefsController.getString(key, "")

            if (json.isEmpty()) return@withContext null

            try {
                gson.fromJson(json, DidDocumentIdentity::class.java)
            } catch (e: Exception) {
                println("Failed to parse identity: ${e.message}")
                null
            }
        }

    /**
     * Obtains identity via alias from KeyStore
     */
    override suspend fun getIdentityByAlias(alias: String): DidDocumentIdentity? =
        withContext(Dispatchers.IO) {
            getAllIdentities().find { it.alias == alias }
        }

    /**
     * Obtain default identity
     */
    override suspend fun getDefaultIdentity(): DidDocumentIdentity? =
        withContext(Dispatchers.IO) {
            val defaultDid = prefsController.getString(KEY_DEFAULT_DID, "")
            if (defaultDid.isEmpty()) return@withContext null
            getIdentityByDid(defaultDid)
        }

    /**
     * Lists all identities
     */
    override suspend fun getAllIdentities(): List<DidDocumentIdentity> =
        withContext(Dispatchers.IO) {
            val dids = getAllDidIdentifiers()
            dids.mapNotNull { getIdentityByDid(it) }
                .sortedByDescending { it.lastUsedAt }
        }

    /**
     * Remove identity
     */
    override suspend fun deleteIdentity(did: String) = withContext(Dispatchers.IO) {
        val identity = getIdentityByDid(did)
        val key = "$KEY_PREFIX$did"
        prefsController.clear(key)
        val allDid = getAllDidIdentifiers().toMutableSet()
        allDid.remove(did)
        prefsController.setString(KEY_ALL_DID, gson.toJson(allDid.toList()))

        if (identity?.isDefault == true && allDid.isNotEmpty()) {
            val newDefault = allDid.first()
            prefsController.setString(KEY_DEFAULT_DID, newDefault)
        } else if (allDid.isEmpty()) {
            prefsController.clear(KEY_DEFAULT_DID)
        }

        println("Identity deleted: $did")
    }

    /**
     * Define identity as a standard
     */
    override suspend fun setDefaultIdentity(did: String) = withContext(Dispatchers.IO) {

        val allIdentities = getAllIdentities()
        allIdentities.forEach { identity ->
            if (identity.isDefault) {
                val updated = identity.copy(isDefault = false)
                saveIdentity(updated)
            }
        }

        val identity = getIdentityByDid(did)
        if (identity != null) {
            val updated = identity.copy(isDefault = true)
            saveIdentity(updated)
            prefsController.setString(KEY_DEFAULT_DID, did)
            println("Default identity set: $did")
        }
    }

    /**
     * Updates last used timestamp
     */
    override suspend fun updateLastUsed(did: String) = withContext(Dispatchers.IO) {
        val identity = getIdentityByDid(did) ?: return@withContext
        val updated = identity.copy(lastUsedAt = System.currentTimeMillis())
        saveIdentity(updated)
    }

    /**
     * Checks if there is any identification.
     */
    override suspend fun hasIdentities(): Boolean = withContext(Dispatchers.IO) {
        getAllDidIdentifiers().isNotEmpty()
    }

    /**
     * Count identifier
     */
    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        getAllDidIdentifiers().size
    }

    /**
     * Obtains list of all saved DIDs
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