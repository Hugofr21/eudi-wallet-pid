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

package eu.europa.ec.dashboardfeature.ui.profile

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import eu.europa.ec.dashboardfeature.model.ClaimValue
import eu.europa.ec.dashboardfeature.model.ClaimsUI
import eu.europa.ec.dashboardfeature.ui.home.BleAvailability
import eu.europa.ec.dashboardfeature.ui.profile.compoment.ActionButtons
import eu.europa.ec.dashboardfeature.ui.profile.compoment.NoResults
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.openAppSettings
import eu.europa.ec.uilogic.extension.openBleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

private val ColorAccent  = Color(0xFF1565C0)
private val ColorSuccess = Color(0xFF10B981)
private val ColorError   = Color(0xFFEF4444)

private enum class ClaimCategory(val label: String, val icon: ImageVector) {
    PERSONAL ("Personal Data",  Icons.Default.Person),
    IDENTITY ("Identity",       Icons.Default.Badge),
    ADDRESS  ("Address",        Icons.Default.Home),
    CONTACT  ("Contact",        Icons.Default.ContactMail),
    DATES    ("Dates",          Icons.Default.CalendarToday),
    DOCUMENT ("Document",       Icons.Default.CreditCard),
    NATIONAL ("Nationality",    Icons.Default.Flag),
    OTHER    ("Other",          Icons.Default.Info),
}

private val KEY_CATEGORY = mapOf(
    "given name"                     to ClaimCategory.PERSONAL,
    "family name"                    to ClaimCategory.PERSONAL,
    "birth family name"              to ClaimCategory.PERSONAL,
    "birth given name"               to ClaimCategory.PERSONAL,
    "family name birth"              to ClaimCategory.PERSONAL,
    "given name birth"               to ClaimCategory.PERSONAL,
    "given name unicode"             to ClaimCategory.PERSONAL,
    "family name unicode"            to ClaimCategory.PERSONAL,
    "full name"                      to ClaimCategory.PERSONAL,
    "middle name"                    to ClaimCategory.PERSONAL,
    "sex"                            to ClaimCategory.PERSONAL,
    "gender"                         to ClaimCategory.PERSONAL,
    "age"                            to ClaimCategory.PERSONAL,
    "age in years"                   to ClaimCategory.PERSONAL,
    "age over 18"                    to ClaimCategory.PERSONAL,
    "age birth year"                 to ClaimCategory.PERSONAL,

    "personal administrative number" to ClaimCategory.IDENTITY,
    "national id"                    to ClaimCategory.IDENTITY,
    "ssn"                            to ClaimCategory.IDENTITY,

    "document number"                to ClaimCategory.DOCUMENT,
    "issuing authority"              to ClaimCategory.DOCUMENT,
    "issuing authority unicode"      to ClaimCategory.DOCUMENT,
    "issuing country"                to ClaimCategory.DOCUMENT,
    "issuing jurisdiction"           to ClaimCategory.DOCUMENT,

    "address"                        to ClaimCategory.ADDRESS,
    "resident address"               to ClaimCategory.ADDRESS,
    "resident city"                  to ClaimCategory.ADDRESS,
    "resident country"               to ClaimCategory.ADDRESS,
    "resident postal code"           to ClaimCategory.ADDRESS,
    "resident street"                to ClaimCategory.ADDRESS,
    "resident state"                 to ClaimCategory.ADDRESS,
    "resident house number"          to ClaimCategory.ADDRESS,
    "place of birth"                 to ClaimCategory.ADDRESS,

    "email"                          to ClaimCategory.CONTACT,
    "email address"                  to ClaimCategory.CONTACT,
    "phone"                          to ClaimCategory.CONTACT,
    "phone number"                   to ClaimCategory.CONTACT,
    "mobile"                         to ClaimCategory.CONTACT,

    "birthdate"                      to ClaimCategory.DATES,
    "birth date"                     to ClaimCategory.DATES,
    "date of birth"                  to ClaimCategory.DATES,
    "expiry date"                    to ClaimCategory.DATES,
    "date of expiry"                 to ClaimCategory.DATES,
    "expiration date"                to ClaimCategory.DATES,
    "issuance date"                  to ClaimCategory.DATES,
    "issue date"                     to ClaimCategory.DATES,
    "date of issuance"               to ClaimCategory.DATES,
    "iat"                            to ClaimCategory.DATES,
    "exp"                            to ClaimCategory.DATES,

    "nationality"                    to ClaimCategory.NATIONAL,
    "nationalities"                  to ClaimCategory.NATIONAL,
    "citizenship"                    to ClaimCategory.NATIONAL,
    "country of birth"               to ClaimCategory.NATIONAL,
)

