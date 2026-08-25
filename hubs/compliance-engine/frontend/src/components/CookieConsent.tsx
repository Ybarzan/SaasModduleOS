import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Cookie } from 'lucide-react';
import { getConsent, setConsent, onConsentChange, onReopenCookieSettings } from '../lib/cookieConsent';

// Bandeau de consentement cookies (CNIL/RGPD). S'affiche tant qu'aucun choix
// n'a ete fait ; se referme et retient le choix (localStorage) une fois
// "Accepter" ou "Refuser" clique. Reouvrable via #cookie-settings dans le footer.
export default function CookieConsent() {
  const [visible, setVisible] = useState(() => getConsent() === null);

  useEffect(() => {
    return onConsentChange(() => setVisible(false));
  }, []);

  useEffect(() => {
    return onReopenCookieSettings(() => setVisible(true));
  }, []);

  if (!visible) return null;

  return (
    <div
      role="dialog"
      aria-label="Consentement cookies"
      className="fixed bottom-0 left-0 right-0 z-[60] p-4 md:p-6 bg-surface border-t border-line shadow-[0_-8px_30px_rgba(0,0,0,0.12)]"
    >
      <div className="container-narrow mx-auto flex flex-col md:flex-row items-start md:items-center gap-4">
        <div className="flex items-start gap-3 flex-1">
          <Cookie size={22} className="text-accent shrink-0 mt-0.5" />
          <p className="text-sm text-ink-soft leading-relaxed">
            Nous utilisons des cookies strictement nécessaires au fonctionnement d'IncoKalk, et, avec
            votre accord, des cookies de mesure d'audience pour comprendre l'usage du site. Vous pouvez
            accepter ou refuser ces derniers à tout moment. Voir notre{' '}
            <Link to="/confidentialite" className="text-accent hover:underline">
              politique de confidentialité
            </Link>
            .
          </p>
        </div>
        <div className="flex items-center gap-3 shrink-0 self-end md:self-auto">
          <button
            onClick={() => setConsent('rejected')}
            className="px-5 py-2.5 rounded-none font-semibold text-sm text-ink-soft border border-line hover:bg-bg transition-colors"
          >
            Refuser
          </button>
          <button
            onClick={() => setConsent('accepted')}
            className="px-5 py-2.5 rounded-none font-semibold text-sm bg-accent text-white hover:bg-accent-strong transition-colors shadow-md shadow-accent/20"
          >
            Accepter
          </button>
        </div>
      </div>
    </div>
  );
}
