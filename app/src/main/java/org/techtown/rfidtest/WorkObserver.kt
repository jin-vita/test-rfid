package org.techtown.rfidtest

import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

object WorkObserver {
    fun waitFor(work: ObservableWork) {
        Observable.timer(1, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { work.onWorkDone(work.run()) }
    }

    interface ObservableWork {
        fun run()
        fun onWorkDone(result: Any)
    }
}