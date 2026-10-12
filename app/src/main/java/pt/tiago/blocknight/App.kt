package pt.tiago.blocknight

import android.app.Application

class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
