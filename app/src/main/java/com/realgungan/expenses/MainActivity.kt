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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private val sdf = SimpleDateFormat("EEEE: d - HH:mm", Locale.getDefault())

class MainActivity : ComponentActivity() {

    private var fileUri by mutableStateOf<Uri?>(null)
    private var showCorruptFileDialog by mutableStateOf(false)

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("file_uri", it.toString()).apply()
            fileUri = it
        }
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("file_uri", it.toString()).apply()
            fileUri = it
        }
    }

    private fun handleCorruptFile() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("file_uri").apply()
        fileUri = null
        showCorruptFileDialog = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Synchronously check for a valid, persisted URI before composing the UI.
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedUriString = prefs.getString("file_uri", null)
        if (savedUriString != null) {
            val savedUri = Uri.parse(savedUriString)
            val hasPermission = contentResolver.persistedUriPermissions.any { it.uri == savedUri }
            if (hasPermission) {
                fileUri = savedUri // Set the initial state
            } else {
                // If we lost permission, the URI is stale. Clear it.
                prefs.edit().remove("file_uri").apply()
            }
        }

        setContent {
            if (showCorruptFileDialog) {
                CorruptFileDialog(
                    onDismiss = { showCorruptFileDialog = false },
                    onSelectNewFile = { openFileLauncher.launch(arrayOf("application/json")) }
                )
            }
            else if (fileUri == null) {
                FilePickerScreen(
                    onCreateFile = { createFileLauncher.launch("expenses_data.json") },
                    onOpenFile = { openFileLauncher.launch(arrayOf("application/json")) }
                )
            } else {
                fileUri?.let { uri ->
                    ExpensesApp(uri, onCorruptFile = ::handleCorruptFile)
                }
            }
        }
    }
}

@Composable
fun CorruptFileDialog(onDismiss: () -> Unit, onSelectNewFile: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrupt File") },
        text = { Text("The selected file is corrupt and cannot be read. Please select a different file.") },
        confirmButton = {
            TextButton(onClick = {
                onSelectNewFile()
                onDismiss()
            }) {
                Text("Select New File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
fun ExpensesApp(uri: Uri, onCorruptFile: () -> Unit) {
    val context = LocalContext.current
    val monthsData = remember(uri) {
        try {
            loadMonths(context, uri)
        } catch (e: Exception) {
            onCorruptFile()
            emptyList<MonthData>()
        }
    }

    if (monthsData.isNotEmpty()) {
        ExpensesTheme(darkTheme = true) {
            ExpensesAppContent(uri, monthsData)
        }
    }
}

@Composable
fun ExpensesAppContent(uri: Uri, initialMonths: List<MonthData>) {
    val context = LocalContext.current

    var months by remember { mutableStateOf(initialMonths) }
    var currentMonthIndex by remember { mutableStateOf(0) }
    var showNewMonthPrompt by remember { mutableStateOf(false) }
    var lastDeletedExpense by remember { mutableStateOf<Pair<Int, Expense>?>(null) }

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

    fun exportMonth() {
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Expenses File"))
    }

    val currentMonth = months.getOrNull(currentMonthIndex)

    if (currentMonth != null) {
        val availableAmount = currentMonth.startingAmount - currentMonth.expenses.filter { !it.isDeferred }.sumOf { it.amount }

        fun undoDelete() {
            lastDeletedExpense?.let { (index, expense) ->
                val newExpenses = currentMonth.expenses.toMutableList().apply { add(index, expense) }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            }
            lastDeletedExpense = null
        }

        MainScreen(
            months = months,
            currentMonth = currentMonth,
            currentMonthIndex = currentMonthIndex,
            availableAmount = availableAmount,
            showNewMonthPrompt = showNewMonthPrompt,
            lastDeletedExpense = lastDeletedExpense,
            onUndoDelete = ::undoDelete,
            onUndoPromptShown = { lastDeletedExpense = null },
            onNewMonthPromptShown = { showNewMonthPrompt = false },
            onMonthSelected = { index -> currentMonthIndex = index },
            onAddNewMonth = {
                val sourceMonth = months[currentMonthIndex]
                val deferredExpensesToCopy = sourceMonth.expenses.filter { it.isDeferred }

                val newMonth = createNewMonth().copy(
                    expenses = deferredExpensesToCopy.map { it.copy(isDeferred = false) }
                )

                // The old month is not changed. The deferred expenses remain.
                val newMonthsList = months.toMutableList()
                newMonthsList.add(0, newMonth)
                months = newMonthsList

                currentMonthIndex = 0
                showNewMonthPrompt = true
            },
            onDeleteMonth = ::deleteMonth,
            onExportMonth = ::exportMonth,
            onAddExpense = { expense ->
                val timestamp = System.currentTimeMillis()
                val formattedDate = sdf.format(Date(timestamp))
                val newExpense = expense.copy(timestamp = timestamp, formattedDate = formattedDate)
                val newExpenses = currentMonth.expenses.toMutableList().apply { add(0, newExpense) }
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
                val amount = newAmount.toDoubleOrNull() ?: 0.0
                updateMonth(currentMonthIndex, currentMonth.copy(startingAmount = amount))
            }
        )
    }
}
