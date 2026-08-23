import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { getConsent, onConsentChange } from '../lib/cookieConsent';

// GA4, charge uniquement si VITE_GA_MEASUREMENT_ID est renseignee (voir .env.example)
// ET si l'utilisateur a explicitement accepte les cookies de mesure d'audience
// (bandeau CookieConsent). Sans l'un des deux, ce composant ne fait rien --
// aucun script tiers charge, aucun cookie depose. Suit les changements de route
// une fois le consentement donne (SPA : pas de rechargement de page, donc pas
// de page_view automatique sans ce cablage).
const GA_ID = import.meta.env.VITE_GA_MEASUREMENT_ID as string | undefined;

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

let loaded = false;

function loadGtag(id: string) {
  if (loaded) return;
  loaded = true;

  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${id}`;
  document.head.appendChild(script);

  window.dataLayer = window.dataLayer || [];
  window.gtag = function gtag(...args: unknown[]) {
    window.dataLayer!.push(args);
  };
  window.gtag('js', new Date());
  // send_page_view desactive : on envoie nous-memes les page_view au changement
  // de route (voir useEffect ci-dessous), pour eviter le doublon au chargement.
  window.gtag('config', id, { send_page_view: false });
}

export default function GoogleAnalytics() {
  const location = useLocation();
  const [consented, setConsented] = useState(() => getConsent() === 'accepted');

  useEffect(() => {
    return onConsentChange((value) => setConsented(value === 'accepted'));
  }, []);

  useEffect(() => {
    if (GA_ID && consented) loadGtag(GA_ID);
  }, [consented]);

  useEffect(() => {
    if (!GA_ID || !consented || !window.gtag) return;
    window.gtag('event', 'page_view', {
      page_path: location.pathname + location.search,
      page_location: window.location.href,
      page_title: document.title,
    });
  }, [location, consented]);

  return null;
}
