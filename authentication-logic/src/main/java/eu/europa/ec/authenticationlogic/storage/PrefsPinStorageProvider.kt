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
import eu.europa.ec.authenticationlogic.provider.PinStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController

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
        val (saltB64, hashB64) = cryptoController.encryptPin(pin)
//        val cipher = cryptoController.getCipher(
//            encrypt = true,
//            ivBytes = null,
//            userAuthenticationRequired = false
//        )
//
//        val encryptedPin = cryptoController.encryptDecrypt(
//            cipher,
//            pin.toByteArray(Charsets.UTF_8)
//        )
//
//        val cipherText = cryptoController.encryptDecrypt(cipher,encryptedPin)
        println("Salt setPin: $saltB64")
        println("Hash setPin: $hashB64")
//        println("IV setPin: $cipherText")
//
//        prefsController.setString(KEY_IV, Base64.encodeToString(cipherText, Base64.NO_WRAP))
        prefsController.setString(KEY_SALT, saltB64)
        prefsController.setString(KEY_HASH, hashB64)
    }

    override fun retrievePin(): String {
        val salt = prefsController.getString(KEY_SALT, "")
        val hash = prefsController.getString(KEY_HASH, "")
        val iv = prefsController.getString(KEY_IV, "")
        print("Salt: $salt")
        print("hash: $hash")
        if (salt.isEmpty()  || hash.isEmpty()) return ""
        val saltDecode = Base64.decode(salt, Base64.NO_WRAP)
        val ivDecode = Base64.decode(iv, Base64.NO_WRAP)
        print("Salt decode: $saltDecode")
        print("IV decode: $ivDecode")
        return hash
    }

    override fun isPinValid(pin: String): Boolean {
        val saltB64 = prefsController.getString(KEY_SALT, "")
        val hashB64 = prefsController.getString(KEY_HASH, "")
        if (saltB64.isEmpty() || hashB64.isEmpty()) return false
        val verifyPin = cryptoController.verifyPin(pin, saltB64, hashB64)
//        print("Verify pin: $verifyPin")
        return verifyPin
    }
}
