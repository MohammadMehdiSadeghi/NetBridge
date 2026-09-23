import { execFile } from 'child_process'
import { promisify } from 'util'

const run = promisify(execFile)

const PS_PRELUDE = `
$ErrorActionPreference = 'Stop'
$path = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'
`

const PS_NOTIFY = `
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class NetBridgeWinInet {
  [DllImport("wininet.dll", SetLastError=true)]
  public static extern bool InternetSetOption(int hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);
}
"@
[NetBridgeWinInet]::InternetSetOption(0, 39, IntPtr.Zero, 0) | Out-Null
[NetBridgeWinInet]::InternetSetOption(0, 37, IntPtr.Zero, 0) | Out-Null
`

export interface ProxySnapshot {
  enabled: boolean
  server: string
}

export async function readSystemProxy(): Promise<ProxySnapshot> {
  const script = `${PS_PRELUDE}
    $en = (Get-ItemProperty -Path $path -Name ProxyEnable -ErrorAction SilentlyContinue).ProxyEnable
    $sv = (Get-ItemProperty -Path $path -Name ProxyServer -ErrorAction SilentlyContinue).ProxyServer
    [pscustomobject]@{ enabled = ($en -eq 1); server = [string]$sv } | ConvertTo-Json -Compress
  `
  try {
    const { stdout } = await run('powershell', ['-NoProfile', '-NonInteractive', '-Command', script])
    const parsed = JSON.parse(stdout.trim()) as { enabled: boolean; server: string }
    return { enabled: !!parsed.enabled, server: parsed.server || '' }
  } catch {
    return { enabled: false, server: '' }
  }
}

export async function applySystemProxy(server: string): Promise<void> {
  const script = `${PS_PRELUDE}
    Set-ItemProperty -Path $path -Name ProxyEnable -Value 1 -Type DWord
    Set-ItemProperty -Path $path -Name ProxyServer -Value '${server.replace(/'/g, "''")}' -Type String
    ${PS_NOTIFY}
  `
  await run('powershell', ['-NoProfile', '-NonInteractive', '-Command', script])
}

export async function clearSystemProxy(previous: ProxySnapshot): Promise<void> {
  const script = `${PS_PRELUDE}
    if ($true) {
      Set-ItemProperty -Path $path -Name ProxyEnable -Value $([int]([bool]$previous.enabled)) -Type DWord
      $server = '${(previous.server || '').replace(/'/g, "''")}'
      if ($server -ne '') {
        Set-ItemProperty -Path $path -Name ProxyServer -Value $server -Type String
      } else {
        Remove-ItemProperty -Path $path -Name ProxyServer -ErrorAction SilentlyContinue
      }
    }
    ${PS_NOTIFY}
  `
  await run('powershell', ['-NoProfile', '-NonInteractive', '-Command', script])
}
