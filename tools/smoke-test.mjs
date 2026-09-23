/**
 * NetBridge smoke test — verifies the real traffic path without a phone or Electron.
 *
 * It stands up a fake VPN-side server and a fake phone HTTP proxy, exactly in the
 * shape the real Android app exposes, then drives the desktop's real ProxyChain
 * against it and asserts on the bytes that came out the far end.
 *
 *   PC app -> 127.0.0.1:18080 (ProxyChain) -> "phone":8080 -> "VPN" -> internet
 *
 * Run:  node tools/smoke-test.mjs
 *
 * Why this exists: the Electron binary cannot always be downloaded (restricted
 * network), so `npm run dev` may be unavailable. This proves the networking core —
 * which is where the hard problems live — on any machine with Node.
 */

import http from 'node:http'
import net from 'node:net'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'

/**
 * The Electron entry bundle is an app, not a library, so it does not export
 * ProxyChain. Compile just that module to plain CommonJS and load it directly —
 * the code under test is byte-for-byte the same class the app uses.
 */
const root = path.resolve(import.meta.dirname, '..')
const smokeDir = path.join(root, 'desktop', '.smoke')

if (!fs.existsSync(path.join(smokeDir, 'proxyChain.js'))) {
  console.error(
    '\nMissing build artefact. Run this first:\n' +
      '  cd desktop && ./node_modules/.bin/tsc src/main/proxyChain.ts \\\n' +
      '      --outDir .smoke --module commonjs --target es2022 \\\n' +
      '      --esModuleInterop --skipLibCheck\n'
  )
  process.exit(1)
}

const require = createRequire(import.meta.url)
const { ProxyChain } = require(path.join(smokeDir, 'proxyChain.js'))

const LOCAL_PORT = 18099
const PHONE_PORT = 18080
const SOCKS_PORT = 11080

let pass = 0
let fail = 0

function ok(name, detail = '') {
  pass += 1
  console.log(`  \u2713 ${name}${detail ? ` — ${detail}` : ''}`)
}

function bad(name, err) {
  fail += 1
  console.log(`  \u2717 ${name}\n      ${err?.message ?? err}`)
}

function check(name, fn) {
  process.stdout.write(`  ... ${name}\r`)
  return fn().then(
    () => {
      ok(name)
    },
    (e) => {
      bad(name, e)
    }
  )
}

/** Decodes a chunked body (what Node's http server uses when no length is set). */
function decodeChunked(buf) {
  let out = ''
  let offset = 0
  while (offset < buf.length) {
    const nl = buf.indexOf('\r\n', offset)
    if (nl === -1) break
    const size = parseInt(buf.toString('latin1', offset, nl), 16)
    if (!Number.isFinite(size) || size === 0) break
    offset = nl + 2
    out += buf.toString('utf8', offset, offset + size)
    offset += size + 2
  }
  return out
}

/** Collects a full HTTP response from a socket, including the body. */
function readResponse(socket) {
  return new Promise((resolve, reject) => {
    let raw = Buffer.alloc(0)
    const onData = (chunk) => {
      raw = Buffer.concat([raw, chunk])
      const text = raw.toString('latin1')
      const headEnd = text.indexOf('\r\n\r\n')
      if (headEnd === -1) return
      const head = text.slice(0, headEnd)
      const bodyStart = headEnd + 4

      const lenMatch = /content-length:\s*(\d+)/i.exec(head)
      if (lenMatch) {
        const want = Number(lenMatch[1])
        if (raw.length - bodyStart >= want) {
          cleanup()
          resolve({
            head,
            body: raw.subarray(bodyStart, bodyStart + want).toString('utf8'),
            raw: raw.subarray(bodyStart, bodyStart + want)
          })
        }
        return
      }

      if (/transfer-encoding:\s*chunked/i.test(head)) {
        const body = raw.subarray(bodyStart)
        // A complete chunked body ends with the zero-length terminator.
        if (body.includes(Buffer.from('0\r\n\r\n'))) {
          cleanup()
          resolve({ head, body: decodeChunked(body), raw: body })
        }
        return
      }

      if (/connection:\s*close/i.test(head)) {
        cleanup()
        resolve({ head, body: raw.subarray(bodyStart).toString('utf8'), raw: raw.subarray(bodyStart) })
      }
    }
    const onError = (e) => {
      cleanup()
      reject(e)
    }
    const onTimeout = () => {
      cleanup()
      reject(new Error('socket timeout'))
    }
    function cleanup() {
      socket.removeListener('data', onData)
      socket.removeListener('error', onError)
      socket.removeListener('timeout', onTimeout)
    }
    socket.on('data', onData)
    socket.once('error', onError)
    socket.once('timeout', onTimeout)
  })
}

