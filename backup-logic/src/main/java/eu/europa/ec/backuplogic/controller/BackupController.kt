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
import java.io.File

interface BackupController {
    fun exportBackup(value: CharArray)
    fun importBackup(value: CharArray)
    fun deleteBackup()
    fun restoreBackup(value: CharArray)
}


class BackupControllerImpl(
    private val context: Context
) : BackupController  {
    companion object {
        private const val LOG_FILE_NAME_TXT = "eudi-android-wallet-backup%g.zip"
        private const val FILE_SIZE_LIMIT = 5242880
        private const val FILE_LIMIT = 10
    }

    private val logsDir = File(context.filesDir.absolutePath + "/backup")


    override fun exportBackup(key: CharArray) {
        TODO("Not yet implemented")
    }

    override fun importBackup(value: CharArray) {
        TODO("Not yet implemented")
    }

    override fun deleteBackup() {
        TODO("Not yet implemented")
    }

    override fun restoreBackup(value: CharArray) {
        TODO("Not yet implemented")
    }

}
