package eu.europa.ec.businesslogic.controller.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

interface KeyPairController{
    suspend fun getPublicKey(alias: String): PublicKey
    suspend fun retrieveOrGenerateECKeyPair(alias: String, userAuthenticationRequired: Boolean): KeyPair

    fun hasECKey(alias: String): Boolean
    suspend fun signData(alias: String, data: ByteArray): ByteArray
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean
    suspend fun deleteECKey(alias: String)
    fun listAliases(): List<String>
}



class KeyPairControllerImpl(
    private val keystoreController: KeystoreController,
) : KeyPairController {

    companion object {
        private const val ALIAS_PREFIX = "did_ec_"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    override suspend fun getPublicKey(alias: String): PublicKey =
        withContext(Dispatchers.IO) {
            keystoreController.getPublicKey(alias)
                ?: throw SecurityException("Public key not found for alias: $alias")
        }

    override suspend fun retrieveOrGenerateECKeyPair(
        alias: String,
        userAuthenticationRequired: Boolean
    ): KeyPair =
        withContext(Dispatchers.IO) {
            if (hasECKey(alias)) {
                return@withContext getKeyPair(alias)
                    ?: throw SecurityException("The key exists but failed to recover.: $alias")
            } else {
                val newKeyPair = keystoreController.retrieveOrGenerateECKeyPair(alias, userAuthenticationRequired)
                    ?: throw SecurityException("Fail of in generate new KeyPair for the alias: $alias")
                return@withContext newKeyPair
            }
        }

    override fun hasECKey(alias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun signData(alias: String, data: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            val keyPair = getKeyPair(alias)
                ?: throw SecurityException("Key not found: $alias")

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(keyPair.private)
            signature.update(data)

            signature.sign()
        }

    override fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKey: PublicKey
    ): Boolean {
        return try {
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun deleteECKey(alias: String) = withContext(Dispatchers.IO) {
        keystoreController.deleteKey(alias)
    }

    override fun listAliases(): List<String> {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.aliases().toList().filter { it.startsWith(ALIAS_PREFIX) }
        } catch (e: Exception) {
            emptyList()
        }
    }


    private fun getKeyPair(alias: String): KeyPair? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            val publicKey = keyStore.getCertificate(alias)?.publicKey

            if (privateKey != null && publicKey != null) {
                KeyPair(publicKey, privateKey)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}