/** One request through an HTTP proxy, returning status line + body. */
function proxyRequest({ proxyPort, target, headers = {} }) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(proxyPort, '127.0.0.1')
    socket.setTimeout(8000)

    // Attach the reader before writing: a fast proxy can answer in the same tick,
    // and a listener added afterwards would miss the data and hang forever.
    const pending = readResponse(socket)

    socket.once('connect', () => {
      const lines = [`GET ${target} HTTP/1.1`, `Host: ${new URL(target).host}`]
      for (const [k, v] of Object.entries(headers)) lines.push(`${k}: ${v}`)
      lines.push('Connection: close', '', '')
      socket.write(lines.join('\r\n'))
    })
    socket.once('error', reject)

    pending
      .then((res) => {
        socket.destroy()
        const statusLine = res.head.split('\r\n')[0]
        resolve({ status: Number(statusLine.split(' ')[1]), body: res.body, statusLine })
      })
      .catch((e) => {
        socket.destroy()
        reject(e)
      })
  })
}

// ---------------------------------------------------------------------------
// Fake upstream: the "internet" as seen from inside the phone's VPN tunnel.
// ---------------------------------------------------------------------------
const reachedUpstream = []

const vpnSideServer = http.createServer((req, res) => {
  reachedUpstream.push(req.url)
  if (req.url === '/big') {
    const payload = 'x'.repeat(50_000)
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Content-Length': payload.length })
    res.end(payload)
    return
  }
  res.writeHead(200, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ ok: true, path: req.url }))
})

// A bare TCP echo server, for the CONNECT/HTTPS-style tunnel path.
const vpnSideTcp = net.createServer((sock) => {
  sock.on('data', (d) => sock.write(d))
})

// ---------------------------------------------------------------------------
// Fake phone: an HTTP proxy bound to 0.0.0.0, like the Android app exposes.
// Counts requests so we can prove the chain really is a chain.
// ---------------------------------------------------------------------------
const phoneRequests = []

const phoneProxy = http.createServer((req, res) => {
  phoneRequests.push(req.url)
  const parsed = new URL(req.url)
  const upstream = http.request(
    {
      host: parsed.hostname,
      port: parsed.port || 80,
      path: parsed.pathname + parsed.search,
      method: req.method,
      headers: { ...req.headers, host: parsed.host }
    },
    (up) => {
      res.writeHead(up.statusCode ?? 502, up.headers)
      up.pipe(res)
    }
  )
  upstream.on('error', () => {
    res.writeHead(502).end('upstream unreachable')
  })
  req.pipe(upstream)
})

phoneProxy.on('connect', (req, clientSocket, head) => {
  phoneRequests.push(`CONNECT ${req.url}`)
  const [host, port] = req.url.split(':')
  const upstream = net.connect(Number(port), host, () => {
    clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n')
    if (head?.length) upstream.write(head)
    upstream.pipe(clientSocket)
    clientSocket.pipe(upstream)
  })
  upstream.on('error', () => clientSocket.destroy())
  clientSocket.on('error', () => upstream.destroy())
})

