# پروتکل NetBridge — LAN

ارتباط بین اپ دسک‌تاپ و اپ اندروید روی شبکه محلی (هات‌اسپات یا USB tethering).

## ۱. پروکسی اشتراکی گوشی

| سرویس | پورت پیش‌فرض | آدرس |
|-------|--------------|------|
| HTTP Proxy | `8080` | `0.0.0.0` |
| SOCKS5 | `1080` | `0.0.0.0` |

- بدون احراز هویت (محافظت از طریق رمز هات‌اسپات WPA2)
- خروجی هر اتصال پروکسی، ترافیک خودِ گوشی است → از VPN فعال عبور می‌کند
- رزولوشن DNS سمت گوشی انجام می‌شود (از مسیر VPN)

### HTTP Proxy

- درخواست‌های `CONNECT host:port` (تunnel HTTPS)
- درخواست‌های `GET/POST http://host/path` (HTTP مطلق) با بازنویسی به origin-form
- پس از `200 Connection Established` یا پاسخ اولیه، لوله دوطرفه

### SOCKS5

- بدون احراز هویت (`05 00`)
- پشتیبانی از IPv4، دامنه و IPv6
- `CONNECT` فقط

## ۲. API کنترل (پورت 7777)

HTTP/1.1 + JSON روی `0.0.0.0:7777`

### جفت‌سازی

```
POST /api/v1/pair
Content-Type: application/json

{"code": "123456"}
```

پاسخ:

```json
{"token": "b3c1..."}
```

- کد ۶ رقمی در برگه «اشتراک‌گذاری» اپ گوشی نمایش داده می‌شود.
- توکن پس از جفت‌سازی دائمی است و در دسک‌تاپ ذخیره می‌شود.

### وضعیت (نیازمند توکن)

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

| فیلد | معنا |
|------|------|
| `internetReachable` | خودِ گوشی الان مسیر سالم به اینترنت دارد |
| `transport` | `USB_TETHER` / `WIFI_HOTSPOT` / `NONE` |
| `reachable` | اشتراک روشن است و آدرس قابل‌اتصالی وجود دارد |
| `ips` | فقط آدرس‌های قابل‌اتصال (بدون rmnet و TUN) |
| `warning` | کد پایدار `no_internet` / `no_vpn` / `no_transport` یا `null` — UI هر طرف متن را به زبان خودش می‌سازد |

> `internetReachable = false` معمولاً یعنی تونل VPN بالا آمده ولی شبکه زیرین آن
> قطع است. در این حالت پروکسی هیچ چیزی را فوروارد نمی‌کند و دسک‌تاپ باید به کاربر
> بگوید VPN را قطع کند — نه اینکه VPN را روشن کند.

### کلاینت‌ها (نیازمند توکن)

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

هر آدرس IP یک کلاینت است و همه سوکت‌های آن در یک رکورد جمع می‌شوند. کلاینتی که
۹۰ ثانیه خبری از آن نباشد و اتصال بازی نداشته باشد حذف می‌شود.

### شیر اشتراک

```
POST /api/v1/start     Authorization: Bearer <token>   → {"ok": true}
POST /api/v1/stop      Authorization: Bearer <token>   → {"ok": true}
POST /api/v1/recycle   Authorization: Bearer <token>   → {"code": "654321"}   # کد جدید
```

## ۳. کشف خودکار (mDNS)

- نام سرویس: `_netbridge._tcp.local`
- سرویس API کنترل را روی پورت `7777` اعلام می‌کند
- پس از کشف آدرس IP، دسک‌تاپ از `/status` پورت پروکسی را می‌گیرد

## ۴. System Proxy ویندوز

دسک‌تاپ یک پروکسی محلی روی `127.0.0.1:18080` بالا می‌آورد و registry زیر را ست می‌کند:

```
HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
  ProxyEnable = 1 (DWORD)
  ProxyServer = 127.0.0.1:18080 (SZ)
```

بدون نیاز به Administrator. هنگام قطع اتصال، مقادیر قبلی بازگردانی می‌شوند.

زنجیره:

```
مرورگر/برنامه → 127.0.0.1:18080 → پروکسی گوشی:8080 → VPN گوشی → اینترنت
```
