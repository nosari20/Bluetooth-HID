package com.fnosari.bluetoothhid.services

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice
import com.fnosari.bluetoothhid.connectivity.hid.HIDReport
import com.fnosari.bluetoothhid.connectivity.hid.HIDReportQueue
import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyStroke
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyboardLayout
import com.fnosari.bluetoothhid.connectivity.hid.layouts.Layouts
import com.fnosari.bluetoothhid.connectivity.hid.layouts.TextTyper
import com.fnosari.bluetoothhid.connectivity.hid.layouts.TypedText
import com.fnosari.bluetoothhid.model.Host
import com.fnosari.bluetoothhid.model.TypingSpeed
import com.fnosari.bluetoothhid.persistance.preferences.DataStorePreferencesProvider
import com.fnosari.bluetoothhid.persistance.preferences.Preferences
import com.fnosari.bluetoothhid.persistance.preferences.PreferencesProvider
import com.fnosari.bluetoothhid.utils.PointerMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.Locale

/**
 * Owns the HID connection so it survives activity changes. Activities start and bind it
 * with [com.fnosari.bluetoothhid.utils.rememberHIDService].
 *
 * Plain (not foreground) service: it lives while a screen uses it or work is in progress, and
 * stops when idle with no app screen visible ([ServicePolicy]). No notification.
 */
@SuppressLint("MissingPermission") // service only starts after BLUETOOTH_CONNECT is granted
class HIDService : Service(), HIDDevice.HIDDeviceCallBack {

