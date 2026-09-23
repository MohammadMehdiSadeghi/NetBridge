# راهنمای اجرا (Run)

دو اپ داریم و هر کدام روش اجرای خودش را دارد. این سند کوتاه و دقیق است.

---

## خلاصه یک‌خطی

| چه چیزی | دستور | پیش‌نیاز |
|---------|-------|----------|
| تست‌ها (همین حالا) | `npm test` | Node |
| اپ دسکتاپ (توسعه) | `npm run dev:desktop` | باینری Electron |
| اپ دسکتاپ (اجرای بیلد) | `npm run run:desktop` | باینری Electron + بیلد |
| ساخت `exe` | `npm run desktop:dist` | باینری Electron |
| اپ اندروید | Android Studio → Run | JDK 17 + Android SDK |

---

## ۱. تست‌ها — همین حالا کار می‌کند

```bash
npm test
```

هیچ پیش‌نیازی جز Node لازم نیست. شش مرحله اجرا می‌شود و در آخر صریح می‌گوید
چه چیزی تست **نشد** (تا سبز بودن تست را با «همه‌چیز کار می‌کند» اشتباه نگیرید).

---

## ۲. اپ دسکتاپ

### گام اول: باینری Electron

`npm install` باینری Electron را دانلود می‌کند، ولی روی بعضی شبکه‌ها این دانلود
نصفه رها می‌شود (GitHub یک ۳۰۲ می‌دهد و صفر بایت می‌فرستد). اگر
`desktop/node_modules/electron/dist/electron.exe` وجود ندارد، این را اجرا کنید:

```bash
cd desktop
ELECTRON_MIRROR="https://registry.npmmirror.com/-/binary/electron/" \
  node node_modules/electron/install.js
```

اگر این هم نصفه ماند (اتصال طولانی قطع می‌شود):

```bash
cd ..
node tools/fetch-electron.mjs      # دانلود تکه‌تکه با ادامه از محل قطع
```

اسکریپت `fetch-electron.mjs` فایل را در تکه‌های ۵۱۲ کیلوبایتی می‌گیرد و از جایی که
قطع شده ادامه می‌دهد، پس هر بار که قطع شد فقط همان را دوباره اجرا کنید تا تمام شود.

بعد از دانلود، این را چک کنید:

```bash
ls desktop/node_modules/electron/dist/electron.exe
```

### گام دوم: اجرا

```bash
npm run dev:desktop
```

این `electron-vite dev` را اجرا می‌کند: رابط React را با hot-reload بالا می‌آورد و
پنجره اپ را باز می‌کند.

اگر فقط می‌خواهید بیلد نهایی را اجرا کنید (بدون hot-reload):

```bash
npm run desktop:build
npm run run:desktop
```

### ساختن نصب‌کننده

```bash
npm run desktop:dist        # → desktop/release/NetBridge Setup x.x.x.exe
```

---

## ۳. اپ اندروید

روی این سیستم **نمی‌شود ساخت** (نه JDK 17، نه Android SDK). روی سیستمی که
Android Studio دارد:

1. Android Studio → **Open** → پوشه `mobile/`
2. صبر کنید Gradle Sync تمام شود
3. گوشی را با USB وصل کنید و USB Debugging را روشن کنید
4. **Run ▶** را بزنید

یا فقط APK بسازید:

```bash
cd mobile
gradlew.bat assembleDebug      # ویندوز
./gradlew assembleDebug        # لینوکس/مک
```

خروجی: `mobile/app/build/outputs/apk/debug/app-debug.apk`

سپس روی گوشی نصب کنید و مراحل `docs/testing.md` را برای تست واقعی برو.

---

## ۴. ترتیب درست برای تست واقعی

1. `npm test` — مطمئن شوید پایه سالم است
2. `npm run dev:desktop` — اپ دسکتاپ بالا بیاید
3. اپ اندروید را در Android Studio اجرا/نصب کنید
4. **VPN گوشی را روشن کنید** (حالت همه برنامه‌ها)
5. **هات‌اسپات یا USB tethering را روشن کنید**
6. در اپ گوشی دکمه اشتراک را بزنید
7. در اپ دسکتاپ: برگه دستگاه → جستجو → کد ۶ رقمی → جفت‌سازی
8. برگه اتصال → دکمه بزرگ
9. تأیید نهایی با `curl` طبق `docs/testing.md` گام ۴

---

## ۵. اگر اپ دسکتاپ پنجره باز نکرد

| نشانه | معنی | کار |
|-------|------|-----|
| `Electron failed to install correctly` | باینری نیست | گام اول بالا را اجرا کنید |
| پنجره باز می‌شود ولی سفید است | رابط بالا نیامده | `npm run desktop:build` و بعد `npm run run:desktop` |
| `EADDRINUSE` روی ۱۸۰۸۰ | پورت اشغال است | در تنظیمات اپ پورت را عوض کنید یا اپ قبلی را ببندید |
| گوشی پیدا نمی‌شود | mDNS روی هات‌اسپات برخی رام‌ها کار نمی‌کند | IP دستی بدهید (`192.168.42.129` یا `192.168.43.1`) |
