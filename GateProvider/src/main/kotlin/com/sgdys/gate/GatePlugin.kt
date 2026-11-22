package com.sgdys.gate

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GatePlugin : Plugin() {
    override fun load(context: Context) {
        // The Gate provider ko CloudStream me register karo
        registerMainAPI(GateProvider())
    }
}
