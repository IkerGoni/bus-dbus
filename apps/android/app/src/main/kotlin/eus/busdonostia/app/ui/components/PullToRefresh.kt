package eus.busdonostia.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.runningFold

/**
 * Pull-to-refresh con color de marca e indicador propio. La háptica se dispara **solo** al cruzar
 * el umbral con el gesto (nunca al refrescar por programa): `distanceFraction` solo cambia con el
 * arrastre del usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction }
            .runningFold(0f to 0f) { (_, previous), current -> previous to current }
            .filter { (previous, current) -> crossedRefreshThreshold(previous, current) }
            .collect { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        content = content,
    )
}

/** Pura: true solo al cruzar el umbral hacia arriba (un aviso háptico por gesto). */
fun crossedRefreshThreshold(previous: Float, current: Float): Boolean = previous < 1f && current >= 1f
