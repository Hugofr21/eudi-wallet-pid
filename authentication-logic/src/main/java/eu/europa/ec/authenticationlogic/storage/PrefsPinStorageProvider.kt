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
        private const val KEY_HASH = "DevicePinHash"
    }

    override fun setPin(pin: String) {
        val (saltB64, hashB64) = cryptoController.hashPin(pin)

        prefsController.setString(KEY_SALT, saltB64)
        prefsController.setString(KEY_HASH, hashB64)
    }

    override fun isPinValid(pin: String): Boolean {
        val storedSalt = prefsController.getString(KEY_SALT, "")
        val storedHash = prefsController.getString(KEY_HASH, "")

        if (storedSalt.isEmpty() || storedHash.isEmpty()) {
            return false
        }

        return cryptoController.verifyPin(pin, storedSalt, storedHash)
    }

    override fun hasPin(): Boolean {
        return prefsController.getString(KEY_HASH, "").isNotEmpty()
    }

    override fun clearPin() {
        prefsController.setString(KEY_SALT, "")
        prefsController.setString(KEY_HASH, "")
    }
}