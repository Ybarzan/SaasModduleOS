import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import { AlertTriangle } from 'lucide-react';

const TODO = '[À COMPLÉTER]';

export default function LegalNotice() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white">
      <Seo
        title="Mentions légales"
        description="Mentions légales d'IncoKalk : éditeur, hébergeur et informations légales."
        path="/mentions-legales"
        noindex
      />
      <div className="container mx-auto px-4 py-10 max-w-3xl">
        <Breadcrumbs items={[{ label: 'Mentions légales' }]} />

        <div className="my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Mentions légales
          </h1>
        </div>

        <div className="bg-danger/10 border border-danger/40 rounded-none p-5 mb-10 flex gap-3">
          <AlertTriangle size={20} className="text-danger shrink-0 mt-0.5" />
          <div className="text-sm text-danger">
            <strong>Page non publiable en l'état.</strong> Les mentions légales sont une obligation
            légale en France (art. 6-III de la LCEN) et doivent contenir l'identité réelle et vérifiable
            de la société éditrice. Les champs marqués {TODO} ci-dessous doivent être remplacés par les
            informations exactes avant toute mise en ligne publique ; cette page a volontairement été
            désindexée (noindex) en attendant.
          </div>
        </div>

        <div className="space-y-10">
          <section>
            <h2 className="text-xl font-bold text-ink mb-3">1. Éditeur du site</h2>
            <div className="text-ink-soft leading-relaxed space-y-1">
              <p>Raison sociale : {TODO}</p>
              <p>Forme juridique : {TODO}</p>
              <p>Capital social : {TODO}</p>
              <p>Siège social : {TODO}</p>
              <p>SIREN / SIRET : {TODO}</p>
              <p>RCS : {TODO}</p>
              <p>Numéro de TVA intracommunautaire : {TODO}</p>
              <p>Directeur de la publication : {TODO}</p>
              <p>
                Contact :{' '}
                <a href="mailto:contact@incokalk.com" className="text-accent hover:underline">
                  contact@incokalk.com
                </a>
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-xl font-bold text-ink mb-3">2. Hébergement</h2>
            <div className="text-ink-soft leading-relaxed space-y-1">
              <p>Hébergeur : {TODO}</p>
              <p>Adresse : {TODO}</p>
              <p>Contact : {TODO}</p>
            </div>
          </section>

          <section>
            <h2 className="text-xl font-bold text-ink mb-3">3. Propriété intellectuelle</h2>
            <p className="text-ink-soft leading-relaxed">
              L'ensemble des éléments du site IncoKalk (textes, graphismes, logo, marque, code source)
              est protégé par le droit de la propriété intellectuelle. Toute reproduction ou
              représentation, totale ou partielle, sans autorisation préalable est interdite.
            </p>
          </section>

          <section>
            <h2 className="text-xl font-bold text-ink mb-3">4. Données personnelles</h2>
            <p className="text-ink-soft leading-relaxed">
              Le traitement des données personnelles est décrit dans notre{' '}
              <a href="/confidentialite" className="text-accent hover:underline">
                politique de confidentialité
              </a>
              .
            </p>
          </section>

          <section>
            <h2 className="text-xl font-bold text-ink mb-3">5. Conditions d'utilisation</h2>
            <p className="text-ink-soft leading-relaxed">
              L'utilisation du service est soumise à nos{' '}
              <a href="/cgu" className="text-accent hover:underline">
                Conditions Générales d'Utilisation
              </a>
              .
            </p>
          </section>

          <section>
            <h2 className="text-xl font-bold text-ink mb-3">6. Médiation de la consommation</h2>
            <p className="text-ink-soft leading-relaxed">
              Conformément à l'article L. 616-1 du Code de la consommation, si applicable, les
              coordonnées du médiateur de la consommation compétent seront précisées ici : {TODO}.
            </p>
          </section>
        </div>
      </div>
    </div>
  );
}
