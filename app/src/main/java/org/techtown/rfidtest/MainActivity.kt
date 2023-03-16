package org.techtown.rfidtest

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apulsetech.lib.event.DeviceEvent
import com.apulsetech.lib.event.ReaderEventListener
import com.apulsetech.lib.remote.thread.RemoteController
import com.apulsetech.lib.remote.type.Module
import com.apulsetech.lib.remote.type.RemoteDevice
import com.apulsetech.lib.rfid.Reader
import org.techtown.rfidtest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ReaderEventListener {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG: String = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1322
    }

    private val mBluetoothAdapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val mBleSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }
    private val prefs: PreferenceUtil by lazy { PreferenceUtil(this) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val loading by lazy { LoadingDialog(this) }

    private lateinit var mReader: Reader
    private lateinit var remoteDevice: RemoteDevice
    private val timeout: Int = 10000
    private var isReconnect = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loading.findViewById<ConstraintLayout>(R.id.loading).setOnClickListener {
            isReconnect = !isReconnect
            App.showToast("재연결 상태. isReconnect : $isReconnect")
        }
        binding.button.setOnClickListener { connectByMac(App.mac) }
        binding.button2.setOnClickListener { connectByMac("00:05:C4:C1:00:14") }
        binding.button3.setOnClickListener {
            if (::mReader.isInitialized) mReader.destroy()
            isReconnect = false
            binding.idOutput.text = "기기가 연결되지 않았습니다"
        }
        binding.button4.setOnClickListener {
            if (::mReader.isInitialized) mReader.destroy()
            isReconnect = true
            binding.idOutput.text = "기기가 연결되지 않았습니다"
            if (isReconnect) connectRfid()
        }

        connectRfid()
    }

    private fun connectRfid() {
        // 블루투스를 지원하는지 여부
        if (!mBleSupported) return
        val granted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH)
        // 블루투스 권한이 허용되어 있는지 확인
        if (granted == PackageManager.PERMISSION_GRANTED) {
            // 블루투스가 꺼져 있으면 켤 것인지 물어보기
            if (!mBluetoothAdapter.isEnabled)
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
            // 블루투스가 켜져있을 때 자동연결상황이라면 연결시도
            else if (isReconnect) connectByMac(App.mac)
        }
        // 블루투스 권한이 없으면 요청
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) App.debug(TAG, "Bluetooth is on!")
            else App.debug(TAG, "Bluetooth is off!")

            connectRfid()
        }
    }

    private fun connectByMac(address: String) {
        App.debug(TAG, "connectByMac called")
        val btDevice: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(address)
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
        val reader = Reader.getReader(applicationContext, remoteDevice, false, timeout)
        if (reader == null) {
            binding.idOutput.text = "리더기 전원 확인"

            if (isReconnect) {
                App.showToast("리더기가 꺼져있나요? 재연결합니다!")
                App.error(TAG, "리더기가 꺼져있나요? 재연결합니다!")
                handler.postDelayed(::initialize, 3000)
            } else {
                loading.cancel()
                App.showToast("리더기가 꺼져있나요? 재연결하지 않습니다!")
                App.error(TAG, "리더기가 꺼져있나요? 재연결하지 않습니다!")
            }
            return
        }

        mReader = reader

        if (!mReader.start()) {
            mReader.destroy()
            binding.idOutput.text = "모듈 연결에 실패"
            App.showToast("RFID 모듈 연결에 실패하여 재연결합니다!")
            App.error(TAG, "RFID 모듈 연결에 실패하여 재연결합니다!")
            handler.postDelayed(::initialize, 3000)
            return
        }

        App.debug(TAG, "reader open success!")
        mReader.setEventListener(this)
        loading.cancel()
        App.showToast("리더기에 성공적으로 연결하였습니다!")
        binding.idOutput.text = "RFID MODEL : ${remoteDevice.name}\nMAC : ${remoteDevice.address}"
        val controller = RemoteController.getRemoteControllerIfAvailable()
        controller.apply {
            setRemoteDeviceTriggerActiveModule(Module.RFID)
            remoteDeviceBootSoundState = false
            remoteDeviceVibratorState = false
            remoteDeviceSoundState = false
            remoteDeviceSoundVolume = 2
        }
    }

    override fun onReaderDeviceStateChanged(state: DeviceEvent) {

        App.debug(TAG, "onReaderDeviceStateChanged called. DeviceEvent : $state")
        if (state == DeviceEvent.DISCONNECTED) {
            binding.idOutput.text = "기기가 연결되지 않았습니다"
            mReader.destroy()
            if (isReconnect) App.showToast("RFID 모듈에 재연결합니다!")
            else App.showToast("RFID 모듈의 연결을 끊었습니다!")
            connectRfid()
        }
    }
}