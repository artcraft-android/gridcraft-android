package io.github.artcraftandroid.gridcraft

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.github.artcraftandroid.gridcraft.engine.WorkbookFiles
import io.github.artcraftandroid.gridcraft.ui.AppRoot
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.GridcraftTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguages.attach(newBase))
    }

    private val openWorkbook = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // keep the right to write back to it
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            vm.open(uri, writable = true)
        }
    }

    private val saveAs = registerForActivityResult(ActivityResultContracts.CreateDocument(WorkbookFiles.XLSX_TYPE)) { uri ->
        if (uri != null) vm.saveTo(uri)
    }

    /** Insert › Pictures: the system's photo picker (no permission needed). */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) vm.tell("The picture can't be read.") else vm.insertPicture(bytes)
        }
    }

    /** Insert › Pictures › Place in Cell › From File...: any picture file. */
    private val pickPictureFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) vm.tell("The picture can't be read.") else vm.insertPicture(bytes)
        }
    }

    /** Insert › Pictures › Camera: the camera app takes the picture. */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) lifecycleScope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
            }
            vm.insertPicture(bytes)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the bars' icons follow the theme: dark on the light bars, light on the dark ones
        enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        AppLanguages.migrate(this)
        window.isNavigationBarContrastEnforced = false
        setContent {
            GridcraftTheme(vm.themeChoice) {
                val dark = GridColors.dark
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                AppRoot(vm, onOpen = ::pickWorkbook)
            }
        }
        lifecycleScope.launch {
            for (name in vm.saveAsRequests) launchSafely { saveAs.launch(name) }
        }
        lifecycleScope.launch {
            for (url in vm.linkRequests) launchSafely { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        }
        lifecycleScope.launch {
            for (kind in vm.pictureRequests) launchSafely {
                when (kind) {
                    "camera" -> takePhoto.launch(null)
                    "file" -> pickPictureFile.launch("image/*")
                    else -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
        lifecycleScope.launch {
            for ((file, name) in vm.printRequests) launchSafely {
                getSystemService(android.print.PrintManager::class.java)?.print(name, io.github.artcraftandroid.gridcraft.engine.PdfPrintAdapter(file, name), null)
            }
        }
        lifecycleScope.launch {
            for ((uri, type) in vm.shareRequests) {
                val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                launchSafely { startActivity(Intent.createChooser(send, null)) }
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    /** Leaving the app saves the changes to the workbook's own file (AutoSave). */
    override fun onStop() {
        super.onStop()
        vm.autoSaveNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun pickWorkbook() = launchSafely { openWorkbook.launch(WorkbookFiles.OPEN_TYPES) }

    private fun launchSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: ActivityNotFoundException) {
            vm.tell("No app on this phone can do that.")
        } catch (_: SecurityException) {
            vm.tell("GridCraft isn't allowed to do that.")
        }
    }

    /** Workbooks other apps open (VIEW) or hand over to edit (EDIT) with GridCraft. */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> vm.open(uri, writable = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            Intent.ACTION_EDIT -> vm.open(uri, writable = true)
        }
    }
}
