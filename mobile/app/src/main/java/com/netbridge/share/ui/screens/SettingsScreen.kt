package com.netbridge.share.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.netbridge.share.R
import com.netbridge.share.data.SettingsStore
import com.netbridge.share.share.ShareManager
import com.netbridge.share.ui.theme.Bg1
import com.netbridge.share.ui.theme.Brand
import com.netbridge.share.ui.theme.Stroke
import com.netbridge.share.ui.theme.TextPrimary
import com.netbridge.share.ui.theme.TextSecondary

@Composable
fun SettingsScreen() {
    val state by ShareManager.state.collectAsState()
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }

    var httpPort by remember { mutableStateOf(state.httpPort.toString()) }
    var socksPort by remember { mutableStateOf(state.socksPort.toString()) }
    var controlPort by remember { mutableStateOf(state.controlPort.toString()) }
    var autoShare by remember { mutableStateOf(store.autoShare) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                PortField(stringResource(R.string.http_proxy_port), httpPort) { httpPort = it }
                Spacer(Modifier.height(12.dp))
                PortField(stringResource(R.string.socks5_port), socksPort) { socksPort = it }
                Spacer(Modifier.height(12.dp))
                PortField(stringResource(R.string.control_api_port), controlPort) { controlPort = it }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_start_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.auto_start_desc),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = autoShare,
                    onCheckedChange = {
                        autoShare = it
                        store.autoShare = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Brand,
                        checkedThumbColor = Color(0xFF041018)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val h = httpPort.toIntOrNull()
                val s = socksPort.toIntOrNull()
                val c = controlPort.toIntOrNull()
                if (h == null || s == null || c == null ||
                    h !in 1..65535 || s !in 1..65535 || c !in 1..65535
                ) {
                    Toast.makeText(context, context.getString(R.string.invalid_ports), Toast.LENGTH_SHORT).show()
                } else {
                    ShareManager.setPorts(h, s, c)
                    Toast.makeText(context, context.getString(R.string.saved_toast), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brand,
                contentColor = Color(0xFF041018)
            )
        ) {
            Text(stringResource(R.string.save_ports), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Bg1,
            border = BorderStroke(1.dp, Stroke)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.about), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${stringResource(R.string.home_title)} ${state.version}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_desc),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PortField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(5)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Brand,
                unfocusedBorderColor = Stroke,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = Brand,
                unfocusedLabelColor = TextSecondary,
                cursorColor = Brand
            )
        )
    }
}
