package org.codebase.sharefilesviablutooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private lateinit var mySelectedBluetoothDevice: BluetoothDevice
private lateinit var bluetoothManager: BluetoothManager
private lateinit var socket: BluetoothSocket
private lateinit var myHandler: Handler

@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : AppCompatActivity() {
    val requestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Log.e("Issue", "Bluetooth Not On")

            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("Issue", "Bluetooth On")

                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
            }
            launcher.launch(enableBtIntent)
        }



        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        pairedDevices?.forEach { device ->
            mySelectedBluetoothDevice = device
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address

            bluetoothNameTextView.text = "$deviceName"
            bluetoothAddressTextView.text = "$deviceHardwareAddress"
        }

        AcceptThread().start()
        myHandler = Handler(Looper.myLooper()!!)

        connectToDeviceButton.setOnClickListener{
            ConnectThread(mySelectedBluetoothDevice).start()
        }

        disconnectButton.setOnClickListener{
            Log.d("Other phone", "Closing socket and connection")
            socket.close()
            connectedOrNotTextView.text = "Not connected"
            connectToDeviceButton.isEnabled = true; disconnectButton.isEnabled = false; sendMessageButton.isEnabled = false
        }

        sendMessageButton.setOnClickListener{
            if (writeMessageEditText.length() > 0) {
                val connectThreadInstance = ConnectThread(mySelectedBluetoothDevice)
                connectThreadInstance.writeMessage(writeMessageEditText.text.toString())
                return@setOnClickListener
            }
            else {
                Toast.makeText(applicationContext, "Empty message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

        // Register for broadcasts when a device is discovered.
        searchDeviceButtonId.setOnClickListener {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(receiver, filter)

            val requestCode = 1;
            val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            launcher.launch(discoverableIntent)

        }

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
                    Log.e("Receive", device.toString())
                    searchedDeviceNameId.text = "$deviceName"
                    searchedDeviceAddressId.text = "$deviceHardwareAddress"
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
//        private var cancelled: Boolean
//        private val serverSocket: BluetoothServerSocket?

//        init {
//            if (bluetoothAdapter.isEnabled) {
//                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("test", uuid)
//                cancelled = false
//            } else {
//                serverSocket = null
//                cancelled = true
//            }
//        }
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BluetoothApp", uuid)
        }

        override fun run() {
            var socket: BluetoothSocket
            var shouldLoop = true

            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    ConnectedThread(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }

            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }

    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(uuid)
        }


        override fun run() {
            Log.e("Socket", "$uuid")
            Log.e("Socket", "$mmSocket")
            bluetoothAdapter.cancelDiscovery()

            try {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
                }
                Log.d("You", "Connecting socket")
                myHandler.post {
                    connectedOrNotTextView.text = "Connecting..."
                    connectToDeviceButton.isEnabled = false
                }
//                mmSocket.let { socket->
//                    Log.e("Socket1", "$socket")
//
//
//
//                }
                socket = mmSocket!!

                socket.connect()
                ConnectedThread(socket)


                Log.d("You", "Socket connected")
                myHandler.post {
                    connectedOrNotTextView.text = "Connected"
                    connectToDeviceButton.isEnabled = false;
                    disconnectButton.isEnabled = true;
                    sendMessageButton.isEnabled = true
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
            val buffer = ByteArray(1024)
            var bytes = 0
            while (true) {
                try {
                    bytes = inputStream.read(buffer, bytes, 1024 - bytes)
                    val receivedMessage = String(buffer).substring(0, bytes)

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}