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
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


interface CryptoController {

    /**
     * Generates a code verifier for Proof Key for Code Exchange (PKCE).
     *
     * This function generates a cryptographically random string that is used as the code
     * verifier in the PKCE flow. The code verifier is a high-entropy string that is
     * difficult to guess. It is used to protect against authorization code interception attacks.
     *
     * The generated code verifier is a Base64 URL-safe encoded string without padding or wrapping.
     *
     * @return A [String] representing the generated code verifier.
     */
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

    fun hashPin(pin: String): Pair<String, String>

    fun verifyPhrase(attempt:List<String>, storedHashB64: String): Boolean

    fun deriveKey(password: String, salt: ByteArray): ByteArray

    fun deriveKeyFromMnemonic(password: List<String>, salt: ByteArray): ByteArray

    fun generateSalt():ByteArray

    fun generateSaltFromMnemonic(mnemonic: List<String>):ByteArray

    fun deriveIvFromMnemonic(mnemonic: List<String>): ByteArray

    fun computeVerifier(keyBytes: ByteArray): ByteArray

    fun generateIv(): ByteArray
}

class CryptoControllerImpl(
    private val keystoreController: KeystoreController
) : CryptoController {

    companion object {
        private const val AES_EXTERNAL_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val CODE_VERIFIER_MIN = 43
        private const val CODE_VERIFIER_MAX = 128
        const val MAX_GUID_LENGTH = 64
        private const val SALT_BITS = 16

        private const val PBKDF2_ITERATIONS = 310_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH_BYTES = 32
        private const val GCM_IV_LENGTH_BYTES = 12
    }


    override fun generateCodeVerifier(): String {
        val codeLength = (CODE_VERIFIER_MIN..CODE_VERIFIER_MAX).random()
        val random = SecureRandom()
        val code = ByteArray(codeLength)
        random.nextBytes(code)
        return Base64.encodeToString(
            code,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
            .take(codeLength)
    }

    override fun getCipher(
        encrypt: Boolean,
        ivBytes: ByteArray?,
        userAuthenticationRequired: Boolean
    ): Cipher? {
        return try {
            val cipher = Cipher.getInstance(AES_EXTERNAL_TRANSFORMATION)
            val key = keystoreController.retrieveOrGenerateSecretKey(userAuthenticationRequired)
            if (encrypt) {
//                println("getCipher → init ENCRYPT_MODE with ivBytes")
                cipher.init(Cipher.ENCRYPT_MODE, key)
            } else {
//                println("getCipher → init DECRYPT_MODE with ivBytes")
                if (ivBytes == null || ivBytes.isEmpty()) throw IllegalArgumentException("IV required for decryption")
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)
            }
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


    override fun encryptDecrypt(cipher: Cipher?, byteArray: ByteArray): ByteArray {
        return cipher?.doFinal(byteArray) ?: ByteArray(0)
    }


    override fun verifyPin(
        attempt: String,
        storedSaltB64: String,
        storedHashB64: String
    ): Boolean {
        return try {
            val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
            val storedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)
            val attemptHash = deriveKey(attempt, salt)
            MessageDigest.isEqual(storedHash, attemptHash)
        } catch (e: Exception) {
            false
        }
    }

    override fun hashPin(pin: String): Pair<String, String> {
        val salt = generateSalt()
        val hash = deriveKey(pin, salt)

        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return saltB64 to hashB64
    }

    override fun verifyPhrase(
        attempt: List<String>,
        storedHashB64: String
    ): Boolean {
        val salt = generateSaltFromMnemonic(attempt)
        val storedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)
        val attemptHash = deriveKeyFromMnemonic(attempt, salt)
        if (storedHash.size != attemptHash.size) return false
        return storedHash.indices.all { storedHash[it] == attemptHash[it] }
    }


    override fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val normalizedPassword = Normalizer.normalize(password, Normalizer.Form.NFKD)
        val spec = PBEKeySpec(
            normalizedPassword.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    override fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BITS)
        random.nextBytes(salt)
        return salt
    }


    /**
     * Derivation function (KDF) based on the HMAC
     * Normalize sentence because of unicode characters
     * HMAC-based Extract-and-Expand Key Derivation Function (HKDF) rfc5869
     */

    override fun generateSaltFromMnemonic(mnemonic: List<String>): ByteArray {
        val normalized = Normalizer.normalize(mnemonic.joinToString(" "), Normalizer.Form.NFKD)
        val seed = ("$normalized::salt").toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)
        return hash.copyOfRange(0, 16)
    }


    override fun deriveKeyFromMnemonic(password: List<String>, salt: ByteArray): ByteArray {
        val mnemonicString = Normalizer.normalize(
            password.joinToString(" ").trim(),
            Normalizer.Form.NFKD
        )
        val spec = PBEKeySpec(
            mnemonicString.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec).encoded
        return key
    }

    override fun deriveIvFromMnemonic(mnemonic: List<String>): ByteArray {
        val phrase = mnemonic.joinToString(" ").trim()
        val normalized = Normalizer
            .normalize(phrase, Normalizer.Form.NFKD)
            .toByteArray(Charsets.UTF_8)
        val hash = MessageDigest
            .getInstance("SHA-256")
            .digest(normalized)
        return hash.copyOfRange(0, 12)
    }

    /** HMAC-SHA256(key, domain-separator) → one-way verifier. */
    override fun computeVerifier(keyBytes: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal("eudiw-passphrase-verifier-v1".toByteArray(Charsets.UTF_8))
    }

    override fun generateIv(): ByteArray =
        ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom.getInstanceStrong().nextBytes(it) }

}