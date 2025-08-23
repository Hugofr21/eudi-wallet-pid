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

package eu.europa.ec.uilogic.component.wrap

import android.graphics.drawable.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.VSpacer

data class ActionCardConfig(
    val title: String,
    val icon: IconDataUi,
    val primaryButtonText: String,
    val secondaryButtonText: String,
)

@Composable
fun WrapActionCard(
    modifier: Modifier = Modifier,
    config: ActionCardConfig,
    onActionClick: () -> Unit = {},
    onLearnMoreClick: () -> Unit = {},
    minHeight: Dp = 150.dp,
    base : Color
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clickable(
                onClick = onActionClick,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            )
            .border(
                width = 1.dp,
                color = DeepBlue.copy(alpha = 0.06f),
            ),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            base,
                            base.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_MEDIUM.dp)
        ) {

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClick = { onLearnMoreClick() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Learn more",
                    tint = OceanBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        base.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )

                    )
                    WrapImage(
                        modifier = Modifier
                            .size(110.dp)
                            .shadow(8.dp, shape = CircleShape, clip = false),
                        iconData = config.icon,
                        contentScale = ContentScale.Fit
                    )
                }

                VSpacer.Medium()

                Text(
                    text = config.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepBlue,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@ThemeModePreviews
@Composable
private fun WrapActionCardPreview() {
    PreviewTheme {
        WrapActionCard(
            config = ActionCardConfig(
                title = "Authenticate, authorise transactions and share your digital documents in person or online.",
                icon = AppIcons.WalletActivated,
                primaryButtonText = "Authenticate",
                secondaryButtonText = "Learn more",
            ),
            base = LightSkyBlue
        )
    }
}