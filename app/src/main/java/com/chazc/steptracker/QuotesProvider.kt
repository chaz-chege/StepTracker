package com.chazc.steptracker

import com.chazc.steptracker.ui.theme.ThemeConfig
import java.util.Calendar

object QuotesProvider {
    fun getQuoteOfDay(theme: ThemeConfig): String {
        val quotes = theme.quotes.ifEmpty { listOf("I'm gonna be King of the Pirates!") }
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return quotes[dayOfYear % quotes.size]
    }
}
