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
        private const val KEY_SALT = "DevicePassphraseSalt"
        private const val KEY_DATA = "DevicePassphraseEncrypted"
        private const val KEY_IV = "DevicePassphraseIv"
    }

    override fun hasPassphrase(): Boolean {
        return prefs.getString(KEY_HASH, "").isNotBlank() &&
                prefs.getString(KEY_SALT, "").isNotBlank()
    }

    override fun getSaltAndHash(): Pair<String, String>?  {
        val saltB64 = prefs.getString(KEY_SALT, "")
        val hashB64 = prefs.getString(KEY_HASH, "")
        return if (saltB64.isNotBlank() && hashB64.isNotBlank()) {
            saltB64 to hashB64
        } else null
    }

    override fun setPassphrase(passphrase: List<String>) {

        val salt = crypto.generateSalt()
        val keyBytes = crypto.deriveKeyList(passphrase, salt)

        prefs.setString(KEY_HASH, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
        prefs.setString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))

        val cipher = crypto.getCipher(encrypt = true)
            ?: throw IllegalStateException("Cipher init failed")
        val joinedPassphrase = passphrase.joinToString(" ")
        val encrypted = crypto.encryptDecrypt(cipher, joinedPassphrase.toByteArray(Charsets.UTF_8))
        prefs.setString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
        prefs.setString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
    }

    override fun verifyPassphrase(input: List<String>): Boolean {
        val saltB64 = prefs.getString(KEY_SALT, "")
        val hashB64 = prefs.getString(KEY_HASH, "")
        if (saltB64.isBlank() || hashB64.isBlank()) return false
        return crypto.verifyPhrase(input, saltB64, hashB64)
    }

    override fun retrievePassphrase(): String {
        val dataB64 = prefs.getString(KEY_DATA, "")
        val ivB64 = prefs.getString(KEY_IV, "")
        if (dataB64.isBlank() || ivB64.isBlank()) {
            throw IllegalStateException("Encrypted passphrase not found")
        }
        val encrypted = Base64.decode(dataB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipher = crypto.getCipher(encrypt = false, ivBytes = iv)
            ?: throw IllegalStateException("Cipher init failed")
        val plainBytes = crypto.encryptDecrypt(cipher, encrypted)
        return String(plainBytes, Charsets.UTF_8)
    }

}
