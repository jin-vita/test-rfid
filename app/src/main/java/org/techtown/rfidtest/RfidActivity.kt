package org.techtown.rfidtest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.apulsetech.lib.event.DeviceEvent
import com.apulsetech.lib.event.ReaderEventListener
import com.apulsetech.lib.remote.thread.RemoteController
import com.apulsetech.lib.remote.type.Module
import com.apulsetech.lib.remote.type.RemoteDevice
import com.apulsetech.lib.rfid.Reader
import org.techtown.rfidtest.databinding.ActivityRfidBinding

class RfidActivity : AppCompatActivity(), ReaderEventListener {
    private lateinit var binding: ActivityRfidBinding

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val loading by lazy { LoadingDialog(this) }
    private var mInitialized = false
    private lateinit var mReader: Reader
    private lateinit var remoteDevice: RemoteDevice
    private val timeout: Int = 10000

    companion object {
        private const val TAG: String = "RfidActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRfidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = intent
        remoteDevice = intent.getParcelableExtra(Extras.REMOTE_DEVICE)!!

        loading.show()
        WorkObserver.waitFor(object : WorkObserver.ObservableWork {
            override fun run() {
                initialize()
            }

            override fun onWorkDone(result: Any) {
                mInitialized = true
            }
        })

        binding.button.setOnClickListener { disconnectRfid(false) }
        binding.button2.setOnClickListener { disconnectRfid(true) }
    }

    private fun initialize() {
        App.debug(TAG, "initialize called. remoteDevice : $remoteDevice, timeout : $timeout")
        val reader =
            Reader.getReader(applicationContext, remoteDevice, false, timeout) ?: Reader.getReader(applicationContext)
        if (reader == null) {
            App.showToast("리더기가 꺼져있나요? 재연결합니다!")
            App.error(TAG, "리더기가 꺼져있나요? 재연결합니다!")
            handler.postDelayed(::initialize, 3000)
            return
        } else mReader = reader
        if (mReader.start()) {
            loading.cancel()
            App.debug(TAG, "reader open success!")
            mReader.setEventListener(this@RfidActivity)
            if (mReader.isRemoteDevice) {
                App.showToast("리더기에 성공적으로 연결하였습니다!")
                binding.idOutput.text = "RFID MODEL : ${remoteDevice.name}\nMAC : ${remoteDevice.address}"
                val controller = RemoteController.getRemoteControllerIfAvailable()
                if (controller != null) {
                    controller.setRemoteDeviceTriggerActiveModule(Module.RFID)
                    controller.remoteDeviceBootSoundState = false
                    controller.remoteDeviceVibratorState = false
                    controller.remoteDeviceSoundState = false
                    controller.remoteDeviceSoundVolume = 1
                }
            }
        } else {
            mReader.destroy()
            App.showToast("RFID 모듈 연결에 실패하여 재연결합니다!")
            App.error(TAG, "RFID 모듈 연결에 실패하여 재연결합니다!")
            handler.postDelayed(::initialize, 3000)
        }
    }

    private fun disconnectRfid(isReconnect: Boolean) {
        mReader.destroy()
        if (isReconnect) App.showToast("RFID 모듈에 재연결합니다!")
        else App.showToast("RFID 모듈의 연결을 끊었습니다!")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(Extras.TRY_RECONNECT, isReconnect)
        startActivity(intent)
        finish()
    }

    override fun onReaderDeviceStateChanged(state: DeviceEvent) {
        App.debug(TAG, "DeviceEvent : $state")
        if (state == DeviceEvent.DISCONNECTED) {
            disconnectRfid(true)
        } else if (state == DeviceEvent.USB_CHARGING_ENABLED) {
        }
    }

    override fun onBackPressed() {
        disconnectRfid(false)
    }
}