package com.skydoves.chatgpt

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

class BrowserAIManager(private val context: Context) {
    fun openAIChat(url: String) {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}