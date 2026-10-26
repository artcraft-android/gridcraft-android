package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.uiText

/** Insert at the caret or replace only the selected portion, preserving the rest of the edit. */
internal fun insertDefinedName(value: TextFieldValue, name: String): TextFieldValue {
    val start = value.selection.min
    return TextFieldValue(value.text.replaceRange(start, value.selection.max, name), TextRange(start + name.length))
}

@Composable
internal fun DefinedNamesDialog(vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text(uiText("Paste Names")) },
        text = {
            if (vm.definedNames.isEmpty()) {
                Text(uiText("This workbook has no defined names."))
            } else {
                Column {
                    Text(uiText("Choose a name to insert into the formula."), color = GridColors.OnSurface)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(vm.definedNames.size) { i ->
                            val (name, reference) = vm.definedNames[i]
                            Column(Modifier.fillMaxWidth().clickable { vm.pasteDefinedName(name) }.padding(vertical = 10.dp)) {
                                Text(name, color = GridColors.OnSettings)
                                Text(reference, color = GridColors.SettingsValue, style = TextStyle(textDirection = TextDirection.Ltr))
                            }
                        }
                    }
                    if (!vm.editing) Text(uiText("Paste List writes names and references from the active cell."), color = GridColors.SettingsValue)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::pasteDefinedNameList, enabled = vm.definedNames.isNotEmpty() && !vm.editing) {
                Text(uiText("Paste List"))
            }
        },
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text(uiText("Cancel")) } },
        containerColor = GridColors.Surface,
        titleContentColor = GridColors.OnSurface,
        textContentColor = GridColors.OnSurface,
    )
}
