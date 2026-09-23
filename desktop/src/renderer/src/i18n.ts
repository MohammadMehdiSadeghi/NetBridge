export type Lang = 'fa' | 'en'

const fa = {
  appName: 'NetBridge',
  tagline: 'اشتراک با VPN',
  tabConnect: 'اتصال',
  tabDevice: 'دستگاه',
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
  errPhoneNotFound:
    'گوشی پیدا نشد — آن را انتخاب کنید یا IP را دستی وارد کنید',
  errPairFirst: 'ابتدا با کد ۶ رقمی گوشی جفت‌سازی کنید',
  errPhoneUnreachable: 'گوشی در دسترس نیست',
  errStartShareFailed: 'روشن کردن اشتراک گوشی ناموفق بود',
  errNoConnectableAddress:
    'گوشی اشتراک را روشن کرده ولی آدرس قابل‌اتصالی ندارد. هات‌اسپات یا USB tethering را روی گوشی روشن کنید.',
  errScanFailed: 'جستجوی گوشی ناموفق بود'
}

const en: typeof fa = {
  appName: 'NetBridge',
  tagline: 'Share with VPN',
  tabConnect: 'Connect',
  tabDevice: 'Device',
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
  errPhoneNotFound: 'Phone not found — select it or enter the IP manually',
  errPairFirst: 'Pair with the phone’s 6-digit code first',
  errPhoneUnreachable: 'Phone is unreachable',
  errStartShareFailed: 'Failed to start sharing on the phone',
  errNoConnectableAddress:
    'Sharing is on but the phone has no connectable address. Turn on hotspot or USB tethering.',
  errScanFailed: 'Phone scan failed'
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
  switch (code) {
    case 'select_phone_first':
      return d.errSelectPhone
    case 'pairing_failed':
      return withDetail(d.errPairingFailed)
    case 'phone_not_found':
      return d.errPhoneNotFound
    case 'pair_first':
      return d.errPairFirst
    case 'phone_unreachable':
      return withDetail(d.errPhoneUnreachable)
    case 'start_share_failed':
      return withDetail(d.errStartShareFailed)
    case 'no_connectable_address':
      return d.errNoConnectableAddress
    case 'scan_failed':
      return withDetail(d.errScanFailed)
    default:
      return error
  }
}
