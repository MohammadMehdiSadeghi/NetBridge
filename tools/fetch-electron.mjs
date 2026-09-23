// Downloads the Electron zip in chunks with retries, because a single full-file
// request gets dropped partway through on this network.
import https from 'node:https'
import fs from 'node:fs'

const URL_ =
  'https://registry.npmmirror.com/-/binary/electron/v33.4.11/electron-v33.4.11-win32-x64.zip'
const OUT = 'desktop/_electron.zip'

function head() {
  return new Promise((resolve) => {
    const go = (url, depth = 0) => {
      if (depth > 5) return resolve({ err: 'too many redirects' })
      const req = https.get(url, (r) => {
        if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) {
          r.resume()
          return go(new URL(r.headers.location, url).toString(), depth + 1)
        }
        const len = Number(r.headers['content-length'])
        r.resume()
        resolve({ len, status: r.statusCode })
      })
      req.on('error', (e) => resolve({ err: e.message }))
      req.setTimeout(20000, () => {
        req.destroy()
        resolve({ err: 'timeout' })
      })
    }
    go(URL_)
  })
}

function fetchRange(from, to) {
  return new Promise((resolve, reject) => {
    const go = (url, depth = 0) => {
      if (depth > 5) return reject(new Error('too many redirects'))
      const req = https.get(url, { headers: { Range: `bytes=${from}-${to}` } }, (r) => {
        if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) {
          r.resume()
          return go(new URL(r.headers.location, url).toString(), depth + 1)
        }
        if (r.statusCode !== 206 && r.statusCode !== 200) {
          r.resume()
          return reject(new Error(`status ${r.statusCode}`))
        }
        const chunks = []
        r.on('data', (d) => chunks.push(d))
        r.on('end', () => resolve(Buffer.concat(chunks)))
        r.on('error', reject)
      })
      req.on('error', reject)
      req.setTimeout(30000, () => req.destroy(new Error('chunk timeout')))
    }
    go(URL_)
  })
}

const h = await head()
if (h.err || !h.len) {
  console.log('HEAD failed:', JSON.stringify(h))
  process.exit(1)
}
const totalMb = (h.len / 1048576).toFixed(1)
console.log(`total size: ${totalMb} MB`)

const CHUNK = 512 * 1024
let done = 0
let retries = 0

// Resume from whatever is already on disk so a killed run is not wasted.
// Must be read BEFORE opening with 'w', which truncates the file to zero.
try {
  const existing = fs.statSync(OUT).size
  if (existing > 0 && existing < h.len) {
    done = existing
    console.log(`resuming from ${(existing / 1048576).toFixed(1)} MB`)
  }
} catch {
  /* fresh start */
}

// 'r+' keeps existing bytes; fall back to 'w' when the file is new.
const fd = fs.existsSync(OUT) ? fs.openSync(OUT, 'r+') : fs.openSync(OUT, 'w')

for (let off = done; off < h.len; off += CHUNK) {
  const end = Math.min(off + CHUNK - 1, h.len - 1)
  let buf = null
  for (let t = 0; t < 8 && !buf; t++) {
    try {
      buf = await fetchRange(off, end)
    } catch {
      retries += 1
      await new Promise((r) => setTimeout(r, 400 * (t + 1)))
    }
  }
  if (!buf) {
    console.log(`\nfailed at offset ${off} after retries`)
    fs.closeSync(fd)
    process.exit(1)
  }
  fs.writeSync(fd, buf, 0, buf.length, off)
  done += buf.length
  process.stdout.write(`\r${(done / 1048576).toFixed(1)} / ${totalMb} MB  (retries: ${retries})`)
}

fs.closeSync(fd)
console.log('\ndone ->', OUT)
