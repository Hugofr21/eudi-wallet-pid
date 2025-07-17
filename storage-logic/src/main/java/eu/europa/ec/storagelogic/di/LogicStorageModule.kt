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

package eu.europa.ec.storagelogic.di

import android.content.Context
import android.util.Base64
import androidx.room.Room
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.storagelogic.dao.BackupLogDao
import eu.europa.ec.storagelogic.service.DatabaseService
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.annotation.Singleton
import com.google.inject.Provides;
import eu.europa.ec.authenticationlogic.controller.storage.SQLCipherStorageController
import eu.europa.ec.storagelogic.dao.IssuerLogDao
import java.security.SecureRandom

@Module
@ComponentScan("eu.europa.ec.storagelogic")
class LogicStorageModule


@Provides
@Singleton
fun provideAppDatabase(
    ctx: Context,
    storage: SQLCipherStorageController
): DatabaseService {
    if (!storage.hasSQLCipherKey()) {
        val randomBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val randomValue = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
//        println("MyApp: SQLCipher passphrase not found, generating...")
//        println("Random bytes: (length=${randomValue.length})")
//        println("Random passphrase: (length=${randomValue.length})")
        storage.setSQLCipherKey(randomValue)
    }

    val passphrase = storage.retrieveSQLCipherKey()
    val keyBytes   = SQLiteDatabase.getBytes(passphrase.toCharArray())
    val factory    = SupportFactory(keyBytes)
    return Room.databaseBuilder(ctx, DatabaseService::class.java, "eudi.app.wallet.storage")
        .openHelperFactory(factory)
//        .fallbackToDestructiveMigration(true)
        .build()
}


@Single
fun provideBookmarkDao(service: DatabaseService): BookmarkDao = service.bookmarkDao()

@Single
fun provideRevokedDocumentDao(service: DatabaseService): RevokedDocumentDao =
    service.revokedDocumentDao()

@Single
fun provideTransactionLogDao(service: DatabaseService): TransactionLogDao =
    service.transactionLogDao()

@Single
fun provideBackupLogDao(service: DatabaseService): BackupLogDao =
    service.backupLogDao()

@Single
fun provideIssuerLogDao(service: DatabaseService): IssuerLogDao =
    service.issuerLogDao()