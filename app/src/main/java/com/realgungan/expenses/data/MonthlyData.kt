package com.realgungan.expenses.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Expense(
    val description: String,
    val amount: Double,
    val timestamp: Long? = null,
    val formattedDate: String? = null
)

@Serializable
data class MonthData(val monthYear: String, val startingAmount: Double, val expenses: List<Expense>)

// A more lenient JSON parser
private val json = Json { isLenient = true; ignoreUnknownKeys = true }

internal fun loadMonths(context: Context, uri: Uri): List<MonthData> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val jsonString = inputStream.bufferedReader().readText()
            if (jsonString.isNotBlank()) {
                json.decodeFromString<List<MonthData>>(jsonString)
            } else {
                listOf(createNewMonth())
            }
        } ?: listOf(createNewMonth())
    } catch (e: SerializationException) {
        // This will be caught by the UI and trigger the corrupt file dialog.
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
        listOf(createNewMonth())
    }
}

internal fun saveMonths(context: Context, uri: Uri, months: List<MonthData>) {
    try {
        // Use a file descriptor with "w" (truncate) mode for a robust, guaranteed overwrite.
        context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                // Explicitly truncate the file to 0 bytes before writing to prevent corruption.
                outputStream.channel.truncate(0)
                val jsonString = Json.encodeToString(months)
                outputStream.writer().use { writer ->
                    writer.write(jsonString)
                }
            }
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
