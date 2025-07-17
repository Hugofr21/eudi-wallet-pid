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
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.storagelogic.model.BackupLog
import java.io.File
import java.util.UUID


interface BackupController {
    fun exportBackup(passPhrase: CharArray, provider: String)
    fun importBackup(passPhrase: CharArray)
    fun deleteBackup()
    fun restoreBackup(passPhrase: CharArray)
}


class BackupControllerImpl(
    private val context: Context,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
) : BackupController  {
    companion object {
        private const val LOG_FILE_NAME_TXT = "eudi-android-wallet-backup%g.zip"
        private const val FILE_SIZE_LIMIT = 5242880
        private const val FILE_LIMIT = 10
    }

    private val logsDir = File(context.filesDir.absolutePath + "/backup")


    override fun exportBackup(passPhrase: CharArray, provider: String) {

        val backupLog = BackupLog(
            identifier = UUID.randomUUID().toString(),
            provider = provider,
            createdAt = System.currentTimeMillis()
        )
        TODO("Not yet implemented")

    }

    override fun importBackup(passPhrase: CharArray) {
        TODO("Not yet implemented")
    }

    override fun deleteBackup() {
        TODO("Not yet implemented")
    }

    override fun restoreBackup(passPhrase: CharArray) {
        TODO("Not yet implemented")
    }

}
