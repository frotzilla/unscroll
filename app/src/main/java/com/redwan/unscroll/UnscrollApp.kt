package com.redwan.unscroll

import android.app.Application
import com.redwan.unscroll.data.Store

class UnscrollApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
