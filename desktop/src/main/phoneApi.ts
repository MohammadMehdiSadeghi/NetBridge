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
    const res = await fetch(`http://${host}:7777${path}`, {
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
  } finally {
    clearTimeout(timer)
  }
}

export async function pair(host: string, code: string): Promise<string> {
  const res = await request<{ token: string }>(host, '/api/v1/pair', undefined, 'POST', {
    code
  })
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
