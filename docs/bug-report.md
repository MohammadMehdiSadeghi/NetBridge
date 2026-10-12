# گزارش باگ‌های NetBridge

تاریخ بازبینی: ۲۰۲۶/۱۰/۱۲ · دامنه: کل ریپازیتوری (desktop + mobile + tools + CI)

## خلاصه اجرایی

بازبینی استاتیک کل کدبیس انجام شد. علاوه بر آن، تست‌های خودِ پروژه واقعاً اجرا شدند:

| بررسی | نتیجه |
|---|---|
| `tsc -p tsconfig.node.json` (main + preload) | ✅ بدون خطا |
| `tsc -p tsconfig.web.json` (renderer) | ✅ بدون خطا |
| `tools/contract-test.mjs` (قرارداد Kotlin ↔ TS) | ✅ ۴۴ تست پاس |
| `tools/smoke-test.mjs` (مسیر واقعی ترافیک) | ✅ ۹ تست پاس |
| `electron-vite build` | ⚠️ قابل اجرا نبود — پکیج پلتفرمی `@esbuild/win32-x64` روی این ماشین نصب نشد (مشکل محیط، نه باگ پروژه) |
| کامپایل Kotlin/APK | ⚠️ قابل اجرا نبود — JDK 17 و Android SDK موجود نیست (همان‌طور که خود `docs/testing.md` اعتراف می‌کند) |

**نتیجه مهم:** هیچ‌کدام از باگ‌های زیر توسط تست‌های موجود پوشش داده نمی‌شوند. یعنی وضعیت «سبز» تست‌ها به معنی سالم بودن محصول نیست.

مجموع: **۲۰ باگ** (۵ بحرانی/بالا، ۸ متوسط، ۷ مورد پایین/نظافتی).

---

## جدول خلاصه

| # | شدت | عنوان | محل |
|---|---|---|---|
| B1 | 🔴 بحرانی | بازیابی System Proxy همیشه `ProxyEnable=0` می‌نویسد | `desktop/src/main/systemProxy.ts:70` |
| B2 | 🔴 بحرانی | تشخیص اشتباه آدرس‌ها و ترنسپورت وقتی گوشی به وای‌فای وصل است | `mobile/.../net/Interfaces.kt:22,109` · `NetworkMonitor.kt:96` |
| B3 | 🔴 بحرانی | `internetReachable` هرگز با VPN روشن `false` نمی‌شود → هشدار `no_internet` غیرقابل‌دستیابی | `NetworkMonitor.kt:114` · `Interfaces.kt:235` |
| B4 | 🟠 بالا | کلید «Auto System Proxy» هم‌گام نیست و عملاً خاموش‌شدنی نیست | `ipc.ts:531` · `App.tsx:637` |
| B5 | 🟠 بالا | «توقف اشتراک» تونل‌های در جریان را نمی‌بندد | `HttpProxyServer.kt:109` · `Socks5Server.kt:95` |
| B6 | 🟡 متوسط | پاسخ ۵۰۰ پشتیبانِ Control API هرگز ارسال نمی‌شود | `ControlApiServer.kt:143` |
| B7 | 🟡 متوسط | `sharing` بعد از مرگ پروکسی پاک نمی‌شود → `/start` دروغ می‌گوید `ok:true` | `ShareManager.kt:220,277` |
| B8 | 🟡 متوسط | شمارنده‌های بایت به‌خاطر race گم می‌شوند | `ShareManager.kt:374` |
| B9 | 🟡 متوسط | ثبت mDNS/NSD روی چرخش صفحه (rotation) نشت می‌کند | `MainActivity.kt:37,46` · `NsdAdvertiser.kt:35` |
| B10 | 🟡 متوسط | `ShareService` در هر `onStartCommand` یک collector جدید می‌سازد | `ShareService.kt:53` |
| B11 | 🟡 متوسط | CI ویندوز همیشه fail می‌شود (الگوی مسیر فایل با نام واقعی نمی‌خواند) | `.github/workflows/desktop.yml:66` |
| B12 | 🟡 متوسط | بدنهٔ chunked در درخواست HTTP بی‌صدا حذف می‌شود | `HttpProxyServer.kt:214` |
| B13 | 🟡 متوسط | هدر `Connection` تکراری به upstream فرستاده می‌شود | `HttpProxyServer.kt:212` |
| B14 | ⚪ پایین | کد مرده‌ای که مستندات را نقض می‌کند (مسیر egress پروکسی) | `Interfaces.kt:251` · `HttpProxyServer.kt:127` |
| B15 | ⚪ پایین | CSP باعث بلاک شدن React Fast Refresh در حالت dev می‌شود | `renderer/index.html:7` |
| B16 | ⚪ پایین | `Socks5Server.start()` شکست را گزارش نمی‌کند و با `stop()` مسابقه دارد | `Socks5Server.kt:48` |
| B17 | ⚪ پایین | SOCKS5 لیست متدهای کلاینت را نادیده می‌گیرد | `Socks5Server.kt:138` |
| B18 | ⚪ پایین | لینک‌های دانلود README مرده‌اند + APK دیباگ در ریپو کامیت می‌شود | `README.md:65` · `android.yml:48` |
| B19 | ⚪ پایین | `ShareManager.init()` دوباره صدا زده می‌شود و state زنده را پاک می‌کند | `ShareManager.kt:80` · `BootReceiver.kt:20` |
| B20 | ⚪ پایین | توکن و کد جفت‌سازی به‌صورت متن ساده ذخیره می‌شوند + `allowBackup=true` | `store.ts:43` · `AndroidManifest.xml:17` |

