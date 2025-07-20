package eu.europa.ec.backuplogic.controller

import eu.europa.ec.storagelogic.model.BackupLog
import eu.europa.ec.storagelogic.dao.BackupLogDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface WalletBackupLogController {
    /**
     * Stores a new backup log entry.
     */
    suspend fun storeBackupLog(log: BackupLog)

    /**
     * Stores multiple backup log entries.
     */
    suspend fun getStoreBackupLogs(logs: List<BackupLog>)

    /**
     * Retrieves a backup log by its identifier.
     */
    suspend fun getBackupLog(identifier: String): BackupLog?

    /**
     * Retrieves all backup logs.
     */
    suspend fun getAllBackupLogs(): List<BackupLog>

    /**
     * Updates an existing backup log.
     */
    suspend fun updateBackupLog(log: BackupLog)

    /**
     * Deletes a backup log by its identifier.
     */
    suspend fun deleteBackupLog(identifier: String)

    /**
     * Deletes all backup logs.
     */
    suspend fun deleteAllBackupLogs()

    /**
     * Retrieves the backup log closest to the specified time.
     */
    suspend fun getClosestBackupLog(currentTime: Long): BackupLog?
}


class WalletBackupLogControllerImpl(
    private val backupLogDao: BackupLogDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WalletBackupLogController {

    override suspend fun storeBackupLog(log: BackupLog) = withContext(dispatcher) {
        try {
            backupLogDao.store(log)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to store backup log: ${e.message}", e)
        }
    }

    override suspend fun getStoreBackupLogs(logs: List<BackupLog>) = withContext(dispatcher) {
        try {
            backupLogDao.storeAll(logs)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to store backup logs: ${e.message}", e)
        }
    }

    override suspend fun getBackupLog(identifier: String): BackupLog? = withContext(dispatcher) {
        try {
            backupLogDao.retrieve(identifier)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to retrieve backup log: ${e.message}", e)
        }
    }

    override suspend fun getAllBackupLogs(): List<BackupLog> = withContext(dispatcher) {
        try {
            backupLogDao.retrieveAll()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to retrieve all backup logs: ${e.message}", e)
        }
    }

    override suspend fun updateBackupLog(log: BackupLog) = withContext(dispatcher) {
        try {
            backupLogDao.update(log)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to update backup log: ${e.message}", e)
        }
    }

    override suspend fun deleteBackupLog(identifier: String) = withContext(dispatcher) {
        try {
            backupLogDao.delete(identifier)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to delete backup log: ${e.message}", e)
        }
    }

    override suspend fun deleteAllBackupLogs() = withContext(dispatcher) {
        try {
            backupLogDao.deleteAll()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to delete all backup logs: ${e.message}", e)
        }
    }

    override suspend fun getClosestBackupLog(currentTime: Long): BackupLog? = withContext(dispatcher) {
        try {
            backupLogDao.retrieveClosest(currentTime)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to retrieve closest backup log: ${e.message}", e)
        }
    }
}