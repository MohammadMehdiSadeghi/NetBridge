import { contextBridge, ipcRenderer } from 'electron'

export interface DiscoveredPhone {
  address: string
  name: string
  port: number
}

const api = {
  getState: () => ipcRenderer.invoke('netbridge:getState'),
  scan: () => ipcRenderer.invoke('netbridge:scan'),
  selectPhone: (address: string) => ipcRenderer.invoke('netbridge:selectPhone', address),
  manualHost: (host: string) => ipcRenderer.invoke('netbridge:manualHost', host),
  pair: (code: string) => ipcRenderer.invoke('netbridge:pair', code),
  connect: () => ipcRenderer.invoke('netbridge:connect'),
  disconnect: () => ipcRenderer.invoke('netbridge:disconnect'),
  stopPhoneShare: () => ipcRenderer.invoke('netbridge:stopPhoneShare'),
  setLocalPort: (port: number) => ipcRenderer.invoke('netbridge:setLocalPort', port),
  setSystemProxy: (enabled: boolean) =>
    ipcRenderer.invoke('netbridge:setSystemProxy', enabled),
  forgetPairing: () => ipcRenderer.invoke('netbridge:forgetPairing'),
  refresh: () => ipcRenderer.invoke('netbridge:refresh'),
  onState: (callback: (state: unknown) => void) => {
    const listener = (_e: Electron.IpcRendererEvent, state: unknown): void =>
      callback(state)
    ipcRenderer.on('netbridge:state', listener)
    return () => ipcRenderer.removeListener('netbridge:state', listener)
  }
}

contextBridge.exposeInMainWorld('netbridge', api)

export type NetBridgeApi = typeof api
