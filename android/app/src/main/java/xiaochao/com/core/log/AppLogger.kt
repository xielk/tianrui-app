package xiaochao.com.core.log

import android.util.Log

class AppLogger {
    fun info(entry: LogEntry) {
        runCatching {
            Log.i(entry.tag, "${entry.message} | ${entry.payload}")
        }.getOrElse {
            println("I/${entry.tag}: ${entry.message} | ${entry.payload}")
        }
    }

    fun error(entry: LogEntry) {
        runCatching {
            Log.e(entry.tag, "${entry.message} | ${entry.payload}")
        }.getOrElse {
            println("E/${entry.tag}: ${entry.message} | ${entry.payload}")
        }
    }
}
