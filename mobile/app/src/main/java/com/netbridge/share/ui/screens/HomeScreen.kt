package com.netbridge.share.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netbridge.share.R
import com.netbridge.share.net.Transport
import com.netbridge.share.share.ShareManager
import com.netbridge.share.ui.transportLabel
import com.netbridge.share.ui.warningText
import com.netbridge.share.ui.theme.Accent
import com.netbridge.share.ui.theme.Bg0
import com.netbridge.share.ui.theme.Bg1
import com.netbridge.share.ui.theme.Bg2
import com.netbridge.share.ui.theme.Brand
import com.netbridge.share.ui.theme.BonyadeKoodak
import com.netbridge.share.ui.theme.Danger
import com.netbridge.share.ui.theme.Success
import com.netbridge.share.ui.theme.Stroke
import com.netbridge.share.ui.theme.TextPrimary
import com.netbridge.share.ui.theme.TextSecondary
import com.netbridge.share.ui.theme.Warning

@Composable
fun HomeScreen() {
    val state by ShareManager.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(28.dp))

        PowerButton(
            active = state.sharing,
            onClick = { ShareManager.toggleSharing() }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (state.sharing) stringResource(R.string.sharing_active) else stringResource(R.string.tap_to_start),
            style = MaterialTheme.typography.titleMedium,
            color = if (state.sharing) Brand else TextSecondary
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Warning,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.status_phone_vpn),
                value = if (state.vpnActive) stringResource(R.string.active) else stringResource(R.string.inactive),
                color = if (state.vpnActive) Success else Warning,
                icon = Icons.Default.Shield
            )
            StatusCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.status_clients),
                value = state.clients.toString(),
                color = Brand,
                icon = Icons.Default.DeviceHub
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.status_phone_internet),
                value = if (state.internetReachable) stringResource(R.string.status_ok) else stringResource(R.string.status_down),
                color = if (state.internetReachable) Success else Danger,
                icon = Icons.Default.Language
            )
            StatusCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.status_system_link),
                value = transportLabel(state.transport),
                color = if (state.transport == Transport.NONE) Warning else Brand,
                icon = Icons.Default.Wifi
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.status_upload),
                value = formatBytes(state.bytesIn),
                color = Accent,
                icon = Icons.Default.ArrowUpward
            )
            StatusCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.status_download),
                value = formatBytes(state.bytesOut),
                color = Success,
                icon = Icons.Default.ArrowDownward
            )
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (state.ips.isNotEmpty()) Brand else TextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.phone_addresses),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.ips.isEmpty()) {
                    Text(
                        text = stringResource(R.string.phone_addresses_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    state.ips.forEach { ip ->
                        Text(
                            text = ip,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ports_line, state.httpPort, state.controlPort),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (state.warning != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Warning.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Warning.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.needs_attention),
                        style = MaterialTheme.typography.titleMedium,
                        color = Warning
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = warningText(state.warning) ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PowerButton(active: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseValue"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f + pulse * 0.03f else 1f,
        label = "scale"
    )
    val ringColor by animateColorAsState(
        targetValue = if (active) Brand else Stroke,
        label = "ring"
    )
    val glow = if (active) 0.35f + pulse * 0.25f else 0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .shadow(
                    elevation = if (active) 28.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = Brand.copy(alpha = glow),
                    spotColor = Brand.copy(alpha = glow)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (active) listOf(Bg2, Bg1) else listOf(Bg1, Bg0)
                    )
                )
                .border(width = 3.dp, color = ringColor, shape = CircleShape)
                .clickable(onClick = onClick)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Brand.copy(alpha = 0.15f) else Bg2
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (active) Brand else TextSecondary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (active) stringResource(R.string.on) else stringResource(R.string.off),
                color = if (active) Brand else TextSecondary,
                fontFamily = BonyadeKoodak,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Bg1,
        border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
