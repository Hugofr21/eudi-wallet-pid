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

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import java.security.KeyStore
import java.security.Provider
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface KeystoreController {
    fun retrieveOrGenerateSecretKey( UserAuthenticationRequired:Boolean = true): SecretKey?
    fun deleteKey(alias: String)
    fun rotateKey(oldAlias: String): String?
}

class KeystoreControllerImpl(
    private val prefKeys: PrefKeys,
    private val logController: LogController,
) : KeystoreController {

    companion object {
        private const val STORE_TYPE = "AndroidKeyStore"
        private const val KEY__ALGORITHM  = KeyProperties.KEY_ALGORITHM_AES // KEY_ALGORITHM_EC
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE // ENCRYPTION_PADDING_PKCS7
        private const val KEY_SIZE = 256
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
            println("KeystoreControllerImpl.retrieveOrGenerateSecretKey: alias=$alias")
            if (alias.isEmpty()) {
                val newAlias = createPublicKey()
                println("KeystoreControllerImpl.retrieveOrGenerateSecretKey: newAlias=$newAlias")
                generateBiometricSecretKey(newAlias)
                prefKeys.setAlias(newAlias)
                getBiometricSecretKey(it, newAlias)
            } else {
                 getBiometricSecretKey(it, alias)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun generateBiometricSecretKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(
            KEY__ALGORITHM,
            STORE_TYPE
        )

        keyGenerator.init(createdKeyGenParameterSpec(alias))
        keyGenerator.generateKey()
    }


    private fun createdKeyGenParameterSpec(alias: String): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
            )

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            try {
//                builder.setIsStrongBoxBacked(true)
//                println("Keystore: tentando StrongBox")
//            } catch (e: UnsupportedOperationException) {
//                println("Keystore: não há StrongBox; fallback")
//            }
//        }
        return builder.build()
    }


    private fun getBiometricSecretKey(keyStore: KeyStore, alias: String): SecretKey {
        keyStore.load(null)
        return keyStore.getKey(alias, null) as SecretKey
    }

    /**
     * Get random string
     *
     * @return a string containing 64 characters
     */
    private fun createPublicKey(): String {
        val randomBytes = ByteArray(32);
        randomSecret.nextBytes(randomBytes)
        val stringBase64 = Base64.encodeToString(randomBytes, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
        return stringBase64.substring(0, 32)
    }


    override fun deleteKey(alias: String){
        try {
            androidKeyStore?.deleteEntry(alias)
            prefKeys.setAlias("")
            logController.d(this.javaClass.simpleName, { "Key $alias deleted" })
        }catch (e: Exception){
            logController.e(this.javaClass.simpleName, e)
        }
    }

    override fun rotateKey(oldAlias: String): String? {
        val newAlias = createPublicKey()
        androidKeyStore?.deleteEntry(oldAlias)
        generateBiometricSecretKey(newAlias)
        prefKeys.setAlias(newAlias)
        return  newAlias
    }


}