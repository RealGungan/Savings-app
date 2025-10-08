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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realgungan.expenses.data.Expense
import com.realgungan.expenses.data.MonthData
import com.realgungan.expenses.ui.theme.ExpensesTheme

@Composable
fun MainScreen(
    months: List<MonthData>,
    currentMonth: MonthData,
    currentMonthIndex: Int,
    availableAmount: Double,
    showNewMonthPrompt: Boolean,
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

    LaunchedEffect(currentMonth, showNewMonthPrompt) {
        if (showNewMonthPrompt) {
            showIncomeDialog = true
            onNewMonthPromptShown()
        } else if (currentMonth.startingAmount == 0.0 && currentMonth.expenses.isEmpty()) {
            showIncomeDialog = true
        }
    }

    if (showIncomeDialog) {
        IncomeDialog(
            onDismiss = { showIncomeDialog = false },
            onConfirm = { income, percentage ->
                val startingAmount = income - (income * percentage / 100)
                onStartingAmountChange(startingAmount.toDouble())
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

                Text(
                    text = String.format("Available: %.2f", availableAmount),
                    style = MaterialTheme.typography.titleLarge,
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
fun IncomeDialog(onDismiss: () -> Unit, onConfirm: (income: Int, percentage: Int) -> Unit) {
    var incomeInput by remember { mutableStateOf("") }
    var percentageInput by remember { mutableStateOf("") }
    var showPercentageDialog by remember { mutableStateOf(false) }

    if (!showPercentageDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("How much do you earn?") },
            text = { TextField(value = incomeInput, onValueChange = { incomeInput = it }) },
            confirmButton = {
                TextButton(onClick = {
                    if (incomeInput.toIntOrNull() != null) showPercentageDialog = true
                }) {
                    Text("Next")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("How much you wanna save (%)?") },
            text = { TextField(value = percentageInput, onValueChange = { percentageInput = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val income = incomeInput.toIntOrNull()
                    val percentage = percentageInput.toIntOrNull()
                    if (income != null && percentage != null) {
                        onConfirm(income, percentage)
                    }
                }) {
                    Text("Confirm")
                }
            }
        )
    }
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
                expenses = mutableListOf(
                    Expense("Groceries", 150.0),
                    Expense("Rent", 500.0)
                ).toList()
            )
        )

        MainScreen(
            months = sampleMonths,
            currentMonth = sampleMonths[0],
            currentMonthIndex = 0,
            availableAmount = 350.0,
            showNewMonthPrompt = false,
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
