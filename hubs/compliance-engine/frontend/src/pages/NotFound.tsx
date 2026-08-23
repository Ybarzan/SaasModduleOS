import { Link } from 'react-router-dom';
import { Home, Calculator, HelpCircle, ArrowRight, Compass } from 'lucide-react';
import Seo from '../components/Seo';

// Page 404 personnalisee. Limite connue des SPA sans rendu serveur : le
// serveur repond toujours 200 sur index.html quel que soit le chemin, donc
// le vrai statut HTTP 404 n'est pas envoye (contrairement a une balise
// <meta name="robots" content="noindex">, qui elle fonctionne cote client
// et empeche l'indexation de ces URL invalides).
export default function NotFound() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white flex items-center">
      <Seo
        title="Page introuvable (404)"
        description="Cette page n'existe pas ou plus."
        path={typeof window !== 'undefined' ? window.location.pathname : '/404'}
        noindex
      />
      <div className="container mx-auto px-4 py-20 max-w-2xl text-center">
        <div className="inline-flex items-center justify-center w-24 h-24 rounded-3xl bg-accent-soft border border-accent-soft mb-8">
          <Compass size={44} className="text-accent" />
        </div>
        <div className="text-7xl font-extrabold text-line mb-4">404</div>
        <h1 className="text-3xl md:text-4xl font-extrabold text-ink mb-4">
          Cette page a pris une mauvaise route
        </h1>
        <p className="text-lg text-ink-soft mb-10 max-w-lg mx-auto">
          La page que vous cherchez n'existe pas ou a été déplacée. Voici quelques
          destinations utiles pour repartir du bon pied.
        </p>

        <div className="grid sm:grid-cols-3 gap-4 mb-10">
          <Link
            to="/"
            className="card-moroccan p-6 flex flex-col items-center gap-3 hover:-translate-y-1 transition-transform"
          >
            <Home size={24} className="text-accent" />
            <span className="font-semibold text-ink">Accueil</span>
          </Link>
          <Link
            to="/pricing"
            className="card-moroccan p-6 flex flex-col items-center gap-3 hover:-translate-y-1 transition-transform"
          >
            <Calculator size={24} className="text-accent" />
            <span className="font-semibold text-ink">Tarifs</span>
          </Link>
          <Link
            to="/faq"
            className="card-moroccan p-6 flex flex-col items-center gap-3 hover:-translate-y-1 transition-transform"
          >
            <HelpCircle size={24} className="text-accent" />
            <span className="font-semibold text-ink">FAQ</span>
          </Link>
        </div>

        <Link to="/" className="btn-primary inline-flex items-center gap-2">
          Retour à l'accueil
          <ArrowRight size={18} />
        </Link>
      </div>
    </div>
  );
}
