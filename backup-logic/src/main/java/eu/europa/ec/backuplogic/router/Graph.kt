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

package eu.europa.ec.backuplogic.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import eu.europa.ec.backuplogic.ui.backup.BackupScreen
import eu.europa.ec.backuplogic.ui.listWordsBackup.ListWordsBackupScreen
import eu.europa.ec.backuplogic.ui.quizPhraseWords.QuizPhraseWordsScreen
import eu.europa.ec.backuplogic.ui.viewBackup.ViewBackupScreen
import eu.europa.ec.uilogic.navigation.BackupScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.featureBackupGraph(navController: NavController) {
    navigation(
        startDestination = BackupScreens.Backup.screenRoute,
        route = ModuleRoute.BackupModule.route
    ) {

        composable(route = BackupScreens.Backup.screenRoute) {
            BackupScreen(navController, koinViewModel())
        }

        composable(route = BackupScreens.BackupPhraseList.screenRoute) {
            ListWordsBackupScreen(navController, koinViewModel())
        }

        composable(route = BackupScreens.BackupPhraseListCreated.screenRoute) {
            QuizPhraseWordsScreen(navController, koinViewModel())
        }

        composable(route = BackupScreens.ViewRestore.screenRoute) {
            ViewBackupScreen(navController, koinViewModel())
        }

        composable(route = BackupScreens.Restore.screenRoute) {
            QuizPhraseWordsScreen(navController, koinViewModel())
        }
    }
}