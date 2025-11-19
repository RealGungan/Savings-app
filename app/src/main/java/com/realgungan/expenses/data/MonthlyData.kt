package com.realgungan.expenses.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Expense(val description: String, val amount: Double, val timestamp: Long? = null)

@Serializable
data class MonthData(val monthYear: String, val startingAmount: Double, val expenses: List<Expense>)

internal fun loadMonths(context: Context, uri: Uri): List<MonthData> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val jsonString = inputStream.bufferedReader().readText()
            if (jsonString.isNotBlank()) {
                Json.decodeFromString<List<MonthData>>(jsonString)
            } else {
                listOf(createNewMonth())
            }
        } ?: listOf(createNewMonth())
    } catch (e: Exception) {
        e.printStackTrace()
        listOf(createNewMonth())
    }
}

internal fun saveMonths(context: Context, uri: Uri, months: List<MonthData>) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val jsonString = Json.encodeToString(months)
            outputStream.writer().use { it.write(jsonString) }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal fun createNewMonth(): MonthData {
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val newMonthName = sdf.format(Date())
    // New months start with a clean slate.
    return MonthData(monthYear = newMonthName, startingAmount = 0.0, expenses = emptyList())
}
