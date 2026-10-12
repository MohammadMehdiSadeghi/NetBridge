/**
 * Phone-side control-API contract test.
 *
 * The Android control API (port 7777) is a fixed HTTP/1.1 + JSON contract that the
 * desktop app depends on. We cannot run the Kotlin server here (no Android SDK on
 * this machine), but we CAN assert the contract itself: that the exact request
 * shapes the desktop sends are the shapes the docs promise, and that the response
 * fields the desktop reads are present.
 *
 * This catches the most likely cross-language breakage: a field renamed on one side
 * and not the other.
 *
 * Run:  node tools/contract-test.mjs
 */

import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

let pass = 0
let fail = 0

function check(name, fn) {
  try {
    fn()
    pass += 1
    console.log(`  \u2713 ${name}`)
  } catch (e) {
    fail += 1
    console.log(`  \u2717 ${name}\n      ${e.message}`)
  }
}

const root = path.resolve(import.meta.dirname, '..')
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8')

const kotlinApi = read('mobile/app/src/main/java/com/netbridge/share/share/ControlApiServer.kt')
const kotlinState = read('mobile/app/src/main/java/com/netbridge/share/share/ShareManager.kt')
const kotlinLabels = read('mobile/app/src/main/java/com/netbridge/share/ui/Labels.kt')
const desktopApi = read('desktop/src/main/phoneApi.ts')
const docs = read('docs/protocol.md')

console.log('\nNetBridge control-API contract test\n')

// ---- Endpoints exist on the phone side ------------------------------------
for (const route of [
  '/api/v1/pair',
  '/api/v1/status',
  '/api/v1/clients',
  '/api/v1/start',
  '/api/v1/stop',
  '/api/v1/recycle'
]) {
  check(`Kotlin server implements ${route}`, () => {
    assert.ok(kotlinApi.includes(route), `${route} not found in ControlApiServer.kt`)
  })
}

// ---- Every field the desktop reads is emitted by the phone ----------------
const statusFields = [
  'sharing',
  'vpnActive',
  'internetReachable',
  'transport',
  'transportLabel',
  'reachable',
  'clients',
  'bytesIn',
  'bytesOut',
  'ips',
  'httpPort',
  'socksPort',
  'controlPort',
  'code',
  'warning',
  'version'
]

for (const field of statusFields) {
  check(`/status emits "${field}"`, () => {
    assert.ok(
      kotlinApi.includes(`"${field}"`),
      `"${field}" is not written by the Kotlin status handler — the desktop reads it`
    )
  })
}

// ---- The desktop's TypeScript interface matches ---------------------------
check('desktop PhoneStatus declares every field the phone sends', () => {
  const iface = desktopApi.slice(
    desktopApi.indexOf('export interface PhoneStatus'),
    desktopApi.indexOf('const TIMEOUT_MS')
  )
  const missing = statusFields.filter((f) => !iface.includes(f))
  assert.deepEqual(missing, [], `missing from phoneApi.ts: ${missing.join(', ')}`)
})

// ---- Client record fields --------------------------------------------------
for (const field of ['address', 'connections', 'bytesUp', 'bytesDown', 'firstSeen', 'lastSeen']) {
  check(`/clients emits "${field}"`, () => {
    assert.ok(kotlinApi.includes(`"${field}"`), `"${field}" missing from clients handler`)
  })
}

// ---- Auth is enforced on everything except pairing -------------------------
check('pairing is the only unauthenticated endpoint', () => {
  const pairIdx = kotlinApi.indexOf('"/api/v1/pair"')
  const authIdx = kotlinApi.indexOf('if (token.isEmpty() || token != readToken())')
  assert.ok(pairIdx !== -1, 'could not locate the pair route')
  assert.ok(authIdx !== -1, 'could not locate the auth guard')
  assert.ok(pairIdx < authIdx, 'the auth guard runs before pairing, which would break pairing')
})

// ---- The failure states the desktop depends on are reachable ---------------
check('internetReachable and vpnActive are computed independently', () => {
  // Both must exist as separate state fields; collapsing them was the original bug.
  assert.ok(kotlinState.includes('val vpnActive: Boolean = false'))
  assert.ok(kotlinState.includes('val internetReachable: Boolean = false'))
})

check('a warning is produced for tunnel-with-no-uplink', () => {
  assert.ok(
    kotlinState.includes('"no_internet"'),
    'ShareManager does not emit the no_internet warning code'
  )
  assert.ok(
    kotlinLabels.includes('"no_internet"'),
    'phone UI does not resolve the no_internet warning code'
  )
  assert.ok(
    read('mobile/app/src/main/res/values-fa/strings.xml').includes('warn_no_internet'),
    'Persian locale missing the no-internet warning'
  )
  assert.ok(
    read('mobile/app/src/main/res/values/strings.xml').includes('warn_no_internet'),
    'default (English) locale missing the no-internet warning'
  )
})

