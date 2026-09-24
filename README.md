# NetBridge

Share your phone internet to the PC — with the phone's VPN applied to the shared
connection. Like PdaNet, but free, open source, and under your control.

Interface: Persian (RTL) on the phone, English + Persian on the desktop.

**Requires:** Windows 10/11 · Android 8+ · Node.js 20+ (desktop build only)

## Quick start

### Android

Install the ready APK from this repository (no build required):

```
https://github.com/MohammadMehdiSadeghi/NetBridge/raw/main/NetBridge.apk
```

Or open the repo home page → `NetBridge.apk` → **Download**. The same file is
replaced on every update; the link stays stable.

### Windows

Build the installer yourself (needs [Node.js 20+](https://nodejs.org)):

```bash
git clone https://github.com/MohammadMehdiSadeghi/NetBridge.git
cd NetBridge
npm install --prefix desktop
npm run desktop:dist
```

Output: `desktop/release/*.exe`

Development run (no installer): `npm run desktop:dev`

### Connect

1. Turn the VPN on the phone (All-apps mode)
2. NetBridge app → **Share**
3. Pick a path:
   - **Hotspot** — phone IP usually `192.168.43.1`
   - **USB** — plug the cable and enable **USB tethering** → phone IP usually `192.168.42.129`
4. Desktop app → find phone → enter the 6-digit code → **Connect**
5. Done. Windows System Proxy is on.

> **USB:** plugging the cable alone does nothing. Until **USB tethering** is on,
> the PC cannot reach the phone proxy.

> **Windows says no internet?** Turn off **Metered connection** for that adapter;
> some builds ignore the proxy on metered links.

## Why NetBridge?

By default, PC traffic after hotspot/USB sharing **bypasses the phone VPN** and
leaves the tunnel. Existing tools were paid, closed, or did not apply the VPN to
the shared link. NetBridge builds a proxy bridge so the exit is the phone's own
traffic — the shared connection includes the VPN. No root, no server, no VPN app
changes.

```
PC browser → Windows System Proxy → phone proxy → phone VPN → internet
```

## What it does

* **Phone app (Android)** — Kotlin + Jetpack Compose, Persian/RTL. Detects VPN
  state (on / on-but-no-internet / off), starts the HTTP + SOCKS5 proxy and the
  pairing API.
* **Desktop app (Windows)** — Electron + React + TypeScript. Finds the phone on
  the LAN, pairs with a 6-digit code, and sets the Windows System Proxy.
* **VPN-aware egress** — outbound sockets bind to the tunnel network so traffic
  actually goes through the VPN (`Interfaces.preferredRoute()`).
* **Pairing** — control port `7777` is probed first; clear errors if the phone
  is unreachable, the code is wrong, or the VPN has no underlay internet.
* **Byte counters** — live up/down totals on both sides.

## Ports

| Port | Role |
|------|------|
| 8080 | Phone HTTP proxy (hotspot / USB) |
| 1080 | Phone SOCKS5 |
| 7777 | Control API + pairing |
| 18080 | Desktop local proxy (Windows System Proxy) |

## Project layout

| Folder | Contents |
|--------|----------|
| `mobile/` | Android app — Kotlin + Jetpack Compose |
| `desktop/` | Windows app — Electron + React + TypeScript |
| `docs/` | Architecture, LAN protocol, deployment, testing |
| `tools/` | Automated tests (smoke + contract) |

## Tests

```bash
npm test
```

Typecheck, build, smoke test (real `ProxyChain` against a fake phone), and a
38-check Kotlin ↔ TypeScript contract test — no phone and no Android Studio needed.

## Troubleshooting

| Symptom | Meaning | Action |
|---------|---------|--------|
| "Phone internet unreachable" | VPN tunnel without underlay | Turn VPN off or check mobile data |
| "System link: none" | Neither hotspot nor USB on | Turn one on |
| "VPN not active" | Share works but traffic skips tunnel | Enable VPN in All-apps mode |
| Connected clients empty | Requests never reach the phone | Check phone IP and Windows firewall |

Full table → [docs/deployment.md](docs/deployment.md)

## Documentation

- [Architecture](docs/architecture.md)
- [LAN protocol](docs/protocol.md)
- [Deployment and troubleshooting](docs/deployment.md)
- [Running guide](docs/running.md)
- [Testing guide](docs/testing.md)

## Limitations

* System Proxy covers browsers and most Windows apps — same as PdaNet
* Raw UDP (some games) is not supported yet (system TUN later)
* Keep the VPN app in **All-apps** mode

## License

Released under the **MIT License** — full text in [`LICENSE`](LICENSE).

Free to use, copy, modify, publish and distribute, as long as the copyright notice
and the license text stay with the software. Provided "as is", without warranty.

---

# NetBridge

اینترنت گوشی را با گوشی به اشتراک بگذارید — طوری که **VPN گوشی روی اتصال اشتراکی
هم اعمال شود.** مثل PdaNet، ولی رایگان، متن‌باز و تحت کنترل شما.

رابط کاربری: فارسی (راست‌به‌چپ) روی گوشی؛ انگلیسی و فارسی روی دسکتاپ.

**نیازمندی‌ها:** Windows 10/11 · Android 8+ · Node.js 20+ (فقط برای ساخت دسکتاپ)

## شروع سریع

### اندروید

APK آماده را از همین ریپو نصب کنید (بدون نیاز به ساخت):

```
https://github.com/MohammadMehdiSadeghi/NetBridge/raw/main/NetBridge.apk
```

یا از صفحهٔ اصلی ریپو → فایل `NetBridge.apk` → **Download**. با هر به‌روزرسانی همین
فایل جایگزین می‌شود و لینک ثابت می‌ماند.

### ویندوز

نصبی را خودتان بسازید (نیاز به [Node.js 20+](https://nodejs.org)):

```bash
git clone https://github.com/MohammadMehdiSadeghi/NetBridge.git
cd NetBridge
npm install --prefix desktop
npm run desktop:dist
```

خروجی: `desktop/release/*.exe`

اجرای توسعه (بدون نصبی): `npm run desktop:dev`

### اتصال

1. VPN را روی گوشی روشن کنید (حالت All-apps)
2. برنامه NetBridge → **Share**
3. یکی از مسیرها را انتخاب کنید:
   - **هاست‌اسپات** — معمولاً IP گوشی `192.168.43.1`
   - **USB** — کابل را وصل کنید و **USB tethering** را روشن کنید → معمولاً `192.168.42.129`
4. برنامه دسکتاپ → پیدا کردن گوشی → کد ۶ رقمی → **Connect**
5. تمام. System Proxy ویندوز روشن است.

> **USB:** فقط وصل کردن کابل کافی نیست. تا **USB tethering** روشن نشود، رایانه به
> پروکسی گوشی راه ندارد.

> **ویندوز می‌گوید اینترنت نیست؟** برای همان آداپتور **Metered connection** را خاموش
> کنید؛ برخی نسخه‌ها روی لینک‌های metered پروکسی را نادیده می‌گیرند.

## چرا NetBridge؟

به‌صورت پیش‌فرض، ترافیک رایانه بعد از اشتراک هاست‌اسپات/USB **از کنار VPN گوشی
رد می‌شود** و از تونل خارج می‌شود. ابزارهای موجود یا پولی بودند، یا بسته، یا VPN
را روی لینک اشتراکی اعمال نمی‌کردند. NetBridge یک پل پروکسی می‌سازد تا خروجی،
ترافیک خود گوشی باشد — یعنی اتصال اشتراکی شامل VPN می‌شود. بدون روت، بدون سرور،
بدون تغییر برنامهٔ VPN.

```
مرورگر رایانه → System Proxy ویندوز → پروکسی گوشی → VPN گوشی → اینترنت
```

## برنامه چه کاری انجام می‌دهد؟

* **برنامهٔ گوشی (اندروید)** — Kotlin + Jetpack Compose، فارسی/RTL. وضعیت VPN را
  تشخیص می‌دهد (روشن / روشن بدون اینترنت گوشی / خاموش)، پروکسی HTTP + SOCKS5 و
  API جفت‌سازی را بالا می‌آورد.
* **برنامهٔ دسکتاپ (ویندوز)** — Electron + React + TypeScript. گوشی را در شبکه
  پیدا می‌کند، با کد ۶ رقمی جفت می‌شود و System Proxy ویندوز را تنظیم می‌کند.
* **خروجی آگاه از VPN** — سوکت‌های خروجی به شبکهٔ تونل bind می‌شوند تا ترافیک واقعاً
  از VPN رد شود (`Interfaces.preferredRoute()`).
* **جفت‌سازی** — اول پورت کنترل `7777` بررسی می‌شود؛ خطاهای روشن اگر گوشی قابل
  دسترسی نباشد، کد اشتباه باشد، یا VPN اینترنت پایه نداشته باشد.
* **شمارندهٔ بایت** — مجموع آپلود/دانلود زنده در هر دو سمت.

## پورت‌ها

| پورت | نقش |
|------|-----|
| 8080 | پروکسی HTTP گوشی (هاست‌اسپات / USB) |
| 1080 | SOCKS5 گوشی |
| 7777 | API کنترل + جفت‌سازی |
| 18080 | پروکسی محلی دسکتاپ (System Proxy ویندوز) |

## ساختار پروژه

| پوشه | محتوا |
|------|-------|
| `mobile/` | اپ اندروید — Kotlin + Jetpack Compose |
| `desktop/` | اپ ویندوز — Electron + React + TypeScript |
| `docs/` | معماری، پروتکل LAN، استقرار، تست |
| `tools/` | تست‌های خودکار (smoke + contract) |

## تست‌ها

```bash
npm test
```

تایپ‌چک، بیلد، تست دود (کلاس واقعی `ProxyChain` مقابل گوشی ساختگی) و ۳۸ بررسی
قرارداد Kotlin ↔ TypeScript — بدون گوشی و بدون Android Studio.

## عیب‌یابی

| علامت | معنی | اقدام |
|-------|------|-------|
| "Phone internet unreachable" | تونل VPN بدون اینترنت پایه | VPN را خاموش کنید یا دیتا را چک کنید |
| "System link: none" | نه هاست‌اسپات و نه USB روشن است | یکی را روشن کنید |
| "VPN not active" | اشتراک کار می‌کند ولی ترافیک از کنار تونل می‌رود | VPN را در حالت All-apps فعال کنید |
| لیست کلاینت‌ها خالی | درخواست‌ها به گوشی نمی‌رسند | IP گوشی و فایروال ویندوز را چک کنید |

جدول کامل → [docs/deployment.md](docs/deployment.md)

## مستندات

- [معماری](docs/architecture.md)
- [پروتکل LAN](docs/protocol.md)
- [استقرار و عیب‌یابی](docs/deployment.md)
- [راهنمای اجرا](docs/running.md)
- [راهنمای تست](docs/testing.md)

## محدودیت‌ها

* پوشش System Proxy: مرورگرها و بیشتر برنامه‌های ویندوز — مثل PdaNet
* ترافیک UDP خام (بعضی بازی‌ها): فعلاً پشتیبانی نمی‌شود (TON سیستمی بعداً)
* برنامهٔ VPN را در حالت **All-apps** نگه دارید

## مجوز

این پروژه تحت **مجوز MIT** منتشر شده است. متن کامل در [`LICENSE`](LICENSE).

استفاده، کپی، تغییر، انتشار و توزیع آزاد است؛ مشروط بر اینکه اطلاعیه کپی‌رایت و
متن مجوز همراه نرم‌افزار باقی بماند. این نرم‌افزار **«همان‌طور که هست»** ارائه
می‌شود و هیچ ضمانتی ندارد.
