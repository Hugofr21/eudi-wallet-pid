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

package eu.europa.ec.consentuser.di



import eu.europa.ec.backuplogic.controller.BackupController
import eu.europa.ec.consentuser.interactor.BackupRestoreInteractor
import eu.europa.ec.consentuser.interactor.BackupRestoreInteractorImpl
import eu.europa.ec.consentuser.interactor.ConsentInteractor
import eu.europa.ec.consentuser.interactor.ConsentInteractorImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan("eu.europa.ec.consentuser")
class FeatureConsentUserModule

@Factory
fun provideConsentInteractor(): ConsentInteractor = ConsentInteractorImpl()

@Factory
fun provideBackupRestoreInteractor(backupController: BackupController):
        BackupRestoreInteractor = BackupRestoreInteractorImpl(backupController)

