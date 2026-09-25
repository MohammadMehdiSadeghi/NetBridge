import { useState } from 'react'
import {
  Activity,
  ArrowDown,
  ArrowUp,
  Cable,
  Check,
  CircleX,
  Info,
  Link2,
  MonitorSmartphone,
  Power,
  RefreshCw,
  Settings2,
  ShieldCheck,
  ShieldOff,
  Unlink,
  Wifi,
  WifiOff,
  X
} from 'lucide-react'
import { formatBytes, useNetBridge } from './hooks/useNetBridge'
import {
  errorText,
  getLang,
  isFa,
  setLang,
  t,
  transportLabel,
  warningText,
  type Lang
} from './i18n'

type Tab = 'home' | 'device' | 'guide' | 'settings'

export default function App(): JSX.Element {
  const netbridge = useNetBridge()
  const { state } = netbridge
  const [tab, setTab] = useState<Tab>('home')
  const [, setLangTick] = useState(0)
  const strings = t()
  const dir = isFa() ? 'rtl' : 'ltr'

  const toggleLang = (): void => {
    const next: Lang = isFa() ? 'en' : 'fa'
    setLang(next)
    setLangTick((n) => n + 1)
  }

  return (
    <div className="flex h-full" dir={dir}>
      {/* Sidebar */}
      <aside className="flex w-60 shrink-0 flex-col border-stroke bg-bg1" style={dir === 'rtl' ? { borderLeftWidth: 1 } : { borderRightWidth: 1 }}>
        <div className="flex items-center gap-3 px-5 py-6">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand/15 text-brand">
            <Link2 size={22} />
          </div>
          <div>
            <div className="text-lg font-extrabold leading-tight text-ink">{strings.appName}</div>
            <div className="text-[11px] text-muted">{strings.tagline}</div>
          </div>
        </div>

        <nav className="flex flex-1 flex-col gap-1 px-3">
          <TabButton
            active={tab === 'home'}
            onClick={() => setTab('home')}
            icon={<Power size={18} />}
            label={strings.tabConnect}
          />
          <TabButton
            active={tab === 'device'}
            onClick={() => setTab('device')}
            icon={<MonitorSmartphone size={18} />}
            label={strings.tabDevice}
          />
          <TabButton
            active={tab === 'guide'}
            onClick={() => setTab('guide')}
            icon={<Info size={18} />}
            label={strings.tabGuide}
          />
          <TabButton
            active={tab === 'settings'}
            onClick={() => setTab('settings')}
            icon={<Settings2 size={18} />}
            label={strings.tabSettings}
          />
        </nav>

        <div className="border-t border-stroke p-4 text-[11px] leading-5 text-muted">
          <div className="flex items-center justify-between">
            <span>{strings.status}</span>
            <StatusDot active={state.connected} />
          </div>
          <div className="mt-1 truncate font-mono text-[10px] text-muted/80">
            {state.phoneHost || strings.noPhone}
          </div>
          <button
            onClick={toggleLang}
            className="mt-3 w-full rounded-lg border border-stroke bg-bg2 px-2 py-1.5 text-[11px] font-semibold text-muted hover:text-ink"
          >
            {getLang() === 'fa' ? 'English' : 'فارسی'}
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="relative flex-1 overflow-y-auto bg-bg0">
        {state.error && (
          <div className="mx-6 mt-4 flex items-start justify-between gap-3 rounded-xl border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
            <span>{errorText(state.error)}</span>
            <button onClick={() => void netbridge.refresh()} className="shrink-0 opacity-70 hover:opacity-100">
              <X size={16} />
            </button>
          </div>
        )}

        {tab === 'home' && <HomeView key={getLang()} netbridge={netbridge} />}
        {tab === 'device' && <DeviceView key={getLang()} netbridge={netbridge} />}
        {tab === 'guide' && <GuideView key={getLang()} />}
        {tab === 'settings' && <SettingsView key={getLang()} netbridge={netbridge} />}
      </main>
    </div>
  )
}

