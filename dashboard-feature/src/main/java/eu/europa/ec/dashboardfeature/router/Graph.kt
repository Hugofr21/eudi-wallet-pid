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

package eu.europa.ec.dashboardfeature.router

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import eu.europa.ec.backuplogic.ui.backup.BackupScreen
import eu.europa.ec.dashboardfeature.BuildConfig
import eu.europa.ec.dashboardfeature.ui.dashboard.DashboardScreen
import eu.europa.ec.dashboardfeature.ui.did.qrcode.SharingVcScreen
import eu.europa.ec.dashboardfeature.ui.document_sign.DocumentSignScreen
import eu.europa.ec.dashboardfeature.ui.documents.detail.DocumentDetailsScreen
import eu.europa.ec.dashboardfeature.ui.profile.ProfileScreen
import eu.europa.ec.dashboardfeature.ui.settings.SettingsScreen
import eu.europa.ec.dashboardfeature.ui.transactions.detail.TransactionDetailsScreen
import eu.europa.ec.dashboardfeature.ui.wifi.info.InfoWifiAware
import eu.europa.ec.uilogic.navigation.BackupScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.WIFIScreens
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun NavGraphBuilder.featureDashboardGraph(navController: NavController) {
    navigation(
        startDestination = DashboardScreens.Dashboard.screenRoute,
        route = ModuleRoute.DashboardModule.route
    ) {
        composable(
            route = DashboardScreens.Dashboard.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.Dashboard.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_AGE + DashboardScreens.Dashboard.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_HAIP + DashboardScreens.Dashboard.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.EUDI_OPENID4VP_SCHEME_QR + DashboardScreens.Dashboard.screenRoute
                },
            )
        ) {
            DashboardScreen(
                hostNavController = navController,
                viewModel = koinViewModel(),
                documentsViewModel = koinViewModel(),
                homeViewModel = koinViewModel(),
                transactionsViewModel = koinViewModel(),
                wifiAwareViewModel = koinViewModel(),
            )
        }

        composable(
            route = DashboardScreens.Settings.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.Settings.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_AGE + DashboardScreens.Settings.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_HAIP + DashboardScreens.Settings.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.EUDI_OPENID4VP_SCHEME_QR + DashboardScreens.Settings.screenRoute
                },
            ),
        ) {
            SettingsScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        composable(
            route = DashboardScreens.DocumentSign.screenRoute
        ) {
            DocumentSignScreen(navController, koinViewModel())
        }

        composable(
            route = DashboardScreens.DocumentDetails.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.DocumentDetails.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_AGE + DashboardScreens.DocumentDetails.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_HAIP + DashboardScreens.DocumentDetails.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.EUDI_OPENID4VP_SCHEME_QR + DashboardScreens.DocumentDetails.screenRoute
                },
            ),
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                },
            )
        ) {
            DocumentDetailsScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString("documentId").orEmpty(),
                        )
                    }
                )
            )
        }

        composable(
            route = DashboardScreens.TransactionDetails.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.TransactionDetails.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_AGE + DashboardScreens.TransactionDetails.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_HAIP + DashboardScreens.TransactionDetails.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.EUDI_OPENID4VP_SCHEME_QR + DashboardScreens.TransactionDetails.screenRoute
                },
            ),
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.StringType
                },
            )
        ) {
            TransactionDetailsScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString("transactionId").orEmpty(),
                        )
                    }
                )
            )
        }

        composable(route = DashboardScreens.Profile.screenRoute ){
            ProfileScreen(
                navController, koinViewModel())
        }

        composable(WIFIScreens.Info.screenRoute) {
                InfoWifiAware (navController, koinViewModel())
        }

        composable(DashboardScreens.SharingData.screenRoute) {
            SharingVcScreen(navController, koinViewModel())
        }


    }
}