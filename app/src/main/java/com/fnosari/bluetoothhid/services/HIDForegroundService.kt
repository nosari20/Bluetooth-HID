package com.fnosari.bluetoothhid.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.fnosari.bluetoothhid.BluetoothHidApp
import com.fnosari.bluetoothhid.R
import com.fnosari.bluetoothhid.activities.main.MainActivity
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
import java.util.Locale

/**
 * Owns the HID connection so it survives activity changes. Activities bind
 * with [com.fnosari.bluetoothhid.utils.rememberHIDService].
 */
@SuppressLint("MissingPermission") // service only starts after BLUETOOTH_CONNECT is granted
class HIDForegroundService : Service(), HIDDevice.HIDDeviceCallBack {

    companion object {
        private const val LOG_TAG = "HIDForegroundService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "com.fnosari.bluetoothhid.action.DISCONNECT"
        const val ACTION_QUIT = "com.fnosari.bluetoothhid.action.QUIT"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HIDForegroundService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        val service: HIDForegroundService get() = this@HIDForegroundService
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

    /** Follows Bluetooth on/off at runtime. */
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                // The profile proxy stays registered: on STATE_ON the system reconnects it,
                // onServiceConnected registers the app again and onAppRegistered → Idle.
                BluetoothAdapter.STATE_OFF -> {
                    queue.failJobs()
                    _state.value = HIDState.Unavailable(HIDDevice.HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED)
                    updateNotification(getString(R.string.notification_idle), connected = false)
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
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_idle), connected = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        registerReceiver(adapterReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        device.open()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect()
            ACTION_QUIT -> quit()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        closing = true
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
                updateNotification(getString(R.string.notification_connected, host.name), connected = true)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                // host lost while typing → Failed(index) so the user can resume
                queue.failJobs()
                if (_state.value !is HIDState.Unavailable) _state.value = HIDState.Idle
                _layout.value = Layouts.byId(defaultLayoutId())
                updateNotification(getString(R.string.notification_idle), connected = false)
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

    fun quit() {
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

    // ---- Notification ----

    private fun updateNotification(text: String, connected: Boolean) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, connected))
    }

    private fun buildNotification(text: String, connected: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        fun serviceAction(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode,
            Intent(this, HIDForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, BluetoothHidApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
        if (connected) {
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.notification_disconnect),
                    serviceAction(ACTION_DISCONNECT, 1)).build()
            )
        }
        builder.addAction(
            Notification.Action.Builder(null, getString(R.string.notification_quit),
                serviceAction(ACTION_QUIT, 2)).build()
        )
        return builder.build()
    }
}
