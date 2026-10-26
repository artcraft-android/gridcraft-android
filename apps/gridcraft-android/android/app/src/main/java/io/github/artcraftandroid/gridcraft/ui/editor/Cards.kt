package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.ui.res.stringResource
import io.github.artcraftandroid.gridcraft.R
import io.github.artcraftandroid.gridcraft.ui.uiText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.core.TableCards
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons

/** How many of a card's fields after its title the list shows. */
private const val CARD_LINES = 3

/**
 * Cards View: the table's rows as cards, its first column the title and the next few fields
 * under it; a tap opens a card in full, where a field tapped is changed.
 */
@Composable
fun CardsScreen(vm: AppViewModel, t: TableCards) {
    val open = vm.cardOpen?.takeIf { it in t.cards.indices }
    Column(Modifier.fillMaxSize().background(GridColors.Surface)) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(GridColors.StatusBand).bottomEdge(GridColors.BarEdge), verticalAlignment = Alignment.CenterVertically) {
            BarButton(LucideIcons.ArrowLeft.glyph(), GridColors.OnBar, { if (open != null) vm.cardOpen = null else vm.closeCards() }, width = 48.dp, height = 56.dp, label = "Back")
            Text(
                if (open != null) cardTitle(t, open) else t.name,
                color = GridColors.OnBar,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (open != null) {
                BarButton(LucideIcons.ChevronUp.glyph(), GridColors.OnBar, { vm.cardOpen = open - 1 }, enabled = open > 0, width = 48.dp, height = 56.dp, label = "Previous Card")
                BarButton(LucideIcons.ChevronDown.glyph(), GridColors.OnBar, { vm.cardOpen = open + 1 }, enabled = open < t.cards.size - 1, width = 48.dp, height = 56.dp, label = "Next Card")
            } else {
                Text(stringResource(R.string.card_count, t.cards.size, if (t.more) "+" else ""), color = GridColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.padding(end = 16.dp))
            }
        }
        if (open != null) CardDetail(vm, t, open) else CardList(vm, t)
    }
}

private fun cardTitle(t: TableCards, i: Int): String = t.cards.getOrNull(i)?.values?.firstOrNull()?.ifBlank { null } ?: "(blank)"

@Composable
private fun CardList(vm: AppViewModel, t: TableCards) {
    if (t.cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(uiText("This table has no rows to show."), color = GridColors.OnSurface, fontSize = 15.sp) }
        return
    }
    val shape = RoundedCornerShape(8.dp)
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(t.cards.size) { i ->
            val card = t.cards[i]
            Column(
                Modifier.fillMaxWidth().background(GridColors.Bar, shape).border(1.dp, GridColors.BarEdge, shape).clickable { vm.cardOpen = i }.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(cardTitle(t, i), color = GridColors.OnSettings, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                for (f in 1..minOf(CARD_LINES, t.fields.size - 1)) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.fields[f], color = GridColors.SettingsValue, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.4f))
                        Spacer(Modifier.width(12.dp))
                        Text(card.values.getOrNull(f).orEmpty(), color = GridColors.OnSettings, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

/** A card in full: every field, a tap on one to change it. */
@Composable
private fun CardDetail(vm: AppViewModel, t: TableCards, i: Int) {
    val card = t.cards[i]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        t.fields.forEachIndexed { f, name ->
            Column(Modifier.fillMaxWidth().clickable { vm.editCardField(card.row, f) }.bottomEdge(GridColors.Divider).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(name, color = GridColors.SettingsValue, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(card.values.getOrNull(f).orEmpty().ifEmpty { " " }, color = GridColors.OnSettings, fontSize = 16.sp)
            }
        }
    }
    // the field starts from what its cell holds (a formula, or the value as typed)
    vm.cardEdit?.let { (f, start) ->
        var text by remember(i, f) { mutableStateOf(start) }
        AlertDialog(
            onDismissRequest = { vm.cardEdit = null },
            title = { Text(t.fields.getOrNull(f).orEmpty()) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.setCardValue(card.row, f, text) }) { Text(uiText("OK")) } },
            dismissButton = { TextButton(onClick = { vm.cardEdit = null }) { Text(uiText("Cancel")) } },
        )
    }
}
