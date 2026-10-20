package com.aamnnr.duitku.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aamnnr.duitku.ui.components.LocalHideBalance
import com.aamnnr.duitku.ui.components.neuSurface
import com.aamnnr.duitku.ui.theme.CardShadow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aamnnr.duitku.domain.TransactionItem
import android.net.Uri
import com.aamnnr.duitku.data.remote.DraftPayload
import com.aamnnr.duitku.ui.screens.account.AccountScreen
import com.aamnnr.duitku.ui.screens.add.AddSheet
import com.aamnnr.duitku.ui.screens.assistant.AssistantScreen
import com.aamnnr.duitku.ui.screens.inbox.InboxScreen
import com.aamnnr.duitku.ui.screens.scan.ScanReceiptScreen
import com.aamnnr.duitku.ui.screens.telegram.TelegramScreen
import com.aamnnr.duitku.ui.screens.transaction.FormPrefill
import java.time.LocalDate
import com.aamnnr.duitku.ui.screens.category.CategoriesScreen
import com.aamnnr.duitku.ui.screens.history.HistoryScreen
import com.aamnnr.duitku.ui.screens.home.HomeScreen
import com.aamnnr.duitku.ui.screens.insight.InsightScreen
import com.aamnnr.duitku.ui.screens.more.SettingsScreen
import com.aamnnr.duitku.ui.screens.plan.PlanScreen
import com.aamnnr.duitku.ui.screens.plan.RealityScreen
import com.aamnnr.duitku.ui.screens.transaction.FormMode
import com.aamnnr.duitku.ui.screens.transaction.TransactionFormScreen
import com.aamnnr.duitku.ui.screens.wallet.WalletsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.YearMonth

object Routes {
    const val HOME = "home"
    const val INSIGHT = "insight"
    const val PLAN = "plan"
    const val SETTINGS = "settings"
    const val REALITY = "reality"
    const val HISTORY = "history?walletId={walletId}&categoryId={categoryId}&month={month}"
    const val TRANSACTION =
        "transaction?id={id}&mode={mode}&walletId={walletId}&amount={amount}&categoryId={categoryId}&note={note}&date={date}&draftId={draftId}"
    const val WALLETS = "wallets"
    const val CATEGORIES = "categories"
    const val ACCOUNT = "account"
    const val INBOX = "inbox"
    const val ASSISTANT = "assistant"
    const val SCAN = "scan"
    const val TELEGRAM = "telegram"

    fun history(walletId: String? = null, categoryId: String? = null, month: YearMonth? = null): String =
        withQuery("history", "walletId" to walletId, "categoryId" to categoryId, "month" to month?.toString())

    fun transaction(
        id: String? = null,
        mode: FormMode? = null,
        walletId: String? = null,
        amount: Long? = null,
        categoryId: String? = null,
        note: String? = null,
        date: String? = null,
        draftId: String? = null,
    ): String = withQuery(
        "transaction",
        "id" to id, "mode" to mode?.name, "walletId" to walletId, "amount" to amount?.toString(),
        "categoryId" to categoryId, "note" to note, "date" to date, "draftId" to draftId,
    )

    /** Form catat yang sudah terisi dari draf (bot, asisten, atau scan struk). */
    fun transactionFromDraft(d: DraftPayload, draftId: String? = null) = transaction(
        mode = if (d.type == "INCOME") FormMode.INCOME else FormMode.EXPENSE,
        walletId = d.walletId, amount = d.amount, categoryId = d.categoryId, note = d.note, date = d.date, draftId = draftId,
    )

    private fun withQuery(base: String, vararg params: Pair<String, String?>): String {
        val query = params.filter { it.second != null }.joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
        return if (query.isEmpty()) base else "$base?$query"
    }
}

/**
 * Menampilkan snackbar "Urungkan". Berjalan di scope tingkat aplikasi, jadi tetap bekerja
 * walau layar yang memicunya sudah ditutup (mis. hapus dari form lalu kembali).
 */
