package com.fnosari.bluetoothhid.connectivity.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Makes the phone a Bluetooth HID device (keyboard + mouse + media keys)
 * through the BluetoothHidDevice profile.
 *
 * Call [open] to get the profile proxy and register the HID app, then
 * [connect] a bonded host. Results arrive on [callBack] (main thread).
 *
 * @param context used for Bluetooth system services and the main executor.
 * @param callBack receives registration, connection and error events.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked in open()
class HIDDevice(private val context: Context, private val callBack: HIDDeviceCallBack) {

    companion object {
        private const val LOG_TAG = "HIDDevice"
        private const val SDP_NAME = "Bluetooth HID"
        private const val SDP_DESCRIPTION = "Keyboard and mouse"
        private const val SDP_PROVIDER = "fnosari"
    }

    /** Callback interface for HID events. */
    interface HIDDeviceCallBack {
        fun onAppRegistered(registered: Boolean, pluggedDevice: BluetoothDevice?)
        fun onConnectionStateChanged(device: BluetoothDevice, state: Int)
        fun onError(code: HIDDeviceExitCode)
    }

    /** Possible error codes. */
    enum class HIDDeviceExitCode {
        ERROR_BLUETOOTH_NOT_SUPPORTED,
        ERROR_BLUETOOTH_NOT_ENABLED,
        ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED,
        ERROR_HID_PROFILE_NOT_SUPPORTED,
        ERROR_HID_REGISTER_FAILED,
        ERROR_HID_NOT_CONNECTED,
        ERROR_HID_SEND_FAILED
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    @Volatile
    private var hid: BluetoothHidDevice? = null

    @Volatile
    private var closed = true

    @Volatile
    var connectedDevice: BluetoothDevice? = null
        private set

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(LOG_TAG, "onAppStatusChanged registered=$registered plugged=$pluggedDevice")
            callBack.onAppRegistered(registered, pluggedDevice)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(LOG_TAG, "onConnectionStateChanged $device state=$state")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
            } else if (device == connectedDevice) {
                connectedDevice = null
            }
            callBack.onConnectionStateChanged(device, state)
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id.toInt()) {
                HIDReport.Keyboard.ID -> 8
                HIDReport.Mouse.ID -> 5
                HIDReport.Consumer.ID -> 2
                else -> 0
            }
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && size > 0) {
                hid?.replyReport(device, type, id, ByteArray(size))
            } else {
                hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Keyboard LEDs (caps lock…) are ignored
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(LOG_TAG, "HID profile proxy connected")
            val device = proxy as BluetoothHidDevice
            if (closed) {
                // close() ran before the proxy arrived: never register on a dead instance
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
                return
            }
            hid = device
            val sdp = BluetoothHidDeviceAppSdpSettings(
                SDP_NAME,
                SDP_DESCRIPTION,
                SDP_PROVIDER,
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HIDDescriptor.BYTES
            )
            if (!device.registerApp(sdp, null, null, context.mainExecutor, hidCallback)) {
                Log.d(LOG_TAG, "registerApp failed")
                callBack.onError(HIDDeviceExitCode.ERROR_HID_REGISTER_FAILED)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(LOG_TAG, "HID profile proxy disconnected")
            hid = null
            connectedDevice = null
            callBack.onAppRegistered(false, null)
        }
    }

    /** Gets the HID profile proxy and registers the HID app. */
    fun open() {
        closed = false
        val a = adapter
        when {
            a == null -> callBack.onError(HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_SUPPORTED)
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                callBack.onError(HIDDeviceExitCode.ERROR_BLUETOOTH_CONNECT_PERMISSION_NOT_GRANTED)
            !a.isEnabled -> callBack.onError(HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED)
            !a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) ->
                callBack.onError(HIDDeviceExitCode.ERROR_HID_PROFILE_NOT_SUPPORTED)
        }
    }

    /** Unregisters the HID app and releases the profile proxy. */
    fun close() {
        closed = true
        val h = hid ?: return
        try {
            h.unregisterApp()
        } catch (e: Exception) {
            Log.d(LOG_TAG, "unregisterApp failed: ${e.message}")
        }
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        hid = null
        connectedDevice = null
    }

    fun connect(device: BluetoothDevice): Boolean = hid?.connect(device) ?: false

    fun disconnect(): Boolean {
        val d = connectedDevice ?: return false
        return hid?.disconnect(d) ?: false
    }

    /** Sends one input report to the connected host. Returns false when it could not be queued. */
    fun sendReport(report: HIDReport): Boolean {
        val d = connectedDevice ?: return false
        val h = hid ?: return false
        return h.sendReport(d, report.id, report.toBytes())
    }

    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList() ?: emptyList()
}
