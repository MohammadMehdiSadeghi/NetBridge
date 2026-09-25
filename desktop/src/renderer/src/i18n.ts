export type Lang = 'fa' | 'en'

const fa = {
  appName: 'NetBridge',
  tagline: 'اشتراک با VPN',
  tabConnect: 'اتصال',
  tabDevice: 'دستگاه',
  tabGuide: 'راهنما',
  tabSettings: 'تنظیمات',
  status: 'وضعیت',
  noPhone: 'بدون گوشی',
  connected: 'متصل',
  disconnected: 'قطع',
  homeTitle: 'اتصال سیستم',
  homeSubtitle: 'ترافیک ویندوز از VPN فعالِ گوشی عبور می‌کند',
  connecting: 'در حال اتصال…',
  disconnect: 'قطع اتصال',
  connect: 'اتصال',
  notPaired: 'هنوز جفت‌سازی نشده — به برگه «دستگاه» بروید',
  findPhoneFirst: 'ابتدا گوشی را پیدا کنید (برگه «دستگاه»)',
  phoneVpn: 'VPN گوشی',
  phoneShare: 'شیر گوشی',
  linkPath: 'راه اتصال',
  systemProxy: 'System Proxy',
  on: 'روشن',
  off: 'خاموش',
  active: 'فعال',
  inactive: 'غیرفعال',
  phoneNoInternet:
    'اینترنت خودِ گوشی در دسترس نیست. اگر VPN وصل است ولی گوشی اینترنت ندارد، VPN را قطع کنید یا اتصال داده را بررسی کنید — در این حالت پروکسی نمی‌تواند چیزی را فوروارد کند.',
  uploadPc: 'ارسال PC',
  downloadPc: 'دریافت PC',
  phoneClients: 'کلاینت‌های گوشی',
  chainConns: 'اتصال‌های زنجیره',
  clientsOnPhone: 'کلاینت‌های متصل به گوشی',
  activeCount: '%d فعال',
  noClientsYet:
    'هنوز سیستمی از طریق پروکسی گوشی ترافیک رد نکرده. اگر سیستم وصل است ولی اینجا خالی می‌ماند، یعنی درخواست‌ها به گوشی نمی‌رسند — آدرس گوشی را بررسی کنید.',
  connections: '%d اتصال',
  trafficPath: 'مسیر ترافیک',
  noRoot: 'بدون Root',
  windowsApps: 'برنامه‌های ویندوز',
  phonePort: 'گوشی',
  vpnPhone: 'VPN گوشی',
  internet: 'اینترنت',
  deviceTitle: 'دستگاه',
  deviceSubtitle: 'گوشی را در شبکه هات‌اسپات / USB پیدا و جفت‌سازی کنید',
  scanning: 'در حال جستجو…',
  scanPhone: 'جستجوی گوشی',
  foundPhones: 'گوشی‌های پیداشده',
  nothingFound:
    'چیزی پیدا نشد. مطمئن شوید PC به هات‌اسپات گوشی وصل است یا کابل USB وصل و «اشتراک اینترنت USB» روی گوشی روشن است، سپس IP دستی وارد کنید — برای USB معمولاً 192.168.42.129 و برای هات‌اسپات معمولاً 192.168.43.1.',
  manualIp: 'IP دستی، مثلاً 192.168.43.1',
  select: 'انتخاب',
  pairing: 'جفت‌سازی',
  pairingHint: 'کد ۶ رقمی را از برگه «اشتراک‌گذاری» اپ گوشی بخوانید.',
  phonePaired: 'گوشی جفت شده است',
  forget: 'فراموش کن',
  settingsSubtitle: 'رفتار اپ دسک‌تاپ',
  localProxyPort: 'پورت پروکسی محلی (System Proxy)',
  saved: 'ذخیره شد ✓',
  save: 'ذخیره',
  windowsConnectsTo: 'ویندوز به این آدرس وصل می‌شود:',
  autoSystemProxy: 'تنظیم خودکار System Proxy',
  autoSystemProxyDesc:
    'هنگام اتصال روشن و هنگام قطع، به حالت قبل برمی‌گردد (بدون Admin).',
  tips: 'نکته‌ها',
  tipVpn:
    'اپ VPN گوشی را در حالت «همه برنامه‌ها» بگذارید تا خروجی پروکسی هم از تونل رد شود.',
  tipUsb:
    'برای USB: کابل را وصل کنید و در تنظیمات گوشی «اشتراک اینترنت USB» را روشن کنید.',
  tipMetered:
    'اگر Windows تشخیص داد که اینترنت ندارد و پروکسی را نادیده گرفت، «متراکم بودن اتصال» را در تنظیمات شبکه همان اتصال خاموش کنید.',
  tipFirewall:
    'اگر گوشی پیدا نشد، IP دستی بدهید و فایروال ویندوز را برای اپ باز کنید.',
  versionLine: 'نسخه ۱.۰.۰ — پروژه NetBridge',
  transportHotspot: 'هات‌اسپات',
  transportNone: 'بدون اتصال',
  warnNoInternet:
    'اینترنت خودِ گوشی در دسترس نیست. اگر VPN وصل است ولی گوشی اینترنت ندارد، VPN را قطع کنید یا اتصال داده را بررسی کنید.',
  warnNoVpn:
    'VPN روی گوشی فعال نیست؛ اشتراک کار می‌کند ولی ترافیک از تونل رد نمی‌شود.',
  warnNoTransport:
    'هات‌اسپات یا USB tethering خاموش است؛ سیستم راهی برای اتصال به گوشی ندارد.',
  errSelectPhone: 'ابتدا گوشی را انتخاب کنید',
  errPairingFailed: 'جفت‌سازی ناموفق',
  errPairTimeout:
    'گوشی به پورت کنترل جواب نداد — IP درست را در برگه «دستگاه» انتخاب کنید و هات‌اسپات/USB را روشن بگذارید (معمولاً 192.168.43.1 یا 192.168.42.129).',
  errPairRefused: 'پورت کنترل گوشی بسته است — اپ گوشی را باز و دوباره جستجو کنید.',
  errPairUnreachable: 'به IP انتخابی گوشی دسترسی نیست — آدرس دیگری را انتخاب کنید یا IP دستی وارد کنید.',
  errInvalidCode: 'کد ۶ رقمی اشتباه است — کد روی صفحهٔ گوشی را دوباره بخوانید.',
  errPhoneNotFound:
    'گوشی پیدا نشد — آن را انتخاب کنید یا IP را دستی وارد کنید',
  errPairFirst: 'ابتدا با کد ۶ رقمی گوشی جفت‌سازی کنید',
  errPhoneUnreachable: 'گوشی در دسترس نیست',
  errStartShareFailed: 'روشن کردن اشتراک گوشی ناموفق بود',
  errPhoneProxyUnreachable:
    'پورت پروکسی گوشی باز نیست — در اپ گوشی «اشتراک‌گذاری» را روشن کنید و دوباره تلاش کنید.',
  errSystemProxyFailed:
    'تنظیم System Proxy ویندوز ناموفق بود — تنظیمات شبکه یا آنتی‌ویروس را بررسی کنید.',
  errNoConnectableAddress:
    'گوشی اشتراک را روشن کرده ولی آدرس قابل‌اتصالی ندارد. هات‌اسپات یا USB tethering را روی گوشی روشن کنید.',
  errScanFailed: 'جستجوی گوشی ناموفق بود',
  checklistTitle: 'چک‌لیست اتصال',
  checklistSubtitle: 'هر گام قرمز دقیقاً همان جایی است که اشتباه رفتید',
  stepPhone: '۱. گوشی انتخاب شده باشد',
  stepPhoneFail: 'اینجا را اشتباه رفتی: هنوز گوشی انتخاب نشده — برگه «دستگاه» → جستجو یا IP دستی.',
  stepPaired: '۲. با کد ۶ رقمی جفت‌سازی شده باشد',
  stepPairedFail: 'اینجا را اشتباه رفتی: جفت‌سازی نشده — کد ۶ رقمی گوشی را وارد کنید.',
  stepVpn: '۳. VPN گوشی روشن باشد',
  stepVpnFail: 'اینجا را اشتباه رفتی: VPN گوشی روشن نیست — در گوشی VPN را روشن کنید.',
  stepLink: '۴. راه اتصال (USB/هات‌اسپات) وصل باشد',
  stepLinkFail: 'اینجا را اشتباه رفتی: راه اتصال قطع است — هات‌اسپات یا USB tethering را روشن کنید.',
  stepShare: '۵. شیر اشتراک گوشی روشن باشد',
  stepShareFail: 'اینجا را اشتباه رفتی: اشتراک گوشی خاموش است — دکمه اتصال را بزنید تا روشن شود.',
  stepConnected: '۶. اتصال و System Proxy روشن باشند',
  stepConnectedFail: 'اینجا را اشتباه رفتی: هنوز وصل نشده — دکمه بزرگ اتصال را بزنید.',
  guideTitle: 'راهنمای اتصال',
  guideSubtitle: 'یکی از دو راه را انتخاب و مرحله‌به‌مرحله پیش بروید',
  guideUsb: 'اتصال با کابل USB',
  guideHotspot: 'اتصال با هات‌اسپات وای‌فای',
  guideUsb1: '۱. کابل USB را بین گوشی و رایانه وصل کنید.',
  guideUsb2: '۲. در تنظیمات گوشی «اشتراک اینترنت USB» را روشن کنید.',
  guideUsb3: '۳. VPN دلخواه را روشن کنید (حالت همه برنامه‌ها).',
  guideUsb4: '۴. اپ گوشی را باز و اشتراک را روشن بگذارید.',
  guideUsb5: '۵. IP معمول گوشی: 192.168.42.129',
  guideUsb6: '۶. اینجا: جستجو → کد ۶ رقمی → اتصال.',
  guideHotspot1: '۱. هات‌اسپات گوشی را با رمز WPA2 روشن کنید.',
  guideHotspot2: '۲. رایانه را به همان هات‌اسپات وصل کنید.',
  guideHotspot3: '۳. VPN دلخواه را روشن کنید (حالت همه برنامه‌ها).',
  guideHotspot4: '۴. اپ گوشی را باز و اشتراک را روشن بگذارید.',
  guideHotspot5: '۵. IP معمول گوشی: 192.168.43.1',
  guideHotspot6: '۶. اینجا: جستجو → کد ۶ رقمی → اتصال.',
  guideNote: 'فقط وصل‌کردن کابل کافی نیست؛ حتماً «اشتراک اینترنت USB» روشن شود.',
  builtBy: 'ساختهٔ Mohammad Mehdi Sadeghi',
  linkGithub: 'گیت‌هاب',
  linkLinkedin: 'لینکدین',
  githubUrl: 'https://github.com/MohammadMehdiSadeghi',
  linkedinUrl: 'https://www.linkedin.com/in/mohammad-mehdi-sadeghi',
  wrongPrefix: 'اینجا را اشتباه رفتی:',
  noAnswer: 'بدون پاسخ'
}