private fun categorise(key: String): ClaimCategory =
    KEY_CATEGORY[key.trim().lowercase()] ?: ClaimCategory.OTHER

private val HERO_BIRTHDATE_KEYS  = setOf("birthdate", "birth date", "date of birth")
private val HERO_AGE18_KEYS      = setOf("age over 18")
private val HERO_SKIP_KEYS       = HERO_BIRTHDATE_KEYS + HERO_AGE18_KEYS

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navHostController: NavController,
    viewModel: ProfileViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect

    ContentScreen(
        isLoading         = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack            = { viewModel.setEvent(Event.GoBack) },
        stickyBottom      = { paddingValues ->
            if (state.firstName.isNotBlank() || state.lastName.isNotBlank()) {
                ActionButtons(
                    viewModel     = viewModel,
                    paddingValues = paddingValues,
                    isLoading     = state.isLoading,
                )
            }
        }
    ) { paddingValues ->
        if (state.firstName.isBlank() && state.lastName.isBlank()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                NoResults(modifier = Modifier.fillMaxWidth())
            }
        } else {
            Content(
                state                 = state,
                effectFlow            = effects,
                onNavigationRequested = { handleNavigationEffect(it, navHostController, context) },
                paddingValues         = paddingValues
            )
        }
    }

    if (state.bleAvailability == BleAvailability.NO_PERMISSION) {
        RequiredPermissionsAsk(state) { viewModel.setEvent(it) }
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop              -> navHostController.popBackStack()
                is Effect.Navigation.SwitchScreen     -> navHostController.navigate(effect.screenRoute) {
                    popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                }
                is Effect.Navigation.OnAppSettings    -> context.openAppSettings()
                is Effect.Navigation.OnSystemSettings -> context.openBleSettings()
            }
        }
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop              -> context.finish()
        is Effect.Navigation.SwitchScreen     -> navController.navigate(navigationEffect.screenRoute) {
            popUpTo(navigationEffect.popUpToScreenRoute) { inclusive = navigationEffect.inclusive }
        }
        is Effect.Navigation.OnAppSettings    -> context.openAppSettings()
        is Effect.Navigation.OnSystemSettings -> context.openBleSettings()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
) {
    val scrollState = rememberScrollState()

    val birthDate = remember(state.claimsUi) {
        state.claimsUi
            .firstOrNull { it.key.trim().lowercase() in HERO_BIRTHDATE_KEYS }
            ?.let { (it.value as? ClaimValue.Simple)?.text }
    }

    val ageOver18: Boolean? = remember(state.claimsUi) {
        val raw = state.claimsUi
            .firstOrNull { it.key.trim().lowercase() in HERO_AGE18_KEYS }
            ?.let { (it.value as? ClaimValue.Simple)?.text }
        when (raw?.trim()?.lowercase()) {
            "true", "1"  -> true
            "false", "0" -> false
            else         -> null
        }
    }

    val grouped = remember(state.claimsUi) {
        state.claimsUi
            .filterNot { it.key.trim().lowercase() in HERO_SKIP_KEYS }
            .sortedBy { it.key.trim().lowercase() }
            .groupBy { categorise(it.key) }
            .toSortedMap(compareBy { it.ordinal })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top    = paddingValues.calculateTopPadding(),
                bottom = 0.dp,
                start  = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                end    = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
            )
            .verticalScroll(scrollState),
    ) {
        ProfileHeroCard(
            state     = state,
            birthDate = birthDate,
            ageOver18 = ageOver18,
        )

        Spacer(Modifier.height(20.dp))

        grouped.forEach { (category, claims) ->
            ClaimCategorySection(category = category, claims = claims)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(28.dp))
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            if (effect is Effect.Navigation) onNavigationRequested(effect)
        }.collect()
    }
}

