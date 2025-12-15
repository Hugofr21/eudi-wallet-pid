package eu.europa.ec.dashboardfeature.ui.scanner.documentSelection

import android.graphics.drawable.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.VSpacer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSelectionScreen(
    navHostController: NavController,
    viewModel: DocumentSelectionViewModel,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { }
    ) { paddingValues ->
        Content(
            state = state,
            paddingValues = paddingValues,
            viewModel = viewModel
        )
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navHostController.popBackStack()
                is Effect.Navigation.SwitchScreen -> {
                    navHostController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(
    state: State,
    paddingValues: PaddingValues,
    viewModel: DocumentSelectionViewModel? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Seleção de Documento",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Escolha o tipo de documento que deseja digitalizar para iniciar a leitura.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            onClick = { viewModel?.setEvent(Event.GoIdentifierDocument) },
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Documentos de Identificação",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Leitura de passaporte e cartão de cidadão (frente e verso / MRZ)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(20f))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            onClick = { viewModel?.setEvent(Event.GoDrivingLicense) },
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,

        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Carta de Condução",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Leitura da licença de motorista",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).align(Alignment.Top)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Certifique-se de que o documento está em um local bem iluminado e sem reflexos para garantir uma leitura precisa.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun ScannScreenContentPreview() {
    PreviewTheme {
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.BACKABLE,
            onBack = {
            },
            stickyBottom = {
            }
        ) { paddingValues ->
            Content(
                state = State(),
                paddingValues = paddingValues
            )
        }
    }
}