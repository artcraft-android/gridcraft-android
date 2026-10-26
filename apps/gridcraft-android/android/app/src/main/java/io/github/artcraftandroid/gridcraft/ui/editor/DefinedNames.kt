package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.uiText

/** Insert at the caret or replace only the selected portion, preserving the rest of the edit. */
internal fun insertDefinedName(value: TextFieldValue, name: String): TextFieldValue {
    val start = value.selection.min
    return TextFieldValue(value.text.replaceRange(start, value.selection.max, name), TextRange(start + name.length))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefinedNamesSheet(vm: AppViewModel) {
    val maximumListHeight = minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * 0.4f)
    ModalBottomSheet(
        onDismissRequest = { vm.dialog = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GridColors.Surface,
        contentColor = GridColors.OnSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(uiText("Paste Names"), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            if (vm.definedNames.isEmpty()) {
                Text(uiText("This workbook has no defined names."))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = maximumListHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.definedNames.size) { i ->
                        val (name, reference) = vm.definedNames[i]
                        Column(
                            Modifier.fillMaxWidth().background(GridColors.Card, RoundedCornerShape(8.dp))
                                .clickable { vm.pasteDefinedName(name) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(name, color = GridColors.OnSettings, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(reference, color = GridColors.SettingsValue, fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(textDirection = TextDirection.Ltr))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.dialog = null }) { Text(uiText("Cancel")) }
                TextButton(onClick = vm::pasteDefinedNameList, enabled = vm.definedNames.isNotEmpty() && !vm.editing) {
                    Text(uiText("Paste List"))
                }
            }
        }
    }
}
