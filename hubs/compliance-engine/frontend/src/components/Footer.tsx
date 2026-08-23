import { Link } from 'react-router-dom';
import { Calculator, Mail, Phone, MapPin } from 'lucide-react';
import { reopenCookieSettings } from '../lib/cookieConsent';

const footerLinks = {
  'Produit': [
    { label: 'Les 7 Hubs métier', to: '/hubs' },
    { label: "Cas d'usage", to: '/cas-usage' },
    { label: 'Calculateur Incoterms', to: '/simulation' },
    { label: 'Dashboard', to: '/dashboard' },
    { label: 'Comparaison de devis', to: '/quotes' },
    { label: 'Suivi d\'expéditions', to: '/shipments' },
    { label: 'Intégrations ERP', to: '/erp' },
    { label: 'API pour développeurs', to: '/developers' },
  ],
  'Transport': [
    { label: 'Transporteurs', to: '/carriers' },
    { label: 'Fournisseurs', to: '/providers' },
    { label: 'Notifications', to: '/notifications' },
    { label: 'Équipe', to: '/team' },
    { label: 'Calculateur CO₂', to: '/co2' },
  ],
  'Ressources': [
    { label: 'Guide Incoterms 2020', to: '/guide-incoterms' },
    { label: "Coût d'une erreur douanière", to: '/cout-erreur-douane' },
    { label: 'FAQ', to: '/faq' },
    { label: 'Statut du service', to: '/status' },
  ],
  'Entreprise': [
    { label: 'Contactez-nous', to: 'mailto:contact@incokalk.com' },
    { label: 'Conditions d\'utilisation', to: '/cgu' },
    { label: 'Politique de confidentialité', to: '/confidentialite' },
    { label: 'Mentions légales', to: '/mentions-legales' },
  ],
};

const Footer = () => {
  return (
    <footer className="bg-surface-2 border-t border-line relative">
      <div className="container-narrow mx-auto px-4 py-16 relative z-10">
        <div className="grid grid-cols-2 md:grid-cols-6 gap-8">
          {/* Brand column */}
          <div className="col-span-2">
            <Link to="/" className="flex items-center gap-2.5 font-extrabold text-xl mb-4">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-accent to-accent-strong flex items-center justify-center shadow-md shadow-accent/20">
                <Calculator size={18} className="text-white" />
              </div>
              <span className="text-ink">Inco<span className="text-accent">Kalk</span></span>
            </Link>
            <p className="text-sm text-ink-soft leading-relaxed mb-6">
              Le bon outil, pour la bonne route et la bonne décision. Logiciel de calcul de coûts Incoterms pour les exportateurs et importateurs.
            </p>
            <div className="space-y-2.5">
              <a href="mailto:contact@incokalk.com" className="flex items-center gap-2 text-sm text-ink-soft hover:text-accent transition-colors">
                <Mail size={14} />
                contact@incokalk.com
              </a>
              <a href="tel:+33100000000" className="flex items-center gap-2 text-sm text-ink-soft hover:text-accent transition-colors">
                <Phone size={14} />
                +33 1 00 00 00 00
              </a>
              <div className="flex items-center gap-2 text-sm text-ink-soft">
                <MapPin size={14} />
                Paris, France
              </div>
            </div>
          </div>

          {/* Link columns */}
          {Object.entries(footerLinks).map(([title, links]) => (
            <div key={title}>
              <h3 className="text-sm font-bold text-ink mb-4 uppercase tracking-wide">{title}</h3>
              <ul className="space-y-2.5">
                {links.map((link) => (
                  <li key={link.label}>
                    {link.to.startsWith('mailto:') ? (
                      <a
                        href={link.to}
                        className="text-sm text-ink-soft hover:text-accent transition-colors"
                      >
                        {link.label}
                      </a>
                    ) : (
                      <Link
                        to={link.to}
                        className="text-sm text-ink-soft hover:text-accent transition-colors"
                      >
                        {link.label}
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>

      {/* Bottom bar */}
      <div className="border-t border-line relative z-10">
        <div className="container-narrow mx-auto px-4 py-6 flex flex-col md:flex-row items-center justify-between gap-4">
          <p className="text-sm text-ink-soft">
            &copy; {new Date().getFullYear()} IncoKalk. Tous droits réservés.
          </p>
          <div className="flex items-center gap-6">
            <Link to="/cgu" className="text-sm text-ink-soft hover:text-accent transition-colors">
              Conditions d'utilisation
            </Link>
            <Link to="/confidentialite" className="text-sm text-ink-soft hover:text-accent transition-colors">
              Politique de confidentialité
            </Link>
            <button
              onClick={reopenCookieSettings}
              className="text-sm text-ink-soft hover:text-accent transition-colors"
            >
              Cookies
            </button>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
