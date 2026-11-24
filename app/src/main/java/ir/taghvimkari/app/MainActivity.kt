package ir.taghvimkari.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import ir.taghvimkari.app.ui.Navy
import ir.taghvimkari.app.ui.PeopleViewModel
import ir.taghvimkari.app.ui.TaghvimApp
import ir.taghvimkari.app.ui.TaghvimTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PeopleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 enforces edge-to-edge; the header is dark navy, the bottom bar is white.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Navy.toArgb()),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            TaghvimTheme { TaghvimApp(viewModel) }
        }
        if (savedInstanceState == null) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** A data file opened with, or shared to, this app is offered for import (after confirmation). */
    private fun handleIncoming(incoming: Intent?) {
        val uri = incoming?.let { extractUri(it) } ?: return
        viewModel.offerImport(uri)
    }

    @Suppress("DEPRECATION")
    private fun extractUri(i: Intent): Uri? = when (i.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        else -> null
    }
}
