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

package eu.europa.ec.consentuser.ui.restore.setupSlider

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
@Composable
fun FirstPage(
    onFileSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { selectedUri ->

                val doc = DocumentFile.fromSingleUri(context, selectedUri)
                val name = doc?.name?.trim()
                val mime = context.contentResolver.getType(selectedUri)
                val lower = name?.lowercase()
                val isByExtension = lower != null && (lower.endsWith(".zip.enc") || lower.endsWith(".enc"))
                val isByMime = mime != null && (
                        mime == "application/zip" ||
                                mime == "application/octet-stream" ||
                                mime == "application/x-zip-compressed" ||
                                mime.startsWith("application/")
                        )
                if (isByExtension || isByMime) {
                    context.contentResolver.takePersistableUriPermission(
                        selectedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    selectedFileName = name ?: selectedUri.lastPathSegment
                    onFileSelected(selectedUri)
                } else {

                    val displayName = name ?: selectedUri.lastPathSegment ?: "selected file"
                    Toast.makeText(
                        context,
                        "Invalid file: $displayName!.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {

        WrapText(
            text = stringResource(R.string.consent_backup_first_page_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        )

        WrapText(
            text = stringResource(R.string.consent_backup_first_page_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            )
        )

        OutlinedTextField(
            value = selectedFileName ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.selected_file_label_placeholder)) },
            placeholder = { Text(stringResource(R.string.no_file_selected)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            singleLine = true,
            trailingIcon = {
                if (selectedFileName != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { filePickerLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                    )
                ) },
                modifier = Modifier
                    .height(36.dp)
                    .wrapContentWidth()
                    .defaultMinSize(minWidth = 80.dp),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_file_button),
                    style = MaterialTheme.typography.labelLarge.merge(
                        TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    )
                )
            }
        }
    }
}
