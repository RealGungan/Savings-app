package com.realgungan.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realgungan.expenses.data.Expense
import com.realgungan.expenses.data.MonthData
import com.realgungan.expenses.ui.theme.ExpensesTheme
import kotlinx.coroutines.launch

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
    onExportMonth: (MonthData) -> Unit,
    onAddExpense: (Expense) -> Unit,
    onRemoveExpense: (Int) -> Unit,
    onSaveExpenseEdit: (Int, Expense) -> Unit,
    onStartingAmountChange: (Double) -> Unit
) {
    var newExpenseInput by remember { mutableStateOf("") }
    var editingExpenseIndex by remember { mutableStateOf<Int?>(null) }
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showMonthSelector by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentMonth, showNewMonthPrompt) {
        if (showNewMonthPrompt) {
            showIncomeDialog = true
            onNewMonthPromptShown()
        } else if (currentMonth.startingAmount == 0.0 && currentMonth.expenses.isEmpty()) {
            showIncomeDialog = true
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

    editingExpenseIndex?.let { index ->
        val originalExpense = currentMonth.expenses[index]
        EditExpenseDialog(
            expense = originalExpense,
            onDismiss = { editingExpenseIndex = null },
            onSave = { updatedText ->
                val parts = updatedText.split(",").map(String::trim)
                if (parts.size == 2) {
                    val newAmount = parts[1].toDoubleOrNull() ?: originalExpense.amount
                    val newDescription = parts[0]
                    onSaveExpenseEdit(index, originalExpense.copy(description = newDescription, amount = newAmount))
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
                        val parts = newExpenseInput.split(",").map(String::trim)
                        if (parts.size == 2) {
                            val amount = parts[1].toDoubleOrNull()
                            if (amount != null) {
                                onAddExpense(Expense(description = parts[0], amount = amount))
                                newExpenseInput = ""
                            }
                        }
                    },
                    enabled = newExpenseInput.isNotBlank()
                ) {
                    Text("Add")
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
                        .clickable { onExportMonth(currentMonth) }
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
                            Text(text = "${expense.description}, ${expense.amount}", modifier = Modifier.weight(1f))
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
fun IncomeDialog(onDismiss: () -> Unit, onConfirm: (amount: Double) -> Unit) {
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
            TextButton(onClick = {
                amountInput.toDoubleOrNull()?.let {
                    onConfirm(it)
                }
            }) {
                Text("Confirm")
            }
        }
    )
}


@Composable
fun EditExpenseDialog(expense: Expense, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var updatedText by remember { mutableStateOf("${expense.description}, ${expense.amount}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = { TextField(value = updatedText, onValueChange = { updatedText = it }) },
        confirmButton = { TextButton(onClick = { onSave(updatedText) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            onExportMonth = { _ -> },
            onAddExpense = {},
            onRemoveExpense = {},
            onSaveExpenseEdit = { _, _ -> },
            onStartingAmountChange = {}
        )
    }
}