const en: typeof fa = {
  appName: 'NetBridge',
  tagline: 'Share with VPN',
  tabConnect: 'Connect',
  tabDevice: 'Device',
  tabGuide: 'Guide',
  tabSettings: 'Settings',
  status: 'Status',
  noPhone: 'No phone',
  connected: 'Connected',
  disconnected: 'Offline',
  homeTitle: 'System connection',
  homeSubtitle: 'Windows traffic goes through the phone’s active VPN',
  connecting: 'Connecting…',
  disconnect: 'Disconnect',
  connect: 'Connect',
  notPaired: 'Not paired yet — open the Device tab',
  findPhoneFirst: 'Find your phone first (Device tab)',
  phoneVpn: 'Phone VPN',
  phoneShare: 'Phone share',
  linkPath: 'Link path',
  systemProxy: 'System Proxy',
  on: 'On',
  off: 'Off',
  active: 'Active',
  inactive: 'Inactive',
  phoneNoInternet:
    'The phone itself has no internet. If the VPN is connected but the phone has no data, turn the VPN off or check mobile data — the proxy cannot forward anything in this state.',
  uploadPc: 'PC upload',
  downloadPc: 'PC download',
  phoneClients: 'Phone clients',
  chainConns: 'Chain connections',
  clientsOnPhone: 'Clients connected to phone',
  activeCount: '%d active',
  noClientsYet:
    'No system has sent traffic through the phone proxy yet. If the PC is connected but this is empty, requests are not reaching the phone — check the phone address.',
  connections: '%d connections',
  trafficPath: 'Traffic path',
  noRoot: 'No root',
  windowsApps: 'Windows apps',
  phonePort: 'Phone',
  vpnPhone: 'Phone VPN',
  internet: 'Internet',
  deviceTitle: 'Device',
  deviceSubtitle: 'Find and pair the phone on the hotspot / USB network',
  scanning: 'Scanning…',
  scanPhone: 'Scan for phone',
  foundPhones: 'Found phones',
  nothingFound:
    'Nothing found. Make sure the PC is on the phone’s hotspot, or USB is plugged in with USB tethering on, then enter a manual IP — usually 192.168.42.129 for USB and 192.168.43.1 for hotspot.',
  manualIp: 'Manual IP, e.g. 192.168.43.1',
  select: 'Select',
  pairing: 'Pair',
  pairingHint: 'Read the 6-digit code from the phone app’s Sharing tab.',
  phonePaired: 'Phone is paired',
  forget: 'Forget',
  settingsSubtitle: 'Desktop app behavior',
  localProxyPort: 'Local proxy port (System Proxy)',
  saved: 'Saved ✓',
  save: 'Save',
  windowsConnectsTo: 'Windows connects to:',
  autoSystemProxy: 'Auto System Proxy',
  autoSystemProxyDesc:
    'Turns on when connected and restores the previous state on disconnect (no Admin).',
  tips: 'Tips',
  tipVpn: 'Keep the phone VPN in “All apps” mode so proxy traffic goes through the tunnel.',
  tipUsb: 'For USB: plug the cable and enable USB tethering in phone settings.',
  tipMetered:
    'If Windows says there is no internet and ignores the proxy, turn off “Metered connection” for that network.',
  tipFirewall: 'If the phone is not found, enter a manual IP and allow the app through Windows Firewall.',
  versionLine: 'Version 1.0.0 — NetBridge project',
  transportHotspot: 'Hotspot',
  transportNone: 'No link',
  warnNoInternet:
    'The phone itself has no internet. If the VPN is connected but the phone has no data, turn the VPN off or check mobile data.',
  warnNoVpn:
    'VPN is not active on the phone; sharing works but traffic does not go through the tunnel.',
  warnNoTransport:
    'Hotspot or USB tethering is off; the PC has no way to connect.',
  errSelectPhone: 'Select a phone first',
  errPairingFailed: 'Pairing failed',
  errPairTimeout:
    'The phone did not answer on the control port — pick the phone IP again in the Device tab and keep hotspot/USB on (usually 192.168.43.1 or 192.168.42.129).',
  errPairRefused: 'The phone control port is closed — open the phone app and scan again.',
  errPairUnreachable: 'Cannot reach the selected phone IP — choose another address or enter it manually.',
  errInvalidCode: 'Wrong 6-digit code — read the code on the phone screen again.',
  errPhoneNotFound: 'Phone not found — select it or enter the IP manually',
  errPairFirst: 'Pair with the phone’s 6-digit code first',
  errPhoneUnreachable: 'Phone is unreachable',
  errStartShareFailed: 'Failed to start sharing on the phone',
  errPhoneProxyUnreachable:
    'The phone proxy port is closed — turn Sharing on in the phone app and try again.',
  errSystemProxyFailed:
    'Could not set the Windows System Proxy — check network settings or antivirus.',
  errNoConnectableAddress:
    'Sharing is on but the phone has no connectable address. Turn on hotspot or USB tethering.',
  errScanFailed: 'Phone scan failed',
  checklistTitle: 'Connection checklist',
  checklistSubtitle: 'Each red step is exactly where you went wrong',
  stepPhone: '1. A phone is selected',
  stepPhoneFail: 'You went wrong here: no phone selected — Device tab → scan or manual IP.',
  stepPaired: '2. Paired with the 6-digit code',
  stepPairedFail: 'You went wrong here: not paired — enter the phone’s 6-digit code.',
  stepVpn: '3. Phone VPN is on',
  stepVpnFail: 'You went wrong here: phone VPN is off — turn the VPN on in the phone app.',
  stepLink: '4. USB / hotspot link is up',
  stepLinkFail: 'You went wrong here: system link is down — turn on hotspot or USB tethering.',
  stepShare: '5. Phone sharing is on',
  stepShareFail: 'You went wrong here: phone sharing is off — press Connect to turn it on.',
  stepConnected: '6. Connected and System Proxy on',
  stepConnectedFail: 'You went wrong here: not connected yet — press the big Connect button.',
  guideTitle: 'Connection guide',
  guideSubtitle: 'Pick one of the two paths and follow the steps',
  guideUsb: 'USB cable',
  guideHotspot: 'Wi‑Fi hotspot',
  guideUsb1: '1. Plug the USB cable between phone and PC.',
  guideUsb2: '2. Turn on USB tethering in phone settings.',
  guideUsb3: '3. Turn on your VPN (All-apps mode).',
  guideUsb4: '4. Keep the phone app open with sharing on.',
  guideUsb5: '5. Typical phone IP: 192.168.42.129',
  guideUsb6: '6. Here: scan → 6-digit code → Connect.',
  guideHotspot1: '1. Turn on the phone hotspot (WPA2 password).',
  guideHotspot2: '2. Join that hotspot from the PC.',
  guideHotspot3: '3. Turn on your VPN (All-apps mode).',
  guideHotspot4: '4. Keep the phone app open with sharing on.',
  guideHotspot5: '5. Typical phone IP: 192.168.43.1',
  guideHotspot6: '6. Here: scan → 6-digit code → Connect.',
  guideNote: 'Plugging the cable alone is not enough — USB tethering must be ON.',
  builtBy: 'Built by Mohammad Mehdi Sadeghi',
  linkGithub: 'GitHub',
  linkLinkedin: 'LinkedIn',
  githubUrl: 'https://github.com/MohammadMehdiSadeghi',
  linkedinUrl: 'https://www.linkedin.com/in/mohammad-mehdi-sadeghi',
  wrongPrefix: 'You went wrong here:',
  noAnswer: 'No response'
}