---

## وضعیت رفع

همهٔ ۲۰ مورد رفع شد. برای هر رفع، یک تست رگرسیون در `tools/contract-test.mjs` اضافه شد (۴۴ → ۶۰ بررسی) تا این باگ‌ها دوباره برنگردند.

| # | وضعیت | فایل(های) تغییر‌یافته |
|---|---|---|
| B1 | ✅ رفع شد | `desktop/src/main/systemProxy.ts` |
| B2 | ✅ رفع شد | `mobile/.../net/Interfaces.kt`, `NetworkMonitor.kt` |
| B3 | ✅ رفع شد | `mobile/.../net/NetworkMonitor.kt`, `Interfaces.kt`, `share/ShareManager.kt` |
| B4 | ✅ رفع شد | `desktop/src/main/ipc.ts`, `renderer/src/hooks/useNetBridge.ts`, `renderer/src/App.tsx` |
| B5 | ✅ رفع شد | `mobile/.../share/HttpProxyServer.kt`, `Socks5Server.kt` |
| B6 | ✅ رفع شد | `mobile/.../share/ControlApiServer.kt` |
| B7 | ✅ رفع شد | `mobile/.../share/ShareManager.kt`, `HttpProxyServer.kt`, `Socks5Server.kt` |
| B8 | ✅ رفع شد | `mobile/.../share/ShareManager.kt` |
| B9 | ✅ رفع شد | `mobile/.../net/NsdAdvertiser.kt`, `MainActivity.kt` |
| B10 | ✅ رفع شد | `mobile/.../share/ShareService.kt` |
| B11 | ✅ رفع شد | `.github/workflows/desktop.yml` |
| B12 | ✅ رفع شد | `mobile/.../share/HttpProxyServer.kt` |
| B13 | ✅ رفع شد | `mobile/.../share/HttpProxyServer.kt` |
| B14 | ✅ رفع شد | `Interfaces.kt`, `NetworkMonitor.kt`, `HttpProxyServer.kt`, `Socks5Server.kt`, `ShareManager.kt`, `res/values*/strings.xml`, `docs/architecture.md`, `docs/testing.md` |
| B15 | ✅ رفع شد | `desktop/src/renderer/index.html`, `desktop/electron.vite.config.ts` |
| B16 | ✅ رفع شد | `mobile/.../share/Socks5Server.kt` |
| B17 | ✅ رفع شد | `mobile/.../share/Socks5Server.kt` |
| B18 | ✅ رفع شد | `.github/workflows/desktop.yml`, `android.yml`, `README.md` |
| B19 | ✅ رفع شد | `mobile/.../share/ShareManager.kt` |
| B20 | ✅ رفع شد | `desktop/src/main/store.ts`, `mobile/app/src/main/AndroidManifest.xml` |

### تأیید پس از رفع

| بررسی | نتیجه |
|---|---|
| `tsc -p tsconfig.node.json` (main + preload + vite config) | ✅ بدون خطا |
| `tsc -p tsconfig.web.json` (renderer) | ✅ بدون خطا |
| `tools/contract-test.mjs` | ✅ ۶۰ پاس، ۰ خطا |
| `tools/smoke-test.mjs` | ✅ ۹ پاس، ۰ خطا |
| بررسی توازن بلوک‌ها در ۲۲ فایل Kotlin | ✅ همه متوازن |
| کامپایل Kotlin / APK | ⚠️ هنوز روی این ماشین ممکن نیست (JDK 17 و Android SDK موجود نیست) — باید یک‌بار در Android Studio ساخته شود |
| `electron-vite build` | ⚠️ روی این ماشین ممکن نیست (پکیج پلتفرمی `@esbuild/win32-x64` نصب نشد) |

### نکات باقی‌مانده که نیاز به تأیید روی سخت‌افزار واقعی دارند

