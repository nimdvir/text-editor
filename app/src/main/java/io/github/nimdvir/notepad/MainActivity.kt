package io.github.nimdvir.notepad

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.nimdvir.notepad.ui.NotepadApp
import io.github.nimdvir.notepad.ui.theme.NotepadTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NotepadTheme {
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
        vm.saveDraft()
    }

    /** "Open with Notepad" from Google Drive, Files, email attachments, etc. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) {
            vm.openFromIntent(uri)
        }
    }
}
