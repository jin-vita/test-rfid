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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apulsetech.lib.remote.type.RemoteDevice
import org.techtown.rfidtest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG: String = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1322
    }

    private val mBluetoothAdapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val mBleSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }
    private val prefs: PreferenceUtil by lazy { PreferenceUtil(this) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener { connectByMac("00:05:C4:C1:00:13") }
        binding.button2.setOnClickListener { connectByMac("00:05:C4:C1:00:14") }

    }


    override fun onResume() {
        super.onResume()
        turnOnBluetooth()
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
        val intent = Intent(this, RfidActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra(Extras.REMOTE_DEVICE, device)
        startActivity(intent)
        finish()
    }


    private fun turnOnBluetooth() {
        // 블루투스를 지원하는지 여부
        if (!mBleSupported) return
        // 블루투스 권한이 허용되어 있는지 확인
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 블루투스가 꺼져 있으면 켤 것인지 물어보기
            if (!mBluetoothAdapter.isEnabled) {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
            } else {
                reconnect()
            }
            // 블루투스 권한이 없으면 요청
        } else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), 0)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                App.debug(TAG, "Bluetooth is on!")
                reconnect()
                // 다른 작업 수행
            } else {
                App.debug(TAG, "Bluetooth is off!")
            }
        }
    }


    private fun reconnect() {
        val intent = intent
        val isReconnect = intent.getBooleanExtra(Extras.TRY_RECONNECT, false)
        if (isReconnect) {
            handler.postDelayed({
                connectByMac("00:05:C4:C1:00:13")
            }, 1000)
        }
    }
}