1. **B3** — آستانهٔ `NET_CAPABILITY_VALIDATED` روی بعضی ROMها چند ثانیه بعد از اتصال ست می‌شود؛ ممکن است برای چند لحظه هشدار `no_internet` دیده شود. heartbeat هر ۳ ثانیه خودش را اصلاح می‌کند، ولی اگر روی گوشی خودت لرزش (flapping) دیدی، باید یک شرط تعدیل (مثلاً «اگر WAN وجود دارد ولی validate نشده، تا ۱۰ ثانیه صبر کن») اضافه شود.
2. **B2** — قاعدهٔ جدید فرض می‌کند اینترفیس هات‌اسپات هرگز `wlan0` نیست. اگر روی ROM خاصی این‌طور بود، فقط برچسب `transport` اشتباه می‌شود (لیست آدرس‌ها به‌خاطر fallback سالم می‌ماند).
3. **B20** — توکن روی ویندوز با DPAPI رمز می‌شود؛ اگر فایل تنظیمات به کاربر/ماشین دیگری منتقل شود، رمزگشایی شکست می‌خورد و اپ دوباره کد ۶ رقمی می‌خواهد (رفتار عمدی و مستندشده در کد).
4. **B11/B18** — تغییرات CI قابل تست محلی نبودند؛ منطق glob و trigger با تست رگرسیون جدید بررسی می‌شود، ولی اولین اجرای واقعی روی GitHub Actions باید دیده شود.

---

## 🔴 بحرانی / بالا

### B1 — بازیابی System Proxy همیشه `ProxyEnable = 0` می‌نویسد

**فایل:** `desktop/src/main/systemProxy.ts:70`

```ts
const script = `${PS_PRELUDE}
    Set-ItemProperty -Path $path -Name ProxyEnable -Value $([int]([bool]$previous.enabled)) -Type DWord
