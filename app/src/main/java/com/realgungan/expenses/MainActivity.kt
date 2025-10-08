package com.realgungan.expenses

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.realgungan.expenses.data.Expense
import com.realgungan.expenses.data.MonthData
import com.realgungan.expenses.data.createNewMonth
import com.realgungan.expenses.data.loadMonths
import com.realgungan.expenses.data.saveMonths
import com.realgungan.expenses.ui.MainScreen
import com.realgungan.expenses.ui.theme.ExpensesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpensesApp()
        }
    }
}

@Composable
fun ExpensesApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("expenses_prefs", Context.MODE_PRIVATE) }

    var months by remember { mutableStateOf(loadMonths(prefs)) }
    var currentMonthIndex by remember { mutableStateOf(0) }
    var showNewMonthPrompt by remember { mutableStateOf(false) }
    var lastDeletedExpense by remember { mutableStateOf<Pair<Int, Expense>?>(null) }
    var monthToOverwrite by remember { mutableStateOf<MonthData?>(null) }

    // Save data whenever it changes
    LaunchedEffect(months) {
        saveMonths(prefs, months)
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
            monthToExport.expenses.forEach {
                appendLine("- ${it.description}: ${"%.2f".format(it.amount)}")
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
                val amountPart = line.substringAfterLast(':').trim()
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

        ExpensesTheme(darkTheme = true) {
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
                    val newExpenses = listOf(expense.copy(timestamp = System.currentTimeMillis())) + currentMonth.expenses
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
}
