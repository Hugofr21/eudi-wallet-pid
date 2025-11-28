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
import eu.europa.ec.businesslogic.controller.storage.PrefsController

class PrefsPassphraseStorageProvider(
    private val prefs: PrefsController,
    private val crypto: CryptoController
) : PassphraseStorageProvider {

    companion object {
        private const val KEY_HASH = "DevicePassphraseHash"
        private const val KEY_IV   = "DevicePassphraseIv"
        private const val KEY_SALT   = "DevicePassphraseSalt"
    }

    override fun hasPassphrase(): Boolean =
        prefs.getString(KEY_HASH, "").isNotBlank()

    override fun getHash(): String? =
        prefs.getString(KEY_HASH, "").takeIf { it.isNotBlank() }

    /**
     * Derive and store both:
     *  • the PBKDF2 key (as Base64) under KEY_HASH, and
     *  • the GCM IV    (as Base64) under KEY_IV.
     */
    override fun setPassphrase(passphrase: List<String>) {
        println("PrefsPassphraseStorageProvider  setPassphrase $passphrase")
        val saltBytes = crypto.generateSaltFromMnemonic(passphrase)
        val keyBytes  = crypto.deriveKeyFromMnemonic(passphrase, saltBytes)
        val ivBytes   = crypto.deriveIvFromMnemonic(passphrase)


        prefs.setString(KEY_SALT, Base64.encodeToString(saltBytes, Base64.NO_WRAP))
        prefs.setString(KEY_HASH, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
        prefs.setString(KEY_IV,   Base64.encodeToString(ivBytes, Base64.NO_WRAP))

//        println("Salt (hex): ${saltBytes.joinToString("") { "%02x".format(it) }}")
//        println("Salt (Base64): ${Base64.encodeToString(saltBytes, Base64.NO_WRAP)}")
//
//        println("Key (hex): ${keyBytes.joinToString("") { "%02x".format(it) }}")
//        println("Key (Base64): ${Base64.encodeToString(keyBytes, Base64.NO_WRAP)}")
//
//        println("IV (hex): ${ivBytes.joinToString("") { "%02x".format(it) }}")
//        println("IV (Base64): ${Base64.encodeToString(ivBytes, Base64.NO_WRAP)}")
    }

    /**
     * Verify a passphrase by re‑deriving & comparing against the stored hash.
     */
    override fun verifyPassphrase(input: List<String>): Boolean {
        val stored = prefs.getString(KEY_HASH, "")
        if (stored.isBlank()) return false
        return crypto.verifyPhrase(input, stored)
    }

    /**
     * Return the raw key bytes (to use for AES) or throw if missing.
     */
    override fun retrieveKeyBytes(): ByteArray {
        val keyB64 = prefs.getString(KEY_HASH, "")
        if (keyB64.isBlank()) {
            throw IllegalStateException("No passphrase hash stored in preferences")
        }
        val decoded = Base64.decode(keyB64, Base64.NO_WRAP)
        return decoded
    }

    /**
     * Return the raw GCM IV (to use for AES/GCM) or throw if missing.
     */
    override fun retrieveIv(): ByteArray {
        val ivB64 = prefs.getString(KEY_IV, "")
        if (ivB64.isBlank()) {
            throw IllegalStateException("No passphrase hash stored in preferences")
        }
        return Base64.decode(ivB64, Base64.NO_WRAP)
    }

    /**
     * Return the raw GCM IV (to use for AES/GCM) or throw if missing.
     */
    override fun retrieveSalt(): ByteArray {
        val saltB64 = prefs.getString(KEY_SALT, "")
        if (saltB64.isBlank()) {
            throw IllegalStateException("No passphrase hash stored in preferences")
        }
        return Base64.decode(saltB64, Base64.NO_WRAP)
    }
}
