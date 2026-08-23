// Theme clair/sombre, choix utilisateur persistant. index.html applique le
// theme stocke avant le premier rendu React (script inline) pour eviter un
// flash du mauvais theme -- ce module doit rester en phase avec ce script.
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'incokalk-theme';
const EVENT_NAME = 'incokalk-theme-changed';

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export function getTheme(): Theme {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    if (v === 'light' || v === 'dark') return v;
  } catch {
    // stockage indisponible -- retombe sur la preference systeme
  }
  return systemPrefersDark() ? 'dark' : 'light';
}

export function setTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark');
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // stockage indisponible -- le theme ne persistera pas au prochain chargement
  }
  window.dispatchEvent(new CustomEvent<Theme>(EVENT_NAME, { detail: theme }));
}

export function toggleTheme(): Theme {
  const next: Theme = getTheme() === 'dark' ? 'light' : 'dark';
  setTheme(next);
  return next;
}

export function onThemeChange(cb: (theme: Theme) => void): () => void {
  const handler = (e: Event) => cb((e as CustomEvent<Theme>).detail);
  window.addEventListener(EVENT_NAME, handler);
  return () => window.removeEventListener(EVENT_NAME, handler);
}
