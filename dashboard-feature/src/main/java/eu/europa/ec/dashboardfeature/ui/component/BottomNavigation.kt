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

package eu.europa.ec.dashboardfeature.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.europa.ec.dashboardfeature.ui.dashboard.DashboardViewModel
import eu.europa.ec.dashboardfeature.ui.home.compoment.ScanButton



private val NightSky       = Color(0xFF071523)
private val OceanBlueDark  = Color(0xFF113865)
private val AccentCobalt   = Color(0xFF2F6CF0)
private val SoftHighlight  = Color(0xFFCFE9FF)


private val LightOceanBg   = Color(0xFFEFF7FF)
private val LightOceanBar  = Color(0xFFD9EDFF)
private val LightAccent    = Color(0xFF2A5ED9)
private val LightHighlight = Color(0xFF0048D2)
private val LightUnselected= Color(0xFF345A8A)

data class NavPalette(
    val containerColor: Color,
    val accent: Color,
    val highlight: Color,
    val unselected: Color
)

sealed class BottomNavigationItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: IconDataUi,
) {
    data object Home : BottomNavigationItem(
        route = "HOME",
        titleRes = R.string.home_screen_title,
        icon = AppIcons.Home
    )

    data object Documents : BottomNavigationItem(
        route = "DOCUMENTS",
        titleRes = R.string.documents_screen_title,
        icon = AppIcons.Documents
    )

    data object Transactions : BottomNavigationItem(
        route = "TRANSACTIONS",
        titleRes = R.string.transactions_screen_title,
        icon = AppIcons.Transactions
    )

    data object WifiAware : BottomNavigationItem(
        route = "WIFIAWARE",
        titleRes = R.string.wifi_screen_title,
        icon = AppIcons.Search
    )

}

@Composable
fun BottomNavigationBar(navController: NavController, viewModel: DashboardViewModel? = null) {
    val navItems = listOf(
        BottomNavigationItem.Home,
        BottomNavigationItem.Documents,
        BottomNavigationItem.Transactions,
        BottomNavigationItem.WifiAware
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isDark = isSystemInDarkTheme()

    val palette = if (isDark) {
        NavPalette(
            containerColor = OceanBlueDark.copy(alpha = 0.96f),
            accent = AccentCobalt,
            highlight = SoftHighlight,
            unselected = Color.White
        )
    } else {
        NavPalette(
            containerColor = LightOceanBar,
            accent = LightAccent,
            highlight = LightHighlight,
            unselected = LightUnselected
        )
    }

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth(),
        containerColor = palette.containerColor,
    ) {
        navItems.forEach { screen ->
            NavigationBarItem(
                icon = {
                    WrapIcon(iconData = screen.icon)
                },
                label = { Text(text = stringResource(screen.titleRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.highlight,
                    selectedTextColor = palette.highlight,
                    indicatorColor = palette.accent.copy(alpha = 0.14f),
                    unselectedIconColor = palette.unselected.copy(alpha = 0.70f),
                    unselectedTextColor = palette.unselected.copy(alpha = 0.70f)
                ),
                selected = currentDestination?.hierarchy?.any {
                    it.route == screen.route
                } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun BottomNavigationBarPreview() {
    PreviewTheme {
        BottomNavigationBar(rememberNavController(),null)
    }
}