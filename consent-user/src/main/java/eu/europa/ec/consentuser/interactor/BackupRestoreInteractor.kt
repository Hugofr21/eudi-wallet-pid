package eu.europa.ec.consentuser.interactor

import android.net.Uri
import eu.europa.ec.backuplogic.controller.BackupController


sealed class RestoreInteractorPartialState {
    data class Success(val msg: String) : RestoreInteractorPartialState()

    data class Failure(val error: String) : RestoreInteractorPartialState()
}


interface BackupRestoreInteractor {
    suspend fun restoreWallet(file: Uri?, passPhrase: List<String>): List<String>
}

class BackupRestoreInteractorImpl(
private val controller: BackupController
) : BackupRestoreInteractor {

    override suspend fun restoreWallet(
        file: Uri?,
        passPhrase: List<String>
    ): List<String> {
        require(passPhrase.isNotEmpty()) { "Passphrase cannot be empty." }
        val normalized = passPhrase.map { it.trim() }
        require(normalized.all { it.isNotEmpty() }) { "All words in the passphrase must be non-empty." }
         if (file != null) controller.restoreBackup(file, passPhrase)
        return emptyList()
    }
}