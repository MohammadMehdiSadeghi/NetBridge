package com.netbridge.share.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netbridge.share.R
import com.netbridge.share.ui.theme.Bg1
import com.netbridge.share.ui.theme.Brand
import com.netbridge.share.ui.theme.Stroke
import com.netbridge.share.ui.theme.TextPrimary
import com.netbridge.share.ui.theme.TextSecondary
import com.netbridge.share.ui.theme.Warning

@Composable
fun GuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.guide_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        GuideCard(
            title = stringResource(R.string.guide_usb),
            steps = listOf(
                stringResource(R.string.guide_usb_1),
                stringResource(R.string.guide_usb_2),
                stringResource(R.string.guide_usb_3),
                stringResource(R.string.guide_usb_4),
                stringResource(R.string.guide_usb_5),
                stringResource(R.string.guide_usb_6)
            ),
            note = stringResource(R.string.guide_note),
            icon = { Icon(Icons.Default.Cable, contentDescription = null, tint = Brand) }
        )

        Spacer(Modifier.height(16.dp))

        GuideCard(
            title = stringResource(R.string.guide_hotspot),
            steps = listOf(
                stringResource(R.string.guide_hotspot_1),
                stringResource(R.string.guide_hotspot_2),
                stringResource(R.string.guide_hotspot_3),
                stringResource(R.string.guide_hotspot_4),
                stringResource(R.string.guide_hotspot_5),
                stringResource(R.string.guide_hotspot_6)
            ),
            note = null,
            icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Brand) }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GuideCard(
    title: String,
    steps: List<String>,
    note: String?,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Bg1,
        border = BorderStroke(1.dp, Stroke)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(10.dp))
            steps.forEach { s ->
                Text(
                    text = s,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (note != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    color = Warning,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
