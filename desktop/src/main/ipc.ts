import { BrowserWindow, ipcMain, shell } from 'electron'
import type { Store } from './store'
import type { ProxyChain } from './proxyChain'
import { discoverPhones, type DiscoveredPhone } from './discovery'
import {
  getClients,
  getStatus,
  pair as apiPair,
  probeControl,
  startSharing,
  stopSharing,
  CONTROL_PORT,
  type PhoneClient
} from './phoneApi'
import {
  applySystemProxy,
  clearSystemProxy,
  readSystemProxy,
  type ProxySnapshot
} from './systemProxy'

export interface DesktopState {
  connected: boolean
  connecting: boolean
  paired: boolean
  phoneHost: string
  phoneHttpPort: number
  vpnActive: boolean
  /** The phone has a working route to the internet on its own. */
  internetReachable: boolean
  /** 'USB' | 'hotspot' | 'none' — how the PC is attached to the phone. */
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

const TRANSPORT_LABELS: Record<string, string> = {
  USB_TETHER: 'USB',
  WIFI_HOTSPOT: 'Hotspot',
  NONE: 'No link'
}

export function transportLabel(transport: string): string {
  return TRANSPORT_LABELS[transport] ?? transport
}

export class AppController {
  private state: DesktopState
  private savedProxy: ProxySnapshot = { enabled: false, server: '' }
  private proxyApplied = false
  private pollTimer: NodeJS.Timeout | null = null
  private refreshTimer: NodeJS.Timeout | null = null

  constructor(
    private store: Store,
    private chain: ProxyChain,
    private getWindow: () => BrowserWindow | null
  ) {
    const s = store.get()
    this.state = {
      connected: false,
      connecting: false,
      paired: !!s.token,
      phoneHost: s.phoneHost,
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
      localPort: s.localPort,
      scanning: false,
      candidates: [],
      error: null
    }
  }

  getState(): DesktopState {
    return {
      ...this.state,
      bytesIn: this.chain.stats.bytesIn,
      bytesOut: this.chain.stats.bytesOut,
      chainConnections: this.chain.stats.connections
    }
  }

  private emit(): void {
    const win = this.getWindow()
    if (win && !win.isDestroyed()) {
      win.webContents.send('netbridge:state', this.getState())
    }
  }

  private patch(partial: Partial<DesktopState>): void {
    this.state = { ...this.state, ...partial }
    this.emit()
  }

  private fail(message: string): never {
    this.patch({ error: message, connecting: false })
    throw new Error(message)
  }

  async scan(): Promise<DiscoveredPhone[]> {
    this.patch({ scanning: true, error: null })
    try {
      const found = await discoverPhones(2500, (list) => {
        this.patch({ candidates: list })
      })
      // mDNS can announce an interface the PC cannot dial (phone on home Wi‑Fi
      // while we are on USB). Probe control:7777 and keep reachable ones first.
      const probed = await Promise.all(
        found.map(async (p) => ({
          ...p,
          port: p.port || CONTROL_PORT,
          reachable: await probeControl(p.address, p.port || CONTROL_PORT)
        }))
      )
      probed.sort((a, b) => Number(b.reachable) - Number(a.reachable))

      // Common tether addresses when mDNS missed or everything is unreachable.
      for (const fallback of ['192.168.43.1', '192.168.42.129']) {
        if (probed.some((p) => p.address === fallback)) continue
        if (await probeControl(fallback)) {
          probed.push({ address: fallback, name: 'tether', port: CONTROL_PORT, reachable: true })
        }
      }

      const phones = probed
      this.patch({ scanning: false, candidates: phones })
      // Prefer a host whose control API actually answers.
      const alive = phones.find((p) => p.reachable)
      const current = this.state.phoneHost
      const currentAlive = current && phones.some((p) => p.address === current && p.reachable)
      if (alive && !currentAlive) {
        await this.selectPhone(alive.address)
      }
      return phones
    } catch (e) {
      this.patch({ scanning: false, error: `scan_failed::${(e as Error).message}` })
      return []
    }
  }

