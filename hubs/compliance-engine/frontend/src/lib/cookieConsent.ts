// Consentement cookies (CNIL/RGPD) : opt-in explicite avant tout depot de cookie
// de mesure d'audience non essentiel. GoogleAnalytics.tsx ne charge gtag.js que
// si le consentement vaut 'accepted'.
export type ConsentValue = 'accepted' | 'rejected';

const STORAGE_KEY = 'incokalk-cookie-consent';
const EVENT_NAME = 'incokalk-cookie-consent-changed';

export function getConsent(): ConsentValue | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'accepted' || v === 'rejected' ? v : null;
  } catch {
    return null;
  }
}

export function setConsent(value: ConsentValue) {
  try {
    localStorage.setItem(STORAGE_KEY, value);
  } catch {
    // stockage indisponible (navigation privée stricte...) -- le bandeau
    // reviendra a chaque visite, ce qui reste conforme (pas de traceur pose).
  }
  window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: value }));
}

export function onConsentChange(cb: (value: ConsentValue) => void): () => void {
  const handler = (e: Event) => cb((e as CustomEvent<ConsentValue>).detail);
  window.addEventListener(EVENT_NAME, handler);
  return () => window.removeEventListener(EVENT_NAME, handler);
}

const REOPEN_EVENT_NAME = 'incokalk-open-cookie-settings';

export function reopenCookieSettings() {
  window.dispatchEvent(new Event(REOPEN_EVENT_NAME));
}

export function onReopenCookieSettings(cb: () => void): () => void {
  window.addEventListener(REOPEN_EVENT_NAME, cb);
  return () => window.removeEventListener(REOPEN_EVENT_NAME, cb);
}
