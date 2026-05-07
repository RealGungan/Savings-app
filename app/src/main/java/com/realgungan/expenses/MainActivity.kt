package com.realgungan.expenses

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        Image(
            painter = painterResource(id = R.drawable.naruto),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(150.dp)
                .padding(bottom = 16.dp)
        )
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
            ExpensesAppContent(uri, monthsData, onCorruptFile = onCorruptFile)
        }
    }
}


@Composable
fun ExpensesAppContent(uri: Uri, initialMonths: List<MonthData>, onCorruptFile: () -> Unit) {
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
        val availableAmount = currentMonth.startingAmount - currentMonth.expenses.filter { expense ->
            if (expense.startMonth != null && expense.endMonth != null) {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                try {
                    val currentD = sdf.parse(currentMonth.monthYear)
                    val startD = sdf.parse(expense.startMonth)
                    val endD = sdf.parse(expense.endMonth)
                    // Only subtract if current month is within the debt period
                    currentD != null && startD != null && endD != null && 
                            !currentD.before(startD) && !currentD.after(endD)
                } catch (e: Exception) {
                    !expense.isDeferred
                }
            } else {
                !expense.isDeferred
            }
        }.sumOf { it.amount }

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
                val newMonth = createNewMonth()
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val newMonthDate = try { sdf.parse(newMonth.monthYear) } catch(e: Exception) { Date() }

                val sourceMonth = months[currentMonthIndex]
                val expensesToCopy = sourceMonth.expenses.filter { expense ->
                    if (expense.startMonth != null && expense.endMonth != null) {
                        try {
                            val endD = sdf.parse(expense.endMonth)
                            // Copy if it's still active or starts in the future relative to the new month
                            newMonthDate != null && endD != null && !newMonthDate.after(endD)
                        } catch (e: Exception) { false }
                    } else {
                        // Non-debt expenses only carry over if they were explicitly deferred in the source month
                        expense.isDeferred
                    }
                }.map { expense ->
                    if (expense.startMonth != null && expense.endMonth != null) {
                        try {
                            val endD = sdf.parse(expense.endMonth)
                            
                            // Calculate remaining months relative to the new month's name
                            val calNew = Calendar.getInstance().apply { time = newMonthDate }
                            val calEnd = Calendar.getInstance().apply { time = endD }
                            val monthsLeft = (calEnd.get(Calendar.YEAR) - calNew.get(Calendar.YEAR)) * 12 +
                                             (calEnd.get(Calendar.MONTH) - calNew.get(Calendar.MONTH)) + 1
                            
                            expense.copy(
                                isDeferred = false, // Reset deferred flag as the range handles activation
                                remainingMonths = monthsLeft.coerceAtLeast(0)
                            )
                        } catch (e: Exception) { expense }
                    } else {
                        // Normal deferred expense becomes active in the new month
                        expense.copy(isDeferred = false)
                    }
                }

                val finalNewMonth = newMonth.copy(expenses = expensesToCopy)

                val newMonthsList = months.toMutableList()
                newMonthsList.add(0, finalNewMonth)
                months = newMonthsList

                currentMonthIndex = 0
                showNewMonthPrompt = true
            },
            onDeleteMonth = ::deleteMonth,
            onExportMonth = ::exportMonth,
            onAddExpense = { expense ->
                val timestamp = System.currentTimeMillis()
                val formattedDate = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))

                var finalExpense = expense.copy(timestamp = timestamp, formattedDate = formattedDate)

                if (finalExpense.totalMonths > 1 || finalExpense.isDeferred) {
                    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    val baseMonth = currentMonth.monthYear
                    val baseDate = try { sdf.parse(baseMonth) } catch(e: Exception) { null } ?: Date()

                    val calendar = Calendar.getInstance().apply { time = baseDate }
                    if (finalExpense.isDeferred) {
                        calendar.add(Calendar.MONTH, 1)
                    }
                    val calculatedStartMonth = sdf.format(calendar.time)

                    calendar.add(Calendar.MONTH, finalExpense.totalMonths - 1)
                    val calculatedEndMonth = sdf.format(calendar.time)

                    finalExpense = finalExpense.copy(
                        amount = finalExpense.amount / finalExpense.totalMonths,
                        startMonth = calculatedStartMonth,
                        endMonth = calculatedEndMonth
                    )
                }

                val newExpenses = currentMonth.expenses.toMutableList().apply { add(0, finalExpense) }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            },
            onRemoveExpense = { expenseIndex ->
                lastDeletedExpense = currentMonth.expenses[expenseIndex].let { expenseIndex to it }
                val newExpenses = currentMonth.expenses.toMutableList().also { it.removeAt(expenseIndex) }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            },
            onSaveExpenseEdit = { expenseIndex, updatedExpense ->
                var finalExpense = updatedExpense
                if (finalExpense.totalMonths > 1 || finalExpense.isDeferred) {
                    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    val isFreshDebt = finalExpense.startMonth == null
                    val baseMonth = finalExpense.startMonth ?: currentMonth.monthYear
                    val baseDate = try { sdf.parse(baseMonth) } catch(e: Exception) { null } ?: Date()

                    val calendar = Calendar.getInstance().apply { time = baseDate }
                    if (isFreshDebt && finalExpense.isDeferred) {
                        calendar.add(Calendar.MONTH, 1)
                    }
                    val calculatedStartMonth = sdf.format(calendar.time)

                    calendar.add(Calendar.MONTH, finalExpense.totalMonths - 1)
                    val calculatedEndMonth = sdf.format(calendar.time)

                    finalExpense = finalExpense.copy(
                        amount = finalExpense.amount / finalExpense.totalMonths,
                        startMonth = calculatedStartMonth,
                        endMonth = calculatedEndMonth
                    )
                }
                val newExpenses = currentMonth.expenses.toMutableList().also { it[expenseIndex] = finalExpense }
                updateMonth(currentMonthIndex, currentMonth.copy(expenses = newExpenses))
            },
            onStartingAmountChange = { newAmount ->
                val amount = newAmount.toDoubleOrNull() ?: 0.0
                updateMonth(currentMonthIndex, currentMonth.copy(startingAmount = amount))
            }
        )
    }
}