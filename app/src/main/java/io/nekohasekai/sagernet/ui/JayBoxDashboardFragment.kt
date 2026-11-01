package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.text.method.ScrollingMovementMethod
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import libcore.Libcore
import moe.matsuri.nb4a.utils.SendLog

class JayBoxDashboardFragment : ToolbarFragment(R.layout.layout_jaybox_dashboard) {
    private var stateView: TextView? = null
    private var profileView: TextView? = null
    private var modeView: TextView? = null
    private var uploadView: TextView? = null
    private var downloadView: TextView? = null
    private var totalView: TextView? = null
    private var logView: TextView? = null
    private var logFilter = ""
    private var logContent = ""
    private var logReadInFlight = false
    private val logHandler = Handler(Looper.getMainLooper())
    private val logRefresh = object : Runnable {
        override fun run() {
            refreshLogs()
            logHandler.postDelayed(this, 5_000L)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.title = getString(R.string.menu_jaybox_dashboard)
        stateView = view.findViewById(R.id.dashboard_state)
        profileView = view.findViewById(R.id.dashboard_profile)
        modeView = view.findViewById(R.id.dashboard_mode)
        uploadView = view.findViewById(R.id.dashboard_upload)
        downloadView = view.findViewById(R.id.dashboard_download)
        totalView = view.findViewById(R.id.dashboard_session_totals)
        logView = view.findViewById(R.id.dashboard_log_text)
        logView?.movementMethod = ScrollingMovementMethod.getInstance()
        view.findViewById<android.widget.EditText>(R.id.dashboard_log_filter).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                logFilter = s?.toString().orEmpty()
                renderLogs()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        view.findViewById<View>(R.id.dashboard_clear_logs).setOnClickListener { clearLogs() }
        view.findViewById<View>(R.id.dashboard_open_logs).setOnClickListener {
            (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_logcat)
        }
        updateState(DataStore.serviceState)
        updateMode()
        updateCurrentNode(DataStore.currentProfile)
    }

    override fun onResume() {
        super.onResume()
        logHandler.removeCallbacks(logRefresh)
        logHandler.post(logRefresh)
    }

    override fun onPause() {
        logHandler.removeCallbacks(logRefresh)
        super.onPause()
    }

    private fun refreshLogs() {
        if (view == null || logReadInFlight) return
        logReadInFlight = true
        runOnDefaultDispatcher {
            val text = try {
                String(SendLog.getNekoLog(32L * 1024))
            } catch (e: Exception) {
                e.readableMessage
            }
            onMainDispatcher {
                logReadInFlight = false
                if (view != null) {
                    logContent = text
                    renderLogs()
                }
            }
        }
    }

    private fun renderLogs() {
        val target = logView ?: return
        val lines = logContent.lineSequence().filter { line ->
            logFilter.isBlank() || line.contains(logFilter, ignoreCase = true)
        }.toList()
        val text = lines.joinToString("\n").ifBlank { getString(R.string.dashboard_logs_empty) }
        val styled = SpannableString(text)
        var offset = 0
        for (line in lines) {
            val color = when {
                line.contains("ERROR[", true) || line.contains(" [Error]", true) -> Color.RED
                line.contains("WARN[", true) || line.contains(" [Warning]", true) -> Color.rgb(220, 140, 0)
                line.contains("INFO[", true) || line.contains(" [Info]", true) -> Color.rgb(60, 140, 70)
                else -> Color.GRAY
            }
            if (line.isNotEmpty()) styled.setSpan(ForegroundColorSpan(color), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            offset += line.length + 1
        }
        target.text = styled
    }

    private fun clearLogs() {
        runOnDefaultDispatcher {
            try {
                Libcore.nekoLogClear()
                Runtime.getRuntime().exec("/system/bin/logcat -c")
                onMainDispatcher {
                    logContent = ""
                    renderLogs()
                }
            } catch (e: Exception) {
                onMainDispatcher { (activity as? MainActivity)?.snackbar(e.readableMessage)?.show() }
            }
        }
    }

    fun updateCurrentNode(profileId: Long) {
        val target = profileView ?: return
        val context = context ?: return
        val profileName = try {
            ProfileManager.getProfile(profileId)?.displayName()
        } catch (_: Exception) {
            null
        }
        target.text = context.getString(
            R.string.dashboard_current_node,
            profileName ?: context.getString(R.string.dashboard_no_current_node)
        )
    }

    fun updateState(state: BaseService.State) {
        val context = context ?: return
        stateView?.text = context.getString(
            when (state) {
                BaseService.State.Connected -> R.string.dashboard_state_connected
                BaseService.State.Connecting -> R.string.dashboard_state_connecting
                BaseService.State.Stopping -> R.string.dashboard_state_stopping
                BaseService.State.Stopped, BaseService.State.Idle -> R.string.dashboard_state_stopped
            }
        )
        if (state != BaseService.State.Connected) {
            val zeroRate = android.text.format.Formatter.formatFileSize(context, 0L)
            uploadView?.text = context.getString(R.string.dashboard_upload_value, zeroRate)
            downloadView?.text = context.getString(R.string.dashboard_download_value, zeroRate)
            totalView?.setText(R.string.dashboard_session_unknown)
        }
    }

    fun updateMode() {
        val context = context ?: return
        val mode = if (DataStore.serviceMode == Key.MODE_VPN) {
            R.string.dashboard_mode_vpn
        } else {
            R.string.dashboard_mode_proxy
        }
        modeView?.text = context.getString(R.string.dashboard_mode, context.getString(mode))
    }

    fun updateSpeed(data: SpeedDisplayData) {
        val context = context ?: return
        val upload = android.text.format.Formatter.formatFileSize(context, data.txRateProxy)
        val download = android.text.format.Formatter.formatFileSize(context, data.rxRateProxy)
        uploadView?.text = context.getString(R.string.dashboard_upload_value, upload)
        downloadView?.text = context.getString(R.string.dashboard_download_value, download)
        totalView?.text = context.getString(
            R.string.dashboard_session_totals_value,
            android.text.format.Formatter.formatFileSize(context, data.txTotal),
            android.text.format.Formatter.formatFileSize(context, data.rxTotal)
        )
    }

    override fun onDestroyView() {
        logHandler.removeCallbacks(logRefresh)
        stateView = null
        profileView = null
        modeView = null
        uploadView = null
        downloadView = null
        totalView = null
        logView = null
        super.onDestroyView()
    }
}
