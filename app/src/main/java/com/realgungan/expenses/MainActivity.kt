package com.realgungan.expenses

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.realgungan.expenses.data.Expense
import com.realgungan.expenses.data.MonthData
import com.realgungan.expenses.data.createNewMonth
import com.realgungan.expenses.data.loadMonths
import com.realgungan.expenses.data.saveMonths
import com.realgungan.expenses.ui.MainScreen
import com.realgungan.expenses.ui.theme.ExpensesTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var fileUri by mutableStateOf<Uri?>(null)

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            fileUri = it
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            fileUri = it
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            }
            val savedUriString = prefs.getString("file_uri", null)

            if (savedUriString != null) {
                fileUri = Uri.parse(savedUriString)
            }

            if (fileUri == null) {
                FilePickerScreen(
                    onCreateFile = { createFileLauncher.launch("expenses_data.json") },
                    onOpenFile = { openFileLauncher.launch(arrayOf("application/json")) }
                )
            } else {
                fileUri?.let { uri ->
                    LaunchedEffect(uri) {
                        prefs.edit().putString("file_uri", uri.toString()).apply()
                    }
                    ExpensesApp(uri)
                }
            }
        }
    }
}

@Composable
fun FilePickerScreen(onCreateFile: () -> Unit, onOpenFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "To sync your data, please create a new expense file or open an existing one.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onCreateFile) {
            Text("Create a New Expense File")
        }
        Text("Or", modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onOpenFile) {
            Text("Open an Existing File")
        }
    }
}

@Composable
fun ExpensesApp(uri: Uri) {
    ExpensesTheme(darkTheme = true) {
        ExpensesAppContent(uri)
    }
}