    companion object {
        private const val LOG_TAG = "HIDService"

        /** Call from a visible screen only (background service starts are not allowed). */
        fun start(context: Context) {
            context.startService(Intent(context, HIDService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        val service: HIDService get() = this@HIDService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var preferences: PreferencesProvider
        private set
    private lateinit var device: HIDDevice
    private lateinit var queue: HIDReportQueue

    private val _state = MutableStateFlow<HIDState>(HIDState.Starting)
    val state: StateFlow<HIDState> = _state.asStateFlow()

    private val _layout = MutableStateFlow<KeyboardLayout>(Layouts.byId("us"))
    val layout: StateFlow<KeyboardLayout> = _layout.asStateFlow()

    private val _typing = MutableStateFlow<TypingProgress?>(null)
    val typing: StateFlow<TypingProgress?> = _typing.asStateFlow()

    private val _events = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TypingEvent> = _events.asSharedFlow()

    @Volatile
    private var mouseButtons = 0
    private var closing = false

    private val appVisible = MutableStateFlow(false)
    private val visibilityObserver = LifecycleEventObserver { owner, _ ->
        appVisible.value = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    /** Follows Bluetooth on/off at runtime. */
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                // The profile proxy stays registered: on STATE_ON the system reconnects it,
                // onServiceConnected registers the app again and onAppRegistered → Idle.
                BluetoothAdapter.STATE_OFF -> {
                    queue.failJobs()
                    _state.value = HIDState.Unavailable(HIDDevice.HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = DataStorePreferencesProvider(this, scope)
        _layout.value = Layouts.byId(defaultLayoutId())
        device = HIDDevice(this, this)
        queue = HIDReportQueue(device::sendReport, currentSpeed().delayMs)
        queue.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(visibilityObserver)
        scope.launch {
            combine(
                state,
                typing.map { it != null }.distinctUntilChanged(),
                appVisible
            ) { s, t, v -> Triple(s, t, v) }
                .distinctUntilChanged()
                .collect { (s, t, v) -> applyPolicy(s, t, v) }
        }
        registerReceiver(adapterReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        device.open()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        closing = true
        ProcessLifecycleOwner.get().lifecycle.removeObserver(visibilityObserver)
        unregisterReceiver(adapterReceiver)
        _state.value = HIDState.Idle
        _typing.value = null
        queue.stop()
        device.close()
        scope.cancel()
        super.onDestroy()
    }

    // ---- HIDDevice callbacks (main thread) ----

    override fun onAppRegistered(registered: Boolean, pluggedDevice: BluetoothDevice?) {
        if (closing) return
        if (!registered) {
            if (_state.value !is HIDState.Unavailable) _state.value = HIDState.Idle
            return
        }
        if (_state.value is HIDState.Starting || _state.value is HIDState.Unavailable) {
            _state.value = HIDState.Idle
        }
        if (preferences.readBoolean(Preferences.AUTO_RECONNECT, true)) {
            val target = pluggedDevice?.address ?: preferences.readString(Preferences.LAST_HOST, "")
            if (target.isNotEmpty()) connect(target)
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        val host = Host(device.address, device.name ?: device.address)
        when (state) {
            BluetoothProfile.STATE_CONNECTING -> _state.value = HIDState.Connecting(host)
            BluetoothProfile.STATE_CONNECTED -> {
                _state.value = HIDState.Connected(host)
                preferences.write(Preferences.LAST_HOST, host.address)
                _layout.value = layoutFor(host.address)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                // host lost while typing → Failed(index) so the user can resume
                queue.failJobs()
                if (_state.value !is HIDState.Unavailable) _state.value = HIDState.Idle
                _layout.value = Layouts.byId(defaultLayoutId())
            }
        }
    }

    override fun onError(code: HIDDevice.HIDDeviceExitCode) {
        Log.d(LOG_TAG, "HID error $code")
        _state.value = HIDState.Unavailable(code)
    }

    // ---- Connection API ----

    fun retry() {
        device.close()
        _state.value = HIDState.Starting
        device.open()
    }

    fun bondedHosts(): List<Host> =
        device.bondedDevices().map { Host(it.address, it.name ?: it.address) }.sortedBy { it.name }

    fun connect(address: String) {
        val target = device.bondedDevices().firstOrNull { it.address == address } ?: return
        device.connect(target)
    }

    fun disconnect() {
        queue.cancelJobs()
        device.disconnect()
    }

    fun setLayoutForCurrentHost(layoutId: String) {
        val host = (state.value as? HIDState.Connected)?.host
        if (host != null) {
            preferences.write(Preferences.hostLayoutKey(host.address), layoutId)
        } else {
            preferences.write(Preferences.DEFAULT_LAYOUT, layoutId)
        }
        _layout.value = Layouts.byId(layoutId)
    }

    private fun defaultLayoutId(): String =
        preferences.readString(Preferences.DEFAULT_LAYOUT, Layouts.defaultIdFor(Locale.getDefault().language))

    private fun layoutFor(address: String): KeyboardLayout =
        Layouts.byId(preferences.readString(Preferences.hostLayoutKey(address), defaultLayoutId()))

    private fun currentSpeed(): TypingSpeed =
        TypingSpeed.fromName(preferences.readString(Preferences.TYPING_SPEED, TypingSpeed.NORMAL.name))

    private val isConnected: Boolean get() = state.value is HIDState.Connected

    // ---- Keyboard API ----

    /**
     * Types [text] on the host. [track] = report progress in [typing] and the
     * outcome in [events] (off for live keyboard echo).
     * @return the typed text plan, or null when not connected.
     */
    fun typeText(text: String, enterAtEnd: Boolean = false, track: Boolean = true): TypedText? {
        if (!isConnected) return null
        val typed = TextTyper.type(if (enterAtEnd) text + "\n" else text, layout.value)
        queue.delayMs = currentSpeed().delayMs
        if (!track) {
            queue.submitJob(typed.reports)
            return typed
        }
        _typing.value = TypingProgress(0, typed.reports.size)
        queue.submitJob(typed.reports, object : HIDReportQueue.JobListener {
            override fun onProgress(sent: Int, total: Int) {
                _typing.value = TypingProgress(sent, total)
            }

            override fun onFinished(result: HIDReportQueue.JobResult) {
                _typing.value = null
                val event = when (result) {
                    HIDReportQueue.JobResult.Completed -> TypingEvent.Completed
                    HIDReportQueue.JobResult.Cancelled -> TypingEvent.Cancelled
                    is HIDReportQueue.JobResult.Failed ->
                        TypingEvent.Interrupted(typed.charIndexAt(result.sentReports), typed.text)
                }
                _events.tryEmit(event)
            }
        })
        return typed
    }

    fun cancelTyping() = queue.cancelJobs()

    fun sendKey(usage: Int, modifiers: Int = 0) {
        if (!isConnected) return
        queue.submitJob(listOf(HIDReport.Keyboard(modifiers, listOf(usage)), HIDReport.Keyboard.RELEASE))
    }

    fun sendStroke(stroke: KeyStroke, extraModifiers: Int = 0) =
        sendKey(stroke.usage, stroke.modifiers or extraModifiers)

    fun backspace(count: Int) {
        if (!isConnected || count <= 0) return
        val press = HIDReport.Keyboard(0, listOf(HIDKeys.BACKSPACE))
        queue.submitJob(List(count * 2) { if (it % 2 == 0) press else HIDReport.Keyboard.RELEASE })
    }

    fun sendConsumer(usage: Int) {
        if (!isConnected) return
        queue.submit(HIDReport.Consumer(usage))
        queue.submit(HIDReport.Consumer.RELEASE)
    }

    // ---- Mouse API ----

    fun mouseMove(dx: Int, dy: Int) {
        if (!isConnected) return
        for ((x, y) in PointerMath.split(dx, dy)) queue.submit(HIDReport.Mouse(mouseButtons, x, y))
    }

    fun mouseScroll(steps: Int) {
        if (!isConnected || steps == 0) return
        queue.submit(HIDReport.Mouse(mouseButtons, wheel = steps.coerceIn(-127, 127)))
    }

    fun mouseClick(button: Int) {
        if (!isConnected) return
        queue.submit(HIDReport.Mouse(mouseButtons or button))
        queue.submit(HIDReport.Mouse(mouseButtons))
    }

    fun setMouseButtons(buttons: Int) {
        mouseButtons = buttons
        if (isConnected) queue.submit(HIDReport.Mouse(buttons))
    }

    // ---- Lifetime ----

    /** Stop when idle and no app screen is visible; otherwise keep running. */
    private fun applyPolicy(state: HIDState, typing: Boolean, visible: Boolean) {
        if (closing) return
        if (ServicePolicy.shouldStop(state, typing, visible)) {
            Log.d(LOG_TAG, "idle and not visible: stopping")
            stopSelf()
        }
    }
}
