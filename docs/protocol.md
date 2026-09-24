# NetBridge protocol — LAN

Communication between the desktop app and the Android app on the local network (hotspot or USB tethering).

## 1. Shared phone proxy

| Service | Default port | Address |
|---------|--------------|---------|
| HTTP Proxy | `8080` | `0.0.0.0` |
| SOCKS5 | `1080` | `0.0.0.0` |

- No authentication (protection via WPA2 hotspot password)
- Each proxy connection exits as the phone's own traffic → passes through the active VPN
- DNS resolution happens on the phone (via the VPN path)

### HTTP Proxy

- `CONNECT host:port` requests (HTTPS tunnel)
- `GET/POST http://host/path` (absolute-form HTTP) rewritten to origin-form
- After `200 Connection Established` or the first response, a bidirectional pipe

### SOCKS5

- No authentication (`05 00`)
- IPv4, domain names, and IPv6
- `CONNECT` only

## 2. Control API (port 7777)

HTTP/1.1 + JSON on `0.0.0.0:7777`

### Pairing

```
POST /api/v1/pair
Content-Type: application/json

{"code": "123456"}
```

Response:

```json
{"token": "b3c1..."}
```

- The 6-digit code is shown on the phone app's Sharing tab.
- The token is persistent after pairing and stored on the desktop.

### Status (requires token)

```
GET /api/v1/status
Authorization: Bearer <token>
```

```json
{
  "sharing": true,
  "vpnActive": true,
  "internetReachable": true,
  "transport": "USB_TETHER",
  "transportLabel": "USB",
  "reachable": true,
  "clients": 1,
  "bytesIn": 10240,
  "bytesOut": 824000,
  "ips": ["192.168.42.129"],
  "httpPort": 8080,
  "socksPort": 1080,
  "controlPort": 7777,
  "code": "123456",
  "warning": null,
  "version": "1.0.0"
}
```

| Field | Meaning |
|-------|---------|
| `internetReachable` | The phone itself currently has a healthy path to the internet |
| `transport` | `USB_TETHER` / `WIFI_HOTSPOT` / `NONE` |
| `reachable` | Sharing is on and a connectable address exists |
| `ips` | Only connectable addresses (no rmnet, no TUN) |
| `warning` | Stable code `no_internet` / `no_vpn` / `no_transport` or `null` — each UI builds its own localized text |

> `internetReachable = false` usually means the VPN tunnel is up but its underlay is down. In that state the proxy forwards nothing and the desktop should tell the user to **turn the VPN off** — not turn it on.

### Clients (requires token)

```
GET /api/v1/clients
Authorization: Bearer <token>
```

```json
{
  "clients": [
    {
      "address": "192.168.42.100",
      "connections": 3,
      "bytesUp": 51200,
      "bytesDown": 4120000,
      "firstSeen": 1737650000000,
      "lastSeen": 1737650120000
    }
  ]
}
```

Each IP address is one client and all of its sockets are rolled into one record. A client unseen for 90 seconds with no open connections is removed.

### Share control

```
POST /api/v1/start     Authorization: Bearer <token>   → {"ok": true}
POST /api/v1/stop      Authorization: Bearer <token>   → {"ok": true}
POST /api/v1/recycle   Authorization: Bearer <token>   → {"code": "654321"}   # new code
```

`/start` returns `{"ok": true}` **only after** the HTTP proxy port is actually
listening. On bind failure it returns HTTP 500 with `{"error": "..."}`. The desktop
treats a 200 as proof that `:httpPort` can accept TCP.

## 3. Auto-discovery (mDNS)

- Service name: `_netbridge._tcp.local.`
- Announces the control API service on port `7777`
- After discovering the IP, desktop fetches proxy ports from `/status`

## 4. Windows System Proxy

Desktop starts a local proxy on `127.0.0.1:18080` and sets:

```
HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
  ProxyEnable = 1 (DWORD)
  ProxyServer = 127.0.0.1:18080 (SZ)
  AutoConfigURL = (removed while connected)
```

A leftover PAC (`AutoConfigURL`) makes Windows ignore `ProxyServer`, so it is
removed for the session and restored on disconnect. No Administrator required.

Chain:

```
Browser/app → 127.0.0.1:18080 → phone proxy:8080 → phone VPN → internet
```
