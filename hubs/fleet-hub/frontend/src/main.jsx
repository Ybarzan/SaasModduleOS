import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import * as Sentry from '@sentry/react'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider, useTheme } from './context/ThemeContext'
import { Capacitor } from '@capacitor/core'
import { StatusBar, Style as StatusBarStyle } from '@capacitor/status-bar'
import { Haptics, ImpactStyle } from '@capacitor/haptics'
import 'leaflet/dist/leaflet.css'
import '@fontsource-variable/jetbrains-mono'
import './styles.css'
import './premium.css'
import './theme.css'

const SENTRY_DSN = import.meta.env.VITE_SENTRY_DSN
if (SENTRY_DSN) {
  Sentry.init({
    dsn: SENTRY_DSN,
    environment: import.meta.env.VITE_APP_ENV || 'production',
    tracesSampleRate: 0.2,
    beforeSend(event) {
      const t = event.exception?.values?.[0]
      if (!t) return event
      if (t.type === 'ChunkLoadError') return null
      if (t.type === 'TypeError' && /failed to fetch|networkerror|load failed/i.test(t.value || '')) return null
      return event
    }
  })
}

if (Capacitor.isNativePlatform()) {
  StatusBar.setStyle({ style: StatusBarStyle.Dark })
  StatusBar.setOverlaysWebView({ overlay: true })
}

function StatusBarSync() {
  const { isDark } = useTheme()
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return
    StatusBar.setStyle({ style: isDark ? StatusBarStyle.Dark : StatusBarStyle.Light })
    StatusBar.setBackgroundColor({ color: isDark ? '#14100c' : '#e6e0d3' })
  }, [isDark])
  return null
}

window.__fhHaptics = {
  light() {
    if (Capacitor.isNativePlatform()) Haptics.impact({ style: ImpactStyle.Light })
  },
  medium() {
    if (Capacitor.isNativePlatform()) Haptics.impact({ style: ImpactStyle.Medium })
  },
  success() {
    if (Capacitor.isNativePlatform()) Haptics.notification({ type: 'SUCCESS' })
  }
}

const CrashFallback = () => (
  <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12, background: '#14100C', color: '#E8E1D2', fontFamily: '"JetBrains Mono", monospace', textAlign: 'center', padding: 24 }}>
    <div style={{ fontSize: 28, fontWeight: 700, fontFamily: '"JetBrains Mono", monospace' }}>Oups, un écran a planté.</div>
    <div style={{ color: '#CFC5B2' }}>Rechargez la page pour continuer. L'erreur a été enregistrée si la supervision est active.</div>
    <button onClick={() => window.location.reload()} style={{ marginTop: 8, padding: '10px 20px', borderRadius: 0, border: '1px solid #362C22', background: '#1C1712', color: '#E8E1D2', cursor: 'pointer', fontFamily: '"JetBrains Mono", monospace' }}>
      Recharger
    </button>
  </div>
)

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Sentry.ErrorBoundary fallback={CrashFallback}>
      <ThemeProvider>
        <StatusBarSync />
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </ThemeProvider>
    </Sentry.ErrorBoundary>
  </React.StrictMode>
)