  async selectPhone(address: string): Promise<void> {
    this.chain.setPhone(address, this.state.phoneHttpPort)
    await this.store.save({ phoneHost: address })
    this.patch({ phoneHost: address, error: null })
    // Try status with existing token
    const token = this.store.get().token
    if (token) {
      try {
        const status = await getStatus(address, token)
        this.chain.setPhone(address, status.httpPort)
        this.patch({
          phoneHttpPort: status.httpPort,
          paired: true,
          phoneSharing: status.sharing,
          vpnActive: status.vpnActive,
          internetReachable: status.internetReachable,
          transport: status.transport,
          phoneClients: status.clients,
          phoneWarning: status.warning,
          error: null
        })
        await this.store.save({ pairingCode: status.code })
      } catch {
        // token invalid — force re-pair
        await this.store.save({ token: '' })
        this.patch({ paired: false })
      }
    }
    this.emit()
  }

  async pairWithCode(code: string): Promise<void> {
    const host = this.state.phoneHost
    if (!host) this.fail('select_phone_first')
    this.patch({ connecting: true, error: null })
    // Fail fast with a clear code instead of a raw AbortError from fetch.
    if (!(await probeControl(host))) {
      this.fail(
        `pairing_failed::no answer from ${host}:${CONTROL_PORT} within 1500ms — pick the phone IP again or turn on hotspot/USB`
      )
    }
    try {
      const token = await apiPair(host, code.trim())
      await this.store.save({ token })
      this.patch({ paired: true, connecting: false, error: null })
      await this.refreshPhone()
    } catch (e) {
      this.fail(`pairing_failed::${(e as Error).message}`)
    }
  }

  async refreshPhone(): Promise<void> {
    const host = this.state.phoneHost
    const token = this.store.get().token
    if (!host || !token) return
    try {
      const status = await getStatus(host, token)
      this.chain.setPhone(host, status.httpPort)
      this.patch({
        phoneHttpPort: status.httpPort,
        phoneSharing: status.sharing,
        vpnActive: status.vpnActive,
        internetReachable: status.internetReachable,
        transport: status.transport,
        phoneClients: status.clients,
        phoneWarning: status.warning,
        paired: true,
        error: null
      })
    } catch {
      // keep last known state; poll will retry
    }

    // Per-client detail is a second round trip and is only meaningful while
    // sharing, so it is fetched separately and its failure is non-fatal.
    if (this.state.phoneSharing) {
      try {
        const clients = await getClients(host, token)
        this.patch({ phoneClientsDetail: clients })
      } catch {
        /* optional */
      }
    }
  }

  /**
   * Drop the OS proxy setting *before* building the tunnel, so no window exists in
   * which Windows would send traffic to a proxy that cannot forward it.
   */
  private async armSystemProxy(): Promise<void> {
    if (!this.store.get().systemProxy) {
      this.patch({ systemProxyOn: false })
      return
    }
    try {
      this.savedProxy = await readSystemProxy()
      await applySystemProxy(`127.0.0.1:${this.state.localPort}`)
      this.proxyApplied = true
      this.patch({ systemProxyOn: true })
    } catch {
      this.proxyApplied = false
      this.patch({ systemProxyOn: false })
    }
  }

  async connect(): Promise<void> {
    const s = this.store.get()
    if (!this.state.phoneHost) {
      this.fail('phone_not_found')
    }
    if (!s.token) {
      this.fail('pair_first')
    }

    this.patch({ connecting: true, error: null })

    const disarm = async (): Promise<void> => {
      if (!this.proxyApplied) return
      try {
        await clearSystemProxy(this.savedProxy)
      } catch {
        /* best effort */
      }
      this.proxyApplied = false
      this.patch({ systemProxyOn: false })
    }

    let status: Awaited<ReturnType<typeof getStatus>>
    try {
      status = await getStatus(this.state.phoneHost, s.token)
    } catch (e) {
      await disarm()
      this.fail(`phone_unreachable::${(e as Error).message}`)
    }

    // Shut down any stale tunnel before touching the system proxy again.
    if (this.chain.listening) await this.chain.stop()
    await this.chain.start(this.state.localPort)

    // From here on the proxy is engaged, so every exit path must either finish the
    // job or take the system proxy back down. Otherwise Windows would be left
    // pointing at a dead port — the classic "no internet after closing the app".
    try {
      await this.armSystemProxy()

      if (!status.sharing) {
        try {
          await startSharing(this.state.phoneHost, s.token)
        } catch (e) {
          await disarm()
          await this.chain.stop()
          this.fail(`start_share_failed::${(e as Error).message}`)
        }
      }

      this.chain.setPhone(this.state.phoneHost, status.httpPort)
      this.patch({
        phoneHttpPort: status.httpPort,
        phoneSharing: true,
        vpnActive: status.vpnActive,
        internetReachable: status.internetReachable,
        transport: status.transport,
        phoneClients: status.clients,
        phoneWarning: status.warning
      })

      // Sharing with no reachable address means the PC has no route to the phone.
      if (status.ips.length === 0) {
        this.patch({ error: 'no_connectable_address' })
      }

      this.patch({
        connected: this.chain.listening,
        connecting: false
      })
      this.startPolling()
    } catch (e) {
      await disarm()
      await this.chain.stop()
      this.patch({ connected: false, connecting: false, error: (e as Error).message })
    }
  }

