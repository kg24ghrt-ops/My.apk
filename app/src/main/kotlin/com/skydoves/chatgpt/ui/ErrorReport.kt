package com.skydoves.chatgpt.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ErrorReport(
    val source: String,
    val message: String,
    val stackTrace: String,
    val timestamp: String = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.US
    ).format(Date())
)