@Composable
fun ExpensesAppContent(uri: Uri) {
    val context = LocalContext.current

    var months by remember { mutableStateOf(loadMonths(context, uri)) }
    var currentMonthIndex by remember { mutableStateOf(0) }
    var showNewMonthPrompt by remember { mutableStateOf(false) }
    var lastDeletedExpense by remember { mutableStateOf<Pair<Int, Expense>?>(null) }
    var monthToOverwrite by remember { mutableStateOf<MonthData?>(null) }

    // Save data whenever it changes
    LaunchedEffect(months) {
        saveMonths(context, uri, months)
    }

    fun updateMonth(index: Int, newMonthData: MonthData) {
        months = months.toMutableList().also { it[index] = newMonthData }
    }

    fun deleteMonth(index: Int) {
        val newMonths = months.toMutableList().also { it.removeAt(index) }
        if (newMonths.isEmpty()) {
            months = listOf(createNewMonth())
        } else {
            months = newMonths
        }
        // Always reset to the first month after deletion for safety.
        currentMonthIndex = 0
    }

    fun exportMonth(monthToExport: MonthData) {
        val totalExpenses = monthToExport.expenses.sumOf { it.amount }
        val finalBalance = monthToExport.startingAmount - totalExpenses

        val shareableString = buildString {
            appendLine("Expense Report for ${monthToExport.monthYear}")
            appendLine("--------------------")
            appendLine("Starting Amount: ${"%.2f".format(monthToExport.startingAmount)}")
            appendLine()
            appendLine("Expenses:")
            monthToExport.expenses.forEach { expense ->
                val dateString = expense.formattedDate ?: expense.timestamp?.let { sdf.format(Date(it)) } ?: ""
                appendLine("- ${expense.description}: ${"%.2f".format(expense.amount)} ($dateString)")
            }
            appendLine()
            appendLine("--------------------")
            appendLine("Total Expenses: ${"%.2f".format(totalExpenses)}")
            appendLine("Final Balance: ${"%.2f".format(finalBalance)}")
        }

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareableString)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Export Month Report")
        context.startActivity(shareIntent)
    }

    fun importMonth(importText: String) {
        try {
            val lines = importText.lines()
            if (lines.size < 5) return // Not a valid report

            val monthYear = lines.first { it.startsWith("Expense Report for") }.removePrefix("Expense Report for ").trim()
            val startingAmount = lines.first { it.startsWith("Starting Amount:") }.removePrefix("Starting Amount: ").trim().toDouble()

            val expensesStartIndex = lines.indexOf("Expenses:")
            if (expensesStartIndex == -1) return // Malformed

            var expensesEndIndex = -1
            for (i in (expensesStartIndex + 1) until lines.size) {
                if (lines[i].startsWith("--------------------")) {
                    expensesEndIndex = i
                    break
                }
            }
            if (expensesEndIndex == -1) return // Malformed

            val expenseLines = lines.subList(expensesStartIndex + 1, expensesEndIndex)

            val expenses = expenseLines.mapNotNull { line ->
                if (line.isBlank() || !line.trim().startsWith("-")) return@mapNotNull null

                val descriptionPart = line.substringBeforeLast(':').removePrefix("-").trim()
                val amountPart = line.substringAfterLast(':').substringBefore('(').trim()
                val amount = amountPart.toDoubleOrNull()

                if (descriptionPart.isNotBlank() && amount != null) {
                    Expense(descriptionPart, amount)
                } else {
                    null
                }
            }

            val newMonth = MonthData(monthYear, startingAmount, expenses)

            val existingIndex = months.indexOfFirst { it.monthYear == newMonth.monthYear }
            if (existingIndex != -1) {
                monthToOverwrite = newMonth
            } else {
                months = listOf(newMonth) + months
                currentMonthIndex = 0
            }
        } catch (e: Exception) {
            // Fail silently if parsing fails
        }
    }

    fun confirmOverwriteImport() {
        monthToOverwrite?.let { newMonth ->
            val existingIndex = months.indexOfFirst { it.monthYear == newMonth.monthYear }
            if (existingIndex != -1) {
                val mutableMonths = months.toMutableList()
                mutableMonths[existingIndex] = newMonth
                months = mutableMonths
                currentMonthIndex = existingIndex
            }
        }
        monthToOverwrite = null
    }

    val currentMonth = months.getOrNull(currentMonthIndex)

    if (currentMonth != null) {
        val availableAmount = currentMonth.startingAmount - currentMonth.expenses.sumOf { it.amount }

        fun undoDelete() {
            lastDeletedExpense?.let { (index, expense) ->
                val newExpenses = currentMonth.expenses.toMutableList().apply { add(index, expense) }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            }
            lastDeletedExpense = null
        }

        val sdf = SimpleDateFormat("EEEE: d - HH:mm", Locale.getDefault())

        MainScreen(
            months = months,
            currentMonth = currentMonth,
            currentMonthIndex = currentMonthIndex,
            availableAmount = availableAmount,
            showNewMonthPrompt = showNewMonthPrompt,
            lastDeletedExpense = lastDeletedExpense,
            monthToOverwrite = monthToOverwrite,
            onConfirmOverwrite = ::confirmOverwriteImport,
            onCancelOverwrite = { monthToOverwrite = null },
            onUndoDelete = ::undoDelete,
            onUndoPromptShown = { lastDeletedExpense = null },
            onNewMonthPromptShown = { showNewMonthPrompt = false },
            onMonthSelected = { index -> currentMonthIndex = index },
            onAddNewMonth = {
                val newMonth = createNewMonth()
                months = listOf(newMonth) + months
                currentMonthIndex = 0
                showNewMonthPrompt = true
            },
            onDeleteMonth = ::deleteMonth,
            onExportMonth = ::exportMonth,
            onImportMonth = ::importMonth,
            onAddExpense = { expense ->
                val timestamp = System.currentTimeMillis()
                val formattedDate = sdf.format(Date(timestamp))
                val newExpense = expense.copy(timestamp = timestamp, formattedDate = formattedDate)
                val newExpenses = listOf(newExpense) + currentMonth.expenses
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            },
            onRemoveExpense = { expenseIndex ->
                lastDeletedExpense = currentMonth.expenses[expenseIndex].let { expenseIndex to it }
                val newExpenses = currentMonth.expenses.toMutableList().also { it.removeAt(expenseIndex) }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            },
            onSaveExpenseEdit = { expenseIndex, updatedExpense ->
                val newExpenses = currentMonth.expenses.toMutableList().also { it[expenseIndex] = updatedExpense }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            },
            onStartingAmountChange = { newAmount ->
                updateMonth(currentMonthIndex, currentMonth.copy(startingAmount = newAmount))
            }
        )
    }
}

private val sdf = SimpleDateFormat("EEEE: d - HH:mm", Locale.getDefault())