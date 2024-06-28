package android.boot.device.api

import android.util.Log

object DeviceLog {
    private const val TAG = "_EcgDevice"
    fun log(vararg logs: String, throwable: Throwable? = null) {
        if (logs.isEmpty() && throwable == null) return

        var tag = TAG
        var log = ""
        if (logs.isNotEmpty()) {
            if (logs.size > 1) {
                tag = logs[0]
                for (i in 1..<logs.size) {
                    log += logs[i]
                }
            } else {
                log = logs[0]
            }
        }

        if (throwable != null) Log.w(tag, "${Thread.currentThread().name}:$log", throwable)
        else Log.i(tag, "${Thread.currentThread().name}:$log")
    }
}