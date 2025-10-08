package com.realgungan.expenses.data

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Expense(val description: String, val amount: Double, val timestamp: Long? = null)

@Serializable
data class MonthData(val monthYear: String, val startingAmount: Double, val expenses: List<Expense>)

internal fun loadMonths(prefs: SharedPreferences): List<MonthData> {
    val jsonString = prefs.getString("all_months_data", null)
    return if (jsonString != null) {
        try {
            Json.decodeFromString<List<MonthData>>(jsonString)
        } catch (e: Exception) {
            listOf(createNewMonth())
        }
    } else {
        listOf(createNewMonth())
    }
}

internal fun saveMonths(prefs: SharedPreferences, months: List<MonthData>) {
    val jsonString = Json.encodeToString(months)
    prefs.edit().putString("all_months_data", jsonString).apply()
}

internal fun createNewMonth(): MonthData {
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val newMonthName = sdf.format(Date())
    // New months start with a clean slate.
    return MonthData(monthYear = newMonthName, startingAmount = 0.0, expenses = emptyList())
}
