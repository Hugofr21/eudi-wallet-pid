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

import eu.europa.ec.authenticationlogic.provider.PinStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.extension.decodeFromBase64
import eu.europa.ec.businesslogic.extension.decodeFromPemBase64String
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import java.security.MessageDigest

class PrefsPinStorageProvider(
    private val prefsController: PrefsController,
    private val cryptoController: CryptoController
) : PinStorageProvider {

    companion object {
        private const val KEY_SALT = "DevicePinSalt"
        private const val KEY_HASH         = "DevicePinHash"
        private const val KEY_IV         = "DevicePinIv"
    }

    override fun setPin(pin: String) {
        val saltByteArray = cryptoController.generateSalt()
        val keyByteArrayDerive = cryptoController.deriveKey(pin, saltByteArray)

        val cipher = cryptoController.getCipher(
            encrypt = true,
            userAuthenticationRequired = false
        )

        val encryptedBytes = cryptoController.encryptDecrypt(
            cipher = cipher,
            byteArray = keyByteArrayDerive
        )

        val ivBytes = cipher?.iv ?: return

        prefsController.setString(KEY_SALT, saltByteArray.encodeToBase64String())
        prefsController.setString(KEY_HASH, encryptedBytes.encodeToBase64String())
        prefsController.setString(KEY_IV, ivBytes.encodeToBase64String())
    }

    override fun retrievePin(): String {
        val ivB64   = prefsController.getString(KEY_IV,     "").ifEmpty { return "" }
        val ctB64   = prefsController.getString(KEY_HASH,     "").ifEmpty { return "" }

        val iv   = decodeFromBase64(ivB64)
        val ct   = decodeFromBase64(ctB64)

        val cipher = cryptoController.getCipher(
            encrypt = false,
            ivBytes = iv,
            userAuthenticationRequired = false
        )
        val decryptedBytes = cryptoController.encryptDecrypt(
            cipher = cipher,
            byteArray = ct
        )
        return decryptedBytes.toString(Charsets.UTF_8)
    }

    override fun isPinValid(pin: String): Boolean {
        val saltB64 = prefsController.getString(KEY_SALT, "")
        val ctB64 = prefsController.getString(KEY_HASH, "")
        val ivB64   = prefsController.getString(KEY_IV,     "")

        val salt = saltB64.decodeFromPemBase64String() ?: return false
        val iv   = ivB64.decodeFromPemBase64String()   ?: return false
        val ct   = ctB64.decodeFromPemBase64String()   ?: return false

        val attemptDerived = cryptoController.deriveKey(pin, salt)

        val cipher = cryptoController.getCipher(
            encrypt = false,
            ivBytes = iv,
            userAuthenticationRequired = false
        ) ?: return false

        val storedDerived = cryptoController.encryptDecrypt(cipher, ct)

        return MessageDigest.isEqual(storedDerived, attemptDerived)
    }
}
