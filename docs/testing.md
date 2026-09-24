# NetBridge testing guide

This document says what can be tested here, what cannot, and exactly what to do for a real on-phone test.

---

## 1. Test without a phone (works right now)

```bash
node tools/test.mjs
```

Six stages run in sequence:

| Stage | What it proves |
|-------|----------------|
| typecheck ×2 | All code is type-safe — a large class of bugs is caught here |
| build | App bundle builds |
| Compile ProxyChain | Real app class prepared for testing |
| smoke test | **Real traffic path** without a phone |
| contract test | Kotlin ↔ TypeScript contract (38 checks) |

### What the smoke test actually does

It builds a fake phone: an HTTP proxy on `127.0.0.1:18080` and an upstream server, then runs the **real `ProxyChain` class** against it:

```
PC app → 127.0.0.1:18099 → fake phone:18080 → fake internet
```

And it verifies:

- HTTP request reaches the end and returns the correct response
- Byte counters increase in both directions
- A 50 KB body passes intact
- `CONNECT` tunnel (what HTTPS uses) carries bytes both ways
- Phone unreachable → immediate `502`, not a hang
- Phone not configured → clean `503`, not a crash
- Port actually binds
- `stop()` releases the port (otherwise next run is `EADDRINUSE`)
- 20 concurrent connections all succeed

> This test found a real bug: when the phone was unreachable, the client socket stayed in `this.sockets` and `stop()` never returned — meaning the Disconnect button could hang and Windows System Proxy would stay on a dead port. Fixed.

---

## 2. What **cannot** be built here

| Item | Blocker | Fix |
|------|---------|-----|
| `apk` | No JDK 17 (only JRE 1.8), no Android SDK, no Gradle | Android Studio |
| `exe` | `electron-builder` must fetch a 115 MB Electron binary from GitHub; download is blocked | Run `npm run dist` on an open network |

Both blockers are network/tooling, not code — with the right tools they build.

---

## 3. Real test: step by step

### Prerequisites

```bash
cd desktop
npm install
npm run dev
```

If `npm run dev` says "Electron binary not found", the binary was not downloaded. On an open network:

```bash
node node_modules/electron/install.js
```

### Step 1 — Build APK

Open folder `mobile/` in Android Studio → Gradle Sync → **Build APK(s)**.

Output: `mobile/app/build/outputs/apk/debug/app-debug.apk`

Install on the phone.

### Step 2 — Prepare the phone

1. **Turn on your VPN** — All-apps mode, not selected-apps. If selected-only, proxy output leaves the tunnel and the whole idea fails.
2. **Turn on the link** — one of these:
   - **Hotspot** on (WPA2 password), **or**
   - Plug USB cable and enable **USB tethering** in phone settings.

   > Plugging the cable alone is not enough. Until USB tethering is on, the `rndis0` interface is not created and the PC has no path.
3. **Open NetBridge** → press Share.

**This is the first checkpoint.** In the app you should see:

| Field | Correct value |
|-------|---------------|
| Phone VPN | Active |
| Phone internet | Healthy |
| System link | USB or hotspot (**not** "none") |
| This phone's addresses | An IP like `192.168.42.129` or `192.168.43.1` |

If "System link: none", step 2 is incomplete. Do not go further.

### Step 3 — Connect the PC

1. Join the hotspot from the PC, or wait for Windows to recognize the USB network.
2. Desktop app → **Device** tab → "Scan for phone".
   If not found, enter manual IP:
   - USB: `192.168.42.129`
   - Hotspot: `192.168.43.1`
3. Enter the 6-digit code from the phone Sharing tab → **Pair**.
4. **Connect** tab → big button.

**Second checkpoint** on the Connect tab:

- "Phone VPN: active"
- "Phone share: active"
- "System Proxy: on"
- "System link" correct

### Step 4 — Prove traffic actually goes through the VPN

This is the most important step. "The site opened" is not enough — you must prove the tunnel is used.

**Method A — IP comparison (best):**

1. Check current IP from the PC: <https://ifconfig.me> or <https://ipinfo.io>
2. Disconnect the desktop app (System Proxy restores)
3. Check the same site from the PC again

If the IP **changed** and matches the phone VPN exit → it works. ✅

**Method B — byte counter test:**

1. On the phone app, Sharing tab → Connected clients
2. Open a heavy page in the PC browser
3. Your PC IP should appear and ↑↓ numbers should climb

If the list stays empty but the site opens, traffic is not going through the proxy.

**Method C — command line:**

```bash
# Real IP without proxy
curl -s https://ifconfig.me

# IP through the NetBridge chain (replace phone address)
curl -s -x http://192.168.42.129:8080 https://ifconfig.me
```

The two IPs must **differ**, and the second must be the VPN exit IP.

### Step 5 — Error-state tests

Break it on purpose and check the messages:

| Test | Action | Expect |
|------|--------|--------|
| VPN without internet | Connect VPN to a dead server | "Phone internet unreachable" |
| No VPN | Turn VPN off | "Phone VPN not active" but share still works |
| Link down | Turn hotspot off | "System link: none" and empty address list |
| Disconnect | Press Disconnect | System Proxy restores and normal PC internet returns |

**The last test matters most.** After Disconnect, PC internet must return immediately. If not, in Windows: Settings → Network → Proxy → turn off manual mode.

---

## 4. If a test fails

First see `docs/deployment.md` — the troubleshooting table maps to the exact app messages. If you cannot find the message, run this from the PC:

```bash
# Is the phone proxy reachable from the PC?
curl -v -x http://<PHONE_IP>:8080 https://example.com

# Raw status from the phone API
curl -H "Authorization: Bearer <TOKEN>" http://<PHONE_IP>:7777/api/v1/status
curl -H "Authorization: Bearer <TOKEN>" http://<PHONE_IP>:7777/api/v1/clients
```

Interpretation:

| Result | Meaning |
|--------|---------|
| `curl -x` works, app does not | Problem is Windows System Proxy, not the phone |
| `curl -x` also fails | Phone/network — address, firewall, or share |
| `/status` works but `/clients` empty | Traffic never reaches the phone |
| `status.sharing=false` | Phone share not turned on |
| `status.ips` empty | Tether interface not created |

---

## 5. What is still untested

To be honest:

- **Kotlin code was never compiled here** — no JDK 17 or Android SDK on this machine. Only manual review. Build once in Android Studio before relying on it. Most likely syntax-error spots: `ShareManager.kt` and `Interfaces.kt`.
- **Real VPN bind path** (`Network.socketFactory`) was not tested on real hardware. If your phone behaves oddly, `preferredRoute()` in `Interfaces.kt` is the first place to look. Priority must always be VPN; binding to physical WAN while VPN is up tunnels around the tunnel.
- **Specific ROMs** that do not expose the tether interface as `NetworkInterface` are not covered (native `GetClients` resolver was deliberately not done).
- **UDP traffic** (some games) is not supported — System Proxy only carries TCP.