  async disconnect(): Promise<void> {
    this.stopPolling()
    if (this.proxyApplied) {
      try {
        await clearSystemProxy(this.savedProxy)
      } catch {
        /* best effort */
      }
      this.proxyApplied = false
    }
    await this.chain.stop()
    this.patch({
      connected: false,
      systemProxyOn: false,
      phoneClientsDetail: [],
      error: null
    })
  }

  async disconnectPhone(): Promise<void> {
    const host = this.state.phoneHost
    const token = this.store.get().token
    if (host && token) {
      try {
        await stopSharing(host, token)
      } catch {
        /* ignore */
      }
    }
    await this.refreshPhone()
  }

  async setLocalPort(port: number): Promise<void> {
    const wasConnected = this.state.connected
    if (wasConnected) await this.disconnect()
    await this.store.save({ localPort: port })
    this.patch({ localPort: port })
    if (wasConnected) await this.connect()
  }

  async setSystemProxyEnabled(enabled: boolean): Promise<void> {
    await this.store.save({ systemProxy: enabled })
    if (!enabled) {
      if (this.proxyApplied) {
        try {
          await clearSystemProxy(this.savedProxy)
        } catch {
          /* best effort */
        }
        this.proxyApplied = false
      }
      this.patch({ systemProxyOn: false })
      return
    }
    // Turning it on mid-session must actually engage it, not just persist it.
    if (this.state.connected && !this.proxyApplied) {
      await this.armSystemProxy()
    }
  }

  async forgetPairing(): Promise<void> {
    await this.store.save({ token: '' })
    this.patch({ paired: false })
  }

  private startPolling(): void {
    this.stopPolling()
    this.pollTimer = setInterval(() => void this.refreshPhone(), 4000)
    this.refreshTimer = setInterval(() => this.emit(), 2000)
  }

  private stopPolling(): void {
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.refreshTimer) clearInterval(this.refreshTimer)
    this.pollTimer = null
    this.refreshTimer = null
  }

  async shutdown(): Promise<void> {
    await this.disconnect()
  }

  registerIpc(): void {
    ipcMain.handle('netbridge:getState', () => this.getState())
    ipcMain.handle('netbridge:scan', () => this.scan())
    ipcMain.handle('netbridge:selectPhone', (_e, address: string) =>
      this.selectPhone(address)
    )
    ipcMain.handle('netbridge:manualHost', async (_e, host: string) => {
      await this.selectPhone(host.trim())
    })
    ipcMain.handle('netbridge:pair', (_e, code: string) => this.pairWithCode(code))
    ipcMain.handle('netbridge:connect', () => this.connect())
    ipcMain.handle('netbridge:disconnect', () => this.disconnect())
    ipcMain.handle('netbridge:stopPhoneShare', () => this.disconnectPhone())
    ipcMain.handle('netbridge:setLocalPort', (_e, port: number) =>
      this.setLocalPort(port)
    )
    ipcMain.handle('netbridge:setSystemProxy', (_e, enabled: boolean) =>
      this.setSystemProxyEnabled(enabled)
    )
    ipcMain.handle('netbridge:forgetPairing', () => this.forgetPairing())
    ipcMain.handle('netbridge:refresh', () => this.refreshPhone())
    ipcMain.handle('netbridge:openExternal', (_e, url: string) => {
      const allowed = new Set([
        'https://github.com/MohammadMehdiSadeghi',
        'https://www.linkedin.com/in/mohammad-mehdi-sadeghi'
      ])
      if (allowed.has(url)) {
        void shell.openExternal(url)
      }
    })
  }
}
