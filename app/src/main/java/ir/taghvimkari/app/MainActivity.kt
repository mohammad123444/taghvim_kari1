package ir.taghvimkari.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import ir.taghvimkari.app.ui.LockScreen
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
    private var stoppedAt = 0L

    /** Result of the phone's own lock screen (PIN / pattern / fingerprint). */
    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) viewModel.unlocked()
    }

    private fun promptUnlock() {
        val km = getSystemService(KeyguardManager::class.java)
        // Without any phone lock there is nothing to ask for: never lock the person out.
        if (km == null || !km.isDeviceSecure) {
            viewModel.unlocked()
            return
        }
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("قفل برنامه", "برای ورود به تقویم کاری، قفل گوشی را وارد کنید")
        if (intent == null) viewModel.unlocked() else unlockLauncher.launch(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        viewModel.autoBackupIfDue()
        // back from more than a minute in the background: lock again
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) viewModel.lockNow()
    }

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
            // With the lock on, the app's screen is hidden from the recent-apps list and from screenshots.
            LaunchedEffect(viewModel.lockEnabled) {
                if (viewModel.lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            TaghvimTheme(viewModel.textSize) {
                Box(Modifier.fillMaxSize()) {
                    TaghvimApp(viewModel)
                    if (viewModel.locked) LockScreen(onUnlock = ::promptUnlock)
                }
            }
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

    private companion object {
        const val LOCK_AFTER_MS = 60_000L
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
