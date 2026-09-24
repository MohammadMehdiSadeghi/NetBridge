# NetBridge architecture

**NetBridge is not a VPN.** NetBridge works like PdaNet: it shares the phone's internet to the PC over hotspot or USB — except it **also applies the VPN active on the phone** to the PC's traffic.

## The problem NetBridge solves

Default behavior:

```
Phone: VPN on ← phone's own traffic goes through VPN
Hotspot: PC packets ← direct forward ← internet   ❌ no VPN
```

With NetBridge:

```
PC app → System Proxy → phone proxy → proxy exit uses phone VPN → internet   ✅
```

## How it works (no root)

1. The user turns on any VPN they like on the phone (all phone app traffic enters the VPN tunnel).
2. NetBridge on the phone starts an **HTTP Proxy** and **SOCKS5** on `0.0.0.0`.
3. When the PC connects to this proxy, the proxy opens destinations on behalf of the PC.
4. The proxy's outbound connection is the phone's own traffic → passes through the existing VPN → reaches the destination.

So the phone-side VPN also applies to the "shared link", with no root and no kernel routing changes.

## Components

```
┌────────────────── LAN: hotspot / USB tethering ──────────────────┐
│                                                                   │
│  ┌──────────────────┐      HTTP :8080      ┌───────────────────┐  │
│  │  Desktop app     │ ───────────────────► │   Android app     │  │
│  │  (Windows)       │      API  :7777      │   "NetBridge"     │  │
│  │                  │ ◄──────────────────► │                   │  │
│  │ System Proxy     │   pairing / status   │  HTTP/SOCKS proxy │  │
│  │ 127.0.0.1:18080  │                      │        │          │  │
│  └──────────────────┘                      │        ▼          │  │
│                                            │  Phone VPN (any   │  │
│                                            │  VPN app)         │  │
│                                            └────────┬──────────┘  │
└─────────────────────────────────────────────────────┼─────────────┘
                                                      │
                                                 Internet
```

### 1. Android app (`mobile/`)

- Kotlin + Jetpack Compose, Persian RTL UI
- HTTP Proxy and SOCKS5 servers on the LAN
- **Network detection based on `ConnectivityManager.allNetworks`**, not `activeNetwork`
- LAN control API for desktop (pairing with a 6-digit code)
- mDNS/NSD auto-discovery
- Foreground service to keep running in background
- Per-client connection and byte stats (`ClientRegistry`)

#### Why `activeNetwork` is not enough

Android marks only **one** network active. The moment USB tethering turns on, that tether interface becomes the active network and `activeNetwork.hasTransport(VPN)` no longer sees the VPN — even if the VPN is really on. This is exactly why "in default mode the system does not use the VPN".

So `Interfaces.enumerate()` walks all networks and separates three things:

- Which networks have VPN transport → `vpnNetworks`
- Which networks have underlay transport (cellular / wifi / ethernet) → `wanNetworks`
- Whether the VPN network also carries underlay (for detection only) → `vpnIsUplink`

These two states must be told apart, because the fix is completely different:

| State | Meaning | User action |
|-------|---------|-------------|
| `internetReachable = false` | Tunnel is up but underlay is down | **Turn VPN off** |
| `vpnActive = false` | Sharing works but is unprotected | **Turn VPN on** |

#### Proxy egress path

Proxy outbound sockets bind with `Network.socketFactory` to the best route:

1. **Whenever a VPN network exists → VPN only.** Explicit bind to physical WAN while VPN is up bypasses the tunnel (the old NetBridge bug).
2. Without VPN → a validated WAN network (normal sharing).
3. If neither → `null`, let Android decide.

Source: `Interfaces.preferredRoute()`. A bind failure never rejects the request; it falls back to the default (no-VPN) route. The result is cached for 5 seconds so toggling VPN mid-session is picked up.

### 2. Desktop app (`desktop/`)

- Electron + React + TypeScript + Tailwind (dark theme, RTL)
- Phone auto-discovery or manual IP
- Pairing with phone code → persistent token
- Local proxy chain with traffic counters
- Windows System Proxy on/off (no Admin)
- Single connect button: phone share + system proxy together

#### Connect order and why it matters

`connect()` starts the local proxy first, then sets System Proxy, and only after that turns on phone sharing. Every error path also restores System Proxy.

If the order were reversed — System Proxy set first, then connecting to the phone fails — Windows would point at a port nobody is listening on and the user would be left "without internet" without knowing why. This is the class of bugs that forces users to kill the app and manually disable Windows proxy.

### 3. Why not a VPN server?

Your need: your own VPN stays on the phone and also applies to the shared link. NetBridge is only the **proxy bridge** between PC and that VPN — like PdaNet, but your product.

## Proxy mode limitations (same as PdaNet)

- Browsers and most Windows apps that honor System Proxy: ✅
- Raw UDP traffic (some games, VoIP): ❌ (needs system-level TUN in a later version)
- VPN app in "selected apps" mode may exclude proxy output from the tunnel → choose "All apps" in the VPN app.