check('a warning is produced for sharing-without-VPN', () => {
  assert.ok(
    kotlinState.includes('"no_vpn"'),
    'ShareManager does not emit the no_vpn warning code'
  )
  assert.ok(
    kotlinLabels.includes('"no_vpn"'),
    'phone UI does not resolve the no_vpn warning code'
  )
  assert.ok(
    read('mobile/app/src/main/res/values-fa/strings.xml').includes('warn_no_vpn'),
    'Persian locale missing the no-VPN warning'
  )
  assert.ok(
    read('mobile/app/src/main/res/values/strings.xml').includes('warn_no_vpn'),
    'default (English) locale missing the no-VPN warning'
  )
})

// ---- Docs agree with the implementation -----------------------------------
check('protocol.md documents /api/v1/clients', () => {
  assert.ok(docs.includes('/api/v1/clients'), 'docs/protocol.md does not document the clients endpoint')
})

check('protocol.md documents internetReachable', () => {
  assert.ok(docs.includes('internetReachable'), 'docs/protocol.md does not document internetReachable')
})

check('protocol.md documents the transport field', () => {
  assert.ok(docs.includes('transport'), 'docs/protocol.md does not document transport')
})

// ---- Bearer token format matches ------------------------------------------
check('desktop sends "Authorization: Bearer <token>"', () => {
  assert.ok(desktopApi.includes('Bearer ${token}'), 'desktop does not use a Bearer token')
})

check('phone strips the "Bearer " prefix', () => {
  assert.ok(kotlinApi.includes('removePrefix("Bearer ")'), 'phone does not parse the Bearer prefix')
})

// ---- Start must not lie: only ok when the HTTP proxy is actually listening ---
check('/start returns ok only after HttpProxyServer.start() succeeds', () => {
  assert.ok(
    kotlinApi.includes('val ok = ShareManager.startSharing'),
    'ControlApiServer /start no longer checks the startSharing return value'
  )
  assert.ok(
    kotlinApi.includes('if (ok)'),
    'ControlApiServer /start does not branch on the start result'
  )
})

