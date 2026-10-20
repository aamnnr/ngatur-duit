package com.aamnnr.duitku.ui.screens.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.ui.AppIcons
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.ColorPicker
import com.aamnnr.duitku.ui.components.ConfirmDialog
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.IconPicker
import com.aamnnr.duitku.ui.components.toColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories = c.profiles.activeProfileId.flatMapLatest { c.categories.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, name: String, type: TransactionType, icon: String, color: Long) = viewModelScope.launch {
        c.categories.save(c.profiles.activeProfileId.first(), id, name, type, icon, color)
    }

    suspend fun usageCount(id: String) = c.categories.usageCount(id)

    fun delete(id: String) = viewModelScope.launch { c.categories.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val all by vm.categories.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pair<CategoryEntity, Int>?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategori") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Tambah kategori") }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (tab == TransactionType.EXPENSE) 0 else 1) {
                Tab(selected = tab == TransactionType.EXPENSE, onClick = { tab = TransactionType.EXPENSE },
                    text = { Text("Pengeluaran") })
                Tab(selected = tab == TransactionType.INCOME, onClick = { tab = TransactionType.INCOME },
                    text = { Text("Pemasukan") })
            }
            LazyColumn {
                items(all.filter { it.type == tab }, key = { it.id }) { cat ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = cat }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(AppIcons.get(cat.icon), cat.color.toColor(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(12.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { deleting = cat to vm.usageCount(cat.id) } }) {
                            Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CategoryEditDialog(
            category = editing,
            onDismiss = { creating = false; editing = null },
        ) { name, icon, color ->
            vm.save(editing?.id, name, editing?.type ?: tab, icon, color)
            creating = false
            editing = null
        }
    }
    deleting?.let { (cat, count) ->
        ConfirmDialog(
            title = "Hapus kategori ${cat.name}?",
            message = if (count > 0) "$count transaksi memakai kategori ini. Transaksinya tetap ada dan masih menampilkan nama kategori ini."
            else "Kategori ini belum dipakai. Aman untuk dihapus.",
            confirmLabel = "Hapus",
            onConfirm = { vm.delete(cat.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: Long) -> Unit,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember { mutableStateOf(category?.icon ?: "more") }
    var color by remember { mutableStateOf(category?.color ?: 0xFF3B2F2A) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Kategori baru" else "Ubah kategori") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama kategori") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text("Ikon", style = MaterialTheme.typography.labelLarge)
                IconPicker(AppIcons.categoryKeys, icon, Color(color)) { icon = it }
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), icon, color) }, enabled = name.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
