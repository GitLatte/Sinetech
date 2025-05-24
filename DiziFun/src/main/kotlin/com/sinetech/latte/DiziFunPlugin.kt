package com.sinetech.latte

import com.sinetech.latte.PremiumVideoExtractor
import com.sinetech.latte.GujanExtractor
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.APIHolder

@CloudstreamPlugin
class DiziFunPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziFun())
    }
}