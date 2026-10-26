package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors

/**
 * Editing a workbook: the header, the grid, then along the bottom the formula bar over either the
 * command bar or the command palette.
 */
@Composable
fun EditorScreen(vm: AppViewModel) {
    BackHandler {
        when {
            vm.moreOpen -> vm.moreOpen = false
            vm.cardEdit != null -> vm.cardEdit = null
            vm.cardOpen != null -> vm.cardOpen = null
            vm.cards != null -> vm.closeCards()
            vm.renaming != null -> vm.renaming = null
            vm.tabMenu != null -> vm.tabMenu = null
            vm.pickingList -> vm.pickingList = false
            vm.filterAt != null -> vm.closeFilter()
            vm.commentsOpen && !vm.paletteOpen -> vm.showComments(false)
            vm.cellMenu -> vm.cellMenu = false
            vm.findOpen -> vm.closeFind()
            vm.tabsOpen -> vm.tabsOpen = false
            vm.tellMeOpen && vm.panels.isEmpty() -> vm.tellMeOpen = false
            vm.panels.isNotEmpty() -> vm.popPanel()
            vm.paletteOpen -> vm.collapsePalette()
            vm.editing -> vm.cancelEdit()
            else -> vm.closeWorkbook()
        }
    }
    vm.cards?.let { t ->
        Box(Modifier.fillMaxSize().background(GridColors.StatusBand).statusBarsPadding().navigationBarsPadding().imePadding()) { CardsScreen(vm, t) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(GridColors.StatusBand).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Keep most of the usable viewport for the workbook. Long panels scroll inside this cap.
        val panelHeight = minOf(360.dp, (maxHeight - 96.dp).coerceAtLeast(0.dp) * 0.45f)
        Column(Modifier.fillMaxSize()) {
            // typing in a shape hides the header, as the phone app does
            if (vm.findOpen) FindBar(vm) else if (vm.editingShapeText == null && !vm.cropping) EditorHeader(vm)
            val formulaBar = vm.showFormulaBar || vm.editing
            Box(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
                ) { GridView(vm, Modifier.fillMaxSize()) }
                // what is typed in the formula bar below gets its help just above it
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    FunctionSuggestions(vm)
                    ListSuggestions(vm)
                    SignatureTip(vm)
                }
                // the ⋯ sheet dims the grid; a tap there closes it
                if (vm.moreOpen) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)).clickable { vm.moreOpen = false })
            }
            EditorDialog(vm)
            vm.renaming?.let { RenameDialog(vm, it) }
            if (vm.moreOpen) {
                MoreSheet(vm, Modifier.heightIn(max = panelHeight))
            } else if (vm.editing && vm.keypad) {
                FormulaBar(vm)
                Keypad(vm)
            } else if (vm.pickingList) {
                ValidationSheet(vm, Modifier.heightIn(max = panelHeight))
            } else if (vm.filterAt != null) {
                FilterSheet(vm, Modifier.heightIn(max = panelHeight))
            } else if (vm.commentsOpen && !vm.paletteOpen) {
                CommentsPane(vm, Modifier.heightIn(max = panelHeight))
            } else if (vm.paletteOpen) {
                if (formulaBar) FormulaBar(vm)
                // Bound the panel instead of giving it a fixed share of the remaining Column height.
                PaletteSheet(vm, Modifier.fillMaxWidth().height(panelHeight))
            } else {
                if (vm.comment != null && !vm.editing) CommentCard(vm) else if (!vm.editing) InputMessage(vm)
                AggregateBar(vm)
                if (formulaBar) FormulaBar(vm)
                // typing, the keyboard takes the command bar's place
                if (!vm.editing) {
                    if (vm.sheetsBar) SheetsBar(vm) else CommandBar(vm)
                }
            }
        }
    }
}
