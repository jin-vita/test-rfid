package org.techtown.testrfid

import android.app.Application
import android.content.Context


class MyApp : Application() {
    init {
        instance = this
    }

    companion object {
        private lateinit var instance: MyApp
        fun getContext(): Context = instance
    }
}