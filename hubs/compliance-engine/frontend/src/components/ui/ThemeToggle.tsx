import { useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { Sun, Moon } from 'lucide-react';
import { getTheme, toggleTheme, type Theme } from '../../lib/theme';

const ThemeToggle = () => {
  // Le theme reel est deja applique par le script inline d'index.html avant le
  // premier rendu ; l'initialiseur paresseux se contente de lire le meme etat
  // pour que l'affichage du bouton soit coherent des le premier rendu.
  const [theme, setThemeState] = useState<Theme>(() => getTheme());
  const reduceMotion = useReducedMotion();

  const handleClick = () => setThemeState(toggleTheme());
  const isDark = theme === 'dark';

  return (
    <button
      onClick={handleClick}
      aria-label={isDark ? 'Passer en thème clair' : 'Passer en thème sombre'}
      aria-pressed={isDark}
      className="relative w-[52px] h-[28px] rounded-full bg-surface-2 border border-line flex items-center px-[3px] shrink-0"
    >
      <motion.span
        className="absolute w-[22px] h-[22px] rounded-full bg-accent flex items-center justify-center text-white"
        animate={{ x: isDark ? 24 : 0 }}
        transition={reduceMotion ? { duration: 0 } : { type: 'spring', stiffness: 500, damping: 30 }}
      >
        {isDark ? <Moon size={12} /> : <Sun size={12} />}
      </motion.span>
    </button>
  );
};

export default ThemeToggle;
