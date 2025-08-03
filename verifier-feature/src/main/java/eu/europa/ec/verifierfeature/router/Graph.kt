package eu.europa.ec.verifierfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.ui.choiseVerifier.ChoiceVerifierScreen
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.FieldLabelsProofAgeScreen
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model.RequestArgs
import eu.europa.ec.verifierfeature.ui.request.RequestVerifierScreen
import eu.europa.ec.verifierfeature.ui.request.RequestVerifierViewModel
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.ParametersHolder
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

            val viewModel = koinViewModel<RequestVerifierViewModel> {
                parametersOf(args)
            }

            RequestVerifierScreen(navController, viewModel)
        }
    }

}



