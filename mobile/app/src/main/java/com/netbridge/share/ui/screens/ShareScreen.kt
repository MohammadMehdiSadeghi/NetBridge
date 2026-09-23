package com.netbridge.share.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netbridge.share.R
import com.netbridge.share.net.Transport
import com.netbridge.share.share.ShareManager
import com.netbridge.share.ui.transportLabel
import com.netbridge.share.ui.theme.Bg1
import com.netbridge.share.ui.theme.Brand
import com.netbridge.share.ui.theme.Danger
import com.netbridge.share.ui.theme.Stroke
import com.netbridge.share.ui.theme.Success
import com.netbridge.share.ui.theme.TextPrimary
import com.netbridge.share.ui.theme.TextSecondary
import com.netbridge.share.ui.theme.Warning

@Composable
fun ShareScreen() {
    val state by ShareManager.state.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.share_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.share_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        // Pairing code card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Password,
                    contentDescription = null,
                    tint = Brand
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pairing_code),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.pairingCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = Brand
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "netbridge_code", state.pairingCode)
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.copy_code))
                    }
                    OutlinedButton(onClick = { ShareManager.recycleCode() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.new_code))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Status summary
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.share_status), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (state.sharing) stringResource(R.string.active) else stringResource(R.string.inactive),
                        color = if (state.sharing) Success else Warning,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.status_phone_vpn), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (state.vpnActive) stringResource(R.string.active) else stringResource(R.string.inactive),
                        color = if (state.vpnActive) Success else Warning,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.connected_clients), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = state.clients.toString(),
                        color = Brand,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.status_system_link), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = transportLabel(state.transport),
                        color = if (state.transport == Transport.NONE) Warning else Brand,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.status_phone_internet), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (state.internetReachable) stringResource(R.string.active) else stringResource(R.string.status_down),
                        color = if (state.internetReachable) Success else Danger,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.http_proxy), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(":${state.httpPort}", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SOCKS5", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(":${state.socksPort}", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.control_api), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(":${state.controlPort}", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // IPs
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.connectable_addresses), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (state.ips.isEmpty()) {
                    Text(
                        stringResource(R.string.hotspot_usb_hint),
                        color = Warning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    state.ips.forEach { ip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ip,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            OutlinedButton(
                                onClick = { copyToClipboard(context, "netbridge_ip", ip) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.copy), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Connected clients — proves at a glance whether the PC actually reached us.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.connected_clients), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val clients = ShareManager.clients()
                if (clients.isEmpty()) {
                    Text(
                        stringResource(R.string.no_clients_yet),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    clients.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    c.address,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "↑ ${formatBytes(c.bytesUp)} · ↓ ${formatBytes(c.bytesDown)}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                stringResource(R.string.connections_count, c.connections),
                                color = if (c.connections > 0) Success else TextSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Steps
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.connect_steps), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val steps = listOf(
                    stringResource(R.string.step_1),
                    stringResource(R.string.step_2),
                    stringResource(R.string.step_3),
                    stringResource(R.string.step_4),
                    stringResource(R.string.step_5),
                    stringResource(R.string.step_6)
                )
                steps.forEach { s ->
                    Text(
                        text = s,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { ShareManager.toggleSharing() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.sharing) Warning else Brand,
                contentColor = androidx.compose.ui.graphics.Color(0xFF041018)
            )
        ) {
            Text(
                text = if (state.sharing) stringResource(R.string.stop_sharing) else stringResource(R.string.start_sharing),
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vpn_all_apps_notice),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}
