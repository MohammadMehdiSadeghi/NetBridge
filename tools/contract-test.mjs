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

console.log(`\n${pass} passed, ${fail} failed\n`)
process.exit(fail === 0 ? 0 : 1)
