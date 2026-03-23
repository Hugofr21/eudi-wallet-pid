package eu.europa.ec.dashboardfeature.ui.scanner.documentSelection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.VSpacer

private val ColorSuccess = Color(0xFF10B981)

@Composable
fun DocumentSelectionScreen(
    navHostController: NavController,
    viewModel: DocumentSelectionViewModel,
) {
    val state   by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop          -> navHostController.popBackStack()
                is Effect.Navigation.SwitchScreen -> navHostController.navigate(effect.screenRoute) {
                    popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                }
            }
        }
    }

    ContentScreen(
        isLoading         = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack            = { viewModel.setEvent(Event.GoBack) },
        stickyBottom      = {}
    ) { paddingValues ->
        Content(
            state         = state,
            paddingValues = paddingValues,
            viewModel     = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state        : State,
    paddingValues: PaddingValues,
    viewModel    : DocumentSelectionViewModel? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text       = "Scan Document",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "Choose the document type to begin reading.",
                fontSize  = 14.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        VSpacer.Medium()

        DocumentOptionCard(
            icon        = Icons.Default.Book,
            title = "Identification Documents",
            description = "Passport and Citizen Card (back · MRZ)",
            badge       = "ID-3 / ID-1",
            onClick     = { viewModel?.setEvent(Event.GoIdentifierDocument) }
        )

        Spacer(Modifier.height(12.dp))

        DocumentOptionCard(
            icon        = Icons.Default.DirectionsCar,
            title       = "Driving License",
            description = "Driver's license reading via MRZ",
            badge       = "ID-1",
            onClick     = { viewModel?.setEvent(Event.GoDrivingLicense) }
        )

        Spacer(Modifier.height(12.dp))

        DocumentOptionCard(
            icon        = Icons.Default.FaceRetouchingNatural,
            title = "Face Detection",
            description = "Biometric face capture for identity verification",
            badge       = "ISO 19794-5",
            onClick     = { viewModel?.setEvent(Event.LivenessFace) }
        )

        VSpacer.Small()

        InfoBanner()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentOptionCard(
    icon       : ImageVector,
    title      : String,
    description: String,
    badge      : String,
    onClick    : () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Texto
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text      = description,
                    fontSize  = 12.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(6.dp))
                // Badge de norma
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text          = badge,
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}


@Composable
private fun InfoBanner() {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector        = Icons.Default.Info,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text      = "Make sure the document is well-lit and free from glare to ensure accurate reading.",
            fontSize  = 13.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp,
            modifier  = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun DocumentSelectionScreenPreview() {
    PreviewTheme {
        ContentScreen(
            isLoading         = false,
            navigatableAction = ScreenNavigateAction.BACKABLE,
            onBack            = {},
            stickyBottom      = {}
        ) { paddingValues ->
            Content(state = State(), paddingValues = paddingValues)
        }
    }
}