```

`$previous` یک شیء جاوااسکریپت است، نه متغیر PowerShell. داخل template literal فقط `${...}` توسط JS جایگزین می‌شود؛ عبارت `$([int]([bool]$previous.enabled))` عیناً به‌عنوان متن به PowerShell می‌رود و چون `$previous` آنجا تعریف نشده، مقدارش `$null` است:

```
$null → [bool]$null = $False → [int]$False = 0
```

**نتیجه:** در `clearSystemProxy` فیلدهای `ProxyServer` و `AutoConfigURL` درست بازیابی می‌شوند، ولی پرچم `ProxyEnable` **همیشه صفر** نوشته می‌شود. اگر کاربر قبل از اتصال پروکسی سیستمی خودش را روشن داشته باشد، پس از Disconnect پروکسی‌اش خاموش می‌شود. کل قرارداد «بازگرداندن وضعیت قبلی» نقض می‌شود.

**اصلاح:** مقدار را در JS محاسبه و به‌صورت عدد تزریق کنید:

```ts
const enabledInt = previous.enabled ? 1 : 0
// ...
Set-ItemProperty -Path $path -Name ProxyEnable -Value ${enabledInt} -Type DWord
```

> نکته: `readSystemProxy()` مقدار درست را می‌خواند و در `savedProxy` نگه می‌دارد؛ فقط مرحلهٔ نوشتن خراب است. به همین دلیل هم در تست‌ها دیده نمی‌شود — تست قرارداد فقط وجود رشتهٔ `AutoConfigURL` را چک می‌کند.

---

### B2 — تشخیص اشتباه «آدرس قابل‌اتصال» و «ترنسپورت» وقتی گوشی به وای‌فای وصل است

**فایل:** `mobile/app/src/main/java/com/netbridge/share/net/Interfaces.kt:22` و `NetworkMonitor.kt:96`

```kotlin
private val WIFI_TETHER_HINTS = listOf("ap", "swlan", "softap", "wlan1", "wlan")
```

پیشوند `"wlan"` هر اینترفیسی که با `wlan` شروع شود را «لینک هات‌اسپات» حساب می‌کند — از جمله `wlan0` که **اینترفیس کلاینت وای‌فای خودِ گوشی** است، نه هات‌اسپات.

دو اثر مستقیم:

1. **`shareAddresses()` آدرس اشتباه برمی‌گرداند** (`Interfaces.kt:109-122`): وقتی گوشی به وای‌فای خانه وصل است، IP همان شبکهٔ خانه (مثلاً `192.168.1.50`) در لیست `ips` قرار می‌گیرد. این لیست از طریق `/api/v1/status` به دسکتاپ می‌رود و دسکتاپ سعی می‌کند همان آدرس را dial کند → دقیقاً همان علامت «گوشی در دسترس نیست» که خود اپ سعی می‌کند تشخیص دهد.
2. **`transport` اشتباه گزارش می‌شود** (`NetworkMonitor.kt:96-98`): با روشن بودن وای‌فای کلاینت، مقدار `WIFI_HOTSPOT` برگردانده می‌شود در حالی که هیچ هات‌اسپاتی وجود ندارد. چک‌لیست UI و `transportLabel` هم بر همین اساس دروغ می‌گویند.

**اصلاح:** `"wlan"` را از `WIFI_TETHER_HINTS` حذف کنید و فقط الگوهای واقعی تترینگ (`ap`, `swlan`, `softap`, `wlan1`, `ap0`...) را نگه دارید؛ برای تشخیص هات‌اسپات به `ConnectivityManager` یا به وجود اینترفیس `ap*`/`swlan*` تکیه کنید، نه پیشوند عمومی `wlan`.

---

### B3 — `internetReachable` هرگز با VPN روشن `false` نمی‌شود

**فایل:** `mobile/.../net/NetworkMonitor.kt:114-118` و `mobile/.../net/Interfaces.kt:235-249`

```kotlin
private fun hasUplink(report: Interfaces.NetworkReport): Boolean {
    if (report.vpnActive) return true      // ← اینجا
    if (report.wanNetworks.isNotEmpty()) return true
    return false
}
```

همین منطق در `Interfaces.hasInternetUplink` تکرار شده است.

اما مستندات پروژه چیز دیگری می‌گویند:

- `docs/protocol.md:86` — «`internetReachable = false` معمولاً یعنی تونل بالاست ولی underlay پایین است… دسکتاپ باید بگوید **VPN را خاموش کن**»
- `docs/architecture.md` جدول: حالت «Tunnel is up but underlay is down → Turn VPN off»

با این پیاده‌سازی، تا وقتی `vpnActive` درست است، `internetReachable` **همیشه** `true` است. در نتیجه `buildWarning()` (`ShareManager.kt:161-166`) هرگز کد `"no_internet"` را در آن سناریو تولید نمی‌کند و آن ویژگی کلیدی — تفکیک «تونل بدون اینترنت» از «بدون VPN» — عملاً وجود ندارد.

بدتر: وقتی نه VPN هست و نه WAN، `internetReachable=false` می‌شود و کد `no_internet` صادر می‌شود (یعنی پیام «VPN را خاموش کن» برای حالتی که VPN روشن نیست) — پیام گمراه‌کننده.

**اصلاح:** وضعیت «VPN روشن» را به‌عنوان اثباتِ اینترنت حساب نکنید؛ فقط وجود یک WAN با `NET_CAPABILITY_VALIDATED` (یا `NET_CAPABILITY_INTERNET` + اعتبارسنجی موفق) را ملاک بگیرید. VPN بودنِ لایهٔ بالاتر نباید نبودِ underlay را بپوشاند.

---

### B4 — کلید «Auto System Proxy» هم‌گام نیست و عملاً خاموش‌شدنی نیست

**فایل:** `desktop/src/main/ipc.ts:531-549` + `desktop/src/renderer/src/App.tsx:637`

`DesktopState.systemProxyOn` یعنی «همین حالا اعمال شده است»، ولی UI آن را به‌عنوان «تنظیم ذخیره‌شده» استفاده می‌کند:

```tsx
onClick={() => void netbridge.setSystemProxy(!state.systemProxyOn)}
```

در سازندهٔ `AppController` (`ipc.ts:88`) مقدار `systemProxyOn: false` ثابت است و هیچ‌جا از `store.get().systemProxy` پر نمی‌شود — در حالی که مقدار پیش‌فرض آن در `store.ts:17` برابر `true` است.

زنجیرهٔ نتیجه:

1. اپ تازه باز می‌شود → تنظیم ذخیره‌شده `true` است، ولی کلید **خاموش** نشان داده می‌شود.
2. کاربر روی کلید می‌زند → `setSystemProxy(true)` صدا زده می‌شود → چون `enabled === true` و `state.connected === false`، هیچ `patch`ی رخ نمی‌دهد → کلید **همان‌طور خاموش می‌ماند**.
3. برای خاموش کردن واقعی، کاربر باید دو بار کلیک کند (بار اول true، بار دوم false).

**اصلاح:** یک فیلد جدا مثل `systemProxySetting` را در `DesktopState` بگذارید که آینهٔ `store.systemProxy` باشد و UI از آن استفاده کند؛ `systemProxyOn` را فقط برای نمایش وضعیت لحظه‌ای نگه دارید.

---

### B5 — «توقف اشتراک» تونل‌های در جریان را نمی‌بندد

**فایل:** `HttpProxyServer.kt:109-120` و `Socks5Server.kt:95-106`

```kotlin
fun stop() {
    if (!running.compareAndSet(true, false)) return
    try { serverSocket?.close() } catch (_: Exception) {}
    serverSocket = null
    scope?.cancel()
    dispatcher?.close()
}
```

فقط سوکتِ listen بسته می‌شود. سوکت‌های کلاینت و upstream که همین حالا در حال انتقال داده هستند بسته نمی‌شوند، و `scope.cancel()` هم `input.read()` مسدودکننده را قطع نمی‌کند (cancellation در Kotlin روی IO مسدودکننده اثر ندارد).

**نتیجه:** بعد از زدن «توقف اشتراک»، ترافیکِ اتصال‌های بازِ ویندوز همچنان از پروکسی رد می‌شود و شمارنده‌ها بالا می‌رود. کلاس `ClientRegistry` هم کلاینت را باز می‌بیند. برای کاربر «خاموش کردم ولی قطع نشد».

**اصلاح:** مجموعهٔ سوکت‌های فعال را (مثل `ProxyChain.sockets`) نگه دارید و در `stop()` همه را `close()` کنید؛ یا از `SocketChannel` + `Selector` (NIO) استفاده کنید تا cancellation مؤثر باشد.

---

## 🟡 متوسط

### B6 — پاسخ ۵۰۰ پشتیبانِ Control API هرگز ارسال نمی‌شود

**فایل:** `ControlApiServer.kt:143-161`

```kotlin
try {
    socket.use { s -> ... }          // use در پایان، سوکت را می‌بندد
} catch (_: Exception) {
    try {
        socket.use { s -> ...500... }  // سوکت همین حالا بسته شده
    } catch (_: Exception) { }
}
```

`use` در Kotlin پس از خروج از بلوک (حتی با استثنا) سوکت را می‌بندد. پس در `catch`، `s.getOutputStream()` روی سوکت بسته استثنا می‌دهد و داخل `catch` داخلی بلعیده می‌شود. کامنت بالای کد می‌گوید «تا هندلر خراب شبیه گوشی مرده به‌نظر نرسد» — این هدف محقق نمی‌شود و کلاینت همچنان connection-بستهٔ بدون پاسخ می‌بیند.

**اصلاح:** بدنهٔ پردازش را از مدیریت سوکت جدا کنید: سوکت را با `use` ببندید ولی منطق را در یک تابع جدا با `try/catch` بنویسید و در `catch` **قبل از** بسته شدن سوکت پاسخ ۵۰۰ را بنویسید.

---

### B7 — `sharing` بعد از مرگ پروکسی پاک نمی‌شود؛ `/start` دروغ می‌گوید

**فایل:** `ShareManager.kt:277-279` و `ShareManager.kt:220`

```kotlin
is HttpProxyServer.Event.Error -> _state.value = _state.value.copy(error = ev.message)
```

وقتی accept-loop پروکسی HTTP خودش می‌میرد (همان حالتی که خودِ کد در `HttpProxyServer.kt:89-98` با دقت مدیریتش کرده)، `ShareManager` فقط `error` را ست می‌کند و `sharing` را `false` نمی‌کند. دو پیامد:

- `ControlApiServer` مسیر `/api/v1/start` مقدار `ShareManager.startSharing()` را صدا می‌زند که در خط ۲۲۰ (`if (_state.value.sharing) return true`) بلافاصله `true` برمی‌گرداند **بدون هیچ تلاشی برای rebind**. پس دسکتاپ `{"ok": true}` می‌گیرد در حالی که هیچ‌چیز روی پورت listen نمی‌کند — دقیقاً همان باگی که کامنت‌های `ControlApiServer.kt:228-231` ادعا می‌کنند رفع شده است.
- UI گوشی همچنان «اشتراک فعال است» نشان می‌دهد.

**اصلاح:** در شاخهٔ `Event.Error` مربوط به مرگ accept-loop، `sharing = false` هم ست کنید (یا `httpServer` را null کنید) و اجازه دهید `startSharing` واقعاً دوباره bind کند.

---

### B8 — شمارنده‌های بایت به‌خاطر race گم می‌شوند

**فایل:** `ShareManager.kt:374-379`

```kotlin
private fun addBytes(fromClient: Long, toClient: Long) {
    _state.value = _state.value.copy(
        bytesIn = _state.value.bytesIn + fromClient,
        bytesOut = _state.value.bytesOut + toClient
    )
}
```

این یک read-modify-write غیراتمی روی `MutableStateFlow.value` است و از چندین تردِ تونل (هر اتصال یک ترد) هم‌زمان صدا زده می‌شود. تحت بار، به‌روزرسانی‌ها روی هم می‌افتند و آمار کمتر از واقع نشان داده می‌شود.

**اصلاح:** از `AtomicLong` برای شمارنده‌ها استفاده کنید (مثل `ClientRegistry`) یا `_state.update { }` را با یک mutex ترکیب کنید.

---

### B9 — ثبت mDNS/NSD روی rotation نشت می‌کند

**فایل:** `MainActivity.kt:37,46-51` و `NsdAdvertiser.kt:35-58`

```kotlin
nsd = NsdAdvertiser(this).also { ShareManager.bindNsd(it) }   // در onCreate

