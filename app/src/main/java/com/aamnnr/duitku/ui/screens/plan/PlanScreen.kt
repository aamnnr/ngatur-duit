package com.aamnnr.duitku.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.local.BudgetEntity
import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.data.local.GoalWithSaved
import com.aamnnr.duitku.domain.BudgetProgress
import com.aamnnr.duitku.domain.RecurringBill
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.budgetProgress
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.domain.detectRecurring
import com.aamnnr.duitku.ui.AppIcons
import com.aamnnr.duitku.ui.LocaleID
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.AmountField
import com.aamnnr.duitku.ui.components.ColorPicker
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.IconPicker
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.MonthlyProgressCard
import com.aamnnr.duitku.ui.components.NeuCircleButton
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.amountFieldValue
import com.aamnnr.duitku.ui.components.money
import com.aamnnr.duitku.ui.components.signedAmount
import com.aamnnr.duitku.ui.components.toColor
import com.aamnnr.duitku.ui.formatCompact
import com.aamnnr.duitku.ui.screens.home.LimitDialog
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class PlanTab(val label: String) { BUDGET("Anggaran"), SAVINGS("Tabungan"), BILLS("Tagihan") }

data class PlanState(
    val budgets: List<BudgetProgress> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val monthSpent: Long = 0,
    val monthlyLimit: Long = 0,
    val goals: List<GoalWithSaved> = emptyList(),
    val bills: List<RecurringBill> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.profiles.activeProfileId.flatMapLatest { pid ->
        val today = LocalDate.now()
        val from = today.minusMonths(4).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val base = combine(
            c.plans.observeBudgets(pid),
            c.categories.observeAll(pid),
            c.transactions.observeItems(pid, from, to),
        ) { budgets, cats, items -> Triple(budgets, cats, items) }
        combine(base, c.settings.monthlyLimit(pid), c.plans.observeGoals(pid)) { (budgets, cats, items), limit, goals ->
            val monthStart = today.withDayOfMonth(1)
            PlanState(
                budgets = budgets.map { budgetProgress(it, items, today) },
                expenseCategories = cats.filter { it.type == TransactionType.EXPENSE },
                monthSpent = computeStats(items.filter { it.date.toLocalDate() >= monthStart }, monthStart, today).expense,
                monthlyLimit = limit,
                goals = goals,
                bills = detectRecurring(items, today),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    private suspend fun pid() = c.profiles.activeProfileId.first()

    fun saveBudget(id: String?, name: String, cats: List<String>, amount: Long, period: String) =
        viewModelScope.launch { c.plans.saveBudget(pid(), id, name, cats, amount, period) }

    fun deleteBudget(id: String) = viewModelScope.launch { c.plans.deleteBudget(id) }
    fun setLimit(amount: Long) = viewModelScope.launch { c.settings.setMonthlyLimit(pid(), amount) }

    fun saveGoal(id: String?, name: String, target: Long, date: Long?, icon: String, color: Long) =
        viewModelScope.launch { c.plans.saveGoal(pid(), id, name, target, date, icon, color) }

    fun deleteGoal(id: String) = viewModelScope.launch { c.plans.deleteGoal(id) }
    fun setGoalStatus(id: String, status: String) = viewModelScope.launch { c.plans.setGoalStatus(id, status) }
    fun addLog(g: GoalWithSaved, amount: Long, note: String?) =
        viewModelScope.launch { c.plans.addLog(g.goal, amount, note, System.currentTimeMillis()) }

    fun logs(goalId: String) = c.plans.observeLogs(goalId)
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", LocaleID)

@Composable
fun PlanScreen(onRecordBill: (RecurringBill) -> Unit, onOpenCalculator: () -> Unit) {
    val vm = appViewModel { PlanViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PlanTab.BUDGET) }
    var budgetDialog by remember { mutableStateOf<BudgetEntity?>(null) }
    var newBudget by remember { mutableStateOf(false) }
    var limitDialog by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf<GoalWithSaved?>(null) }
    var newGoal by remember { mutableStateOf(false) }
    var openGoal by remember { mutableStateOf<String?>(null) }
    StatusBarIcons(lightIcons = false)

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rencana", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (tab != PlanTab.BILLS) {
                    NeuCircleButton(Icons.Outlined.Add, if (tab == PlanTab.BUDGET) "Anggaran baru" else "Target baru", {
                        if (tab == PlanTab.BUDGET) newBudget = true else newGoal = true
                    })
                }
            }
        }
        item { Tabs(tab) { tab = it } }
        when (tab) {
            PlanTab.BUDGET -> {
                item {
                    MonthlyProgressCard("Batas bulanan", s.monthSpent, s.monthlyLimit, onSetLimit = { limitDialog = true })
                }
                if (s.monthlyLimit > 0) {
                    item {
                        val today = LocalDate.now()
                        val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
                        val safe = ((s.monthlyLimit - s.monthSpent) / daysLeft).coerceAtLeast(0)
                        Text(
                            "Aman dibelanjakan ${money(safe)} per hari selama $daysLeft hari lagi.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                if (s.budgets.isEmpty()) {
                    item {
                        EmptyState("Belum ada anggaran kategori", "Tekan + untuk membuat batas belanja, misalnya makan Rp 1,9 jt per bulan.")
                    }
                }
                items(s.budgets, key = { it.budget.id }) { b ->
                    BudgetCard(b, s.expenseCategories) { budgetDialog = b.budget }
                }
            }
            PlanTab.SAVINGS -> {
                item {
                    KopiCard(Modifier.fillMaxWidth()) {
                        Text("Total tabungan terkumpul", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(s.goals.sumOf { it.saved }), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (s.goals.isEmpty()) {
                    item { EmptyState("Belum ada target tabungan", "Tekan + untuk membuat target, misalnya dana darurat atau liburan.") }
                }
                items(s.goals, key = { it.goal.id }) { g -> GoalCard(g) { openGoal = g.goal.id } }
            }
            PlanTab.BILLS -> {
                item {
                    Text(
                        "Duitku mengenali pengeluaran yang muncul di tanggal yang mirip setiap bulan (minimal 2 bulan).",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.bills.isEmpty()) {
                    item { EmptyState("Belum ada tagihan rutin terdeteksi", "Catat tagihan seperti listrik atau internet dengan catatan yang sama setiap bulan.") }
                }
                items(s.bills, key = { it.name + it.categoryId }) { bill -> BillCard(bill) { onRecordBill(bill) } }
            }
        }
        item {
            KopiCard(Modifier.fillMaxWidth().clickable(onClick = onOpenCalculator)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Calculate, MaterialTheme.colorScheme.secondary, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Kalkulator realita", style = MaterialTheme.typography.titleSmall)
                        Text("Gaji per detik, umur, hutang tidur, rokok, langganan",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (limitDialog) {
        LimitDialog(s.monthlyLimit, onDismiss = { limitDialog = false }) { vm.setLimit(it); limitDialog = false }
    }
    if (newBudget || budgetDialog != null) {
        BudgetDialog(
            budget = budgetDialog,
            categories = s.expenseCategories,
            onDismiss = { newBudget = false; budgetDialog = null },
            onDelete = budgetDialog?.let { b -> { vm.deleteBudget(b.id); budgetDialog = null } },
        ) { name, cats, amount, period ->
            vm.saveBudget(budgetDialog?.id, name, cats, amount, period)
            newBudget = false
            budgetDialog = null
        }
    }
    if (newGoal || goalDialog != null) {
        GoalDialog(goalDialog, onDismiss = { newGoal = false; goalDialog = null }) { name, target, date, icon, color ->
            vm.saveGoal(goalDialog?.goal?.id, name, target, date, icon, color)
            newGoal = false
            goalDialog = null
        }
    }
    openGoal?.let { id ->
        s.goals.firstOrNull { it.goal.id == id }?.let { g ->
            GoalSheet(
                g, vm,
                onDismiss = { openGoal = null },
                onEdit = { goalDialog = g; openGoal = null },
            )
        }
    }
}

@Composable
private fun Tabs(selected: PlanTab, onSelect: (PlanTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
    ) {
        PlanTab.entries.forEach { t ->
            val sel = t == selected
            Text(
                t.label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun ProgressLine(ratio: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
private fun BudgetCard(b: BudgetProgress, categories: List<CategoryEntity>, onClick: () -> Unit) {
    val color = when {
        b.ratio >= 1f -> ExpenseRed
        b.ratio >= 0.9f -> MaterialTheme.colorScheme.secondary
        else -> IncomeGreen
    }
    val names = b.budget.categoryIdList.mapNotNull { id -> categories.firstOrNull { it.id == id }?.name }
    val firstCat = categories.firstOrNull { it.id == b.budget.categoryIdList.firstOrNull() }
    KopiCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.get(firstCat?.icon ?: "more"), firstCat?.color.toColor(MaterialTheme.colorScheme.primary), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.budget.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    (if (names.isEmpty()) "Semua pengeluaran" else names.joinToString(", ")) +
                        if (b.budget.period == "WEEKLY") " · mingguan" else " · bulanan",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (b.ratio >= 0.9f) {
                Text(
                    if (b.ratio >= 1f) "Terlampaui" else "Hampir habis",
                    style = MaterialTheme.typography.labelSmall, color = color,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ProgressLine(b.ratio, color)
        Spacer(Modifier.height(8.dp))
        Row {
            Text("${money(b.spent)} / ${formatCompact(b.budget.amount)}", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f))
            Text(
                if (b.remaining >= 0) "Aman ${money(b.safePerDay())}/hari" else "Lebih ${money(-b.remaining)}",
                style = MaterialTheme.typography.bodySmall, color = if (b.remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseRed,
            )
        }
    }
}

@Composable
private fun GoalCard(g: GoalWithSaved, onClick: () -> Unit) {
    val color = g.goal.color.toColor(MaterialTheme.colorScheme.primary)
    val ratio = if (g.goal.targetAmount > 0) g.saved.toFloat() / g.goal.targetAmount else 0f
    KopiCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.get(g.goal.icon), color, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.goal.name, style = MaterialTheme.typography.titleSmall)
                Text(goalSubtitle(g), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = color)
        }
        Spacer(Modifier.height(12.dp))
        ProgressLine(ratio, if (g.goal.status == "DONE") IncomeGreen else color)
        Spacer(Modifier.height(8.dp))
        Text("${money(g.saved)} dari ${formatCompact(g.goal.targetAmount)}", style = MaterialTheme.typography.bodySmall)
    }
}

private fun goalSubtitle(g: GoalWithSaved): String {
    if (g.goal.status == "DONE") return "Tercapai"
    val date = g.goal.targetDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        ?: return "Tanpa tenggat"
    val months = ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), date.withDayOfMonth(1)).coerceAtLeast(1)
    val perMonth = ((g.goal.targetAmount - g.saved).coerceAtLeast(0) / months)
    return "Target ${date.format(dateFmt)} · ${formatCompact(perMonth)}/bulan"
}

@Composable
private fun BillCard(bill: RecurringBill, onRecord: () -> Unit) {
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), bill.nextDue)
    val color = if (daysLeft <= 3) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
    KopiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.get(bill.categoryIcon ?: "receipt"), MaterialTheme.colorScheme.secondary, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("± ${money(bill.typicalAmount)} · tiap tanggal ${bill.dayOfMonth}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (daysLeft <= 0L) "Jatuh tempo hari ini" else "Jatuh tempo ${bill.nextDue.format(dateFmt)} ($daysLeft hari lagi)",
                    style = MaterialTheme.typography.labelSmall, color = color,
                )
            }
            OutlinedButton(onClick = onRecord) { Text("Catat") }
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text("Terdeteksi dari ${bill.months} bulan · ${bill.categoryName ?: "tanpa kategori"}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(
    budget: BudgetEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, List<String>, Long, String) -> Unit,
) {
    var name by remember { mutableStateOf(budget?.name.orEmpty()) }
    var amount by remember { mutableStateOf(amountFieldValue(budget?.amount ?: 0)) }
    var period by remember { mutableStateOf(budget?.period ?: "MONTHLY") }
    var cats by remember { mutableStateOf(budget?.categoryIdList?.toSet() ?: emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget == null) "Anggaran baru" else "Ubah anggaran") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nama (mis. Makan & jajan)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                AmountField(amount, { amount = it }, label = "Batas")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(period == "MONTHLY", { period = "MONTHLY" }, label = { Text("Bulanan") })
                    FilterChip(period == "WEEKLY", { period = "WEEKLY" }, label = { Text("Mingguan") })
                }
                Text("Kategori (kosong = semua pengeluaran)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = c.id in cats,
                            onClick = { cats = if (c.id in cats) cats - c.id else cats + c.id },
                            label = { Text(c.name) },
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Hapus anggaran", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            val value = amount.signedAmount()
            TextButton(
                onClick = { onSave(name.trim().ifEmpty { "Anggaran" }, cats.toList(), value, period) },
                enabled = value > 0,
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(
    goal: GoalWithSaved?,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long?, String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(goal?.goal?.name.orEmpty()) }
    var target by remember { mutableStateOf(amountFieldValue(goal?.goal?.targetAmount ?: 0)) }
    var date by remember { mutableStateOf(goal?.goal?.targetDate) }
    var icon by remember { mutableStateOf(goal?.goal?.icon ?: "savings") }
    var color by remember { mutableStateOf(goal?.goal?.color ?: 0xFFC08A5B) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) "Target tabungan baru" else "Ubah target") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nama (mis. Dana darurat)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                AmountField(target, { target = it }, label = "Target")
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date?.let { "Tenggat: " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt) }
                        ?: "Pilih tenggat (opsional)")
                }
                Text("Ikon", style = MaterialTheme.typography.labelLarge)
                IconPicker(listOf("savings", "home", "car", "flight", "school", "health", "gift", "phone", "child", "star"), icon, Color(color)) { icon = it }
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            val value = target.signedAmount()
            TextButton(onClick = { onSave(name.trim().ifEmpty { "Tabungan" }, value, date, icon, color) }, enabled = value > 0) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    date = state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    pickDate = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { date = null; pickDate = false }) { Text("Tanpa tenggat") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(g: GoalWithSaved, vm: PlanViewModel, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val logs by vm.logs(g.goal.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var amountDialog by remember { mutableStateOf<Boolean?>(null) } // true = setor, false = tarik
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(g.goal.name, style = MaterialTheme.typography.titleLarge)
            Text("${money(g.saved)} dari ${formatCompact(g.goal.targetAmount)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { amountDialog = true }, modifier = Modifier.weight(1f)) { Text("Setor") }
                OutlinedButton(onClick = { amountDialog = false }, modifier = Modifier.weight(1f)) { Text("Tarik") }
            }
            Row {
                TextButton(onClick = onEdit) { Text("Ubah") }
                TextButton(onClick = { vm.setGoalStatus(g.goal.id, if (g.goal.status == "DONE") "ACTIVE" else "DONE") }) {
                    Text(if (g.goal.status == "DONE") "Tandai belum tercapai" else "Tandai tercapai")
                }
                TextButton(onClick = { vm.deleteGoal(g.goal.id); onDismiss() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Riwayat mutasi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            if (logs.isEmpty()) {
                Text("Belum ada setoran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            logs.take(20).forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(l.note ?: if (l.type == "DEPOSIT") "Setor" else "Tarik", style = MaterialTheme.typography.bodyMedium)
                        Text(l.logDate.toLocalDate().format(dateFmt), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        money(l.amount, if (l.type == "DEPOSIT") "+" else "-"),
                        color = if (l.type == "DEPOSIT") IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
    amountDialog?.let { deposit ->
        var value by remember { mutableStateOf(amountFieldValue(0)) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { amountDialog = null },
            title = { Text(if (deposit) "Setor tabungan" else "Tarik tabungan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountField(value, { value = it }, label = "Nominal")
                    OutlinedTextField(note, { note = it.take(60) }, label = { Text("Catatan (opsional)") }, singleLine = true)
                }
            },
            confirmButton = {
                val v = value.signedAmount()
                TextButton(onClick = {
                    vm.addLog(g, if (deposit) v else -v, note.trim().ifEmpty { null })
                    amountDialog = null
                }, enabled = v > 0) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { amountDialog = null }) { Text("Batal") } },
        )
    }
}