function detect(): Lang {
  try {
    const saved = localStorage.getItem('netbridge_lang')
    if (saved === 'fa' || saved === 'en') return saved
  } catch {
    /* ignore */
  }
  const nav = navigator.language || 'en'
  return nav.toLowerCase().startsWith('fa') ? 'fa' : 'en'
}

let current: Lang = detect()

export function getLang(): Lang {
  return current
}

export function setLang(lang: Lang): void {
  current = lang
  try {
    localStorage.setItem('netbridge_lang', lang)
  } catch {
    /* ignore */
  }
  if (typeof document !== 'undefined') {
    document.documentElement.dir = lang === 'fa' ? 'rtl' : 'ltr'
    document.documentElement.lang = lang
  }
}

// Ensure document root direction is set on initial script execution
if (typeof document !== 'undefined') {
  document.documentElement.dir = current === 'fa' ? 'rtl' : 'ltr'
  document.documentElement.lang = current
}

export function t(): typeof fa {
  return current === 'fa' ? fa : en
}

export function isFa(): boolean {
  return current === 'fa'
}

export function transportLabel(transport: string): string {
  if (transport === 'USB_TETHER') return 'USB'
  if (transport === 'WIFI_HOTSPOT') return t().transportHotspot
  if (transport === 'NONE') return t().transportNone
  return transport
}