@Composable
private fun ProfileHeroCard(
    state: State,
    birthDate: String?,
    ageOver18: Boolean?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        ProfileImage(state.imageBase64)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f  to Color.Transparent,
                        0.30f to Color.Black.copy(alpha = 0.05f),
                        1.0f  to Color.Black.copy(alpha = 0.82f),
                    )
                )
        )

        Column(
            modifier            = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fullName = listOf(state.firstName, state.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            Text(
                text       = fullName,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (!birthDate.isNullOrBlank()) {
                    HeroPill(
                        icon  = Icons.Default.CalendarToday,
                        label = birthDate,
                        color = Color.White.copy(alpha = 0.90f),
                    )
                }

                when (ageOver18) {
                    true  -> HeroPill(
                        icon  = Icons.Default.CheckCircle,
                        label = "Age 18+",
                        color = ColorSuccess,
                    )
                    false -> HeroPill(
                        icon  = Icons.Default.RemoveCircleOutline,
                        label = "Under 18",
                        color = ColorError,
                    )
                    null  -> {}
                }
            }
        }
    }
}

@Composable
private fun HeroPill(
    icon: ImageVector,
    label: String,
    color: Color,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier              = Modifier
            .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileImage(imageBase64: String?) {
    val decodedBitmap: Bitmap? = remember(imageBase64) {
        if (imageBase64.isNullOrBlank()) return@remember null
        runCatching {
            val clean = imageBase64
                .substringAfter(",", missingDelimiterValue = imageBase64)
                .replace('_', '/')
                .replace('-', '+')
                .replace("\\s".toRegex(), "")
            val bytes = Base64.decode(clean, Base64.DEFAULT or Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    if (decodedBitmap != null) {
        Image(
            bitmap             = decodedBitmap.asImageBitmap(),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop,
        )
    } else {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(ColorAccent, ColorAccent.copy(alpha = 0.65f)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.AccountCircle,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.30f),
                modifier           = Modifier.size(110.dp),
            )
        }
    }
}

@Composable
private fun ClaimCategorySection(
    category: ClaimCategory,
    claims: List<ClaimsUI>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(bottom = 10.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(30.dp)
                    .background(ColorAccent.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = category.icon,
                    contentDescription = null,
                    tint               = ColorAccent,
                    modifier           = Modifier.size(15.dp),
                )
            }
            Text(
                text          = category.label,
                fontWeight    = FontWeight.SemiBold,
                fontSize      = 12.sp,
                color         = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 0.5.sp,
            )
        }

        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(12.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            val rows = claims.chunked(2)
            rows.forEachIndexed { rowIdx, pair ->
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    pair.forEach { claim ->
                        Column(modifier = Modifier.weight(1f)) {
                            ClaimLabel(claim.key)
                            Spacer(Modifier.height(4.dp))
                            ClaimValueView(claim.value as ClaimValue)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }

                if (rowIdx < rows.lastIndex) {
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaimLabel(key: String) {
    Text(
        text          = formatKey(key),
        fontSize      = 10.sp,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight    = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        maxLines      = 1,
        overflow      = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ClaimValueView(value: ClaimValue) {
    when (value) {
        is ClaimValue.Simple -> Text(
            text       = formatSimpleValue(value.text),
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 3,
            overflow   = TextOverflow.Ellipsis,
        )
        is ClaimValue.Arr -> Text(
            text       = value.items.joinToString(", ") { it.toString() }.ifBlank { "—" },
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 3,
            overflow   = TextOverflow.Ellipsis,
        )
        is ClaimValue.Obj -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            value.entries.forEach { (subKey, subValue) ->
                Text(
                    text       = "${formatKey(subKey)}: $subValue",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatSimpleValue(raw: String?): String {
    if (raw == null) return "—"
    return when (raw.trim().lowercase()) {
        "1"     -> "Male"
        "0"     -> "Female"
        "2"     -> "Other"
        "true"  -> "Yes"
        "false" -> "No"
        ""      -> "—"
        else    -> raw
    }
}

private fun formatKey(key: String): String =
    key.trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    val permissions = mutableListOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    ).also {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && state.isBleCentralClientModeEnabled) {
            it += Manifest.permission.ACCESS_FINE_LOCATION
            it += Manifest.permission.ACCESS_COARSE_LOCATION
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    when {
        permissionsState.allPermissionsGranted -> onEventSend(Event.CreateQrCode)
        else -> {
            onEventSend(Event.OnPermissionStateChanged(BleAvailability.UNKNOWN))
            LaunchedEffect(Unit) { permissionsState.launchMultiplePermissionRequest() }
        }
    }
}