// ---------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------
async function main() {
  console.log('\nNetBridge smoke test — traffic path without a phone\n')

  await new Promise((r) => vpnSideServer.listen(0, '127.0.0.1', r))
  const vpnPort = vpnSideServer.address().port
  await new Promise((r) => vpnSideTcp.listen(0, '127.0.0.1', r))
  const tcpPort = vpnSideTcp.address().port
  await new Promise((r) => phoneProxy.listen(PHONE_PORT, '127.0.0.1', r))

  // Point the fake "VPN-side" targets at the loopback ports we just opened.
  // The phone proxy resolves the host it is given, so we hand it 127.0.0.1:port.

  const chain = new ProxyChain()
  // Must be set before start(), otherwise every request short-circuits to 503.
  chain.setPhone('127.0.0.1', PHONE_PORT)
  await chain.start(LOCAL_PORT)

  // ---- 1. Chain forwards plain HTTP through the phone proxy ----------------
  await check('HTTP request travels PC -> ProxyChain -> phone proxy -> upstream', async () => {
    const res = await proxyRequest({
      proxyPort: LOCAL_PORT,
      target: `http://127.0.0.1:${vpnPort}/hello`
    })
    assert.equal(res.status, 200, `expected 200, got ${res.statusLine}`)
    const parsed = JSON.parse(res.body)
    assert.equal(parsed.path, '/hello')
    assert.equal(phoneRequests.at(-1), `http://127.0.0.1:${vpnPort}/hello`)
    assert.equal(reachedUpstream.at(-1), '/hello')
  })

  // ---- 2. Byte counters move ----------------------------------------------
  await check('byte counters record both directions', async () => {
    const before = { in: chain.stats.bytesIn, out: chain.stats.bytesOut }
    await proxyRequest({
      proxyPort: LOCAL_PORT,
      target: `http://127.0.0.1:${vpnPort}/big`
    })
    assert.ok(chain.stats.bytesIn > before.in, 'bytesIn did not increase')
    assert.ok(chain.stats.bytesOut > before.out, 'bytesOut did not increase')
    assert.ok(
      chain.stats.bytesOut - before.out >= 50_000,
      `expected >=50000 downstream bytes, got ${chain.stats.bytesOut - before.out}`
    )
  })

  // ---- 3. Large payload integrity ----------------------------------------
  await check('a 50 KB body crosses the chain intact', async () => {
    const res = await proxyRequest({
      proxyPort: LOCAL_PORT,
      target: `http://127.0.0.1:${vpnPort}/big`
    })
    assert.equal(res.body.length, 50_000, `body was ${res.body.length} bytes`)
  })

  // ---- 4. CONNECT tunnelling (what HTTPS uses) ----------------------------
  await check('CONNECT tunnel carries bytes end to end', async () => {
    const result = await new Promise((resolve, reject) => {
      const socket = net.connect(LOCAL_PORT, '127.0.0.1')
      socket.setTimeout(8000)
      let buf = ''
      let sentPing = false
      socket.once('connect', () => {
        socket.write(`CONNECT 127.0.0.1:${tcpPort} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n`)
      })
      socket.on('data', (d) => {
        buf += d.toString('latin1')
        // Wait for the proxy's 200, then send a payload and expect it echoed back
        // by the upstream TCP server.
        if (!sentPing && buf.includes('200 Connection Established')) {
          sentPing = true
          socket.write('PING')
          return
        }
        if (sentPing && buf.includes('PING')) {
          socket.destroy()
          resolve(true)
        }
      })
      socket.once('error', reject)
      socket.once('timeout', () => reject(new Error('CONNECT tunnel timed out')))
    })
    assert.equal(result, true)
    assert.ok(
      phoneRequests.some((r) => r.startsWith('CONNECT')),
      'phone proxy never saw a CONNECT'
    )
  })

  // ---- 5. Unreachable phone must not hang, and must return 502 ------------
  await check('unreachable phone returns 502 instead of hanging', async () => {
    const dead = new ProxyChain()
    dead.setPhone('127.0.0.1', 9) // discard port: nothing listens
    await dead.start(LOCAL_PORT + 1)
    const res = await proxyRequest({
      proxyPort: LOCAL_PORT + 1,
      target: `http://127.0.0.1:${vpnPort}/x`
    })
    assert.equal(res.status, 502, `expected 502, got ${res.statusLine}`)
    await dead.stop()
  })

  // ---- 6. No phone configured -> clean 503, not a crash -------------------
  await check('no phone configured returns 503', async () => {
    const idle = new ProxyChain()
    await idle.start(LOCAL_PORT + 2)
    const res = await proxyRequest({
      proxyPort: LOCAL_PORT + 2,
      target: `http://127.0.0.1:${vpnPort}/x`
    })
    assert.equal(res.status, 503, `expected 503, got ${res.statusLine}`)
    await idle.stop()
  })

  // ---- 7. Port binding is real (this is what System Proxy needs) ----------
  await check('ProxyChain binds 127.0.0.1 on the configured port', async () => {
    assert.equal(chain.listening, true)
    const probe = await new Promise((resolve) => {
      const s = net.connect(LOCAL_PORT, '127.0.0.1')
      s.once('connect', () => {
        s.destroy()
        resolve(true)
      })
      s.once('error', () => resolve(false))
    })
    assert.equal(probe, true)
  })

  // ---- 8. Stop releases the port, so a restart cannot hit EADDRINUSE ------
  await check('stop() releases the port', async () => {
    await chain.stop()
    const again = new ProxyChain()
    await again.start(LOCAL_PORT) // would throw EADDRINUSE if not released
    assert.equal(again.listening, true)
    await again.stop()
  })

  // ---- 9. Concurrent connections are handled independently ----------------
  await check('20 concurrent requests all succeed', async () => {
    const c = new ProxyChain()
    c.setPhone('127.0.0.1', PHONE_PORT)
    await c.start(LOCAL_PORT + 3)
    const results = await Promise.all(
      Array.from({ length: 20 }, (_, i) =>
        proxyRequest({
          proxyPort: LOCAL_PORT + 3,
          target: `http://127.0.0.1:${vpnPort}/n${i}`
        })
      )
    )
    assert.equal(results.filter((r) => r.status === 200).length, 20)
    await c.stop()
  })

  await new Promise((r) => phoneProxy.close(r))
  await new Promise((r) => vpnSideServer.close(r))
  await new Promise((r) => vpnSideTcp.close(r))

  console.log(`\n${pass} passed, ${fail} failed\n`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\nSmoke test crashed:', e)
  process.exit(1)
})
