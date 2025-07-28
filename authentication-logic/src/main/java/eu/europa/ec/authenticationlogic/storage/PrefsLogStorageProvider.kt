package eu.europa.ec.authenticationlogic.storage

import android.util.Base64
import eu.europa.ec.authenticationlogic.provider.LogStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.extension.decodeFromBase64
import eu.europa.ec.businesslogic.extension.decodeFromPemBase64String
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import java.security.MessageDigest

data class PrefsLogStorageProvider(
    private val prefsController: PrefsController,
    private val cryptoController: CryptoController
): LogStorageProvider{

    companion object {
        private const val KEY_SALT   = "DeviceLogSalt"
        private const val KEY_IV     = "DeviceLogIv"
        private const val KEY_CT     = "DeviceLogCiphertext"
    }

    override fun retrieveLogKey(): String{
        val ivB64   = prefsController.getString(KEY_IV,     "").ifEmpty { return "" }
        val ctB64   = prefsController.getString(KEY_CT,     "").ifEmpty { return "" }

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

    override fun setLogKey(key: String) {

        val saltByteArray = cryptoController.generateSalt()
        val keyByteArrayDerive = cryptoController.deriveKey(key, saltByteArray)

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
        prefsController.setString(KEY_CT, encryptedBytes.encodeToBase64String())
        prefsController.setString(KEY_IV, ivBytes.encodeToBase64String())
    }

    override fun isLogKeyValid(key: String): Boolean {

        val saltB64 = prefsController.getString(KEY_SALT, "")
        val ctB64 = prefsController.getString(KEY_CT, "")
        val ivB64   = prefsController.getString(KEY_IV,     "")

        val salt = saltB64.decodeFromPemBase64String() ?: return false
        val iv   = ivB64.decodeFromPemBase64String()   ?: return false
        val ct   = ctB64.decodeFromPemBase64String()   ?: return false

        val attemptDerived = cryptoController.deriveKey(key, salt)

        val cipher = cryptoController.getCipher(
            encrypt = false,
            ivBytes = iv,
            userAuthenticationRequired = false
        ) ?: return false

        val storedDerived = cryptoController.encryptDecrypt(cipher, ct)

        return MessageDigest.isEqual(storedDerived, attemptDerived)

    }

}
