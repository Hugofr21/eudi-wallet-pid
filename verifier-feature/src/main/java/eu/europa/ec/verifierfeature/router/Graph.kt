package eu.europa.ec.verifierfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.resourceslogic.BuildConfig
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.verifierfeature.ui.choiseVerifier.ChoiceVerifierScreen
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.FieldLabelsProofAgeScreen
import eu.europa.ec.verifierfeature.ui.initRequest.InitRequestVerifierScreen
import eu.europa.ec.verifierfeature.ui.initRequest.InitRequestVerifierViewModel
import eu.europa.ec.verifierfeature.ui.loading.PresentationLoadingVerifierScreen
import eu.europa.ec.verifierfeature.ui.request.PresentationRequestVerifierScreen
import eu.europa.ec.verifierfeature.ui.success.PresentationSuccessVerifierScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf


fun NavGraphBuilder.featureVerifierGraph(navController: NavController) {
    navigation(
        startDestination = VerifierScreens.ChoiceListTrust.screenRoute,
        route = ModuleRoute.VerifierModule.route
    ) {

        composable(
            route = VerifierScreens.ChoiceListTrust.screenRoute,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) {
            ChoiceVerifierScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString("documentId").orEmpty()
                        )
                    }
                ),
            )
        }

        composable(
            route = VerifierScreens.FieldsLabels.screenRoute,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) {
            FieldLabelsProofAgeScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString("documentId").orEmpty()
                        )
                    }
                ),
            )
        }


        composable(
            route = VerifierScreens.RequestVerifier.screenRoute + "?args={args}",
            arguments = listOf(
                navArgument("args") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            val args = it.arguments?.getString("args").orEmpty()

            val viewModel = koinViewModel<InitRequestVerifierViewModel> {
                parametersOf(args)
            }

            InitRequestVerifierScreen(navController, viewModel)
        }

        // Presentation Document screen navigation

        composable(
            route = VerifierScreens.PresentationRequestVerifier.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + VerifierScreens.PresentationRequestVerifier.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_AGE + VerifierScreens.PresentationRequestVerifier.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_HAIP + VerifierScreens.PresentationRequestVerifier.screenRoute
                },

                ),
            arguments = listOf(
                navArgument(RequestUriConfig.serializedKeyName) {
                    type = NavType.StringType
                },
            )
        ) {
            PresentationRequestVerifierScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString(RequestUriConfig.serializedKeyName).orEmpty()
                        )
                    }
                )
            )
        }

        composable(
            route = VerifierScreens.PresentationLoadingVerifier.screenRoute,
        ) {
            PresentationLoadingVerifierScreen(
                navController,
                koinViewModel()
            )
        }

        composable(
            route = VerifierScreens.PresentationSuccessVerifier.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + VerifierScreens.PresentationSuccessVerifier.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_AGE + VerifierScreens.PresentationSuccessVerifier.screenRoute
                },
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK_HAIP + VerifierScreens.PresentationSuccessVerifier.screenRoute
                },
            ),
        ) {
            PresentationSuccessVerifierScreen(
                navController,
                koinViewModel()
            )
        }

    }

}



