import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'praxio-theme';

function readInitialTheme(): boolean {
  return document.documentElement.classList.contains('dark');
}

/** Bascule clair/sombre -- la classe initiale est déjà posée par le script inline de index.html (évite le flash). */
export function useTheme() {
  const [dark, setDark] = useState(readInitialTheme);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    try {
      localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
    } catch {
      // stockage indisponible (navigation privée, etc.) -- le choix ne survivra pas à un refresh
    }
  }, [dark]);

  const toggle = useCallback(() => setDark((d) => !d), []);

  return { dark, toggle };
}
