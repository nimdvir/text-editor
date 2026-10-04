package io.github.nimdvir.notepad

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import io.github.nimdvir.notepad.ui.NotepadApp
import io.github.nimdvir.notepad.ui.theme.NotepadTheme
import io.github.nimdvir.notepad.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = isDarkTheme(vm.settings.theme)
            LaunchedEffect(dark) {
                // Keep status/navigation bar icons readable when the app theme differs from the system's.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NotepadTheme(dark) {
                NotepadApp(vm = vm, finish = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    /** "Open with Notepad" from Google Drive, Files, email attachments, etc. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) {
            vm.openFromIntent(uri)
        }
    }
}
