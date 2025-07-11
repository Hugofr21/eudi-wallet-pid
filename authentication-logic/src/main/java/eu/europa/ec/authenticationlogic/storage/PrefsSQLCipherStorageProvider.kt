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

package eu.europa.ec.authenticationlogic.storage

import android.util.Base64
import eu.europa.ec.authenticationlogic.provider.SQLCipherStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import java.security.SecureRandom
import javax.crypto.Cipher

/**
 * Provider that generates, encrypts, and stores the passphrase for SQLCipher.
 * Uses PrefsController to store Base64 values (strings).
 * Encryption via CryptoController.getCipher() + encryptDecrypt().
 */
class PrefsSQLCipherStorageProvider(
    private val prefsController: PrefsController,
    private val cryptoController: CryptoController
) : SQLCipherStorageProvider {

    companion object {
        private const val KEY_SALT       = "SQL_SALT"
        private const val KEY_IV         = "SQL_IV"
        private const val KEY_CIPHERTEXT = "SQL_CIPHERTEXT"
        private const val SALT_LEN       = 16
        private const val PASSPHRASE_LEN = 32
    }

    private val secureRandom = SecureRandom()

    /** Returns true if there is already a stored passphrase (IV and ciphertext in prefs). */
    override fun hasKey(): Boolean =
        prefsController.contains(KEY_IV) && prefsController.contains(KEY_CIPHERTEXT)

    /**
     * Defines the passphrase: generates salt, derives key, encrypts with AES/GCM via Keystore and stores it.
     */
    override fun setSQLCipherKey(value: String) {

        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        prefsController.setString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))

        val derived = cryptoController.deriveKey(value, salt)
        val passphrase = Base64.encodeToString(derived, Base64.NO_WRAP)

        val cipher: Cipher = cryptoController.getCipher(
            encrypt = true,
            ivBytes = null,
            userAuthenticationRequired = false
        )
            ?: throw IllegalStateException("Not found cipher!")
        val ciphertext = cryptoController.encryptDecrypt(cipher, passphrase.toByteArray(Charsets.UTF_8))
        val ivBytes    = cipher.iv

        prefsController.setString(KEY_IV, Base64.encodeToString(ivBytes, Base64.NO_WRAP))
        prefsController.setString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
    }

    /**
     * Retrieves the SQLCipher passphrase: if it does not exist, generate a new one and return it;
     * if exists, decrypt and return Base64 string.
     */
    override fun retrieveSQLCipherKey(): String {
        val ivB64     = prefsController.getString(KEY_IV, "")
        val cipherB64 = prefsController.getString(KEY_CIPHERTEXT, "")

        return if (ivB64.isEmpty() || cipherB64.isEmpty()) {
            val randomBytes = ByteArray(PASSPHRASE_LEN).also { secureRandom.nextBytes(it) }
            val randomValue = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
            setSQLCipherKey(randomValue)
            randomValue
        } else {
            val ivBytes     = Base64.decode(ivB64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
            val cipher: Cipher = cryptoController.getCipher(
                encrypt = false,
                ivBytes = ivBytes,
                userAuthenticationRequired = false
            )
                ?: throw IllegalStateException("Cipher not available for decryption")
            val decrypted = cryptoController.encryptDecrypt(cipher, cipherBytes)
            String(decrypted, Charsets.UTF_8)
        }
    }

    /**
     * Checks if the value (KEY) generates the same passphrase as stored.
     */
    override fun isSQLCipherKeyValid(value: String): Boolean {
        val saltB64 = prefsController.getString(KEY_SALT, "")
        if (saltB64.isEmpty()) return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val derived = cryptoController.deriveKey(value, salt)
        val candidate = Base64.encodeToString(derived, Base64.NO_WRAP)
        return candidate == retrieveSQLCipherKey()
    }


}
