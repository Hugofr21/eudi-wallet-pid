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

package eu.europa.ec.storagelogic.infrastructure

import android.content.Context
import java.io.File

object BackupDatabase {

    private fun getDatabasePath(context: Context): File =
        context.getDatabasePath("eudi.app.wallet.storage")

    fun exportDatabase(context: Context, targetFile: File) {
        val src = getDatabasePath(context)
        src.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }


    fun restoreDatabase(context: Context, backupFile: File) {
        val dest = getDatabasePath(context)

        context.openOrCreateDatabase(dest.name, Context.MODE_PRIVATE, null).close()
        backupFile.inputStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}