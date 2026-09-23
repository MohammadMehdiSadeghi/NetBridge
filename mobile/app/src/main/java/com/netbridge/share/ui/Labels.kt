package com.netbridge.share.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.netbridge.share.R
import com.netbridge.share.net.Transport

@Composable
fun transportLabel(transport: Transport): String = when (transport) {
    Transport.USB_TETHER -> stringResource(R.string.transport_usb)
    Transport.WIFI_HOTSPOT -> stringResource(R.string.transport_hotspot)
    Transport.NONE -> stringResource(R.string.transport_none)
}

/** Map the stable warning code from ShareManager into the current locale. */
@Composable
fun warningText(code: String?): String? = when (code) {
    "no_internet" -> stringResource(R.string.warn_no_internet)
    "no_vpn" -> stringResource(R.string.warn_no_vpn)
    "no_transport" -> stringResource(R.string.warn_no_transport)
    null -> null
    else -> code
}
