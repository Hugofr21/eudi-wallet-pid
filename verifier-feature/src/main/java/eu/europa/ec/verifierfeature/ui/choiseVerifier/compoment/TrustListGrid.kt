package eu.europa.ec.verifierfeature.ui.choiseVerifier.compoment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.CheckboxWithTextData
import eu.europa.ec.uilogic.component.WrapCheckboxWithLabel
import eu.europa.ec.verifierfeature.ui.choiseVerifier.VerifierItem

@Composable
fun TrustListGrid(
    items: List<VerifierItem>,
    onCheckedChange: (String, Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items.size) { index ->
            val item = items.get(index)
            WrapCheckboxWithLabel(
                checkboxData = CheckboxWithTextData(
                    isChecked = item.isSelected,
                    enabled = true,
                    onCheckedChange = { onCheckedChange(item.id, it) },
                    text = item.displayName,
                    textPadding = PaddingValues(8.dp)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }
}