check('HttpProxyServer.start() binds synchronously and returns Boolean', () => {
  const httpSrc = read('mobile/app/src/main/java/com/netbridge/share/share/HttpProxyServer.kt')
  assert.ok(
    httpSrc.includes('fun start(): Boolean'),
    'HttpProxyServer.start must return whether the port is listening'
  )
  assert.ok(
    httpSrc.includes('ss.bind(') && !/sc\.launch\s*\{\s*try\s*\{\s*val ss = ServerSocket\(\)/.test(httpSrc),
    'HttpProxyServer must bind before returning (no fire-and-forget bind)'
  )
})

check('ControlApiServer.start() binds synchronously and returns Boolean', () => {
  assert.ok(
    kotlinApi.includes('fun start(): Boolean'),
    'ControlApiServer.start must return whether the port is listening'
  )
})

check('desktop probes the phone HTTP proxy before claiming connected', () => {
  const ipc = read('desktop/src/main/ipc.ts')
  assert.ok(ipc.includes('probeHttp'), 'ipc.ts does not probe the phone data path')
  assert.ok(
    ipc.includes('phone_proxy_unreachable'),
    'ipc.ts does not fail when the phone proxy port is down'
  )
})

check('System Proxy apply clears a leftover PAC (AutoConfigURL)', () => {
  const sp = read('desktop/src/main/systemProxy.ts')
  assert.ok(
    sp.includes('AutoConfigURL'),
    'systemProxy.ts does not touch AutoConfigURL — a PAC would ignore ProxyServer'
  )
})

check('desktop clears the token on 401 so the user can re-pair', () => {
  const ipc = read('desktop/src/main/ipc.ts')
  assert.ok(
    ipc.includes("store.save({ token: '' })"),
    'ipc.ts never clears an invalid token — reconnect would loop forever'
  )
})

// ---------------------------------------------------------------------------
// Regressions found by the 2026-10 audit. Each one is a bug that shipped, so the
// assertion is written against the *broken* shape, not just the fixed one.
// ---------------------------------------------------------------------------

/** Comments legitimately quote the broken code; assertions must look at code only. */
const stripComments = (src) =>
  src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')

check('System Proxy restore computes ProxyEnable in JS, not in PowerShell', () => {
  const sp = stripComments(read('desktop/src/main/systemProxy.ts'))
  assert.ok(
    !sp.includes('$previous.enabled'),
    'clearSystemProxy emits $previous.enabled into the script; PowerShell has no such ' +
      'variable, so [int]([bool]$null) is always 0 and the user\'s own proxy is disabled'
  )
  assert.ok(
    sp.includes('previous.enabled ? 1 : 0'),
    'clearSystemProxy no longer computes the enable flag explicitly'
  )
})

check('desktop workflow uploads the path the portable target actually produces', () => {
  const pkg = JSON.parse(read('desktop/package.json'))
  const raw = pkg.build?.portable?.artifactName
  assert.ok(raw, 'portable.artifactName is not configured')
  const real = raw
    .replace('${productName}', pkg.build.productName)
    .replace('${version}', pkg.version)
    .replace('${ext}', 'exe')
  const glob = real.replace(pkg.version, '*')
  const wf = read('.github/workflows/desktop.yml')
  assert.ok(
    wf.includes(`desktop/release/${glob}`),
    `the workflow never references desktop/release/${glob} (artifactName resolves to ${real}), ` +
      'so upload-artifact finds nothing and fails with if-no-files-found: error'
  )
})

check('a tag push publishes a GitHub Release', () => {
  for (const wf of ['.github/workflows/desktop.yml', '.github/workflows/android.yml']) {
    const src = read(wf)
    assert.ok(/tags:\s*\[/.test(src), `${wf} does not run on tags, so Releases stay empty`)
    assert.ok(
      src.includes('softprops/action-gh-release'),
      `${wf} never creates a release — the README's download links would 404`
    )
  }
})

check('Interfaces.kt does not treat the Wi-Fi client link (wlan0) as a hotspot', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/net/Interfaces.kt')
  const hints = /WIFI_AP_HINTS = listOf\(([^)]*)\)/.exec(src)
  assert.ok(hints, 'could not locate WIFI_AP_HINTS')
  assert.ok(
    !/["']wlan["']/.test(hints[1]),
    'WIFI_AP_HINTS contains a bare "wlan" prefix, which matches wlan0 — the phone\'s own ' +
      'Wi-Fi client interface — and reports a hotspot that does not exist'
  )
  assert.ok(
    src.includes('WIFI_AP_WLAN'),
    'there is no explicit wlan<N> (N > 0) rule for hotspot interfaces'
  )
})

check('internetReachable comes from a validated WAN, not from "a VPN exists"', () => {
  const nm = read('mobile/app/src/main/java/com/netbridge/share/net/NetworkMonitor.kt')
  assert.ok(
    nm.includes('internetReachable = report.hasValidatedWan'),
    'NetworkMonitor no longer derives internetReachable from a validated WAN'
  )
  assert.ok(
    !/if \(report\.vpnActive\) return true/.test(nm),
    'NetworkMonitor still treats an active VPN as proof of internet, which hides the ' +
      'tunnel-with-a-dead-underlay state that the no_internet warning exists for'
  )
})

check('ShareManager clears `sharing` when the HTTP accept loop dies', () => {
  const sm = read('mobile/app/src/main/java/com/netbridge/share/share/ShareManager.kt')
  assert.ok(
    sm.includes('is HttpProxyServer.Event.Stopped'),
    'ShareManager ignores HttpProxyServer.Event.Stopped'
  )
  assert.ok(
    /Event\.Stopped ->[\s\S]{0,700}?sharing = false/.test(sm),
    'the Stopped branch does not set sharing = false, so /api/v1/start would answer ' +
      '{"ok": true} for a dead port and startSharing() would be a no-op'
  )
})

check('both proxies close in-flight tunnels when stopped', () => {
  for (const f of [
    'mobile/app/src/main/java/com/netbridge/share/share/HttpProxyServer.kt',
    'mobile/app/src/main/java/com/netbridge/share/share/Socks5Server.kt'
  ]) {
    const src = read(f)
    assert.ok(src.includes('closeLiveSockets()'), `${f} never closes live tunnel sockets`)
    assert.ok(
      /fun stop\(\)[\s\S]{0,500}?closeLiveSockets\(\)/.test(src),
      `${f}: stop() does not call closeLiveSockets(), so "stop sharing" keeps forwarding`
    )
  }
})

check('the HTTP proxy understands chunked bodies and strips hop-by-hop headers', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/share/HttpProxyServer.kt')
  assert.ok(
    src.includes('readChunkedBody'),
    'HttpProxyServer does not decode Transfer-Encoding: chunked, so such a request is ' +
      'forwarded with an empty body and the upstream hangs or answers 400'
  )
  assert.ok(
    src.includes('STRIPPED_HEADERS'),
    'HttpProxyServer forwards hop-by-hop headers verbatim, producing a duplicate Connection header'
  )
})

check('SOCKS5 only selects a method the client actually offered', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/share/Socks5Server.kt')
  assert.ok(
    /methods\.contains\(0x00\.toByte\(\)\)/.test(src),
    'Socks5Server does not inspect the offered method list (RFC 1928 §3)'
  )
  assert.ok(
    src.includes('0xFF.toByte()'),
    'Socks5Server never answers 0xFF when no offered method is acceptable'
  )
})

