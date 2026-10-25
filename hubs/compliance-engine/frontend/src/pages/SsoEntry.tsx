import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Loader2, XCircle } from 'lucide-react';
import { useAuthStore, type PlanId, type UserRole } from '../stores/auth';

/**
 * Point d'entrée du relais SSO depuis Praxio (voir hubs/portal/backend
 * SsoService) : la réponse de connexion compliance-engine est encodée dans
 * le fragment d'URL (#, jamais envoyé au serveur ni journalisé), décodée ici
 * côté client, puis injectée dans le store d'auth avec exactement le même
 * mapping que Login.tsx -- aucun nouvel appel réseau, aucune nouvelle
 * logique de session.
 */
interface SsoPayload {
  token: string;
  refreshToken: string;
  userId: string;
  email: string;
  plan?: PlanId;
  role?: UserRole;
  fullName?: string;
}

function decodeSsoPayload(hash: string): SsoPayload {
  const raw = hash.replace(/^#/, '');
  const base64 = raw.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes)) as SsoPayload;
}

export default function SsoEntry() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [error, setError] = useState('');

  useEffect(() => {
    // Capturé de façon synchrone (avant le déféré ci-dessous) : StrictMode
    // invoque cet effet deux fois en dev, et la première invocation efface
    // le fragment (navigate() vers /dashboard) avant que la seconde ne
    // puisse le relire -- capturer la valeur ici garantit que les deux
    // invocations décodent le même fragment valide plutôt que la seconde
    // n'échoue sur un fragment déjà vidé.
    const hash = window.location.hash;
    // Déféré en microtâche : voir la même note dans hubs/portal/src/pages/Dashboard.tsx
    // (éviter un setState synchrone dans l'effet, react-hooks/set-state-in-effect).
    Promise.resolve().then(() => {
      try {
        const data = decodeSsoPayload(hash);
        const nameParts = (data.fullName || data.email.split('@')[0]).split(' ');
        const firstName = nameParts[0] || '';
        const lastName = nameParts.slice(1).join(' ') || '';

        login(data.token, data.refreshToken, {
          id: data.userId,
          email: data.email,
          firstName,
          lastName,
          role: data.role || 'USER',
          plan: data.plan || 'FREE',
        });
        navigate('/dashboard', { replace: true });
      } catch {
        setError('Lien de connexion invalide ou expiré. Reconnectez-vous depuis Praxio.');
      }
    });
  }, [login, navigate]);

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-surface rounded-lg shadow-lg p-8 text-center">
        {error ? (
          <>
            <div className="w-16 h-16 bg-danger/10 rounded-full flex items-center justify-center mx-auto mb-4">
              <XCircle className="w-8 h-8 text-danger" />
            </div>
            <h2 className="text-2xl font-bold text-ink mb-2">
              Connexion impossible
            </h2>
            <p className="text-ink-soft mb-6">{error}</p>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 bg-accent text-white px-6 py-3 rounded-lg font-semibold hover:bg-accent-strong transition-colors"
            >
              Se connecter
            </Link>
          </>
        ) : (
          <>
            <Loader2 className="w-12 h-12 text-accent mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-bold text-ink mb-2">
              Connexion en cours...
            </h2>
            <p className="text-ink-soft">Depuis Praxio, sans ressaisir vos identifiants</p>
          </>
        )}
      </div>
    </div>
  );
}