/** Resolve a stable warning code (or pass through free text) into the current locale. */
export function warningText(code: string | null): string | null {
  if (!code) return null
  const d = t()
  if (code === 'no_internet') return d.warnNoInternet
  if (code === 'no_vpn') return d.warnNoVpn
  if (code === 'no_transport') return d.warnNoTransport
  return code
}

/**
 * Resolve a main-process error. Codes are `name` or `name::detail`.
 * Unknown strings (network errors, etc.) are shown as-is.
 */
export function errorText(error: string): string {
  const sep = error.indexOf('::')
  const code = sep === -1 ? error : error.slice(0, sep)
  const detail = sep === -1 ? '' : error.slice(sep + 2)
  const d = t()
  const withDetail = (base: string): string => (detail ? `${base}: ${detail}` : base)
  const wrong = (base: string): string => `${d.wrongPrefix ?? 'اینجا را اشتباه رفتی:'} ${base}`
  switch (code) {
    case 'select_phone_first':
      return wrong(d.errSelectPhone)
    case 'pairing_failed': {
      if (/invalid code/i.test(detail)) return wrong(d.errInvalidCode)
      if (/refused/i.test(detail)) return wrong(d.errPairRefused)
      if (/no answer|abort|timeout/i.test(detail)) return wrong(d.errPairTimeout)
      if (/cannot reach|ENOTFOUND|EHOSTUNREACH|ENETUNREACH/i.test(detail)) {
        return wrong(d.errPairUnreachable)
      }
      return wrong(withDetail(d.errPairingFailed))
    }
    case 'phone_not_found':
      return wrong(d.errPhoneNotFound)
    case 'pair_first':
      return wrong(d.errPairFirst)
    case 'phone_unreachable':
      return wrong(withDetail(d.errPhoneUnreachable))
    case 'start_share_failed':
      return wrong(withDetail(d.errStartShareFailed))
    case 'phone_proxy_unreachable':
      return wrong(withDetail(d.errPhoneProxyUnreachable))
    case 'system_proxy_failed':
      return wrong(withDetail(d.errSystemProxyFailed))
    case 'no_connectable_address':
      return wrong(d.errNoConnectableAddress)
    case 'scan_failed':
      return wrong(withDetail(d.errScanFailed))
    default:
      return error
  }
}
