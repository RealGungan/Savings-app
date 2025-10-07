package com.realgungan.expenses

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
            months = listOf(createNewMonth(null))
        } else {
            months = newMonths
        }
        // Always reset to the first month after deletion for safety.
        currentMonthIndex = 0
    }

    val currentMonth = months.getOrNull(currentMonthIndex)

    if (currentMonth != null) {
        val availableAmount = currentMonth.startingAmount - currentMonth.expenses.sumOf { it.amount }

        fun exportMonth() {
            val totalExpenses = currentMonth.expenses.sumOf { it.amount }
            val finalBalance = currentMonth.startingAmount - totalExpenses

            val shareableString = buildString {
                appendLine("Expense Report for ${currentMonth.monthYear}")
                appendLine("--------------------")
                appendLine("Starting Amount: ${"%.2f".format(currentMonth.startingAmount)}")
                appendLine("\nExpenses:")
                currentMonth.expenses.forEach {
                    appendLine("- ${it.description}: ${"%.2f".format(it.amount)}")
                }
                appendLine("\n--------------------")
                appendLine("Total Wasted: ${"%.2f".format(totalExpenses)}")
                appendLine("Amount Left: ${"%.2f".format(finalBalance)}")
            }

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareableString)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, "Export Month Report")
            context.startActivity(shareIntent)
        }

        ExpensesTheme(darkTheme = true) {
            MainScreen(
                months = months,
                currentMonth = currentMonth,
                currentMonthIndex = currentMonthIndex,
                availableAmount = availableAmount,
                showNewMonthPrompt = showNewMonthPrompt,
                onNewMonthPromptShown = { showNewMonthPrompt = false },
                onMonthSelected = { index -> currentMonthIndex = index },
                onAddNewMonth = {
                    val newMonth = createNewMonth(months.firstOrNull())
                    months = listOf(newMonth) + months
                    currentMonthIndex = 0
                    showNewMonthPrompt = true
                },
                onDeleteMonth = ::deleteMonth,
                onExportMonth = ::exportMonth,
                onAddExpense = { expense ->
                    val newExpenses = listOf(expense) + currentMonth.expenses
                    updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
                },
                onRemoveExpense = { expenseIndex ->
                    val newExpenses = currentMonth.expenses.toMutableList().also { it.removeAt(expenseIndex) }
                    updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
                },
                onSaveExpenseEdit = { expenseIndex, updatedExpense ->
                    val newExpenses = currentMonth.expenses.toMutableList().also { it[expenseIndex] = updatedExpense }
                    updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
                },
                onStartingAmountChange = { newAmount ->
                    val updatedAmount = currentMonth.startingAmount + newAmount
                    updateMonth(currentMonthIndex, currentMonth.copy(startingAmount = updatedAmount))
                }
            )
        }
    }
}
