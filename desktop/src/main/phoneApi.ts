import net from 'net'

export interface PhoneClient {
  address: string
  connections: number
  bytesUp: number
  bytesDown: number
  firstSeen: number
  lastSeen: number
}

export interface PhoneStatus {
  sharing: boolean
  vpnActive: boolean
  /** The phone itself has a working route to the internet. */
  internetReachable: boolean
  /** 'USB_TETHER' | 'WIFI_HOTSPOT' | 'NONE' */
  transport: string
  transportLabel: string
  /** Phone is sharing and has at least one address the PC can dial. */
  reachable: boolean
  clients: number
  bytesIn: number
  bytesOut: number
  ips: string[]
  httpPort: number
  socksPort: number
  controlPort: number
  code: string
  warning: string | null
  version: string
}

const TIMEOUT_MS = 5000
export const CONTROL_PORT = 7777

/** Generic TCP probe — is anything listening on host:port? */
export function probeTcp(host: string, port: number, timeoutMs = 2500): Promise<boolean> {
  return new Promise((resolve) => {
    let settled = false
    const done = (ok: boolean): void => {
      if (settled) return
      settled = true
      socket.removeAllListeners()
      socket.destroy()
      resolve(ok)
    }
    const socket = net.connect({ host, port })
    socket.setTimeout(timeoutMs, () => done(false))
    socket.once('connect', () => done(true))
    socket.once('error', () => done(false))
  })
}

/** Control API (7777) accepted a TCP connect — safe to pair against. */
export function probeControl(
  host: string,
  port: number = CONTROL_PORT,
  timeoutMs = 2500
): Promise<boolean> {
  return probeTcp(host, port, timeoutMs)
}

/** Phone HTTP proxy accepted a TCP connect — the data path can carry traffic. */
export function probeHttp(host: string, port: number, timeoutMs = 2500): Promise<boolean> {
  return probeTcp(host, port, timeoutMs)
}

function networkError(host: string, e: unknown): Error {
  const err = e as Error & { cause?: { code?: string }; code?: string }
  const name = err?.name ?? ''
  const msg = err?.message ?? String(e)
  const code = err?.cause?.code || err?.code || ''
  if (name === 'AbortError' || /abort/i.test(msg)) {
    return new Error(`no answer from ${host}:${CONTROL_PORT} within ${TIMEOUT_MS}ms`)
  }
  if (code === 'ECONNREFUSED' || /ECONNREFUSED/i.test(msg)) {
    return new Error(`connection refused by ${host}:${CONTROL_PORT}`)
  }
  if (
    code === 'ENOTFOUND' ||
    code === 'EHOSTUNREACH' ||
    code === 'ENETUNREACH' ||
    /ENOTFOUND|EHOSTUNREACH|ENETUNREACH/i.test(msg)
  ) {
    return new Error(`cannot reach ${host}`)
  }
  return err instanceof Error ? err : new Error(msg)
}

async function request<T>(
  host: string,
  path: string,
  token?: string,
  method: 'GET' | 'POST' = 'GET',
  body?: unknown
): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const res = await fetch(`http://${host}:${CONTROL_PORT}${path}`, {
      method,
      signal: controller.signal,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {})
      },
      body: body !== undefined ? JSON.stringify(body) : undefined
    })
    const text = await res.text()
    let json: unknown = null
    try {
      json = JSON.parse(text)
    } catch {
      throw new Error(`bad response (${res.status})`)
    }
    if (!res.ok) {
      const err = (json as { error?: string })?.error
      throw new Error(err || `HTTP ${res.status}`)
    }
    return json as T
  } catch (e) {
    throw networkError(host, e)
  } finally {
    clearTimeout(timer)
  }
}

export async function pair(host: string, code: string): Promise<string> {
  const res = await request<{ token?: string }>(host, '/api/v1/pair', undefined, 'POST', {
    code
  })
  if (!res?.token) {
    throw new Error('pairing succeeded but the phone returned no token')
  }
  return res.token
}

export async function getStatus(host: string, token: string): Promise<PhoneStatus> {
  return request<PhoneStatus>(host, '/api/v1/status', token, 'GET')
}

export async function getClients(host: string, token: string): Promise<PhoneClient[]> {
  const res = await request<{ clients: PhoneClient[] }>(
    host,
    '/api/v1/clients',
    token,
    'GET'
  )
  return res.clients ?? []
}

export async function startSharing(host: string, token: string): Promise<void> {
  await request(host, '/api/v1/start', token, 'POST', {})
}

export async function stopSharing(host: string, token: string): Promise<void> {
  await request(host, '/api/v1/stop', token, 'POST', {})
}
