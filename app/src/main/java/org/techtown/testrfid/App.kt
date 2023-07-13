package org.techtown.testrfid

import android.util.Log
import android.widget.Toast

class App {
    companion object {
        var mac = "00:05:C4:C1:00:13"

        /**
         * DEBUG 로그를 찍을 것인지 여부 확인
         */
        var isDebug = true


        /**
         * DEBUG 로그 찍기
         *
         * @param tag
         * @param msg
         */
        fun debug(tag: String, msg: String) {
            if (isDebug) Log.d(tag, msg)
        }

        /**
         * Show Toast message during 1 second
         *
         * @param msg 내용
         */
        fun showToast(msg: String) {
            if (Companion::toast.isInitialized) toast.cancel()
            toast = Toast.makeText(MyApp.getContext(), msg, Toast.LENGTH_SHORT)
            toast.show()
        }

        private lateinit var toast: Toast
    }
}