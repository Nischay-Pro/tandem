package app.tandemforactual.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.tandemforactual.wear.data.WatchStore
import app.tandemforactual.wear.link.PhoneLink
import app.tandemforactual.wear.link.QuickAdds
import app.tandemforactual.wear.ui.TandemWearApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = WatchStore.load(this)
        val startOnQuickAdd = savedInstanceState == null && intent?.getStringExtra(EXTRA_OPEN) == OPEN_QUICK_ADD
        setContent {
            TandemWearApp(
                summaryFlow = store.summary,
                queueFlow = store.queue,
                phoneFlow = store.phoneHasTandem,
                startOnQuickAdd = startOnQuickAdd,
                onAdd = { amount, categoryId, payee -> QuickAdds.add(this, amount, categoryId, payee) },
                onRefresh = { refresh() },
                onInstallOnPhone = { PhoneLink(this).openStoreOnPhone() },
            )
        }
        refresh()
    }

    /** Reads what the phone last put in the Data Layer and asks it for a fresh summary. */
    private fun refresh() {
        lifecycleScope.launch {
            try {
                val link = PhoneLink(this@MainActivity)
                WatchStore.phoneChecked(link.phoneHasTandem())
                val stored = link.storedSummary()
                if (WatchStore.saveSummary(stored)) WatchSurfaces.refresh(this@MainActivity)
                QuickAdds.summaryArrived(this@MainActivity)
                link.requestSummary()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_QUICK_ADD = "quick-add"
    }
}
