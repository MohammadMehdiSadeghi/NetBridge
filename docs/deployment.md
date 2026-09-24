# NetBridge deployment guide

No separate server is required. Just two apps:

## 1. Android app (`mobile/`)

### With Android Studio

1. Android Studio → **Open** → folder `mobile`
2. Wait for Gradle Sync
3. **Build → Build Bundle(s)/APK(s) → Build APK(s)**
4. Output: `mobile/app/build/outputs/apk/debug/app-debug.apk`

### From the command line (needs JDK 17 + Android SDK)

```bash
cd mobile
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

### On-device setup

1. Turn on your VPN (All-apps mode)
2. Open NetBridge → Share/On button
3. Prepare one of the paths:
   - **Hotspot:** turn hotspot on (WPA2), **or**
   - **USB:** plug the cable and enable **USB tethering** in phone settings
4. Sharing tab: 6-digit code + phone IP + ports
5. Open the desktop app on the PC

> **USB does not work with cable alone.** Until USB tethering is on, the
> `rndis`/`ncm` interface is not created on the phone and the PC has no path
> to the proxy. In the app, the "System link" box must show `USB` or `Hotspot`;
> if it shows `none`, this step is incomplete.

> Default ports: proxy `8080`, SOCKS `1080`, control `7777`

### Addresses per mode

| Path | Phone interface | Phone IP | Typical PC IP |
|------|-----------------|----------|---------------|
| Hotspot | `ap*` / `swlan*` | `192.168.43.1` | `192.168.43.x` |
| USB | `rndis0` / `ncm0` | `192.168.42.129` | `192.168.42.x` |

The app only shows **connectable** addresses: `rmnet*` (mobile data) and
`tun*` (VPN tunnel) are filtered out, because the PC cannot reach the phone on them.

## 2. Desktop app (`desktop/`)

Prerequisite: Node.js 20+

```bash
cd desktop
npm install
npm run dev        # development run
npm run typecheck  # type check
npm run dist       # build NSIS installer → desktop/release/*.exe
```

### First connection

1. PC joins phone hotspot — or USB plugged and USB tethering on
2. Desktop app → **Device** tab → phone is auto-discovered (or manual IP)
3. Enter the phone's 6-digit code → pair
4. Press the main Connect button:
   - Local proxy starts
   - Windows System Proxy turns on
   - Phone share activates
5. On disconnect, System Proxy restores previous values

---

## Troubleshooting

### First: look at the Connect screen

Desktop and phone apps now show differentiated status. Match the message to the table — each message asks for exactly one action:

| Message / sign | Meaning | Action |
|----------------|---------|--------|
| "Phone VPN not active" | Share works but traffic skips the tunnel | Enable VPN in All-apps mode |
| "Phone internet unreachable" | Tunnel is up but underlay is down | **Turn VPN off** or check mobile data |
| "System link: none" | Neither hotspot nor USB tethering on | Turn one on |
| "Phone … has no connectable address" | Share on but tether interface not created | Enable USB tethering or hotspot |
| Connected clients list stays empty | Requests never reach the phone | Check phone IP, Windows firewall, and phone VPN |

> **Do not diagnose "no internet" as VPN off.** Many VPN apps report the phone
> as "no internet" briefly after connecting because DNS changes. If
> `internetReachable` is false but the phone browser opens sites, ignore the app;
> if the phone also cannot open sites, turn the VPN off.

### Common problems

| Problem | Fix |
|---------|-----|
| Desktop does not see the phone | Enter manual IP: hotspot `192.168.43.1`, USB `192.168.42.129`; allow the app through Windows Firewall |
| "Wrong code" | Re-read the 6-digit code on the phone; press "New code" on the phone |
| VPN not applied on the system | In the phone VPN app choose All-apps, not selected-apps |
| Hotspot drops with VPN | Some ROMs cannot do both → try USB tethering |
| Windows says "no internet" and sites fail | Set that network adapter to not metered; Windows ignores proxy on metered links |
| Windows proxy does not work | In desktop settings toggle System Proxy off and on |
| Internet does not return after closing the app | App restores System Proxy on disconnect; if it crashed, turn off manual mode in Settings → Network → Proxy |
| A site does not load | Toggle the phone VPN once; set system DNS to `1.1.1.1` |

### Quick checks

```bash
# From PC — replace phone address
curl -v -x http://192.168.42.129:8080 https://example.com

# Raw status from phone API (take token from desktop app)
curl -H "Authorization: Bearer <token>" http://192.168.42.129:7777/api/v1/status
curl -H "Authorization: Bearer <token>" http://192.168.42.129:7777/api/v1/clients
```

`HTTP/1.1 200` means the chain is healthy. If `curl -x` works but the app does not,
the problem is Windows System Proxy, not the phone. If `/clients` stays empty after
`curl -x`, traffic never reached the phone.
