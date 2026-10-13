import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Point d'entrée du relais SSO depuis Praxio (voir hubs/portal/backend
 * SsoService) : la réponse de connexion fleet-hub est encodée dans le
 * fragment d'URL (#, jamais envoyé au serveur ni journalisé), décodée ici
 * côté client, puis injectée dans AuthContext via loginWithSession -- aucun
 * nouvel appel réseau, aucune nouvelle logique de session.
 */
function decodeSsoPayload(hash) {
  const raw = hash.replace(/^#/, '')
  const base64 = raw.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes))
}

export default function SsoEntry() {
  const navigate = useNavigate()
  const { loginWithSession } = useAuth()
  const [error, setError] = useState('')

  useEffect(() => {
    // Capturé de façon synchrone (avant le déféré ci-dessous) : StrictMode
    // invoque cet effet deux fois en dev, et la première invocation efface
    // le fragment (navigate() vers /) avant que la seconde ne puisse le
    // relire -- capturer la valeur ici garantit que les deux invocations
    // décodent le même fragment valide plutôt que la seconde n'échoue sur
    // un fragment déjà vidé.
    const hash = window.location.hash
    // Déféré en microtâche : voir la même note dans hubs/portal/src/pages/Dashboard.tsx
    // (éviter un setState synchrone dans l'effet, react-hooks/set-state-in-effect).
    Promise.resolve().then(() => {
      try {
        const data = decodeSsoPayload(hash)
        loginWithSession(data)
        navigate(data.role === 'SAAS_ADMIN' ? '/admin' : '/', { replace: true })
      } catch {
        setError('Lien de connexion invalide ou expiré. Reconnectez-vous depuis Praxio.')
      }
    })
  }, [loginWithSession, navigate])

  return (
    <div className="page-loading" role="status" aria-live="polite">
      {error ? <div className="alert alert-error">{error}</div> : <span className="spinner" />}
    </div>
  )
}
