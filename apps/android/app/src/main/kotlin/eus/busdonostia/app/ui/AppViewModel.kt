package eus.busdonostia.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.BusDonostiaApp

/** ViewModel con acceso al [AppContainer], sin framework de DI. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM =
    viewModel(key = key) { create((checkNotNull(this[APPLICATION_KEY]) as BusDonostiaApp).container) }
