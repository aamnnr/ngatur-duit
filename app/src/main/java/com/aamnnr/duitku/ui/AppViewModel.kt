package com.aamnnr.duitku.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.DuitkuApp

/** `val vm = appViewModel { DashboardViewModel(it) }` */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as DuitkuApp).container) }
    },
)

/** Akses repository langsung dari composable (untuk aksi yang harus jalan setelah layar ditutup). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as DuitkuApp).container
