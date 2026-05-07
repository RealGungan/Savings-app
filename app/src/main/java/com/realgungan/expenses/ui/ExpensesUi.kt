package com.realgungan.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realgungan.expenses.data.Expense
import com.realgungan.expenses.data.MonthData
import com.realgungan.expenses.ui.theme.ExpensesTheme
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MainScreen(
    months: List<MonthData>,
    currentMonth: MonthData,
    currentMonthIndex: Int,
    availableAmount: Double,
    showNewMonthPrompt: Boolean,
    lastDeletedExpense: Pair<Int, Expense>?,
    onUndoDelete: () -> Unit,
    onUndoPromptShown: () -> Unit,
    onNewMonthPromptShown: () -> Unit,
    onMonthSelected: (Int) -> Unit,
    onAddNewMonth: () -> Unit,
    onDeleteMonth: (Int) -> Unit,
    onExportMonth: () -> Unit,
    onAddExpense: (Expense) -> Unit,
    onRemoveExpense: (Int) -> Unit,
    onSaveExpenseEdit: (Int, Expense) -> Unit,
    onStartingAmountChange: (String) -> Unit
) {
    var newExpenseInput by remember { mutableStateOf("") }
    var editingExpenseIndex by remember { mutableStateOf<Int?>(null) }
    var activeAddMode by remember { mutableStateOf(AddMode.NORMAL) }
    var multiMonthCount by remember { mutableIntStateOf(1) }
    var multiMonthIsDeferred by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showMultiMonthSettings by remember { mutableStateOf(false) }
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showMonthSelector by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(showNewMonthPrompt) {
        if (showNewMonthPrompt) {
            showIncomeDialog = true
            onNewMonthPromptShown()
        }
    }

    LaunchedEffect(lastDeletedExpense) {
        if (lastDeletedExpense != null) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Expense deleted",
                    actionLabel = "Undo",
                    withDismissAction = true
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoDelete()
                }
                onUndoPromptShown()
            }
        }
    }

    if (showIncomeDialog) {
        IncomeDialog(
            onDismiss = { showIncomeDialog = false },
            onConfirm = {
                onStartingAmountChange(it)
                showIncomeDialog = false
            }
        )
    }

    if (showMonthSelector) {
        MonthSelectionDialog(
            months = months,
            onDismiss = { showMonthSelector = false },
            onMonthSelected = {
                onMonthSelected(it)
                showMonthSelector = false
            },
            onDeleteMonth = onDeleteMonth
        )
    }

    if (showAddMenu) {
        AddModeMenu(
            onDismiss = { showAddMenu = false },
            onModeSelected = { mode ->
                showAddMenu = false
                newExpenseInput = "" // Clear data as requested when switching forms
                when (mode) {
                    AddMode.NORMAL -> {
                        activeAddMode = AddMode.NORMAL
                        multiMonthCount = 1
                        multiMonthIsDeferred = false
                    }
                    AddMode.DEBT -> {
                        activeAddMode = AddMode.DEBT
                        multiMonthCount = 1
                        multiMonthIsDeferred = true
                    }
                    AddMode.MULTI -> {
                        showMultiMonthSettings = true
                    }
                }
            }
        )
    }

    if (showMultiMonthSettings) {
        MultiMonthSettingsDialog(
            onDismiss = { showMultiMonthSettings = false },
            onConfirm = { count, deferred ->
                activeAddMode = AddMode.MULTI
                multiMonthCount = count
                multiMonthIsDeferred = deferred
                showMultiMonthSettings = false
                newExpenseInput = ""
            }
        )
    }

    editingExpenseIndex?.let { index ->
        val originalExpense = currentMonth.expenses[index]
        EditExpenseDialog(
            expense = originalExpense,
            onDismiss = { editingExpenseIndex = null },
            onSave = { updatedText ->
                val parts = updatedText.split(",").map(String::trim)
                if (parts.size >= 2) {
                    val description = parts[0]
                    var amountString = parts[1]

                    var isDeferred = false
                    var totalMonths = 1

                    if (parts.size >= 3) {
                        val debtPart = parts[2].lowercase()
                        if (debtPart.endsWith("dx")) {
                            isDeferred = true
                            totalMonths = debtPart.dropLast(2).toIntOrNull() ?: 1
                        } else if (debtPart.endsWith("d")) {
                            totalMonths = debtPart.dropLast(1).toIntOrNull() ?: 1
                        }
                    }

                    if (totalMonths == 1 && amountString.endsWith("d", ignoreCase = true)) {
                        isDeferred = true
                        amountString = amountString.dropLast(1)
                    }

                    val amount = amountString.toDoubleOrNull() ?: originalExpense.amount
                    
                    // If totalMonths or isDeferred changed, we treat it as a new debt calculation
                    val updatedExpense = if (totalMonths != originalExpense.totalMonths || isDeferred != originalExpense.isDeferred) {
                        originalExpense.copy(
                            description = description,
                            amount = amount,
                            isDeferred = isDeferred,
                            totalMonths = totalMonths,
                            remainingMonths = totalMonths,
                            startMonth = null,
                            endMonth = null
                        )
                    } else {
                        originalExpense.copy(
                            description = description,
                            amount = amount,
                            isDeferred = isDeferred
                        )
                    }
                    onSaveExpenseEdit(index, updatedExpense)
                }
                editingExpenseIndex = null
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newExpenseInput,
                    onValueChange = { newExpenseInput = it },
                    placeholder = { Text("CHEVECHA, 3.5") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newExpenseInput.isBlank()) {
                            showAddMenu = true
                        } else {
                            val parts = newExpenseInput.split(",").map(String::trim)
                            if (parts.size >= 2) {
                                val description = parts[0]
                                var amountString = parts[1]

                                var isDeferred = multiMonthIsDeferred
                                var totalMonths = multiMonthCount

                                // Manual override parsing to keep the "Add" button's original functionality
                                if (activeAddMode == AddMode.NORMAL) {
                                    if (parts.size >= 3) {
                                        val debtPart = parts[2].lowercase()
                                        if (debtPart.endsWith("dx")) {
                                            isDeferred = true
                                            totalMonths = debtPart.dropLast(2).toIntOrNull() ?: 1
                                        } else if (debtPart.endsWith("d")) {
                                            totalMonths = debtPart.dropLast(1).toIntOrNull() ?: 1
                                        }
                                    }
                                    if (totalMonths == 1 && amountString.endsWith("d", ignoreCase = true)) {
                                        isDeferred = true
                                        amountString = amountString.dropLast(1)
                                    }
                                } else {
                                    // If in DEBT or MULTI mode, still allow stripping a trailing 'd' if the user typed it
                                    if (amountString.endsWith("d", ignoreCase = true)) {
                                        amountString = amountString.dropLast(1)
                                    }
                                }

                                val amount = amountString.replace(",", ".").toDoubleOrNull()

                                if (amount != null) {
                                    onAddExpense(Expense(
                                        description = description,
                                        amount = amount,
                                        isDeferred = isDeferred,
                                        totalMonths = totalMonths,
                                        remainingMonths = totalMonths
                                    ))
                                    // Reset to default mode after adding
                                    newExpenseInput = ""
                                    activeAddMode = AddMode.NORMAL
                                    multiMonthCount = 1
                                    multiMonthIsDeferred = false
                                }
                            }
                        }
                    }
                ) {
                    Text(when(activeAddMode) {
                        AddMode.NORMAL -> "Add"
                        AddMode.DEBT -> "Add Debt"
                        AddMode.MULTI -> "Add Multi ($multiMonthCount)"
                    })
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with month selector and new month button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = currentMonth.monthYear.replace(" ", "\n"),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { showMonthSelector = true }
                )

                val availableColor = if (currentMonth.startingAmount > 0) {
                    val fraction = (availableAmount / currentMonth.startingAmount).toFloat().coerceIn(0f, 1f)
                    if (fraction > 0.5f) {
                        lerp(Color.Yellow, Color.Green, (fraction - 0.5f) * 2f)
                    } else {
                        lerp(Color.Red, Color.Yellow, fraction * 2f)
                    }
                } else {
                    Color.Unspecified
                }

                Text(
                    text = String.format("Available: %.2f", availableAmount),
                    style = MaterialTheme.typography.titleLarge,
                    color = availableColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable { onExportMonth() }
                )

                IconButton(
                    onClick = onAddNewMonth,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Month")
                }
            }

            // Expenses List
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(currentMonth.expenses) { index, expense ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val text = buildString {
                                    append("${expense.description}, ${String.format(Locale.US, "%.2f", expense.amount)}")
                                    if (expense.isDeferred) append(" (D)")
                                    if (expense.totalMonths > 1) {
                                        append(" (Debt from ${expense.startMonth} till ${expense.endMonth})")
                                    }
                                }
                                Text(text = text)
                                expense.formattedDate?.let {
                                    val label = if (expense.totalMonths > 1 || expense.startMonth != null) "Debt created:" else "Created:"
                                    Text(
                                        text = "$label $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { editingExpenseIndex = index }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onRemoveExpense(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectionDialog(
    months: List<MonthData>,
    onDismiss: () -> Unit,
    onMonthSelected: (Int) -> Unit,
    onDeleteMonth: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Month") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(months) { index, month ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMonthSelected(index) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = month.monthYear,
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = { onDeleteMonth(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Month")
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
fun IncomeDialog(onDismiss: () -> Unit, onConfirm: (input: String) -> Unit) {
    var amountInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for this month") },
        text = {
            TextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                placeholder = { Text("e.g., 500") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amountInput) }) {
                Text("Confirm")
            }
        }
    )
}

@Composable
fun EditExpenseDialog(expense: Expense, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val initialText = buildString {
        if (expense.totalMonths > 1) {
            append("${expense.description}, ${String.format(Locale.US, "%.2f", expense.amount * expense.totalMonths)}, ${expense.totalMonths}d")
            if (expense.isDeferred) append("x")
        } else {
            append("${expense.description}, ${expense.amount}")
            if (expense.isDeferred) append("d")
        }
    }
    var updatedText by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = { TextField(value = updatedText, onValueChange = { updatedText = it }) },
        confirmButton = { TextButton(onClick = { onSave(updatedText) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

enum class AddMode { NORMAL, DEBT, MULTI }

@Composable
fun AddModeMenu(onDismiss: () -> Unit, onModeSelected: (AddMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Mode") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onModeSelected(AddMode.NORMAL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add (Normal)")
                }
                Divider()
                TextButton(
                    onClick = { onModeSelected(AddMode.DEBT) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Debt (For next month)")
                }
                Divider()
                TextButton(
                    onClick = { onModeSelected(AddMode.MULTI) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Multiple months debt")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MultiMonthSettingsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    var totalMonths by remember { mutableStateOf("2") }
    var isDeferred by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Multi-Month Debt Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = totalMonths,
                    onValueChange = { totalMonths = it },
                    label = { Text("Number of months") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDeferred, onCheckedChange = { isDeferred = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Start next month")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val months = totalMonths.toIntOrNull() ?: 2
                    onConfirm(months, isDeferred)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ExpensesTheme(darkTheme = true) {
        val sampleMonths = listOf(
            MonthData(
                monthYear = "January 2024",
                startingAmount = 1000.0,
                expenses = emptyList()
            )
        )

        MainScreen(
            months = sampleMonths,
            currentMonth = sampleMonths[0],
            currentMonthIndex = 0,
            availableAmount = 350.0,
            showNewMonthPrompt = false,
            lastDeletedExpense = null,
            onUndoDelete = {},
            onUndoPromptShown = {},
            onNewMonthPromptShown = {},
            onMonthSelected = {},
            onAddNewMonth = {},
            onDeleteMonth = {},
            onExportMonth = {},
            onAddExpense = {},
            onRemoveExpense = {},
            onSaveExpenseEdit = { _, _ -> },
            onStartingAmountChange = {}
        )
    }
}
