package eu.europa.ec.commonfeature.util.navigation



import androidx.navigation.NavController
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.DcApiIntentHolder
import eu.europa.ec.uilogic.navigation.helper.IntentAction
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.navigation.helper.isDCAPIIntent
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.java.KoinJavaComponent.inject

fun handleIntentAction(
    navController: NavController,
    intentAction: IntentAction,
) {
    if (isDCAPIIntent(intentAction.intent)) {
        DcApiIntentHolder.cacheIntent(intentAction.intent)

        val uiSerializer: UiSerializer by inject(UiSerializer::class.java)

//        val config = RequestUriConfig(
//            presentationMode = PresentationMode.DocumentPresentationForAPI
//        )

//        val arguments = generateComposableArguments(
//            mapOf(
//                RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
//                    config,
//                    RequestUriConfig.Parser
//                ).orEmpty()
//            )
//        )
//
//        val navigationLink = generateComposableNavigationLink(
//            screen = PresentationScreens.PresentationRequest,
//            arguments = arguments
//        )
//
//        navController.navigate(navigationLink) {
//            popUpTo(PresentationScreens.PresentationRequest.screenRoute) { inclusive = true }
//        }
    }
}
