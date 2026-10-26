package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons
import io.github.artcraftandroid.gridcraft.ui.uiText

/** Insert at the caret or replace only the selected portion, preserving the rest of the edit. */
internal fun insertDefinedName(value: TextFieldValue, name: String): TextFieldValue {
    val start = value.selection.min
    return TextFieldValue(value.text.replaceRange(start, value.selection.max, name), TextRange(start + name.length))
}

/** A regular palette page, sharing its header, back navigation and scrolling. */
@Composable
internal fun DefinedNamesPanel(vm: AppViewModel) {
    Scrolling {
        if (vm.definedNames.isEmpty()) {
            Text(
                uiText("This workbook has no defined names."),
                color = GridColors.OnSurface,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        } else {
            for ((name, reference) in vm.definedNames) {
                Column(
                    Modifier.fillMaxWidth().bottomEdge(GridColors.Divider)
                        .clickable { vm.pasteDefinedName(name) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(name, color = GridColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(reference, color = GridColors.OnStrip, fontSize = 13.sp,
                        style = TextStyle(textDirection = TextDirection.Ltr))
                }
            }
        }
        Band()
        PaletteRow(
            "Paste List", LucideIcons.ClipboardPaste.glyph(), onClick = vm::pasteDefinedNameList,
            enabled = vm.definedNames.isNotEmpty() && !vm.editing,
        )
    }
}
