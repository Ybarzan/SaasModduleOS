import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore, type UserRole } from '../stores/auth';

/**
 * Point d'entrée du relais SSO depuis Praxio (voir hubs/portal/backend
 * SsoService) : la réponse de connexion Finance est encodée dans le fragment
 * d'URL (#, jamais envoyé au serveur ni journalisé), décodée ici côté client,
 * puis injectée dans le store d'auth comme un login normal -- aucun nouvel
 * appel réseau, aucune nouvelle logique de session. Même principe que
 * hubs/logistix/src/pages/SsoEntry.tsx.
 */
interface SsoPayload {
  token: string;
  email: string;
  displayName: string;
  role: UserRole;
  companyId: string;
  companyName: string;
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
    // le fragment (navigate() vers /client-invoices) avant que la seconde ne
    // puisse le relire -- capturer la valeur ici garantit que les deux
    // invocations décodent le même fragment valide plutôt que la seconde
    // n'échoue sur un fragment déjà vidé.
    const hash = window.location.hash;
    // Déféré en microtâche : voir la même note dans hubs/portal/src/pages/Dashboard.tsx
    // (éviter un setState synchrone dans l'effet, react-hooks/set-state-in-effect).
    Promise.resolve().then(() => {
      try {
        const data = decodeSsoPayload(hash);
        const [firstName, ...rest] = (data.displayName || data.email.split('@')[0]).split(' ');
        login(data.token, {
          id: data.email,
          email: data.email,
          firstName,
          lastName: rest.join(' '),
          role: data.role,
          company: data.companyName,
          companyId: data.companyId,
        });
        navigate('/client-invoices', { replace: true });
      } catch {
        setError('Lien de connexion invalide ou expiré. Reconnectez-vous depuis Praxio.');
      }
    });
  }, [login, navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg text-ink px-6">
      {error ? (
        <p className="text-sm text-danger">{error}</p>
      ) : (
        <p className="text-sm text-ink-soft">Connexion…</p>
      )}
    </div>
  );
}
