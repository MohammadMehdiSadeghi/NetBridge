import dgram from 'dgram'

export interface DiscoveredPhone {
  address: string
  name: string
  port: number
  /** Control API (7777) accepted a TCP connect — safe to pair against. */
  reachable?: boolean
}

const MDNS_ADDR = '224.0.0.251'
const MDNS_PORT = 5353
const QUERY_NAME = '_netbridge._tcp.local'

function encodeName(name: string): Buffer {
  const parts = name.split('.').filter(Boolean)
  const chunks: number[] = []
  for (const part of parts) {
    const bytes = Buffer.from(part, 'utf8')
    chunks.push(bytes.length)
    for (const b of bytes) chunks.push(b)
  }
  chunks.push(0)
  return Buffer.from(chunks)
}

function buildQuery(): Buffer {
  const header = Buffer.alloc(12)
  header.writeUInt16BE(0, 0) // id
  header.writeUInt16BE(0x0000, 2) // flags: standard query
  header.writeUInt16BE(1, 4) // qdcount
  header.writeUInt16BE(0, 6)
  header.writeUInt16BE(0, 8)
  header.writeUInt16BE(0, 10)
  const question = Buffer.concat([
    encodeName(QUERY_NAME),
    Buffer.from([0x00, 0x0c, 0x00, 0x01]) // PTR, IN
  ])
  return Buffer.concat([header, question])
}

function readName(buf: Buffer, start: number): { name: string; next: number } {
  const parts: string[] = []
  let offset = start
  let next = start
  let jumped = false
  let guard = 0
  while (guard++ < 128 && offset < buf.length) {
    const len = buf[offset]
    if (len === 0) {
      if (!jumped) next = offset + 1
      break
    }
    if ((len & 0xc0) === 0xc0) {
      if (offset + 1 >= buf.length) break
      const ptr = ((len & 0x3f) << 8) | buf[offset + 1]
      if (!jumped) next = offset + 2
      offset = ptr
      jumped = true
      continue
    }
    const end = offset + 1 + len
    if (end > buf.length) break
    parts.push(buf.toString('utf8', offset + 1, end))
    offset = end
    if (!jumped) next = offset
  }
  return { name: parts.join('.'), next }
}

export class DiscoveredOnce {
  static parse(msg: Buffer, from: string): DiscoveredPhone | null {
    if (msg.length < 12) return null
    const flags = msg.readUInt16BE(2)
    const qr = (flags >> 15) & 1
    if (qr !== 1) return null
    const ancount = msg.readUInt16BE(6)
    const arcount = msg.readUInt16BE(10)

    let offset = 12
    const qdcount = msg.readUInt16BE(4)
    for (let i = 0; i < qdcount; i++) {
      const r = readName(msg, offset)
      offset = r.next + 4
      if (offset > msg.length) return null
    }

    let found = false
    let port = 7777
    let name = from

    const total = ancount + arcount
    for (let i = 0; i < total; i++) {
      if (offset + 10 > msg.length) break
      const nm = readName(msg, offset)
      offset = nm.next
      if (offset + 10 > msg.length) break
      const type = msg.readUInt16BE(offset)
      offset += 4 // type, class
      const ttl = msg.readUInt32BE(offset)
      offset += 4
      const rdlen = msg.readUInt16BE(offset)
      offset += 2
      if (offset + rdlen > msg.length) break
      const rdata = msg.subarray(offset, offset + rdlen)
      offset += rdlen

      if (ttl === 0) continue

      if (type === 12 /* PTR */ && nm.name.endsWith('_netbridge._tcp.local')) {
        found = true
        const target = readName(msg, offset - rdlen)
        name = target.name.split('.')[0] || name
      }
      if (type === 33 /* SRV */ && rdlen >= 6) {
        port = rdata.readUInt16BE(4) || port
        found = true
      }
    }

    return found ? { address: from, name, port } : null
  }
}

export function discoverPhones(
  timeoutMs = 2500,
  onUpdate?: (phones: DiscoveredPhone[]) => void
): Promise<DiscoveredPhone[]> {
  return new Promise((resolve) => {
    const found = new Map<string, DiscoveredPhone>()
    let socket: dgram.Socket | null = null
    let done = false

    const finish = (): void => {
      if (done) return
      done = true
      if (socket) {
        try {
          socket.close()
        } catch {
          /* already closed */
        }
      }
      const list = [...found.values()]
      onUpdate?.(list)
      resolve(list)
    }

    const timer = setTimeout(finish, timeoutMs)

    const onMessage = (msg: Buffer, rinfo: dgram.RemoteInfo): void => {
      if (rinfo.address.startsWith('127.')) return
      const phone = DiscoveredOnce.parse(msg, rinfo.address)
      if (phone) {
        found.set(phone.address, phone)
        onUpdate?.([...found.values()])
      }
    }

    const startSocket = (port: number): void => {
      try {
        socket = dgram.createSocket({ type: 'udp4', reuseAddr: true })
        socket.on('error', (err) => {
          if (port === MDNS_PORT) {
            // Port 5353 might be busy on Windows (svchost / Apple Bonjour). Fallback to ephemeral.
            try {
              socket?.close()
            } catch {
              /* ignore */
            }
            startSocket(0)
          } else {
            clearTimeout(timer)
            finish()
          }
        })
        socket.on('message', onMessage)
        socket.bind(port, () => {
          if (!socket) return
          try {
            socket.setMulticastTTL(255)
            socket.setMulticastLoopback(true)
            socket.addMembership(MDNS_ADDR)
          } catch {
            /* platform quirks / already member */
          }
          try {
            socket.send(buildQuery(), MDNS_PORT, MDNS_ADDR)
          } catch {
            /* ignore send failure */
          }
        })
      } catch {
        if (port === MDNS_PORT) {
          startSocket(0)
        } else {
          clearTimeout(timer)
          finish()
        }
      }
    }

    startSocket(MDNS_PORT)
  })
}
