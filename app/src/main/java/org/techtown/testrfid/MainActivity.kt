package org.techtown.testrfid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apulsetech.lib.event.DeviceEvent
import com.apulsetech.lib.event.ReaderEventListener
import com.apulsetech.lib.remote.thread.RemoteController
import com.apulsetech.lib.remote.type.Module
import com.apulsetech.lib.remote.type.RemoteDevice
import com.apulsetech.lib.remote.type.RemoteSetting
import com.apulsetech.lib.rfid.Reader
import com.apulsetech.lib.rfid.type.RFID
import com.apulsetech.lib.rfid.type.RfidResult
import org.techtown.testrfid.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), ReaderEventListener {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG: String = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1322
    }

    private val mBluetoothAdapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val mBleSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val loading by lazy { LoadingDialog(this) }
    private val text by lazy { StringBuilder() }

    private lateinit var mReader: Reader
    private lateinit var remoteDevice: RemoteDevice
    private val timeout: Int = 15000
    private var isReconnect = true
    private var mInventoryStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loading.findViewById<ConstraintLayout>(R.id.loading).setOnClickListener {
            isReconnect = !isReconnect

            App.showToast("재연결 상태. isReconnect : $isReconnect")
        }
        binding.button.setOnClickListener { connectByMac(App.mac) }

        binding.button2.setOnClickListener {
            if (::mReader.isInitialized) mReader.destroy()
            isReconnect = true
            binding.idOutput.text = "기기가 연결되지 않았습니다"
            if (isReconnect) connectRfid()
        }

        binding.button3.setOnClickListener {
            if (::mReader.isInitialized) mReader.destroy()
            isReconnect = false
            binding.idOutput.text = "기기가 연결되지 않았습니다"
        }

        binding.button4.setOnClickListener { toggleInventory() }

        binding.logOutput.movementMethod = ScrollingMovementMethod()

        connectRfid()
    }

    private fun toggleInventory() {
        printLog("toggleInventory called. mInventoryStarted : $mInventoryStarted")
        binding.button4.isEnabled = false
        val result: Int
        if (mInventoryStarted) {
            result = mReader.stopOperation()
            if (result == RfidResult.SUCCESS) {
                printLog("인벤토리 중지!")
                mInventoryStarted = false
                binding.spinnerPowerGain.isEnabled = true
                binding.button4.text = "시작"
            } else {
                printLog("인벤토리 중지에 실패하였습니다!")
            }
        } else {
            result = mReader.startInventory()
            when (result) {
                RfidResult.SUCCESS -> {
                    mInventoryStarted = true
                    binding.spinnerPowerGain.isEnabled = false
                    binding.button4.text = "정지"
                }

                RfidResult.LOW_BATTERY -> printLog("배터리 잔량이 부족합니다!")
                else -> printLog("인벤토리 시작에 실패하였습니다!")
            }
        }
        binding.button4.isEnabled = true
    }


    override fun onReaderEvent(event: Int, result: Int, data: String?) {
        printLog("onReaderEvent(): event=$event, result=$result, data=$data")
        when (event) {
            Reader.READER_CALLBACK_EVENT_INVENTORY -> if (result == RfidResult.SUCCESS) {
                printLog("$data")
            }
        }
    }


    private fun setPower() {
        val powerAdapter = ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item)
        for (i in RFID.Power.MIN_POWER..RFID.Power.MAX_POWER)
            powerAdapter.add(String.format("%.1f dBm", i / 1.0f))
        binding.spinnerPowerGain.adapter = powerAdapter

        val itemSelectedListener: AdapterView.OnItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                printLog("onItemSelected. power : ${i + RFID.Power.MIN_POWER}")
                if (adapterView == binding.spinnerPowerGain) {
                    mReader.radioPower = i + RFID.Power.MIN_POWER
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        binding.spinnerPowerGain.onItemSelectedListener = itemSelectedListener

        val powerGain = mReader.radioPower
        if (powerGain >= RFID.Power.MIN_POWER && powerGain <= RFID.Power.MAX_POWER)
            binding.spinnerPowerGain.setSelection(powerGain - RFID.Power.MIN_POWER)
    }

    override fun onReaderRemoteSettingChanged(type: Int, value: Any) {
        printLog("onReaderRemoteSettingChanged. type=$type value=$value")
        if (type == RemoteSetting.RFID_POWER) {
            val power = value as Int
            binding.spinnerPowerGain.setSelection(power - RFID.Power.MIN_POWER)
        }
    }

    private fun connectRfid() {
        // 블루투스를 지원하는지 여부
        if (!mBleSupported) return
        val granted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH)
        // 블루투스 권한이 허용되어 있는지 확인
        if (granted == PackageManager.PERMISSION_GRANTED) {
            // 블루투스가 꺼져 있으면 켤 것인지 물어보기
            @Suppress("DEPRECATION")
            if (!mBluetoothAdapter.isEnabled)
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
            // 블루투스가 켜져있을 때 자동연결상황이라면 연결시도
            else if (isReconnect) connectByMac(App.mac)
        }
        // 블루투스 권한이 없으면 요청
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) printLog("Bluetooth is on!")
            else printLog("Bluetooth is off!")

            connectRfid()
        }
    }

    private fun connectByMac(address: String) {
        printLog("connectByMac called")
        @SuppressLint("MissingPermission")
        val btDevice: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(address).apply { createBond() }
        val device: RemoteDevice = RemoteDevice.makeBtSppDevice(btDevice)

        if (device.name.isNullOrBlank()) {
            App.showToast("선택한 맥주소에 해당하는 리더기가 없습니다!")
            return
        }

        App.showToast("RFID MODEL : ${device.name}\nMAC : ${device.address}")
        remoteDevice = device
        loading.show()
        handler.postDelayed(::initialize, 500)
    }

    private fun initialize() {
        App.debug(TAG, "initialize called. remoteDevice : $remoteDevice, timeout : $timeout")
        printLog("initialize called. remoteDevice : $remoteDevice, timeout : $timeout")
        val reader = Reader.getReader(applicationContext, remoteDevice, false, timeout)
        if (reader == null) {
            binding.idOutput.text = "리더기 전원 확인"

            if (isReconnect) {
                printLog("리더기가 꺼져있나요? 재연결합니다!")
                handler.postDelayed(::initialize, 3000)
            } else {
                loading.cancel()
                printLog("리더기가 꺼져있나요? 재연결하지 않습니다!")
            }
            return
        }
        mReader = reader
        if (!mReader.start()) {
            mReader.destroy()
            binding.idOutput.text = "모듈 연결에 실패"
            printLog("RFID 모듈 연결에 실패하여 재연결합니다!")
            handler.postDelayed(::initialize, 3000)
            return
        }

        printLog("reader open success!")
        mReader.setEventListener(this)
        loading.cancel()
        App.showToast("리더기에 성공적으로 연결하였습니다!")
        text.append("RFID MODEL : ").appendLine(remoteDevice.name)
            .append("MAC : ").append(remoteDevice.address)
        binding.idOutput.text = text

        val controller = RemoteController.getRemoteControllerIfAvailable()
        controller.apply {
            setRemoteDeviceTriggerActiveModule(Module.RFID)
            remoteDeviceBootSoundState = false
            remoteDeviceVibratorState = false
            remoteDeviceSoundState = false
            remoteDeviceSoundVolume = 2
        }

        setPower()

    }

    override fun onReaderDeviceStateChanged(state: DeviceEvent) {
        printLog("onReaderDeviceStateChanged called. DeviceEvent : $state")
        if (state == DeviceEvent.DISCONNECTED) {
            binding.idOutput.text = "기기가 연결되지 않았습니다"
            mReader.destroy()
            if (isReconnect) App.showToast("RFID 모듈에 재연결합니다!")
            else App.showToast("RFID 모듈의 연결을 끊었습니다!")
            connectRfid()
        }
    }

    fun printLog(message: String) = runOnUiThread {
        @SuppressLint("SimpleDateFormat")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        binding.logOutput.append("[$now] $message\n")
        moveToBottom(binding.logOutput)
    }

    private fun moveToBottom(textView: TextView) = textView.post {
        val scrollAmount = try {
            textView.layout.getLineTop(textView.lineCount) - textView.height
        } catch (_: NullPointerException) {
            0
        }
        if (scrollAmount > 0) textView.scrollTo(0, scrollAmount)
        else textView.scrollTo(0, 0)
    }
}