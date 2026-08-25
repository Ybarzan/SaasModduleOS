import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useAuthStore } from '../stores/auth';

// Barre CTA fixe en bas d'écran, visible uniquement sur mobile (md:hidden),
// pour les pages publiques marketing. Masquée si l'utilisateur est déjà connecté.
export default function StickyMobileCta({
  label = 'Créer un compte gratuit',
  to = '/register',
}: {
  label?: string;
  to?: string;
}) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  if (isAuthenticated) return null;

  return (
    <div className="md:hidden fixed bottom-0 left-0 right-0 z-40 p-3 bg-surface/95 backdrop-blur-sm border-t border-line shadow-[0_-4px_20px_rgba(0,0,0,0.08)] pb-[calc(0.75rem+env(safe-area-inset-bottom))]">
      <Link
        to={to}
        className="flex items-center justify-center gap-2 w-full bg-accent text-white font-bold py-3.5 rounded-none shadow-lg shadow-accent/25 active:scale-[0.98] transition-transform"
      >
        {label}
        <ArrowRight size={18} />
      </Link>
    </div>
  );
}
