import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const DEMO_KEY = 'auvex_demo_key'
const GITHUB = 'https://github.com/gauthambinoy/auvex'

const FEATURES = [
  { icon: 'ti-eye-off', title: 'Redact', text: '23 detectors mask PII, secrets and gov-IDs — in the prompt and the response — before anything leaves.' },
  { icon: 'ti-shield-x', title: 'Block', text: 'Prompt-injection & jailbreak detection stops adversarial calls with a 403, before they reach the model.' },
  { icon: 'ti-gavel', title: 'Govern', text: 'Per-tenant allow / deny / redact policy by data-type, model or user. Most-restrictive wins.' },
  { icon: 'ti-lock-check', title: 'Audit', text: 'A SHA-256 hash-chained, tamper-proof record of every request — the regulator story, provable.' },
  { icon: 'ti-route', title: 'Route', text: 'One alias switches providers — OpenAI, Anthropic, Gemini, Azure — with automatic failover.' },
  { icon: 'ti-radar', title: 'Observe', text: 'Live monitoring, per-model cost & usage, leak breakdowns, SIEM streaming and spend alerts.' },
]

const STATS = [
  { value: '165', label: 'automated tests' },
  { value: '23', label: 'data detectors' },
  { value: '4', label: 'AI providers' },
  { value: '5', label: 'languages' },
]

const CODE = `from openai import OpenAI

client = OpenAI(
    base_url="https://your-gateway/v1",   # ← the only change
    api_key="auvex_sk_...",
)
# every call is now redacted, governed and audited.`