class UndoController(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** Jalankan [action] (mis. hapus), lalu tawarkan [undo] lewat snackbar. */
    fun perform(message: String, action: suspend () -> Unit, undo: suspend () -> Unit) {
        scope.launch {
            action()
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = "Urungkan", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    /** Jalankan [action] di scope aplikasi lalu tampilkan [message] (tanpa tombol Urungkan). */
    fun run(message: String, action: suspend () -> Unit) {
        scope.launch {
            action()
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }
}

val LocalUndo = staticCompositionLocalOf<UndoController> { error("UndoController belum disediakan") }

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val leftTabs = listOf(
    Tab(Routes.HOME, "Beranda", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.WALLETS, "Dompet", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
)
private val rightTabs = listOf(
    Tab(Routes.INSIGHT, "Insight", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab(Routes.PLAN, "Rencana", Icons.Outlined.Flag, Icons.Filled.Flag),
)
private val tabRoutes = (leftTabs + rightTabs).map { it.route }.toSet()

@Composable
fun DuitkuNavHost(openTarget: String? = null, onTargetHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()
    val undo = remember { UndoController(snackbar, rootScope) }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val openTx: (TransactionItem) -> Unit = { nav.navigate(Routes.transaction(id = it.id)) }
    val container = appContainer()
    val hideBalance by container.settings.hideBalance.collectAsStateWithLifecycle(initialValue = false)
    val context = androidx.compose.ui.platform.LocalContext.current

    // Tujuan dari notifikasi atau widget.
    androidx.compose.runtime.LaunchedEffect(openTarget) {
        when (openTarget) {
            null -> return@LaunchedEffect
            "inbox" -> nav.navigate(Routes.INBOX)
            "add" -> showAdd = true
            "plan" -> nav.switchTab(Routes.PLAN)
            "scan" -> nav.navigate(Routes.SCAN)
            else -> nav.switchTab(Routes.HOME)
        }
        onTargetHandled()
    }

    // Catat dengan suara: pengenal suara Android → parser lokal → form terisi.
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val draft = text?.let { com.aamnnr.duitku.domain.VoiceParser.parse(it) }
        if (text != null && draft == null) {
            undo.run("Nominal tidak terdengar: \"$text\". Coba sebut misalnya \"kopi 25 ribu\".") {}
        } else if (draft != null) {
            rootScope.launch {
                val pid = container.profiles.activeProfileId.first()
                val cats = container.categories.observeAll(pid).first().filter { it.type == draft.type }
                val wallets = container.wallets.observeWithBalance(pid).first()
                val category = com.aamnnr.duitku.notif.NotificationParser.suggestCategory(text, cats.map { it.id to it.name })
                val wallet = wallets.firstOrNull { text.contains(it.name, ignoreCase = true) }
                nav.navigate(
                    Routes.transaction(
                        mode = if (draft.type == com.aamnnr.duitku.domain.TransactionType.INCOME) FormMode.INCOME else FormMode.EXPENSE,
                        amount = draft.amount, categoryId = category, note = draft.note, walletId = wallet?.id,
                    ),
                )
            }
        }
    }
    val startVoice: () -> Unit = {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Sebutkan transaksi, mis. \"kopi 25 ribu\"")
        runCatching { voice.launch(intent) }.onFailure {
            undo.run("Pengenal suara tidak tersedia di HP ini.") {}
        }
    }

    CompositionLocalProvider(LocalUndo provides undo, LocalHideBalance provides hideBalance) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (current in tabRoutes) {
                    BottomBar(
                        current = current,
                        onTab = { nav.switchTab(it) },
                        onAdd = { showAdd = true },
                        onQuickExpense = { nav.navigate(Routes.transaction(mode = FormMode.EXPENSE)) },
                    )
                }
            },
        ) { inner ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(inner)) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenTransaction = openTx,
                        onSeeAll = { nav.navigate(Routes.history()) },
                        onOpenWallet = {
                            container.walletSelection.value = it
                            nav.switchTab(Routes.WALLETS)
                        },
                        onOpenProfile = { nav.navigate(Routes.SETTINGS) },
                        onOpenInbox = { nav.navigate(Routes.INBOX) },
                        onTransfer = { nav.navigate(Routes.transaction(mode = FormMode.TRANSFER)) },
                        onScan = { nav.navigate(Routes.SCAN) },
                        onBills = { nav.navigate(Routes.transaction(mode = FormMode.EXPENSE, note = "Tagihan")) },
                        onInsight = { nav.switchTab(Routes.INSIGHT) },
                        onPlan = { nav.switchTab(Routes.PLAN) },
                    )
                }
                composable(Routes.INSIGHT) {
                    InsightScreen(
                        onOpenCategory = { id, month -> nav.navigate(Routes.history(categoryId = id, month = month)) },
                        onOpenHistory = { month -> nav.navigate(Routes.history(month = month)) },
                    )
                }
                composable(Routes.PLAN) {
                    PlanScreen(
                        onRecordBill = { bill ->
                            nav.navigate(
                                Routes.transaction(
                                    mode = FormMode.EXPENSE, amount = bill.typicalAmount, categoryId = bill.categoryId, note = bill.name,
                                ),
                            )
                        },
                        onOpenCalculator = { nav.navigate(Routes.REALITY) },
                    )
                }
                composable(Routes.REALITY) { RealityScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenInsight = { nav.switchTab(Routes.INSIGHT) },
                        onOpenWallets = { nav.switchTab(Routes.WALLETS) },
                        onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onOpenTelegram = { nav.navigate(Routes.TELEGRAM) },
                    )
                }
                composable(
                    Routes.HISTORY,
                    arguments = listOf(
                        optionalArg("walletId"), optionalArg("categoryId"), optionalArg("month"),
                    ),
                ) { entry ->
                    val args = entry.arguments
                    HistoryScreen(
                        initialWalletId = args?.getString("walletId"),
                        initialCategoryId = args?.getString("categoryId"),
                        initialMonth = args?.getString("month")?.let(YearMonth::parse),
                        onOpenTransaction = openTx,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    Routes.TRANSACTION,
                    arguments = listOf("id", "mode", "walletId", "amount", "categoryId", "note", "date", "draftId")
                        .map(::optionalArg),
                ) { entry ->
                    val args = entry.arguments
                    TransactionFormScreen(
                        editId = args?.getString("id"),
                        initialMode = args?.getString("mode")?.let(FormMode::valueOf),
                        initialWalletId = args?.getString("walletId"),
                        onDone = { nav.popBackStack() },
                        prefill = FormPrefill(
                            amount = args?.getString("amount")?.toLongOrNull(),
                            categoryId = args?.getString("categoryId"),
                            note = args?.getString("note"),
                            date = args?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                            draftId = args?.getString("draftId"),
                        ),
                    )
                }
                composable(Routes.WALLETS) {
                    WalletsScreen(
                        onOpenTransaction = openTx,
                        onTransfer = { nav.navigate(Routes.transaction(mode = FormMode.TRANSFER, walletId = it)) },
                        onHistory = { nav.navigate(Routes.history(walletId = it)) },
                    )
                }
                composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ACCOUNT) { AccountScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.INBOX) {
                    InboxScreen(
                        onBack = { nav.popBackStack() },
                        onEdit = { d -> nav.navigate(Routes.transactionFromDraft(d.parsed, draftId = d.id)) },
                    )
                }
                composable(Routes.ASSISTANT) {
                    AssistantScreen(
                        onBack = { nav.popBackStack() },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onEditDraft = { d -> nav.navigate(Routes.transactionFromDraft(d)) },
                    )
                }
                composable(Routes.SCAN) {
                    ScanReceiptScreen(
                        onBack = { nav.popBackStack() },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onContinue = { amount, categoryId, note, date ->
                            val route = Routes.transaction(
                                mode = FormMode.EXPENSE, amount = amount, categoryId = categoryId, note = note, date = date,
                            )
                            nav.navigate(route) { popUpTo(Routes.SCAN) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.TELEGRAM) {
                    TelegramScreen(onBack = { nav.popBackStack() }, onOpenAccount = { nav.navigate(Routes.ACCOUNT) })
                }
            }
        }

        if (showAdd) {
            AddSheet(
                onDismiss = { showAdd = false },
                onPick = { mode ->
                    showAdd = false
                    nav.navigate(Routes.transaction(mode = mode))
                },
                onScan = {
                    showAdd = false
                    nav.navigate(Routes.SCAN)
                },
                onAssistant = {
                    showAdd = false
                    nav.navigate(Routes.ASSISTANT)
                },
                onVoice = {
                    showAdd = false
                    startVoice()
                },
            )
        }
    }
}

private fun optionalArg(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBar(
    current: String?,
    onTab: (String) -> Unit,
    onAdd: () -> Unit,
    onQuickExpense: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Pill melayang berisi 4 tab; ruang tengah untuk tombol +.
        Row(
            Modifier
                .fillMaxWidth()
                .neuSurface(RoundedCornerShape(50), elevation = 12.dp)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leftTabs.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } }
            Spacer(Modifier.weight(1f))
            rightTabs.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } }
        }
        Box(
            Modifier
                .padding(bottom = 22.dp)
                .size(62.dp)
                .shadow(14.dp, CircleShape, ambientColor = CardShadow.copy(alpha = 0.3f), spotColor = CardShadow.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .combinedClickable(
                    onClickLabel = "Catat transaksi",
                    onLongClickLabel = "Catat pengeluaran",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQuickExpense()
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAdd()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "Catat transaksi", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
