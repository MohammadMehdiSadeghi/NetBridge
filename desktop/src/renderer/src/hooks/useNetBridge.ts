import { useCallback, useEffect, useState } from 'react'

export interface DiscoveredPhone {
  address: string
  name: string
  port: number
  reachable?: boolean
}

export interface PhoneClient {
  address: string
  connections: number
  bytesUp: number
  bytesDown: number
  firstSeen: number
  lastSeen: number
}

export interface DesktopState {
  connected: boolean
  connecting: boolean
  paired: boolean
  phoneHost: string
  phoneHttpPort: number
  vpnActive: boolean
  internetReachable: boolean
  transport: string
  phoneSharing: boolean
  phoneClients: number
  phoneClientsDetail: PhoneClient[]
  phoneWarning: string | null
  bytesIn: number
  bytesOut: number
  chainConnections: number
  systemProxyOn: boolean
  localPort: number
  scanning: boolean
  candidates: DiscoveredPhone[]
  error: string | null
}

export const TRANSPORT_LABELS: Record<string, string> = {
  USB_TETHER: 'USB',
  WIFI_HOTSPOT: 'Hotspot',
  NONE: 'No link'
}

export function transportLabel(transport: string): string {
  return TRANSPORT_LABELS[transport] ?? transport
}

const EMPTY: DesktopState = {
  connected: false,
  connecting: false,
  paired: false,
  phoneHost: '',
  phoneHttpPort: 8080,
  vpnActive: false,
  internetReachable: false,
  transport: 'NONE',
  phoneSharing: false,
  phoneClients: 0,
  phoneClientsDetail: [],
  phoneWarning: null,
  bytesIn: 0,
  bytesOut: 0,
  chainConnections: 0,
  systemProxyOn: false,
  localPort: 18080,
  scanning: false,
  candidates: [],
  error: null
}

export function useNetBridge(): {
  state: DesktopState
  scan: () => Promise<void>
  select: (address: string) => Promise<void>
  manual: (host: string) => Promise<void>
  pair: (code: string) => Promise<void>
  connect: () => Promise<void>
  disconnect: () => Promise<void>
  stopPhoneShare: () => Promise<void>
  setLocalPort: (port: number) => Promise<void>
  setSystemProxy: (enabled: boolean) => Promise<void>
  forgetPairing: () => Promise<void>
  refresh: () => Promise<void>
} {
  const [state, setState] = useState<DesktopState>(EMPTY)

  useEffect(() => {
    let alive = true
    void window.netbridge.getState().then((s) => {
      if (alive) setState(s as DesktopState)
    })
    const off = window.netbridge.onState((s) => {
      if (alive) setState(s as DesktopState)
    })
    return () => {
      alive = false
      off()
    }
  }, [])

  const scan = useCallback(async () => {
    await window.netbridge.scan()
  }, [])
  const select = useCallback(async (address: string) => {
    await window.netbridge.selectPhone(address)
  }, [])
  const manual = useCallback(async (host: string) => {
    await window.netbridge.manualHost(host)
  }, [])
  const pair = useCallback(async (code: string) => {
    await window.netbridge.pair(code)
  }, [])
  const connect = useCallback(async () => {
    await window.netbridge.connect()
  }, [])
  const disconnect = useCallback(async () => {
    await window.netbridge.disconnect()
  }, [])
  const stopPhoneShare = useCallback(async () => {
    await window.netbridge.stopPhoneShare()
  }, [])
  const setLocalPort = useCallback(async (port: number) => {
    await window.netbridge.setLocalPort(port)
  }, [])
  const setSystemProxy = useCallback(async (enabled: boolean) => {
    await window.netbridge.setSystemProxy(enabled)
  }, [])
  const forgetPairing = useCallback(async () => {
    await window.netbridge.forgetPairing()
  }, [])
  const refresh = useCallback(async () => {
    await window.netbridge.refresh()
  }, [])

  return {
    state,
    scan,
    select,
    manual,
    pair,
    connect,
    disconnect,
    stopPhoneShare,
    setLocalPort,
    setSystemProxy,
    forgetPairing,
    refresh
  }
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(1)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
}