function TabButton({
  active,
  onClick,
  icon,
  label
}: {
  active: boolean
  onClick: () => void
  icon: JSX.Element
  label: string
}): JSX.Element {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition ${
        active
          ? 'bg-bg2 text-brand'
          : 'text-muted hover:bg-bg2/50 hover:text-ink'
      }`}
    >
      {icon}
      {label}
    </button>
  )
}

function StatusDot({ active }: { active: boolean }): JSX.Element {
  const strings = t()
  return (
    <span className="flex items-center gap-1.5">
      <span
        className={`h-2 w-2 rounded-full ${active ? 'bg-ok shadow-[0_0_8px_#34D399]' : 'bg-muted/50'}`}
      />
      <span className={active ? 'text-ok' : 'text-muted'}>
        {active ? strings.connected : strings.disconnected}
      </span>
    </span>
  )
}

type NetBridgeHook = ReturnType<typeof useNetBridge>

function HomeView({ netbridge }: { netbridge: NetBridgeHook }): JSX.Element {
  const { state } = netbridge
  const [busy, setBusy] = useState(false)
  const strings = t()

  const toggle = async (): Promise<void> => {
    setBusy(true)
    try {
      if (state.connected) await netbridge.disconnect()
      else await netbridge.connect()
    } finally {
      setBusy(false)
    }
  }

  const ring = state.connected ? 'border-brand shadow-glow' : 'border-stroke'
  const pulse = state.connected ? 'animate-pulse' : ''

  return (
    <div className="flex flex-col items-center px-8 py-10">
      <h1 className="text-2xl font-extrabold text-ink">{strings.homeTitle}</h1>
      <p className="mt-1 text-sm text-muted">{strings.homeSubtitle}</p>

      <button
        onClick={() => void toggle()}
        disabled={busy || state.connecting}
        className={`relative mt-10 flex h-52 w-52 items-center justify-center rounded-full border-4 bg-gradient-to-b from-bg2 to-bg1 transition ${ring} ${pulse} disabled:opacity-60`}
      >
        <div className="flex flex-col items-center gap-2">
          <Power
            size={44}
            className={state.connected ? 'text-brand' : 'text-muted'}
          />
          <span
            className={`text-sm font-bold ${state.connected ? 'text-brand' : 'text-muted'}`}
          >
            {state.connecting
              ? strings.connecting
              : state.connected
                ? strings.disconnect
                : strings.connect}
          </span>
        </div>
      </button>

      {!state.paired && state.phoneHost && (
        <p className="mt-4 text-xs text-warn">{strings.notPaired}</p>
      )}
      {!state.phoneHost && (
        <p className="mt-4 text-xs text-warn">{strings.findPhoneFirst}</p>
      )}

      <div className="mt-10 grid w-full max-w-3xl grid-cols-2 gap-4 md:grid-cols-4">
        <Pill
          icon={state.vpnActive ? <ShieldCheck size={16} /> : <ShieldOff size={16} />}
          label={strings.phoneVpn}
          value={state.vpnActive ? strings.active : strings.inactive}
          tone={state.vpnActive ? 'ok' : 'warn'}
        />
        <Pill
          icon={state.phoneSharing ? <Wifi size={16} /> : <WifiOff size={16} />}
          label={strings.phoneShare}
          value={state.phoneSharing ? strings.active : strings.inactive}
          tone={state.phoneSharing ? 'ok' : 'muted'}
        />
        <Pill
          icon={<Cable size={16} />}
          label={strings.linkPath}
          value={transportLabel(state.transport)}
          tone={state.transport === 'NONE' ? 'warn' : 'brand'}
        />
        <Pill
          icon={<Cable size={16} />}
          label={strings.systemProxy}
          value={state.systemProxyOn ? strings.on : strings.off}
          tone={state.systemProxyOn ? 'ok' : 'muted'}
        />
      </div>

      {state.internetReachable === false && state.paired && (
        <div className="mt-4 w-full max-w-3xl rounded-xl border border-warn/40 bg-warn/10 px-4 py-3 text-xs text-warn">
          {strings.phoneNoInternet}
        </div>
      )}

      {state.phoneWarning && (
        <div className="mt-4 w-full max-w-3xl rounded-xl border border-warn/40 bg-warn/10 px-4 py-3 text-xs text-warn">
          {warningText(state.phoneWarning)}
        </div>
      )}

      <Checklist state={state} />

      <div className="mt-6 grid w-full max-w-3xl grid-cols-1 gap-4 sm:grid-cols-4">
        <Stat
          icon={<ArrowUp size={16} className="text-accent" />}
          label={strings.uploadPc}
          value={formatBytes(state.bytesIn)}
        />
        <Stat
          icon={<ArrowDown size={16} className="text-ok" />}
          label={strings.downloadPc}
          value={formatBytes(state.bytesOut)}
        />
        <Stat
          icon={<Activity size={16} className="text-brand" />}
          label={strings.phoneClients}
          value={String(state.phoneClientsDetail.length || state.phoneClients)}
        />
        <Stat
          icon={<Link2 size={16} className="text-brand" />}
          label={strings.chainConns}
          value={String(state.chainConnections)}
        />
      </div>

      <div className="card mt-6 w-full max-w-3xl p-5">
        <div className="mb-3 flex items-center justify-between">
          <span className="text-sm font-bold text-ink">{strings.clientsOnPhone}</span>
          <span className="text-[11px] text-muted">
            {strings.activeCount.replace('%d', String(state.phoneClientsDetail.length || state.phoneClients))}
          </span>
        </div>
        {state.phoneClientsDetail.length === 0 ? (
          <p className="text-xs leading-6 text-muted">{strings.noClientsYet}</p>
        ) : (
          <div className="flex flex-col gap-2">
            {state.phoneClientsDetail.map((c) => (
              <div
                key={c.address}
                className="flex items-center justify-between rounded-xl border border-stroke bg-bg2 px-4 py-2.5"
              >
                <span className="font-mono text-xs text-ink">{c.address}</span>
                <span className="flex items-center gap-4 text-[11px] text-muted">
                  <span>
                    <span className="text-accent">↑ </span>
                    {formatBytes(c.bytesUp)}
                  </span>
                  <span>
                    <span className="text-ok">↓ </span>
                    {formatBytes(c.bytesDown)}
                  </span>
                  <span className={c.connections > 0 ? 'text-ok' : ''}>
                    {strings.connections.replace('%d', String(c.connections))}
                  </span>
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card mt-6 w-full max-w-3xl p-5">
        <div className="mb-3 flex items-center justify-between">
          <span className="text-sm font-bold text-ink">{strings.trafficPath}</span>
          <span className="text-[11px] text-muted">{strings.noRoot}</span>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted" dir={isFa() ? 'rtl' : 'ltr'}>
          <Chip>{strings.windowsApps}</Chip>
          <Arrow className="text-brand">{isFa() ? '←' : '→'}</Arrow>
          <Chip>127.0.0.1:{state.localPort}</Chip>
          <Arrow className="text-brand">{isFa() ? '←' : '→'}</Arrow>
          <Chip>
            {strings.phonePort} :{state.phoneHttpPort}
          </Chip>
          <Arrow className="text-brand">{isFa() ? '←' : '→'}</Arrow>
          <Chip>{strings.vpnPhone}</Chip>
          <Arrow className="text-brand">{isFa() ? '←' : '→'}</Arrow>
          <Chip>{strings.internet}</Chip>
        </div>
      </div>
    </div>
  )
}

function Checklist({ state }: { state: NetBridgeHook['state'] }): JSX.Element {
  const strings = t()
  const steps: Array<{ label: string; ok: boolean; fail: string }> = [
    { label: strings.stepPhone, ok: !!state.phoneHost, fail: strings.stepPhoneFail },
    { label: strings.stepPaired, ok: state.paired, fail: strings.stepPairedFail },
    { label: strings.stepVpn, ok: state.vpnActive, fail: strings.stepVpnFail },
    { label: strings.stepLink, ok: state.transport !== 'NONE', fail: strings.stepLinkFail },
    { label: strings.stepShare, ok: state.phoneSharing, fail: strings.stepShareFail },
    {
      label: strings.stepConnected,
      ok: state.connected && state.systemProxyOn,
      fail: strings.stepConnectedFail
    }
  ]

  return (
    <div className="card mt-6 w-full max-w-3xl p-5">
      <div className="text-sm font-bold text-ink">{strings.checklistTitle}</div>
      <p className="mb-3 text-xs text-muted">{strings.checklistSubtitle}</p>
      <div className="flex flex-col gap-2">
        {steps.map((step) => (
          <div
            key={step.label}
            className={`flex items-start gap-3 rounded-xl border px-4 py-2.5 text-sm ${
              step.ok
                ? 'border-ok/30 bg-ok/10 text-ok'
                : 'border-danger/40 bg-danger/10 text-danger'
            }`}
          >
            {step.ok ? (
              <Check size={16} className="mt-0.5 shrink-0" />
            ) : (
              <CircleX size={16} className="mt-0.5 shrink-0" />
            )}
            <span className="leading-6">{step.ok ? step.label : step.fail}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function GuideView(): JSX.Element {
  const strings = t()
  return (
    <div className="px-8 py-10">
      <h1 className="text-2xl font-extrabold text-ink">{strings.guideTitle}</h1>
      <p className="mt-1 text-sm text-muted">{strings.guideSubtitle}</p>

      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <GuideCard
          icon={<Cable size={20} />}
          title={strings.guideUsb}
          steps={[
            strings.guideUsb1,
            strings.guideUsb2,
            strings.guideUsb3,
            strings.guideUsb4,
            strings.guideUsb5,
            strings.guideUsb6
          ]}
          note={strings.guideNote}
        />
        <GuideCard
          icon={<Wifi size={20} />}
          title={strings.guideHotspot}
          steps={[
            strings.guideHotspot1,
            strings.guideHotspot2,
            strings.guideHotspot3,
            strings.guideHotspot4,
            strings.guideHotspot5,
            strings.guideHotspot6
          ]}
        />
      </div>
    </div>
  )
}

function GuideCard({
  icon,
  title,
  steps,
  note
}: {
  icon: JSX.Element
  title: string
  steps: string[]
  note?: string
}): JSX.Element {
  return (
    <div className="card p-5">
      <div className="mb-4 flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand/15 text-brand">
          {icon}
        </div>
        <span className="text-sm font-bold text-ink">{title}</span>
      </div>
      <ol className="flex flex-col gap-2 text-sm leading-6 text-muted">
        {steps.map((s) => (
          <li key={s} className="rounded-lg border border-stroke bg-bg2 px-3 py-2">
            {s}
          </li>
        ))}
      </ol>
      {note && (
        <p className="mt-4 rounded-lg border border-warn/40 bg-warn/10 px-3 py-2 text-xs text-warn">
          {note}
        </p>
      )}
    </div>
  )
}

function DeviceView({ netbridge }: { netbridge: NetBridgeHook }): JSX.Element {
  const { state } = netbridge
  const [code, setCode] = useState('')
  const [manual, setManual] = useState('')
  const [busy, setBusy] = useState(false)
  const strings = t()

  const doPair = async (): Promise<void> => {
    if (code.trim().length !== 6) return
    setBusy(true)
    try {
      await netbridge.pair(code)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="px-8 py-10">
      <h1 className="text-2xl font-extrabold text-ink">{strings.deviceTitle}</h1>
      <p className="mt-1 text-sm text-muted">{strings.deviceSubtitle}</p>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <button
          className="btn-primary"
          onClick={() => void netbridge.scan()}
          disabled={state.scanning}
        >
          <RefreshCw size={16} className={state.scanning ? 'animate-spin' : ''} />
          {state.scanning ? strings.scanning : strings.scanPhone}
        </button>
        {state.phoneHost && (
          <span className="rounded-lg border border-stroke bg-bg2 px-3 py-2 font-mono text-xs text-brand">
            {state.phoneHost}
          </span>
        )}
      </div>

      <div className="card mt-5 p-5">
        <div className="mb-3 text-sm font-bold text-ink">{strings.foundPhones}</div>
        {state.candidates.length === 0 ? (
          <p className="text-sm leading-6 text-muted">{strings.nothingFound}</p>
        ) : (
          <div className="flex flex-col gap-2">
            {state.candidates.map((phone) => (
              <button
                key={phone.address}
                onClick={() => void netbridge.select(phone.address)}
                className={`flex items-center justify-between rounded-xl border px-4 py-3 text-sm transition ${
                  state.phoneHost === phone.address
                    ? 'border-brand/60 bg-brand/10 text-brand'
                    : 'border-stroke bg-bg2 text-ink hover:border-brand/40'
                }`}
              >
                <span className="font-mono">{phone.address}</span>
                <span className="flex items-center gap-2 text-xs">
                  {phone.reachable === false && (
                    <span className="text-danger">{strings.noAnswer}</span>
                  )}
                  <span className="text-muted">{phone.name}</span>
                </span>
              </button>
            ))}
          </div>
        )}

        <div className="mt-4 flex gap-2">
          <input
            className="input font-mono"
            placeholder={strings.manualIp}
            value={manual}
            onChange={(e) => setManual(e.target.value)}
            dir="ltr"
          />{' '}
          <button
            className="btn-ghost shrink-0"
            onClick={() => void netbridge.manual(manual)}
            disabled={!manual.trim()}
          >
            {strings.select}
          </button>
        </div>
      </div>

      <div className="card mt-5 p-5">
        <div className="mb-1 flex items-center gap-2 text-sm font-bold text-ink">
          {state.paired ? <Check size={16} className="text-ok" /> : <Unlink size={16} className="text-warn" />}
          {strings.pairing}
        </div>
        <p className="mb-4 text-xs text-muted">{strings.pairingHint}</p>
        {state.paired ? (
          <div className="flex items-center justify-between rounded-xl border border-ok/40 bg-ok/10 px-4 py-3">
            <span className="text-sm text-ok">{strings.phonePaired}</span>
            <button className="btn-ghost !py-1.5 text-xs" onClick={() => void netbridge.forgetPairing()}>
              {strings.forget}
            </button>
          </div>
        ) : (
          <div className="flex gap-2">
            <input
              className="input text-center font-mono tracking-[0.4em]"
              placeholder="······"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              dir="ltr"
            />
            <button
              className="btn-primary shrink-0"
              onClick={() => void doPair()}
              disabled={busy || code.length !== 6 || !state.phoneHost}
            >
              {busy ? '…' : strings.pairing}
            </button>
          </div>
        )}
      </div>

      <Checklist state={state} />
    </div>
  )
}

function SettingsView({ netbridge }: { netbridge: NetBridgeHook }): JSX.Element {
  const { state } = netbridge
  const [port, setPort] = useState(String(state.localPort))
  const [saved, setSaved] = useState(false)
  const strings = t()

  const savePort = async (): Promise<void> => {
    const n = Number(port)
    if (!Number.isInteger(n) || n < 1 || n > 65535) return
    await netbridge.setLocalPort(n)
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  return (
    <div className="px-8 py-10">
      <h1 className="text-2xl font-extrabold text-ink">{strings.tabSettings}</h1>
      <p className="mt-1 text-sm text-muted">{strings.settingsSubtitle}</p>

      <div className="card mt-6 p-5">
        <label className="label">{strings.localProxyPort}</label>
        <div className="flex gap-2">
          <input
            className="input font-mono"
            value={port}
            onChange={(e) => setPort(e.target.value.replace(/\D/g, ''))}
            dir="ltr"
          />
          <button className="btn-ghost shrink-0" onClick={() => void savePort()}>
            {saved ? strings.saved : strings.save}
          </button>
        </div>
        <p className="mt-2 text-xs text-muted">
          {strings.windowsConnectsTo} <span className="font-mono">127.0.0.1:{port}</span>
        </p>
      </div>

      <div className="card mt-4 flex items-center justify-between p-5">
        <div>
          <div className="text-sm font-bold text-ink">{strings.autoSystemProxy}</div>
          <p className="mt-1 text-xs text-muted">{strings.autoSystemProxyDesc}</p>
        </div>
        <button
          className={`relative h-7 w-12 rounded-full transition ${
            state.systemProxyOn ? 'bg-brand' : 'bg-stroke'
          }`}
          onClick={() => void netbridge.setSystemProxy(!state.systemProxyOn)}
        >
          <span
            className={`absolute top-1 h-5 w-5 rounded-full bg-white transition-all ${
              isFa()
                ? (state.systemProxyOn ? 'right-6' : 'right-1')
                : (state.systemProxyOn ? 'left-6' : 'left-1')
            }`}
          />
        </button>
      </div>

      <div className="card mt-4 p-5 text-xs leading-6 text-muted">
        <div className="mb-2 text-sm font-bold text-ink">{strings.tips}</div>
        • {strings.tipVpn}
        <br />
        • {strings.tipUsb}
        <br />
        • {strings.tipMetered}
        <br />
        • {strings.tipFirewall}
        <br />
        • {strings.versionLine}
      </div>

      <div className="card mt-4 flex flex-col items-start gap-3 p-5">
        <div className="text-sm font-bold text-ink">{strings.builtBy}</div>
        <div className="flex flex-wrap gap-2">
          <button
            className="btn-ghost"
            onClick={() => void window.netbridge.openExternal(strings.githubUrl)}
          >
            <Link2 size={16} />
            {strings.linkGithub}
          </button>
          <button
            className="btn-ghost"
            onClick={() => void window.netbridge.openExternal(strings.linkedinUrl)}
          >
            <Link2 size={16} />
            {strings.linkLinkedin}
          </button>
        </div>
      </div>
    </div>
  )
}

function Pill({
  icon,
  label,
  value,
  tone
}: {
  icon: JSX.Element
  label: string
  value: string
  tone: 'ok' | 'warn' | 'brand' | 'muted'
}): JSX.Element {
  const colors: Record<string, string> = {
    ok: 'text-ok border-ok/30 bg-ok/10',
    warn: 'text-warn border-warn/30 bg-warn/10',
    brand: 'text-brand border-brand/30 bg-brand/10',
    muted: 'text-muted border-stroke bg-bg1'
  }
  return (
    <div className={`flex flex-col gap-1 rounded-2xl border px-4 py-3 ${colors[tone]}`}>
      <div className="flex items-center gap-1.5 text-[11px] opacity-80">
        {icon}
        {label}
      </div>
      <div className="text-lg font-extrabold">{value}</div>
    </div>
  )
}

function Stat({
  icon,
  label,
  value
}: {
  icon: JSX.Element
  label: string
  value: string
}): JSX.Element {
  return (
    <div className="card flex items-center gap-3 px-4 py-3">
      {icon}
      <div>
        <div className="text-[11px] text-muted">{label}</div>
        <div className="text-sm font-bold text-ink">{value}</div>
      </div>
    </div>
  )
}

function Chip({ children }: { children: React.ReactNode }): JSX.Element {
  return (
    <span className="rounded-lg border border-stroke bg-bg2 px-2.5 py-1.5 font-mono text-[11px] text-ink">
      {children}
    </span>
  )
}

function Arrow({ children, className }: { children: React.ReactNode; className?: string }): JSX.Element {
  return <span className={`px-0.5 font-bold ${className ?? ''}`}>{children}</span>
}
