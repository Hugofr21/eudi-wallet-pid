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
import eu.europa.ec.authenticationlogic.provider.PassphraseStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.CryptoControllerImpl
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import java.security.MessageDigest

class PrefsPassphraseStorageProvider(
    private val prefs: PrefsController,
    private val crypto: CryptoController,
) : PassphraseStorageProvider {

    companion object {
        private const val KEY_VERIFIER = "DevicePassphraseVerifier"
        private const val KEY_IV       = "DevicePassphraseIv"
        private const val KEY_SALT     = "DevicePassphraseSalt"

    }

    override fun hasPassphrase(): Boolean =
        prefs.getString(KEY_VERIFIER, "").isNotBlank()

    /** Returns the stored HMAC verifier (Base64), NOT the key. */
    override fun getHash(): String? =
        prefs.getString(KEY_VERIFIER, "").takeIf { it.isNotBlank() }

    /**
     * Derives and stores:
     *   - Random 32-byte salt  -> KEY_SALT
     *   - Random 12-byte IV    -> KEY_IV
     *   - HMAC verifier        -> KEY_VERIFIER  (key is wiped, never stored)
     */
    override fun setPassphrase(passphrase: List<String>) {
        val saltBytes = crypto.generateSalt()
        val ivBytes   = crypto.generateIv()

        val keyBytes  = crypto.deriveKeyFromMnemonic(passphrase, saltBytes)
        try {
            val verifier = crypto.computeVerifier(keyBytes)
            prefs.setString(KEY_SALT,     Base64.encodeToString(saltBytes, Base64.NO_WRAP))
            prefs.setString(KEY_IV,       Base64.encodeToString(ivBytes,   Base64.NO_WRAP))
            prefs.setString(KEY_VERIFIER, Base64.encodeToString(verifier,  Base64.NO_WRAP))
        } finally {
            keyBytes.fill(0)   // wipe key from heap
        }
    }

    /**
     * Re-derives the key from [input] + stored salt, then compares
     * HMAC verifiers using constant-time MessageDigest.isEqual().
     * [MED-1] Constant-time: prevents timing oracle.
     */
    override fun verifyPassphrase(input: List<String>): Boolean {
        val storedB64 = prefs.getString(KEY_VERIFIER, "")
        if (storedB64.isBlank()) return false

        val saltBytes = retrieveSalt()
        val keyBytes  = crypto.deriveKeyFromMnemonic(input, saltBytes)
        return try {
            val computed = crypto.computeVerifier(keyBytes)
            val stored   = Base64.decode(storedB64, Base64.NO_WRAP)
            MessageDigest.isEqual(computed, stored)   // constant-time
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * [HIGH-1] Key is never stored – always throws.
     * - BackupController must call:
     * - crypto.deriveKey(passphrase, retrieveSalt())
     */
    override fun retrieveKeyBytes(): ByteArray =
        throw UnsupportedOperationException(
            "The passphrase key is never persisted. " +
                    "Re-derive it with: crypto.deriveKey(passphrase, retrieveSalt(), iterations, keyLen)"
        )

    override fun retrieveIv(): ByteArray {
        val b64 = prefs.getString(KEY_IV, "")
        check(b64.isNotBlank()) { "No IV stored – call setPassphrase() first." }
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    override fun retrieveSalt(): ByteArray {
        val b64 = prefs.getString(KEY_SALT, "")
        check(b64.isNotBlank()) { "No salt stored – call setPassphrase() first." }
        return Base64.decode(b64, Base64.NO_WRAP)
    }
}