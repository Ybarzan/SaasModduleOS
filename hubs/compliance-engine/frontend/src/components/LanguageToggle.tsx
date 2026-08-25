import { useLanguageStore } from '../stores/language';

/** Bascule FR/EN pour le pilote i18n (Home hero + Pricing uniquement). */
const LanguageToggle = () => {
  const language = useLanguageStore((s) => s.language);
  const toggle = useLanguageStore((s) => s.toggle);

  return (
    <button
      onClick={toggle}
      aria-label={language === 'fr' ? 'Switch to English' : 'Passer en français'}
      className="px-2.5 py-1 rounded-none text-xs font-bold text-ink-soft border border-line hover:border-accent hover:text-accent transition-colors shrink-0"
    >
      {language === 'fr' ? 'EN' : 'FR'}
    </button>
  );
};

export default LanguageToggle;
