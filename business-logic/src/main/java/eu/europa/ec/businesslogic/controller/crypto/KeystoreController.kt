/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.businesslogic.controller.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.crypto.tink.hybrid.internal.X25519
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface KeystoreController {

    // biometric, pin vc
    fun retrieveOrGenerateSecretKey( userAuthenticationRequired:Boolean): SecretKey?
    fun deleteKey(alias: String)
    fun rotateKey(oldAlias: String, userAuthenticationRequired: Boolean): String?

    // did
    fun retrieveOrGenerateECKeyPair(alias: String, userAuthenticationRequired: Boolean): KeyPair?

    fun getPublicKey(alias: String): PublicKey?

    fun rotateECKey(oldAlias: String, userAuthenticationRequired: Boolean): String?
}

class KeystoreControllerImpl(
    private val prefKeys: PrefKeys,
    private val logController: LogController,
    private val context: Context
) : KeystoreController {

    companion object {
        private const val STORE_TYPE = "AndroidKeyStore"
        private const val KEY__ALGORITHM  = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val KEY_SIZE = 256


        // EC constants (new)
        private const val EC_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC
        private const val EC_CURVE = "secp256r1"
        private const val EC_ALIAS_SUFFIX = "_ec"

    }

    private var androidKeyStore: KeyStore? = null
    private val randomSecret = SecureRandom()

    init {
        loadKeyStore()
    }

    /**
     * Load/Init KeyStore
     */
    private fun loadKeyStore() {
        try {
            androidKeyStore = KeyStore.getInstance(STORE_TYPE)
            androidKeyStore?.load(null)
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
        }
    }

    /**
     * Retrieves the existing biometric secret key if exists or generates a new one if it is the
     * first time.
     */
    override fun retrieveOrGenerateSecretKey(userAuthenticationRequired:Boolean): SecretKey? {
        return androidKeyStore?.let {
            val alias = prefKeys.getAlias()
            if (alias.isEmpty()) {
                val newAlias = createAliasForKey()
                generateBiometricSecretKey(newAlias, userAuthenticationRequired)
                prefKeys.setAlias(newAlias)
                getBiometricSecretKey(it, newAlias)
            } else {
                 getBiometricSecretKey(it, alias)
            }
        }
    }


    /**
     * Retrieves or generates EC P-256 KeyPair for DID operations
     * This is the key used for signing (DID authentication, VC signatures)
     */

    override fun retrieveOrGenerateECKeyPair(alias: String, userAuthenticationRequired: Boolean): KeyPair? {
        return androidKeyStore?.let { keyStore ->
            if (!keyStore.containsAlias(alias)) {
                try {
                    generateECKeyPair(alias, userAuthenticationRequired)
                } catch (e: Exception) {
                    logController.e(this.javaClass.simpleName, e)
                    return null
                }
            }
            getECKeyPair(keyStore, alias)
        }
    }

    @Suppress("DEPRECATION")
    private fun generateBiometricSecretKey(alias: String, userAuthenticationRequired: Boolean) {
        val keyGenerator = KeyGenerator.getInstance(
            KEY__ALGORITHM,
            STORE_TYPE
        )

        keyGenerator.init(createdKeyGenParameterSpec(alias, userAuthenticationRequired))
        keyGenerator.generateKey()
    }


    /**
     * Generate EC P-256 KeyPair in Android KeyStore (StrongBox if available)
     * Follows EIDAS ARF and W3C DID specifications
     */
    private fun generateECKeyPair(alias: String, userAuthenticationRequired: Boolean) {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                EC_ALGORITHM,
                STORE_TYPE
            )

            val spec = createECKeyGenParameterSpec(alias, userAuthenticationRequired)
            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()

            println("EC KeyPair generated successfully: $alias")
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
            throw e
        }
    }


    /**
     * Create KeyGenParameterSpec for EC P-256 keys
     * Enforces hardware-backed storage (StrongBox) when available
     */
    private fun createECKeyGenParameterSpec(
        alias: String,
        userAuthenticationRequired: Boolean
    ): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)

        if (userAuthenticationRequired) {
            builder.setUserAuthenticationRequired(true)
            builder.setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pm = context.packageManager
            val hasStrongBox = pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            if (hasStrongBox) {
                builder.setIsStrongBoxBacked(true)
                println("EC Key using StrongBox (Secure Element)")
            } else {
                println("StrongBox not available, using TEE for EC Key")
            }
        }

        return builder.build()
    }


    private fun createdKeyGenParameterSpec(alias: String, userAuthenticationRequired: Boolean): KeyGenParameterSpec {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setKeySize(KEY_SIZE)

        if (userAuthenticationRequired) {
            keyGenParameterSpec.setUserAuthenticationRequired(true)
            keyGenParameterSpec.setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                keyGenParameterSpec.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                keyGenParameterSpec.setUserAuthenticationValidityDurationSeconds(-1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pm = context.packageManager
            val hasStrongBox = pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            if (hasStrongBox) {
                keyGenParameterSpec.setIsStrongBoxBacked(true)
                println("EC Key using StrongBox (Secure Element)")
            } else {
                println("StrongBox not available, using TEE for EC Key")
            }
        }
        return keyGenParameterSpec.build()
    }


    private fun getBiometricSecretKey(keyStore: KeyStore, alias: String): SecretKey {
        keyStore.load(null)
        return keyStore.getKey(alias, null) as SecretKey
    }

    /**
     * Get random string
     * @return a string containing 64 characters
     */
    private fun createAliasForKey(): String {
        val randomBytes = ByteArray(32);
        randomSecret.nextBytes(randomBytes)
        val stringBase64 = Base64.encodeToString(randomBytes,
            Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
        return stringBase64.take(32)
    }


    override fun deleteKey(alias: String){
        try {
            androidKeyStore?.deleteEntry(alias)
            prefKeys.setAlias("")
            println(
                "Delete key: ${this.javaClass.simpleName}, key: $alias delete"
            )
        }catch (e: Exception){
            println(
                "Delete key: ${this.javaClass.simpleName}, exception: ${e.message}"
            )
        }
    }

    override fun rotateKey(oldAlias: String, userAuthenticationRequired: Boolean): String? {
        val newAlias = createAliasForKey()
        androidKeyStore?.deleteEntry(oldAlias)
        generateBiometricSecretKey(newAlias, userAuthenticationRequired)
        prefKeys.setAlias(newAlias)
        return  newAlias
    }

    /**
     * Get only the public key (for exporting to DID Document)
     */
    override fun getPublicKey(alias: String): PublicKey? {
        return try {
            androidKeyStore?.load(null)
            androidKeyStore?.getCertificate(alias)?.publicKey
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
            null
        }
    }

    /**
     * Retrieve EC KeyPair from KeyStore
     */
    private fun getECKeyPair(keyStore: KeyStore, alias: String): KeyPair? {
        return try {
            keyStore.load(null)
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            val publicKey = keyStore.getCertificate(alias)?.publicKey

            if (privateKey != null && publicKey != null) {
                KeyPair(publicKey, privateKey)
            } else {
                null
            }
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
            null
        }
    }

    override fun rotateECKey(oldAlias: String, userAuthenticationRequired: Boolean): String? {
        val newAlias = createAliasForKey() + EC_ALIAS_SUFFIX
        androidKeyStore?.deleteEntry(oldAlias)
        generateECKeyPair(newAlias, userAuthenticationRequired)
        prefKeys.setECAlias(newAlias)
        return newAlias
    }


}