export function Landing() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)

  async function tryDemo() {
    setBusy(true)
    try {
      await login(DEMO_KEY)
    } catch {
      navigate('/login')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ position: 'relative', zIndex: 1, minHeight: '100vh' }}>
      {/* ambient glow */}
      <div
        aria-hidden
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: -1,
          pointerEvents: 'none',
          background:
            'radial-gradient(60vmax 50vmax at 50% -15%, rgba(124,92,255,0.28), transparent 60%),' +
            'radial-gradient(45vmax 45vmax at 100% 10%, rgba(61,139,255,0.18), transparent 60%)',
        }}
      />

      {/* nav */}
      <nav
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          maxWidth: 1140,
          margin: '0 auto',
          padding: '22px 24px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            className="logo"
            style={{
              width: 32,
              height: 32,
              borderRadius: 10,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <i className="ti ti-shield-lock" style={{ color: '#fff', fontSize: 18 }} aria-hidden />
          </div>
          <span style={{ fontSize: 19, fontWeight: 700, letterSpacing: '-0.02em' }}>Auvex</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <a href={GITHUB} target="_blank" rel="noreferrer" className="lp-btn lp-btn-ghost">
            <i className="ti ti-brand-github" aria-hidden /> GitHub
          </a>
          <button className="lp-btn lp-btn-primary" onClick={tryDemo} disabled={busy}>
            {busy ? 'Loading…' : 'Try the demo'} <i className="ti ti-arrow-right" aria-hidden />
          </button>
        </div>
      </nav>

      {/* hero */}
      <header style={{ maxWidth: 880, margin: '0 auto', padding: '70px 24px 40px', textAlign: 'center' }}>
        <div className="lp-pill">
          <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--color-text-success)', display: 'inline-block' }} />
          Drop-in AI governance · live demo
        </div>
        <h1
          style={{
            fontSize: 'clamp(38px, 6vw, 64px)',
            lineHeight: 1.05,
            fontWeight: 800,
            letterSpacing: '-0.035em',
            margin: '22px 0 0',
          }}
        >
          Govern every AI call
          <br />
          <span className="lp-grad">your company makes.</span>
        </h1>
        <p
          style={{
            fontSize: 'clamp(16px, 2.2vw, 20px)',
            color: 'var(--color-text-secondary)',
            maxWidth: 660,
            margin: '22px auto 0',
            lineHeight: 1.6,
          }}
        >
          Auvex is a drop-in gateway that redacts secrets, blocks prompt injections, enforces policy
          and keeps a tamper-proof audit of every LLM request — across OpenAI, Anthropic, Gemini and
          Azure. Change one URL.
        </p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', marginTop: 32, flexWrap: 'wrap' }}>
          <button className="lp-btn lp-btn-primary lp-btn-lg" onClick={tryDemo} disabled={busy}>
            <i className="ti ti-player-play" aria-hidden /> {busy ? 'Loading…' : 'Try the live demo'}
          </button>
          <a href={GITHUB} target="_blank" rel="noreferrer" className="lp-btn lp-btn-ghost lp-btn-lg">
            <i className="ti ti-brand-github" aria-hidden /> View on GitHub
          </a>
        </div>
        <div style={{ display: 'flex', gap: 28, justifyContent: 'center', marginTop: 48, flexWrap: 'wrap' }}>
          {STATS.map((s) => (
            <div key={s.label} style={{ textAlign: 'center' }}>
              <div className="lp-grad" style={{ fontSize: 30, fontWeight: 800, letterSpacing: '-0.03em' }}>
                {s.value}
              </div>
              <div style={{ fontSize: 13, color: 'var(--color-text-tertiary)' }}>{s.label}</div>
            </div>
          ))}
        </div>
      </header>

      {/* features */}
      <section style={{ maxWidth: 1080, margin: '0 auto', padding: '40px 24px' }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
            gap: 18,
          }}
        >
          {FEATURES.map((f) => (
            <div key={f.title} className="card lp-feature">
              <div className="lp-feature-icon">
                <i className={`ti ${f.icon}`} aria-hidden />
              </div>
              <div style={{ fontSize: 18, fontWeight: 700, margin: '14px 0 6px' }}>{f.title}</div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>{f.text}</div>
            </div>
          ))}
        </div>
      </section>

      {/* how it works */}
      <section style={{ maxWidth: 900, margin: '0 auto', padding: '50px 24px', textAlign: 'center' }}>
        <div className="lp-eyebrow">How it works</div>
        <h2 style={{ fontSize: 'clamp(26px, 4vw, 38px)', fontWeight: 800, letterSpacing: '-0.03em', margin: '10px 0 8px' }}>
          One URL change. Total control.
        </h2>
        <p style={{ color: 'var(--color-text-secondary)', maxWidth: 560, margin: '0 auto 28px', lineHeight: 1.6 }}>
          Auvex is OpenAI-compatible, so every app, SDK and tool already works — just point it at your
          gateway. No rewrites, no SDK lock-in.
        </p>
        <pre className="card mono lp-code">{CODE}</pre>
      </section>

      {/* final CTA */}
      <section style={{ maxWidth: 760, margin: '0 auto', padding: '30px 24px 70px', textAlign: 'center' }}>
        <div className="card lp-cta">
          <h2 style={{ fontSize: 28, fontWeight: 800, letterSpacing: '-0.03em', margin: 0 }}>
            See it catch a leak in real time.
          </h2>
          <p style={{ color: 'var(--color-text-secondary)', margin: '12px 0 24px' }}>
            The live demo is seeded with real governed traffic — open it and watch.
          </p>
          <button className="lp-btn lp-btn-primary lp-btn-lg" onClick={tryDemo} disabled={busy}>
            <i className="ti ti-player-play" aria-hidden /> {busy ? 'Loading…' : 'Try the live demo'}
          </button>
        </div>
      </section>

      <footer
        style={{
          borderTop: '1px solid var(--color-border-tertiary)',
          padding: '24px',
          textAlign: 'center',
          color: 'var(--color-text-tertiary)',
          fontSize: 13,
        }}
      >
        Auvex — enterprise AI gateway ·{' '}
        <a href={GITHUB} target="_blank" rel="noreferrer" style={{ color: 'var(--color-text-info)' }}>
          GitHub
        </a>{' '}
        · built with Java / Spring + React
      </footer>
    </div>
  )
}
