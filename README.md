# NetBridge

**Share phone internet to PC — with the phone's VPN applied to the shared connection.**

Like PdaNet, but free, open source, and under your control.

---

## Why NetBridge?

You enable a VPN on your phone (and share hotspot/USB), but by default PC traffic bypasses the VPN and goes straight to the internet — your shared connection leaks outside the tunnel.

Existing tools did not work for me: they were **paid**, **closed**, or **did not apply my VPN** to the shared link. That is why I built NetBridge.

## How it works

NetBridge builds a **proxy bridge** between PC and phone. Because the proxy exit is the phone's own traffic, it passes through the active phone VPN → **the shared link includes the VPN.** No root, no server, no changing your VPN app.

```
PC browser → Windows System Proxy → phone proxy → phone VPN → internet
```

---

## Project layout

| Folder | Contents |
|--------|----------|
| `mobile/` | Android app — Kotlin + Jetpack Compose (Persian/RTL) |
| `desktop/` | Windows app — Electron + React + TypeScript + Tailwind |
| `docs/` | Architecture, LAN protocol, deployment and testing |
| `tools/` | Automated tests (smoke + contract) |

### Two technical points that make it work

**1. VPN detection.** Android only marks one network active; when USB tethering turns on, that tether interface becomes active and a simple `activeNetwork` check no longer sees the VPN. The app enumerates all networks and separates three states: "VPN on", "VPN on but phone has no internet", and "VPN off". Each has its own message and fix (`mobile/.../net/Interfaces.kt`).

**2. Proxy egress path.** Outbound sockets bind to the network that carries the tunnel, so system traffic actually goes through the VPN instead of the default tether route (`Interfaces.preferredRoute()`). While VPN is up we never bind to the physical WAN (tunnel bypass). If bind fails, we carefully fall back to the default route so no request is dropped without reason.

---

## Quick start

### Android — download APK

The ready-to-install APK lives **in this repository** (no build required):

```
https://github.com/MohammadMehdiSadeghi/NetBridge/raw/main/NetBridge.apk
```

Or on the repo home page → file `NetBridge.apk` → **Download**

> On every update this same file is replaced; the link stays stable.

**Build from source (optional — needs JDK 17 + Android Studio):**

```bash
cd mobile
./gradlew assembleDebug      # Linux/macOS
gradlew.bat assembleDebug    # Windows
```

### Windows — build in terminal

No installer file is shipped in the repo; you build it yourself (requires [Node.js 20+](https://nodejs.org)):

```bash
git clone https://github.com/MohammadMehdiSadeghi/NetBridge.git
cd NetBridge
npm install --prefix desktop
npm run desktop:dist
```

Output: `desktop/release/*.exe`

Development run (no installer):

```bash
npm run desktop:dev
```

### Connect

1. Enable your VPN on the phone (All-apps mode)
2. NetBridge app → Share button
3. Pick one path:
   - **Hotspot:** turn hotspot on, join from PC → phone IP usually `192.168.43.1`
   - **USB:** plug cable and enable **USB tethering** in phone settings → phone IP usually `192.168.42.129`
4. Desktop app on PC → find phone → enter 6-digit code → Connect
5. Done. Windows System Proxy is on.

> **Important USB note:** Plugging the cable alone does not share internet. Until **USB tethering** is on, the PC has no path to the phone proxy.

> **If Windows says no internet:** Some Windows builds ignore the proxy when the tether link is "metered" or has no connectivity probe. Turn off **Metered connection** for that network adapter.

---

## Documentation

- [Architecture](docs/architecture.md)
- [LAN protocol](docs/protocol.md)
- [Deployment and troubleshooting](docs/deployment.md)
- [Running guide](docs/running.md)
- [Testing guide](docs/testing.md)

---

## Tests

```bash
npm test
```

Six stages: desktop typecheck, build, and two tests that run **without a phone and without Android Studio**:

- **smoke test** — runs the real `ProxyChain` class against a fake phone and checks the traffic path, byte counters, `CONNECT` tunnel, error states (`502`/`503`), and 20 concurrent connections.
- **contract test** — 38 checks on the Kotlin ↔ TypeScript contract so a field changed on one side is caught.

To build `exe` / `apk` and test on a real phone → [docs/testing.md](docs/testing.md).

---

## Ports

| Port | Role |
|------|------|
| 8080 | Phone HTTP proxy (hotspot / USB) |
| 1080 | Phone SOCKS5 |
| 7777 | Control API + pairing |
| 18080 | Desktop local proxy (Windows System Proxy) |

---

## Quick troubleshooting

| Symptom | Meaning | Action |
|---------|---------|--------|
| "Phone internet unreachable" | VPN tunnel without underlay | Turn VPN off or check mobile data |
| "System link: none" | Neither hotspot nor USB on | Turn one on |
| "VPN not active" | Share works but traffic skips tunnel | Enable VPN in All-apps mode |
| Connected clients list empty | Requests never reach the phone | Check phone address and Windows firewall |

Full table → [docs/deployment.md](docs/deployment.md)

---

## Limitations

- System Proxy coverage: browsers and most Windows apps — same as PdaNet
- Raw UDP traffic (some games): not supported (system-level TUN in a later version)
- Keep the VPN app in **All-apps** mode

---

## License

[MIT](LICENSE)
