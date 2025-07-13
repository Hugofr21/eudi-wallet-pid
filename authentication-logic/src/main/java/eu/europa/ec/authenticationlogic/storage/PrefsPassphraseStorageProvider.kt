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

import eu.europa.ec.authenticationlogic.provider.PassphraseStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import java.security.MessageDigest

class PrefsPassphraseStorageProvider(
    private val prefsController: PrefsController,
    private val cryptoController: CryptoController
) : PassphraseStorageProvider {

    companion object {
        private const val KEY_PASSPHRASE  = "DevicePassphrase"
        private const val KEY_IV          = "DevicePassphraseIv"
    }

    override fun getPassphrase(): String? {

        val encryptedB64 = prefsController.getString(KEY_PASSPHRASE, "")
        val ivB64        = prefsController.getString(KEY_IV, "")
        if (encryptedB64.isBlank() || ivB64.isBlank()) return null

        val encrypted = android.util.Base64.decode(encryptedB64, android.util.Base64.DEFAULT)
        val iv        = android.util.Base64.decode(ivB64, android.util.Base64.DEFAULT)


        val cipher = cryptoController.getCipher(
            encrypt = false,
            ivBytes = iv,
            userAuthenticationRequired = true
        ) ?: return null

        val decryptedBytes = cryptoController.encryptDecrypt(cipher, encrypted)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    override fun getHasPassphrase(): Boolean {

        val hasEncrypted = prefsController.getString(KEY_PASSPHRASE, "").isBlank().not()
        val hasIv        = prefsController.getString(KEY_IV, "").isBlank().not()
        return hasEncrypted && hasIv
    }

    override fun retrievePassphrase(): String {
        return getPassphrase()
            ?: throw IllegalStateException("Passphrase não configurada.")
    }

    override fun setPassphrase(passphrase: String) {
        // Cria um cipher em modo ENCRYPT
        val cipher = cryptoController.getCipher(
            encrypt = true,
            ivBytes = null,
            userAuthenticationRequired = true
        ) ?: throw IllegalStateException("Não foi possível inicializar cipher")


        val plaintextBytes = passphrase.toByteArray(Charsets.UTF_8)
        val encryptedBytes = cryptoController.encryptDecrypt(cipher, plaintextBytes)

        val iv = cipher.iv

        val encryptedB64 = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.DEFAULT)
        val ivB64        = android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT)

        prefsController.setString(KEY_PASSPHRASE, encryptedB64)
        prefsController.setString(KEY_IV, ivB64)
    }

    override fun isPassphraseValid(passphrase: String): Boolean {
        val stored = getPassphrase() ?: return false
        return MessageDigest.isEqual(
            passphrase.toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8)
        )
    }
}
