package eu.europa.ec.verifierfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import eu.europa.ec.uilogic.navigation.BackupScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.verifierfeature.ui.choiseVerifier.ChoiceVerifierScreen
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.FieldLabelsProofAgeScreen
import org.koin.androidx.compose.koinViewModel


fun NavGraphBuilder.featureVerifierGraph(navController: NavController) {
    navigation(
        startDestination = VerifierScreens.ChoiceListTrust.screenRoute,
        route = ModuleRoute.VerifierModule.route
    ) {

        composable(route = VerifierScreens.ChoiceListTrust.screenRoute) {
            ChoiceVerifierScreen(navController, koinViewModel())
        }

        composable(route = VerifierScreens.FieldsLabels.screenRoute) {
            FieldLabelsProofAgeScreen(navController, koinViewModel())
        }

    }
}
