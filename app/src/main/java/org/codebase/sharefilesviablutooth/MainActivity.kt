package org.codebase.sharefilesviablutooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.util.*

private lateinit var bluetoothAdapter: BluetoothAdapter
private val uuid: UUID = UUID.fromString("06ae0a74-7bd4-43aa-ab5d-2511f3f6bab1") // GENERATE NEW UUID IF IT WONT WORK
private lateinit var mySelectedBluetoothDevice: BluetoothDevice
private lateinit var socket: BluetoothSocket
private lateinit var myHandler: Handler
@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!bluetoothAdapter.isEnabled) {
            Log.e("Issue", "Bluetooth Not On")

            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e("Issue", "Bluetooth On")

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            }
            launcher.launch(enableBtIntent)
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        pairedDevices?.forEach { device ->
            mySelectedBluetoothDevice = device
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address

            bluetoothNameTextView.text = "Device Name: $deviceName"
            bluetoothAddressTextView.text = "Device Address: $deviceHardwareAddress"
        }

        AcceptThread().start()
        myHandler = Handler()

        connectToDeviceButton.setOnClickListener{
            ConnectThread(mySelectedBluetoothDevice).start()
        }

        disconnectButton.setOnClickListener(){
            Log.d("Other phone", "Closing socket and connection")
            socket.close()
            connectedOrNotTextView.text = "Not connected"
            connectToDeviceButton.isEnabled = true; disconnectButton.isEnabled = false; sendMessageButton.isEnabled = false
        }

        sendMessageButton.setOnClickListener{
            if (writeMessageEditText.length() > 0) {
                var connectThreadInstance = ConnectThread(mySelectedBluetoothDevice)
                connectThreadInstance.writeMessage(writeMessageEditText.getText().toString())
                return@setOnClickListener
            }
            else {
                Toast.makeText(applicationContext, "Empty message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

        // Register for broadcasts when a device is discovered.
//        connectToDeviceButton.setOnClickListener {
//            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//            registerReceiver(receiver, filter)
//        }

    }

    val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ActivityResultCallback { result ->
        if (result.resultCode == RESULT_OK) {
            val intentData: Intent? = result.data

            Log.d("data", "$intentData")
            Log.d("data", "${result.resultCode}")
        }
    })

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action.toString()
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    val deviceName = device.name
                    val deviceHardwareAddress = device.address // MAC address
                    bluetoothNameTextView.text = "Device Name: $deviceName"
                    bluetoothAddressTextView.text = "Device Address: $deviceHardwareAddress"
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class AcceptThread() : Thread() {
        private var cancelled: Boolean
        private val serverSocket: BluetoothServerSocket?

        init {
            if (bluetoothAdapter.isEnabled) {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("test", uuid)
                cancelled = false
            } else {
                serverSocket = null
                cancelled = true
            }
        }

        override fun run() {
            var socket: BluetoothSocket
            while (true) {
                if (cancelled) {
                    break
                }
                try {
                    socket = serverSocket!!.accept()
                } catch (e: IOException) {
                    break
                }
                if (!cancelled && socket != null) {
                    Log.d("Other phone", "Connecting")
                    ConnectedThread(socket).start()
                }
            }
        }
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        @SuppressLint("MissingPermission")
        private var newSocket = device.createRfcommSocketToServiceRecord(uuid)

        @SuppressLint("MissingPermission")
        override fun run() {
            try {
                Log.d("You", "Connecting socket")
                myHandler.post {
                    connectedOrNotTextView.text = "Connecting..."
                    connectToDeviceButton.isEnabled = false
                }
                socket = newSocket
                socket.connect()
                Log.d("You", "Socket connected")
                myHandler.post {
                    connectedOrNotTextView.text = "Connected"
                    connectToDeviceButton.isEnabled = false; disconnectButton.isEnabled =
                    true; sendMessageButton.isEnabled = true
                }
            } catch (e1: Exception) {
                Log.e("You", "Error connecting socket, $e1")
                myHandler.post {
                    connectedOrNotTextView.text = "Connection failed"
                    connectToDeviceButton.isEnabled = true; disconnectButton.isEnabled =
                    false; sendMessageButton.isEnabled = false
                }
            }
        }

        fun writeMessage(newMessage: String){
            Log.d("You", "Sending")
            val outputStream = socket.outputStream
            try {
                outputStream.write(newMessage.toByteArray())
                outputStream.flush()
                Log.d("You", "Sent $newMessage")
                myHandler.post {
                    receivedMessageUserTextView.text = "Me: "
                    receivedMessageTextView.text = newMessage
                }
            } catch (e: Exception) {
                Log.e("You", "Cannot send, $e")
                return
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        @SuppressLint("MissingPermission")
        override fun run() {
            val inputStream = socket.inputStream
            var buffer = ByteArray(1024)
            var bytes = 0
            while (true) {
                try {
                    bytes = inputStream.read(buffer, bytes, 1024 - bytes)
                    var receivedMessage = String(buffer).substring(0, bytes)

                    Log.d("Other phone", "New received message: $receivedMessage")
                    myHandler.post {
                        receivedMessageUserTextView.text = mySelectedBluetoothDevice.name + ": "
                        receivedMessageTextView.text = receivedMessage
                    }
                    bytes = 0
                } catch (e :IOException) {
                    e.printStackTrace()
                    Log.d("Other phone", "Error reading")
                    break
                }
            }
        }
    }
}