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

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec


interface CryptoController {
    fun generateCodeVerifier(): String

    /**
     * Returns the [Cipher] needed to create the [androidx.biometric.BiometricPrompt.CryptoObject]
     * for biometric authentication.
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getCipher(encrypt: Boolean = false, ivBytes: ByteArray? = null, userAuthenticationRequired: Boolean = true): Cipher?

    /**
     * Returns the [ByteArray] after the encryption/decryption from the given [Cipher].
     * [cipher] the biometric cipher needed. This can be null but then an empty [ByteArray] is
     * returned.
     * [byteArray] that needed to be encrypted or decrypted (Depending always on [Cipher] provided.
     */
    fun encryptDecrypt(cipher: Cipher?, byteArray: ByteArray): ByteArray

    fun encryptPin(pin: String): Pair<String, String>

    fun verifyPin( attempt: String, storedSaltB64: String, storedHashB64: String): Boolean

    fun deriveKey(password: String, salt: ByteArray): ByteArray

    fun generateSalt():ByteArray
}

class CryptoControllerImpl(
    private val keystoreController: KeystoreController
) : CryptoController {

    companion object {
        private const val AES_EXTERNAL_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val GCM_TAG_SIZE_BITS = 128
        private const val CODE_VERIFIER_MIN = 43
        private const val CODE_VERIFIER_MAX = 128
        const val MAX_GUID_LENGTH = 64
        private const val SALT_BITS = 16
        private const val ITERATION_COUNT = 100_000
        private const val KEY_LENGTH = 256
    }


    override fun generateCodeVerifier(): String {
        val codeLength = (CODE_VERIFIER_MIN..CODE_VERIFIER_MAX).random()
        val random = SecureRandom()
        val code = ByteArray(codeLength)
        random.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    override fun getCipher(encrypt: Boolean, ivBytes: ByteArray?, userAuthenticationRequired: Boolean): Cipher? {
//        println("getCipher → enter; encrypt=$encrypt, ivBytes=${ivBytes?.size}, userAuthRequired=$userAuthenticationRequired")
        return try {
//            println("getCipher → Cipher.getInstance with transformation = $AES_EXTERNAL_TRANSFORMATION")
            val cipher = Cipher.getInstance(AES_EXTERNAL_TRANSFORMATION)
            if (encrypt) {
//                println("getCipher → init ENCRYPT_MODE")
                val key = keystoreController.retrieveOrGenerateSecretKey(userAuthenticationRequired)
//                println("getCipher → obtained secret key: $key")
                cipher.init(Cipher.ENCRYPT_MODE, key)
            } else {
                println("getCipher → init DECRYPT_MODE with ivBytes")
                val key = keystoreController.retrieveOrGenerateSecretKey(userAuthenticationRequired)
//                println("getCipher → obtained secret key: $key")
//                println(
//                    "getCipher → GCMParameterSpec with tagSize=$GCM_TAG_SIZE_BITS, iv=${
//                        ivBytes?.joinToString(
//                            ","
//                        )
//                    }"
//                )
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_SIZE_BITS, ivBytes ?: ByteArray(0))
                )
            }
//            println("getCipher → init completed successfully")
            cipher
        } catch (e: Exception) {
            println("getCipher → exception during cipher init: ${e.message}")
            e.printStackTrace()
            null
        }
    }


    // PBKDF2 with HmacSHA256
    override fun encryptPin(pin: String): Pair<String, String> {
        val salt = generateSalt()
        val key = deriveKey(pin, salt)

        val saltBase64 = Base64.encodeToString(salt, Base64.DEFAULT)
        val keyBase64 = Base64.encodeToString(key, Base64.DEFAULT)

        return saltBase64 to keyBase64

    }

    override fun verifyPin(
        attempt: String,
        storedSaltB64: String,
        storedHashB64: String
    ): Boolean {
        val salt      = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val storedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)
        val attemptHash = deriveKey(attempt, salt)
        if (storedHash.size != attemptHash.size) return false
        return storedHash.indices.all { storedHash[it] == attemptHash[it] }
    }


    override fun encryptDecrypt(cipher: Cipher?, byteArray: ByteArray): ByteArray {
        return cipher?.doFinal(byteArray) ?: ByteArray(0)
    }


    override fun generateSalt():ByteArray{
        val random = SecureRandom()
        val salt = ByteArray(SALT_BITS)
        random.nextBytes(salt)
        return salt
    }

    override fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATION_COUNT,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
