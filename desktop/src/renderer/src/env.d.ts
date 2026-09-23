import type { NetBridgeApi } from '../../preload/index'

declare global {
  interface Window {
    netbridge: NetBridgeApi
  }
}

export {}
