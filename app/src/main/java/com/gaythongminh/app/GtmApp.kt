package com.gaythongminh.app

import android.app.Application
import com.gaythongminh.app.data.Session
import com.gaythongminh.app.util.Notifier
import com.gaythongminh.app.util.Speaker

class GtmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        Speaker.init(this)
        Notifier.createChannels(this)
    }
}
