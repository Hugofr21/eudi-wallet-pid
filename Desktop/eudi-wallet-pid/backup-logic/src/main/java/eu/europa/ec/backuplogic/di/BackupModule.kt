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

import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.interactor.BackupInteractorIml
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.uilogic.serializer.UiSerializer

@Module
@ComponentScan("eu.europa.ec.backuplogic")
class LogicBackupModule

@Factory
fun provideBackupInteractor(
    uiSerializer: UiSerializer,
    resourceProvider: ResourceProvider,
    walletCoreDocumentsController: WalletCoreDocumentsController
): BackupInteractor = BackupInteractorIml(
    uiSerializer,
    resourceProvider,
    walletCoreDocumentsController
)