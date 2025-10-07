package com.realgungan.expenses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.realgungan.expenses.ui.theme.ExpensesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpensesTheme(darkTheme = true) {
                val items = remember { mutableStateListOf<String>() }
                var availableAmount by remember { mutableStateOf("") }
                var newExpenseInput by remember { mutableStateOf("") }

                MainScreen(
                    items = items,
                    availableAmount = availableAmount,
                    newExpenseInput = newExpenseInput,
                    onAvailableAmountChange = { availableAmount = it },
                    onNewExpenseInputChange = { newExpenseInput = it },
                    onAdd = {
                        val parts = newExpenseInput.split(",").map { it.trim() }
                        if (parts.size == 2) {
                            val expenseAmount = parts[1].toDoubleOrNull()
                            val currentAvailable = availableAmount.toDoubleOrNull()

                            if (expenseAmount != null && currentAvailable != null) {
                                val newAmount = currentAvailable - expenseAmount
                                availableAmount = String.format("%.2f", newAmount)
                                items.add(newExpenseInput)
                                newExpenseInput = ""
                            }
                        }
                    },
                    onRemove = { index ->
                        val item = items.getOrNull(index)
                        if (item != null) {
                            val parts = item.split(",").map { it.trim() }
                            if (parts.size == 2) {
                                val expenseAmount = parts[1].toDoubleOrNull()
                                val currentAvailable = availableAmount.toDoubleOrNull()
                                if (expenseAmount != null && currentAvailable != null) {
                                    val newAmount = currentAvailable + expenseAmount
                                    availableAmount = String.format("%.2f", newAmount)
                                    items.removeAt(index)
                                }
                            }
                        }
                    },
                    onSaveEdit = { index, updatedText ->
                        val originalItem = items.getOrNull(index) ?: return@MainScreen

                        val originalAmount = originalItem.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0
                        val updatedAmount = updatedText.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0

                        val difference = originalAmount - updatedAmount
                        val currentAvailable = availableAmount.toDoubleOrNull() ?: 0.0
                        availableAmount = String.format("%.2f", currentAvailable + difference)

                        items[index] = updatedText
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    items: List<String>,
    availableAmount: String,
    newExpenseInput: String,
    onAvailableAmountChange: (String) -> Unit,
    onNewExpenseInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onSaveEdit: (Int, String) -> Unit
) {
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showPercentageDialog by remember { mutableStateOf(false) }
    var incomeInput by remember { mutableStateOf("") }
    var percentageInput by remember { mutableStateOf("") }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }

    // When the available amount is empty, trigger income dialog
    LaunchedEffect(availableAmount) {
        if (availableAmount.isEmpty()) {
            showIncomeDialog = true
        }
    }

    // Handle showing the edit dialog
    editingItemIndex?.let { index ->
        EditDialog(
            itemText = items[index],
            onDismiss = { editingItemIndex = null },
            onSave = { updatedText ->
                onSaveEdit(index, updatedText)
                editingItemIndex = null
            }
        )
    }

    // Income input dialog
    if (showIncomeDialog) {
        AlertDialog(
            onDismissRequest = { showIncomeDialog = false },
            title = { Text("How much do you earn?") },
            text = {
                TextField(
                    value = incomeInput,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) incomeInput = value
                    },
                    placeholder = { Text("Enter your salary") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showIncomeDialog = false
                        if (incomeInput.isNotEmpty()) showPercentageDialog = true
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Percentage input dialog
    if (showPercentageDialog) {
        AlertDialog(
            onDismissRequest = { showPercentageDialog = false },
            title = { Text("How much you wanna save (%)?") },
            text = {
                TextField(
                    value = percentageInput,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) percentageInput = value
                    },
                    placeholder = { Text("Enter a percentage") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPercentageDialog = false
                        if (incomeInput.isNotEmpty() && percentageInput.isNotEmpty()) {
                            val income = incomeInput.toInt()
                            val percentage = percentageInput.toInt()
                            val result = income - (income * percentage)
                            onAvailableAmountChange(result.toString())
                        }
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newExpenseInput,
                    onValueChange = onNewExpenseInputChange,
                    placeholder = { Text("Enter something...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAdd, enabled = newExpenseInput.isNotBlank()) {
                    Text("Add")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            stickyHeader {
                Surface(
                    modifier = Modifier.fillParentMaxWidth(),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "Available: $availableAmount",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            itemsIndexed(items) { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingItemIndex = index }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditDialog(
    itemText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var updatedText by remember(itemText) { mutableStateOf(itemText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = {
            TextField(
                value = updatedText,
                onValueChange = { updatedText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(updatedText) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ExpensesTheme(darkTheme = true) {
        MainScreen(
            items = listOf("Sample 1, 10.0", "Sample 2, 5.5"),
            availableAmount = "100",
            newExpenseInput = "",
            onAvailableAmountChange = {},
            onNewExpenseInputChange = {},
            onAdd = {},
            onRemove = {},
            onSaveEdit = { _, _ -> }
        )
    }
}