override fun onDestroy() {
    if (isFinishing) nsd?.unregister()   // روی rotation اجرا نمی‌شود
    ...
}
```

`MainActivity` هیچ `android:configChanges` ندارد، پس هر چرخش صفحه Activity را نابود و بازمی‌سازد. در `onDestroy` چون `isFinishing == false` است unregister انجام نمی‌شود، ولی در `onCreate` بعدی یک `NsdAdvertiser` **جدید** ساخته و `registerService` دوباره صدا زده می‌شود. فلگ `registered` هم per-instance است و از ثبت قبلی بی‌خبر است.

**نتیجه:** ثبت تکراری/ناموفق سرویس `_netbridge._tcp` و در نتیجه پیدا نشدن گوشی توسط دسکتاپ.

**اصلاح:** `NsdAdvertiser` را در سطح `ShareManager` (یا یک سینگلتون) نگه دارید تا فقط یک‌بار ثبت شود، یا از `Application.registerActivityLifecycleCallbacks` / `ViewModel` برای مدیریت چرخهٔ عمر استفاده کنید.

---

### B10 — `ShareService` در هر `onStartCommand` یک collector جدید می‌سازد

**فایل:** `ShareService.kt:53-63`

هر بار که `ShareManager.startSharing(background = true)` اجرا شود، یک `startForegroundService` می‌رود و `onStartCommand` دوباره اجرا می‌شود؛ هر اجرا یک `scope.launch { ShareManager.state.collect { ... nm.notify(...) } }` جدید اضافه می‌کند. چون `StateFlow` هرگز complete نمی‌شود، این collectorها تا `onDestroy` زنده می‌مانند.

**نتیجه:** با چند بار روشن/خاموش کردن اشتراک، چند collector هم‌زمان روی یک `NOTIFICATION_ID` می‌نویسند (و در نسخه‌های بعدی می‌تواند به کار سنگین تبدیل شود).

**اصلاح:** یک `Job` برای collector نگه دارید و قبل از launch جدید، قبلی را `cancel()` کنید.

---

### B11 — CI ویندوز همیشه fail می‌شود (عدم تطابق نام فایل)

**فایل:** `.github/workflows/desktop.yml:59-68` + `desktop/package.json:42-50`

در `package.json`:

```json
"win": { "target": ["portable"] },
"portable": { "artifactName": "${productName}-Setup-${version}.${ext}" }
```

نام واقعی خروجی: `NetBridge-Setup-1.0.0.exe`

در workflow:

```yaml
- name: Upload installer artifact
  uses: actions/upload-artifact@v4
  with:
    path: desktop/release/NetBridge Setup *.exe     # ← فاصله به‌جای خط تیره
    if-no-files-found: error
