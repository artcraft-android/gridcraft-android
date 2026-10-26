package io.github.artcraftandroid.gridcraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.ui.editor.BarButton
import io.github.artcraftandroid.gridcraft.ui.editor.glyph
import io.github.artcraftandroid.gridcraft.AppLanguages

/** The themes Settings offers. */
private val THEMES = linkedMapOf("system" to "System Default", "light" to "Light", "dark" to "Dark")

private val LANGUAGES = linkedMapOf("system" to "System Default", "ar" to "العربية", "en" to "English")

/** What GridCraft ships with that others made, and their licences. */
private val OPEN_MATERIAL = listOf(
    "Lucide icons: ISC License",
    "Carlito and Caladea typefaces: SIL Open Font License 1.1",
    "Liberation typefaces: SIL Open Font License 1.1",
)

/**
 * Settings: Display Preferences (the theme), General (AutoSave) and About (the version, what
 * GridCraft is, the open material it ships with). Sections have accent titles; an item is its
 * title over its value, with lines between the sections.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, engineVersion: String) {
    BackHandler { vm.closeSettings() }
    var picking by remember { mutableStateOf(false) }
    var pickingLanguage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val languageChoice = AppLanguages.choice(context)
    Column(Modifier.fillMaxSize().background(GridColors.StatusBand).statusBarsPadding().background(GridColors.SettingsBack).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(GridColors.Bar), verticalAlignment = Alignment.CenterVertically) {
            BarButton(LucideIcons.ArrowLeft.glyph(), GridColors.OnBar, { vm.closeSettings() }, width = 56.dp, height = 56.dp, label = "Back")
            Text(uiText("Settings"), color = GridColors.OnBar, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SettingsSection("Display Preferences")
            SettingsItem("Theme", THEMES[vm.themeChoice] ?: "System Default") { picking = true }
            SettingsItem("Language", LANGUAGES[languageChoice] ?: "System Default") { pickingLanguage = true }
            SettingsLine()
            SettingsSection("General")
            SettingsSwitch("AutoSave", "Automatically save files", vm.autoSaveOn, vm::chooseAutoSave)
            SettingsLine()
            SettingsSection("About")
            SettingsItem("GridCraft", androidx.compose.ui.res.stringResource(io.github.artcraftandroid.gridcraft.R.string.engine_version, engineVersion))
            Text(uiText("An open-source spreadsheet for Android, built on the GridCraft engine. It is not affiliated with or endorsed by Microsoft; Excel is a trademark of Microsoft."),
                color = GridColors.SettingsValue,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            SettingsItem("Open material", OPEN_MATERIAL.joinToString("\n"))
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingLanguage) {
        AlertDialog(
            onDismissRequest = { pickingLanguage = false },
            title = { Text(uiText("Language")) },
            text = {
                Column {
                    for ((key, label) in LANGUAGES) {
                        val select = {
                            pickingLanguage = false
                            AppLanguages.choose(context, key)
                        }
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable(onClick = select),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = languageChoice == key,
                                onClick = select,
                                colors = RadioButtonDefaults.colors(selectedColor = GridColors.Accent),
                            )
                            Text(uiText(label), color = GridColors.OnSurface, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingLanguage = false }) { Text(uiText("Cancel")) } },
            containerColor = GridColors.Surface,
            titleContentColor = GridColors.OnSurface,
        )
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(uiText("Theme")) },
            text = {
                Column {
                    for ((key, label) in THEMES) {
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable {
                                vm.chooseTheme(key)
                                picking = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = vm.themeChoice == key,
                                onClick = {
                                    vm.chooseTheme(key)
                                    picking = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = GridColors.Accent),
                            )
                            Text(uiText(label), color = GridColors.OnSurface, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(uiText("Cancel")) } },
            containerColor = GridColors.Surface,
            titleContentColor = GridColors.OnSurface,
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(uiText(title), color = GridColors.Accent, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun SettingsLine() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(GridColors.BarEdge))
}

/** An item: its title over its value; tappable when [onClick] is given. */
@Composable
private fun SettingsItem(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(uiText(title), color = GridColors.OnSettings, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(uiText(value), color = GridColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A switch: the group's title, what it does, then On or Off beside the switch. */
@Composable
private fun SettingsSwitch(title: String, what: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(uiText(title), color = GridColors.OnSettings, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(uiText(what), color = GridColors.OnSettings, fontSize = 14.sp, modifier = Modifier.padding(start = 5.dp, top = 14.dp))
        Row(Modifier.padding(start = 5.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText(if (on) "On" else "Off"), color = GridColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.width(56.dp))
            Box(
                Modifier.size(40.dp, 24.dp).background(if (on) GridColors.Accent else GridColors.CheckOff, RoundedCornerShape(12.dp)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(18.dp).background(if (on) Color.White else GridColors.Disabled, CircleShape))
            }
        }
    }
}
