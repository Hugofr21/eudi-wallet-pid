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

package eu.europa.ec.backuplogic.di

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.PassphraseAuthenticationController
import eu.europa.ec.backuplogic.controller.BackupController
import eu.europa.ec.backuplogic.controller.BackupControllerImpl
import eu.europa.ec.backuplogic.controller.ListWordsController
import eu.europa.ec.backuplogic.controller.ListWordsControllerImpl
import eu.europa.ec.backuplogic.controller.WalletBackupLogController
import eu.europa.ec.backuplogic.controller.WalletBackupLogControllerImpl
import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.interactor.BackupInteractorImpl
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.storagelogic.dao.BackupLogDao
import org.koin.core.annotation.Single

@Module
@ComponentScan("eu.europa.ec.backuplogic")
class LogicBackupModule

@Factory
fun provideListWordsController(
    context: ResourceProvider
): ListWordsController = ListWordsControllerImpl(
    context
)

@Single
fun provideWalletBackupLogController(backupLogDao: BackupLogDao): WalletBackupLogController =
    WalletBackupLogControllerImpl(backupLogDao)



@Factory
fun provideBackupController(
    context: ResourceProvider,
    walletCoreDocumentsController: WalletCoreDocumentsController,
     cryptoController: CryptoController,
    passphraseAuthenticationController: PassphraseAuthenticationController,
    walletBackupLogController: WalletBackupLogController
): BackupController = BackupControllerImpl(
    context,
    walletCoreDocumentsController,
    cryptoController,
    passphraseAuthenticationController,
    walletBackupLogController
)


@Factory
fun provideBackupInteractor(
    listWordsController: ListWordsController,
    backupController: BackupController
): BackupInteractor =
    BackupInteractorImpl(
        listWordsController,
        backupController
    )