```

الگو هیچ فایلی پیدا نمی‌کند و چون `if-no-files-found: error` است، job شکست می‌خورد. ضمناً نام استپ «Package NSIS installer» است ولی تارگت `portable` است.

**اصلاح:** یا `path: desktop/release/NetBridge-Setup-*.exe` و یا `artifactName` را هماهنگ کنید.

---

### B12 — بدنهٔ chunked در درخواست HTTP بی‌صدا حذف می‌شود

**فایل:** `HttpProxyServer.kt:203-226`

```kotlin
val contentLength = headerLines
    .firstOrNull { it.first.equals("content-length", true) }?.second?.toIntOrNull() ?: 0
val body = if (contentLength in 1..4_194_304) readFully(input, contentLength) else ByteArray(0)
```

هدرهای `Transfer-Encoding: chunked` در حلقهٔ کپی **حذف نمی‌شوند** (فقط `proxy-connection` و `proxy-authorization` حذف می‌شوند)، پس به upstream می‌روند؛ اما بدنه چون `Content-Length` وجود ندارد `ByteArray(0)` می‌شود.

**نتیجه:** upstream یک درخواست با `Transfer-Encoding: chunked` ولی بدون بدنه می‌گیرد → یا هنگ می‌کند یا ۴۰۰ برمی‌گرداند. هر آپلود chunked (بعضی APIها، `curl -T`، برخی وب‌اپ‌ها) از این مسیر می‌شکند.

**اصلاح:** یا بدنهٔ chunked را دیکد و دوباره انکد کنید، یا در این حالت درخواست را با ۵۰۱ رد کنید، یا حداقل هدر `Transfer-Encoding` را همراه با نبود بدنه به upstream نفرستید.

---

### B13 — هدر `Connection` تکراری به upstream می‌رود

**فایل:** `HttpProxyServer.kt:205-212`

هدرهای کلاینت عیناً کپی می‌شوند (از جمله `Connection: keep-alive`) و بعد خط `Connection: close` هم اضافه می‌شود:

```
Connection: keep-alive
Connection: close
```

هدرهای hop-by-hop (`Connection`, `Keep-Alive`, `TE`, `Upgrade`, `Transfer-Encoding`) طبق RFC 7230 باید توسط پروکسی حذف/بازنویسی شوند، نه تکرار. رفتار سرورهای مختلف در برابر هدر تکراری متفاوت است و می‌تواند باعث معلق ماندن اتصال شود.

**اصلاح:** قبل از افزودن، هر هدر `Connection`/hop-by-hop را حذف کنید.

---

## ⚪ پایین / نظافت کد

### B14 — کد مرده‌ای که مستندات را نقض می‌کند (مسیر egress پروکسی)

`docs/architecture.md` بخش «Proxy egress path» می‌گوید سوکت‌های خروجی با `Network.socketFactory` به بهترین مسیر (VPN) bind می‌شوند. در کد:

- `Interfaces.preferredRoute()` (`Interfaces.kt:251-256`) **همیشه `null` برمی‌گرداند** و کامنت خودش می‌گوید چرا.
- `HttpProxyServer.route()` و `Socks5Server.route()` تعریف شده‌اند ولی **هیچ‌جا صدا زده نمی‌شوند** (تأیید شده با grep)؛ `connectUpstream()` از `cachedRoute` استفاده نمی‌کند.
- در نتیجه `appContext` در هر دو کلاس پروکسی بی‌استفاده است.

بنابراین مستندات رفتاری را توصیف می‌کنند که وجود ندارد. (عملاً Android خودش سوکت را از تونل عبور می‌دهد، پس محصول کار می‌کند — ولی مستند و کد یکی نیستند و «تازه‌سازی مسیر در میانهٔ نشست» وجود ندارد.)

سایر کدهای مرده:

| مورد | محل |
|---|---|
| `isTetherUp`, `isShareLink`, `gatewayFor`, `localIpv4Addresses`, `hasInternetUplink` | `Interfaces.kt`, `NetworkMonitor.kt` |
| `NetworkReport.vpnNetworks`, `NetworkReport.vpnIsUplink` (محاسبه می‌شوند، خوانده نمی‌شوند) | `Interfaces.kt:206-230` |
| `Iface.prefixLength` (نوشته می‌شود، خوانده نمی‌شود) | `Interfaces.kt:32,56` |
| `Iface.flags` = `nif.index` (شمارهٔ اینترفیس، نه فلگ‌ها) → `isPointToPoint()` هرگز درست کار نمی‌کند | `Interfaces.kt:39,57` |
| `closeQuietly()` در هر دو کلاس پروکسی | `HttpProxyServer.kt:304`, `Socks5Server.kt:258` |
| رشته‌های بی‌استفاده `save`, `saved`, `step_ok`, `wrong_step_prefix` | `res/values/strings.xml` |

---

### B15 — CSP باعث بلاک شدن React Fast Refresh در حالت dev می‌شود

**فایل:** `desktop/src/renderer/index.html:5-8`

```html
content="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:"
```

`@vitejs/plugin-react` در حالت dev یک اسکریپت **inline** به `index.html` تزریق می‌کند (تأیید شده در `node_modules/@vitejs/plugin-react/dist/index.js:257-262` → `attrs: { type: "module" }, children: getPreambleCode(...)`). با `script-src 'self'` و بدون `'unsafe-inline'`، این اسکریپت توسط CSP بلاک می‌شود و Fast Refresh/HMR درست کار نمی‌کند (خطای CSP در کنسول، ری‌لود دستی لازم می‌شود).

**اصلاح:** CSP را فقط برای پروداکشن اعمال کنید (مثلاً با `session.defaultSession.webRequest.onHeadersReceived` در main و بر اساس `app.isPackaged`) یا `'unsafe-inline'` را فقط در dev اضافه کنید. توجه: در پروداکشن مشکلی ندارد چون اسکریپت inline وجود ندارد.

---

### B16 — `Socks5Server.start()` شکست را گزارش نمی‌کند و با `stop()` مسابقه دارد

**فایل:** `Socks5Server.kt:48-93`

برخلاف `HttpProxyServer.start(): Boolean`، این متد `Unit` برمی‌گرداند و `bind` را **داخل** کوروتین انجام می‌دهد:

```kotlin
sc.launch {
    val socket = ServerSocket()
    socket.bind(InetSocketAddress("0.0.0.0", port), 128)
    ...
}
```

پس `ShareManager.startSharing` نمی‌تواند بفهمد SOCKS بالا آمده یا نه (خط ۲۴۳-۲۴۶ فقط `try/catch` بی‌اثر دارد). همچنین اگر `stop()` بلافاصله بعد از `start()` صدا زده شود، `scope.cancel()` ممکن است کوروتین را قبل از `bind` بکشد و سوکت هرگز ساخته نشود.

**اصلاح:** مثل `HttpProxyServer`، bind را سنکرون انجام دهید و `Boolean` برگردانید.

---

### B17 — SOCKS5 لیست متدهای کلاینت را نادیده می‌گیرد

**فایل:** `Socks5Server.kt:134-140`

```kotlin
val nMethods = input.read()
if (nMethods <= 0) return
HttpProxyServer.readFully(input, nMethods)      // متدها خوانده و دور ریخته می‌شوند
output.write(byteArrayOf(0x05, 0x00))           // همیشه «بدون احراز هویت»
```

اگر کلاینت فقط `0x02` (username/password) پیشنهاد داده باشد، سرور باید `0x05 0xFF` (هیچ متد قابل‌قبولی نیست) برگرداند. اینجا همیشه `0x00` برگردانده می‌شود که نقض RFC 1928 است.

---

### B18 — لینک‌های دانلود README مرده‌اند + کامیت شدن APK دیباگ

- `README.md:65-73` به بخش Releases و فایل‌های `NetBridge-Android.apk` / `NetBridge-Windows.exe` ارجاع می‌دهد، ولی هیچ‌کدام از دو workflow هیچ Release‌ای نمی‌سازند — هر دو فقط `actions/upload-artifact` دارند. یعنی این لینک‌ها تا وقتی کسی دستی Release نسازد، به جایی وصل نیستند.
- `.github/workflows/android.yml:36-61` با `assembleDebug` یک APK **دیباگ** می‌سازد و آن را به‌عنوان `NetBridge.apk` در ریشهٔ ریپازیتوری کامیت می‌کند (`git add -f`). کامیت کردن بیلد آرتیفکت در ریپو، هم تاریخچه را سنگین می‌کند و هم APK دیباگ (با `android:debuggable`) را به کاربر نهایی تحویل می‌دهد.
- نام استپ در `desktop.yml:59` «NSIS installer» است ولی تارگت `portable` است و نام فایل `NetBridge-Setup-*.exe`؛ در حالی که README از `NetBridge-Windows.exe` حرف می‌زند.

---

### B19 — `ShareManager.init()` دوباره صدا زده می‌شود و state زنده را پاک می‌کند

**فایل:** `ShareManager.kt:80-101` + `BootReceiver.kt:20`

`init()` در انتها `_state.value = State(...)` می‌گذارد و کامنت `BootReceiver` می‌گوید «صدا زدن دوبارهٔ init بی‌ضرر است». ولی این کار **state زنده** را بازنشانی می‌کند: `sharing`, `clients`, `bytesIn`, `bytesOut` به مقادیر اولیه برمی‌گردند. اگر `Application` قبلاً اجرا شده و اشتراک فعال بوده، `BootReceiver` (یا هر مسیر دیگری که `init` را دوباره صدا بزند) وضعیت را پاک می‌کند.

**اصلاح:** `init()` را idempotent کنید (اگر قبلاً مقداردهی شده، فقط تنظیمات را merge کنید و state را بازنشانی نکنید).

---

### B20 — ذخیرهٔ متن‌سادهٔ توکن و کد جفت‌سازی + `allowBackup=true`

- `desktop/src/main/store.ts:43-48` — توکن دسترسی در `netbridge-settings.json` در `userData` به‌صورت **متن ساده** ذخیره می‌شود.
- `mobile/.../data/SettingsStore.kt` — `apiToken` و `pairingCode` در `SharedPreferences` ساده.
- `AndroidManifest.xml:17` — `android:allowBackup="true"`، پس این مقادیر در بکاپ خودکار اندروید (`adb backup` / Google backup) قرار می‌گیرند.
- پروتکل هم روی HTTP ساده روی LAN کار می‌کند (خود `docs/protocol.md:12` می‌گوید «No authentication» برای پروکسی). یعنی هر کسی روی همان هات‌اسپات می‌تواند از پروکسی به‌عنوان open proxy استفاده کند (این تصمیم طراحی مستندشده است، ولی باید آگاهانه باشد).

**پیشنهاد:** `allowBackup=false` (یا `dataExtractionRules`)، رمزنگاری توکن دسکتاپ با `safeStorage`، و/یا محدود کردن پروکسی به سابنتِ هات‌اسپات.

---

## اولویت پیشنهادی برای رفع

1. **B1** و **B3** — چون مستقیماً تجربهٔ «بعد از Disconnect اینترنتم رفت» و «VPN روشن است ولی چیزی رد نمی‌شود» را می‌سازند.
2. **B2** و **B7** — چون باعث می‌شوند دسکتاپ آدرس اشتباه dial کند یا «متصل» بگوید در حالی که نیست.
3. **B4** و **B5** — باگ‌های قابل‌مشاهده در UI.
4. **B11** — جلوی سبز شدن CI ویندوز را می‌گیرد؛ یک‌خطی است.
5. بقیه بر اساس بودجه.

## پیشنهاد تست (تا این‌ها دیگر برنگردند)

- تست واحد برای `systemProxy` که سمت PowerShell را با یک تابع mock بررسی کند و مطمئن شود مقدار `ProxyEnable` از `snapshot.enabled` می‌آید.
- تستی که `Interfaces.shareAddresses()` را با مجموعهٔ نام اینترفیس‌های شبیه‌سازی‌شده (`wlan0`, `rndis0`, `ap0`, `tun0`, `rmnet_data0`) اجرا کند و انتظار داشته باشد `wlan0` وقتی هات‌اسپات نیست در خروجی نباشد.
- تستی برای `buildWarning()` که سناریوی «VPN روشن + WAN بدون VALIDATED» را `no_internet` انتظار داشته باشد.
- یک تست قرارداد جدید که `artifactName` در `package.json` را با `path` در `desktop.yml` مقایسه کند (چون B11 در همان دستهٔ باگ‌هایی است که تست قرارداد برای پیدا کردنش ساخته شده بود).
