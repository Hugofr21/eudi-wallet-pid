package eu.europa.ec.authenticationlogic.storage

import eu.europa.ec.authenticationlogic.provider.LogStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.extension.decodeFromBase64
import eu.europa.ec.businesslogic.extension.encodeToBase64String

data class PrefsLogStorageProvider(
    private val prefsController: PrefsController,
    private val cryptoController: CryptoController
): LogStorageProvider{

    companion object {
        private const val KEY_ENC = "DeviceLogEnc"
        private const val KEY_IV         = "DeviceLogIv"
        private const val KEY_DERIVE = "DeviceLogKey"
        private const val SALT         = "DeviceLogSalt"
    }

    override fun retrieveLogKey(): String{
        val saltB64 = prefsController.getString(SALT, "").ifEmpty { return "" }
        val hashB64 = prefsController.getString(KEY_DERIVE, "").ifEmpty { return "" }
        val encryptedBase64 = prefsController.getString(KEY_ENC, "").ifEmpty { return "" }
        val ivBase64 = prefsController.getString(KEY_IV, "").ifEmpty { return "" }
        val cipher = cryptoController.getCipher(
            encrypt = false,
            ivBytes = decodeFromBase64(ivBase64),
            userAuthenticationRequired = false
        )
        val decryptedBytes = cryptoController.encryptDecrypt(
            cipher = cipher,
            byteArray = decodeFromBase64(encryptedBase64)
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

        prefsController.setString(SALT, saltByteArray.encodeToBase64String())
        prefsController.setString(KEY_DERIVE, keyByteArrayDerive.encodeToBase64String())
        prefsController.setString(KEY_ENC, encryptedBytes.encodeToBase64String())
        prefsController.setString(KEY_IV, ivBytes.encodeToBase64String())
    }

    override fun isLogKeyValid(key: String): Boolean = retrieveLogKey() == key

}
