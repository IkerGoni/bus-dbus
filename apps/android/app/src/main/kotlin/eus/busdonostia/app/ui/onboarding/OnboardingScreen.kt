package eus.busdonostia.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import eus.busdonostia.app.R
import eus.busdonostia.app.ui.components.BorderedCard
import eus.busdonostia.app.ui.components.OnboardingBeaconIllustration
import eus.busdonostia.app.ui.system.needsNotificationPermission
import eus.busdonostia.app.ui.theme.BusDonostiaTheme
import eus.busdonostia.app.ui.theme.DesignTokens

/** A08 · Onboarding de primera apertura. Se muestra fuera del Scaffold. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinished() }

    OnboardingContentView(
        onAllow = {
            if (needsNotificationPermission(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onFinished()
            }
        },
        onSkip = onFinished,
    )
}

@Composable
internal fun OnboardingContentView(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = DesignTokens.spacingXl, vertical = DesignTokens.spacingLg),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingLg, Alignment.CenterVertically),
        ) {
            OnboardingBeaconIllustration()
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            BorderedCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                    modifier = Modifier.padding(DesignTokens.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                ) {
                    Bullet(Icons.Filled.DirectionsBus, stringResource(R.string.onboarding_bullet_live))
                    Bullet(Icons.Outlined.Notifications, stringResource(R.string.onboarding_bullet_alerts))
                    Bullet(Icons.Filled.Translate, stringResource(R.string.onboarding_bullet_free))
                }
            }
            Text(
                text = stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(DesignTokens.spacingLg))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.touchTargetMin),
        ) {
            Text(stringResource(R.string.onboarding_allow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(DesignTokens.spacingSm))
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.touchTargetMin),
        ) {
            Text(stringResource(R.string.onboarding_skip), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Bullet(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(DesignTokens.lineBadgeLarge),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(DesignTokens.iconMedium),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun OnboardingPreview() {
    BusDonostiaTheme {
        OnboardingContentView(onAllow = {}, onSkip = {})
    }
}
