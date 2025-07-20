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

package eu.europa.ec.backuplogic.controller

import android.content.Context
import android.util.Base64
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.authenticationlogic.controller.authentication.PassphraseAuthenticationController
import eu.europa.ec.backuplogic.controller.dto.toDto
import eu.europa.ec.backuplogic.controller.model.BackupBundle
import eu.europa.ec.backuplogic.controller.model.Credential
import eu.europa.ec.backuplogic.controller.model.KdfParams
import eu.europa.ec.backuplogic.controller.model.MnemonicPhrase
import eu.europa.ec.backuplogic.controller.model.SecurityParams
import eu.europa.ec.backuplogic.controller.model.Signature
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.storagelogic.model.BackupLog
import eu.europa.ec.businesslogic.util.crypto.HashUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


interface BackupController {
    suspend fun exportBackup(passPhrase: String, provider: String):File?
    suspend fun deleteBackup(identifier: String)
    fun restoreBackup(file: File , passPhrase: String)

    suspend fun getLastBackup(): BackupLog?

}


class BackupControllerImpl(
    private val context: Context,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val cryptoController: CryptoController,
    private val passphraseAuthenticationController: PassphraseAuthenticationController,
    private val walletBackupLogController: WalletBackupLogController

) : BackupController  {
    companion object {
        private const val LOG_FILE_NAME_TXT = "eudi-android-wallet-backup%g.enc.json"
        private const val FILE_SIZE_LIMIT = 5242880
        private const val FILE_LIMIT = 10
        private const val NONCE_LENGTH = 12
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ALGORITHM_CREDENTIAL = "AES"

    }

    private val logsDir = File(context.filesDir.absolutePath + "/backup").apply { mkdirs() }

    /**
     *  Created a new backup file.
     *  Using the Key derivation function
     *  saving credentials in encrypted files.
     *  mnemonic phrase in plain text.
     */

    override suspend fun exportBackup(passPhrase: String, provider: String):File?{
        // save password in storage. this device
        passphraseAuthenticationController.setPassphrase(passPhrase)

        // created struct json file
        val json = assembleBackupBundle() ?: throw IllegalStateException("Backup bundle is null")

        // read json file and created zip
        val zipFile = createdFileJsonEncrypt(json)

        // created log backup
        val backupLog = BackupLog(
            identifier = HashUtils.sha256((provider + System.currentTimeMillis()).toByteArray()),
            provider = provider,
            createdAt = System.currentTimeMillis()
        )
        walletBackupLogController.storeBackupLog(backupLog)

        return zipFile
    }


    /**
     *  Delete backup before version, so current version can be restored
     */
    override suspend fun deleteBackup(identifier: String) {
        walletBackupLogController.deleteBackupLog(identifier)
        val file = File(logsDir, identifier)
        if (file.exists()) {
            file.delete()
        }
    }


    /**
     * Read the zip file and extract the JSON
     * Check the phrase that the user is using with this JSON
     * If valid, decrypt the file en.json
     * Read the contents and add the Wallet Controller document
     */
    override fun restoreBackup(file: File , passPhrase: String) {
        val zipInput = ZipInputStream(FileInputStream(file))
        var entry: ZipEntry? = zipInput.nextEntry
        var jsonString: String? = null

        while (entry != null) {
            if (entry.name.endsWith(".json")) {
                jsonString = zipInput.bufferedReader().use { it.readText() }
                break
            }
            entry = zipInput.nextEntry
        }

        val bundle = Gson().fromJson(jsonString, BackupBundle::class.java)
        val saltB64 = bundle.securityParams.mnemonicPhrase?.salt
        val expectedHashB64 = bundle.securityParams.mnemonicPhrase?.hash

        val saltBytes = Base64.decode(saltB64, Base64.NO_WRAP)
        val derivedKeyBytes = cryptoController.deriveKey(passPhrase, saltBytes)
        val derivedHashB64 = Base64.encodeToString(derivedKeyBytes, Base64.NO_WRAP)

        if (!derivedHashB64.contentEquals(expectedHashB64)) {
            throw SecurityException("Passphrase invalid!!!!")
        }



    }

    override suspend fun getLastBackup(): BackupLog? {
        val now = System.currentTimeMillis()
        return walletBackupLogController.getClosestBackupLog(now)

    }

    /**
         * Get the hash and sslr used to create the pbkdf2 derivative of EncryptedSharedPreferences
         * Get Verifiable Credentials in the wallet controller
         * Encrypt credentials
         * Create backup bundle (
         * Create json
         * Encrypted AES file
    */

    private fun assembleBackupBundle(): String?  {

        val documentsDto = walletCoreDocumentsController
            .getAllDocuments()
            .map { it.toDto() }

        val (saltB64, hashB64) = passphraseAuthenticationController
             .getSaltAndHash()
             ?: return null

        val iv = ByteArray(NONCE_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
        val key = Base64.decode(hashB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(key, ALGORITHM_CREDENTIAL)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val kdf = KdfParams(
            alg = ALGORITHM,
            iters = ITERATIONS,
            keyLen = KEY_LENGTH
        )


        val mnemonicPhrase = MnemonicPhrase(
            salt = saltB64,
            hash = hashB64
        )

        val securityParams = SecurityParams(
            requireBiometric = false,
            requirePin = false,
            authValidityDurationSeconds = 30,
            mnemonicPhrase = mnemonicPhrase
        )

        val credentials = documentsDto.map { documentDto ->
            val plain = documentDto.toString().toByteArray(Charsets.UTF_8)
            val encrypted = cipher.doFinal(plain)
            Credential(
                id = documentDto.id,
                type = documentDto.format::class.simpleName ?: "unknown",
                alg = ALGORITHM_CREDENTIAL,
                iv = iv,
                data = encrypted
            )
        }

        val backupData = BackupBundle(
            kdf = kdf,
            securityParams = securityParams,
            credentials = credentials,
            signature = null
        )

        val gson = Gson()
        val jsonPartial = gson.toJson(backupData)
        val hmacBytes = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(jsonPartial.toByteArray(Charsets.UTF_8))
        backupData.signature = Signature(
            alg = "HmacSHA256",
            sig = Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
        )

        val finalJson = gson.toJson(backupData)

        return finalJson

    }

    /**
     * 1) Generates JSON with encrypted data and HMAC
     * 2) Creates backup.json, zip, and then encrypts the zip
     * @return File “backup-{timestamp}.zip.enc” in logsDir
     */

    private fun createdFileJsonEncrypt(json: String): File{
        val jsonFile = File(logsDir, LOG_FILE_NAME_TXT)
        jsonFile.writeText(json)

        val zipFile = File(logsDir, "backup_${System.currentTimeMillis()}.zip")

        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val entry = ZipEntry(jsonFile.name)
                zos.putNextEntry(entry)

                jsonFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }

        //jsonFile.delete()
        return zipFile

    }

}