check('SOCKS5 start() binds synchronously and reports success', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/share/Socks5Server.kt')
  assert.ok(
    src.includes('fun start(): Boolean'),
    'Socks5Server.start must return whether the port is listening'
  )
  assert.ok(
    !/sc\.launch\s*\{\s*try\s*\{\s*val ss = ServerSocket\(\)/.test(src),
    'Socks5Server still binds inside a coroutine, so start() cannot report a failure'
  )
})

check('NsdAdvertiser is a process-wide singleton', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/net/NsdAdvertiser.kt')
  assert.ok(
    src.includes('private constructor'),
    'NsdAdvertiser can still be constructed per Activity — rotation re-registers a live service'
  )
  assert.ok(
    /companion object[\s\S]*?fun get\(/.test(src),
    'NsdAdvertiser has no shared get(), so MainActivity still builds a new one'
  )
})

check('ShareService cancels its previous state collector', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/share/ShareService.kt')
  assert.ok(
    /stateJob\?\.cancel\(\)/.test(src),
    'ShareService launches a new collector on every onStartCommand without cancelling the old one'
  )
})

check('ShareManager.init is idempotent', () => {
  const src = read('mobile/app/src/main/java/com/netbridge/share/share/ShareManager.kt')
  assert.ok(
    /if \(initialized\) return/.test(src),
    'init() runs twice (Application + BootReceiver) and resets live sharing state'
  )
})

check('the desktop stores the pairing token encrypted at rest', () => {
  const src = read('desktop/src/main/store.ts')
  assert.ok(src.includes('safeStorage'), 'store.ts does not use Electron safeStorage')
  assert.ok(src.includes('tokenEnc'), 'store.ts has no encrypted token field')
})

check('the Android app opts out of auto-backup', () => {
  const src = read('mobile/app/src/main/AndroidManifest.xml')
  assert.ok(
    /allowBackup="false"/.test(src),
    'allowBackup is not false — the API token and pairing code would land in cloud backups'
  )
})

check('the System Proxy toggle reflects the persisted setting', () => {
  const app = read('desktop/src/renderer/src/App.tsx')
  assert.ok(
    app.includes('systemProxySetting'),
    'App.tsx drives the toggle from the runtime flag, so it shows "off" while the setting is on'
  )
  assert.ok(
    !app.includes('setSystemProxy(!state.systemProxyOn)'),
    'the toggle still writes the inverse of the runtime flag, which cannot turn the setting off'
  )
})

check('the dev CSP override still matches the HTML it rewrites', () => {
  const html = read('desktop/src/renderer/index.html')
  const cfg = read('desktop/electron.vite.config.ts')

  const prod = /const PROD_CSP =\s*\n?\s*"([^"]+)"/.exec(cfg)
  assert.ok(prod, 'PROD_CSP is no longer a plain string literal the plugin can match on')
  assert.ok(
    html.includes(prod[1]),
    'PROD_CSP in electron.vite.config.ts no longer appears verbatim in index.html, so the ' +
      'dev override silently does nothing and Vite\'s inline Fast Refresh preamble stays blocked'
  )
  assert.ok(
    /const DEV_CSP =[\s\S]*?unsafe-inline/.test(cfg),
    'DEV_CSP no longer allows inline scripts, so dev mode cannot run the React preamble'
  )
  assert.ok(
    /apply:\s*'serve'/.test(cfg),
    'the CSP override is no longer restricted to the dev server'
  )
})

check('the desktop drops an unreadable stored credential instead of retrying it', () => {
  const src = read('desktop/src/main/store.ts')
  assert.ok(
    /unreadable[\s\S]{0,400}?await this\.write\(\)/.test(src),
    'load() keeps a tokenEnc it cannot decrypt, so the app retries it on every launch'
  )
})

console.log(`\n${pass} passed, ${fail} failed\n`)
process.exit(fail === 0 ? 0 : 1)
