import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Check, Minus, X, ArrowRight } from 'lucide-react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';

type Cell = 'yes' | 'partial' | 'no';

interface Row {
  feature: string;
  incokalk: Cell | string;
  cargoson: Cell | string;
  easyship: Cell | string;
  flexport: Cell | string;
}

const ROWS: Row[] = [
  { feature: 'TARIC réel intégré (droits de douane)', incokalk: 'yes', cargoson: 'partial', easyship: 'partial', flexport: 'partial' },
  { feature: 'DEB / Intrastat automatique', incokalk: 'yes', cargoson: 'partial', easyship: 'no', flexport: 'partial' },
  { feature: 'Screening denied parties (DPS) en standard', incokalk: 'yes', cargoson: 'no', easyship: 'no', flexport: 'partial' },
  { feature: 'EORI & facture douanière', incokalk: 'yes', cargoson: 'no', easyship: 'partial', flexport: 'yes' },
  { feature: 'Conçu pour la douane française / DGDDI', incokalk: 'yes', cargoson: 'no', easyship: 'no', flexport: 'partial' },
  { feature: 'Règles of Origin & accords préférentiels EU', incokalk: 'yes', cargoson: 'partial', easyship: 'partial', flexport: 'partial' },
  { feature: 'Comparateur multi-transporteurs', incokalk: 'yes', cargoson: 'yes', easyship: 'yes', flexport: 'yes' },
  { feature: 'Tracking temps réel centralisé', incokalk: 'yes', cargoson: 'yes', easyship: 'yes', flexport: 'yes' },
  { feature: 'Génération CMR / DGD / documents', incokalk: 'yes', cargoson: 'yes', easyship: 'partial', flexport: 'yes' },
  { feature: 'ERP & e-commerce (Odoo, SAP, Shopify)', incokalk: 'yes', cargoson: 'partial', easyship: 'yes', flexport: 'partial' },
  { feature: 'Empreinte CO₂ par expédition', incokalk: 'yes', cargoson: 'yes', easyship: 'partial', flexport: 'yes' },
  { feature: 'Facturation transporteurs (AP)', incokalk: 'yes', cargoson: 'partial', easyship: 'no', flexport: 'yes' },
  { feature: 'White-label client', incokalk: 'yes', cargoson: 'partial', easyship: 'no', flexport: 'yes' },
  { feature: 'API ouverte', incokalk: 'yes', cargoson: 'yes', easyship: 'yes', flexport: 'yes' },
  { feature: 'Tarif d\'entrée', incokalk: '29€/mois', cargoson: 'sur devis', easyship: '~49$/mois', flexport: 'sur devis' },
];

const COLUMNS = [
  { key: 'incokalk' as const, label: 'IncoKalk', highlight: true },
  { key: 'cargoson' as const, label: 'Cargoson' },
  { key: 'easyship' as const, label: 'Easyship' },
  { key: 'flexport' as const, label: 'Flexport' },
];

function CellMark({ value }: { value: Cell | string }) {
  if (value === 'yes') {
    return (
      <span className="inline-flex items-center justify-center w-7 h-7 rounded-full bg-success/10 text-success">
        <Check size={14} strokeWidth={3} />
      </span>
    );
  }
  if (value === 'partial') {
    return (
      <span className="inline-flex items-center justify-center w-7 h-7 rounded-full bg-warning/10 text-warning">
        <Minus size={14} strokeWidth={3} />
      </span>
    );
  }
  if (value === 'no') {
    return (
      <span className="inline-flex items-center justify-center w-7 h-7 rounded-full bg-danger/10 text-danger">
        <X size={14} strokeWidth={3} />
      </span>
    );
  }
  return <span className="text-sm font-semibold text-ink">{value}</span>;
}

export default function PricingComparison() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="Comparatif IncoKalk vs Cargoson, Easyship, Flexport"
        description="Comparaison détaillée d'IncoKalk face à Cargoson, Easyship et Flexport sur la douane française (TARIC, DEB, DPS), le multi-transporteurs et les intégrations ERP."
        path="/pricing-comparison"
      />
      <div className="container mx-auto px-4 py-12 max-w-6xl">
        <Breadcrumbs items={[{ label: 'Tarifs', to: '/pricing' }, { label: 'Comparatif' }]} />
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-2 text-ink-soft hover:text-ink mb-6 mt-6 transition-colors"
        >
          <ArrowLeft size={16} />
          Retour
        </button>

        {/* Hero */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            Comparatif 2026 : le <span className="text-accent">SaaS logistique</span> pour les exportateurs français
          </h1>
          <p className="text-lg text-ink-soft max-w-3xl mx-auto">
            Cargoson, Easyship, Flexport… comparés à IncoKalk sur la douane française (TARIC, DEB,
            DPS) — le point où la plupart des SaaS logistiques sont les plus faibles.
          </p>
        </div>

        {/* Table */}
        <div className="bg-surface rounded-none border border-line overflow-hidden mb-12 shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line bg-bg">
                  <th className="text-left py-4 px-6 font-semibold text-ink-soft w-[28%]">
                    Fonctionnalité
                  </th>
                  {COLUMNS.map((col) => (
                    <th
                      key={col.key}
                      className={`py-4 px-4 text-center font-bold ${
                        col.highlight ? 'text-accent-strong' : 'text-ink-soft'
                      }`}
                    >
                      {col.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {ROWS.map((row, i) => (
                  <tr key={i} className={`border-b border-surface-2 last:border-b-0 ${i % 2 ? 'bg-bg/40' : ''}`}>
                    <td className="py-3.5 px-6 font-medium text-ink">{row.feature}</td>
                    {COLUMNS.map((col) => (
                      <td key={col.key} className="py-3.5 px-4 text-center">
                        <CellMark value={row[col.key]} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Angles */}
        <div className="grid md:grid-cols-3 gap-6 mb-12">
          <div className="bg-surface rounded-none border border-line p-6">
            <h3 className="font-bold text-ink mb-2">Douane française native</h3>
            <p className="text-sm text-ink-soft">
              TARIC réel, DEB, Intrastat, screening DPS, EORI, facture douanière — inclus dans le
              plan Pro, pas en add-on payant.
            </p>
          </div>
          <div className="bg-surface rounded-none border border-line p-6">
            <h3 className="font-bold text-ink mb-2">Pas d'intermédiaire</h3>
            <p className="text-sm text-ink-soft">
              Vous gardez le contrôle total sur vos tarifs négociés et vos relations transporteurs.
              Pas de frais de courtage cachés.
            </p>
          </div>
          <div className="bg-surface rounded-none border border-line p-6">
            <h3 className="font-bold text-ink mb-2">Conçu en France, pour l'EU</h3>
            <p className="text-sm text-ink-soft">
              DGDDI native, EUR.1, EORI FR, seuils DEB 2026 — là où Flexport (US-centric) et Easyship
              restent génériques.
            </p>
          </div>
        </div>

        {/* CTA */}
        <div className="text-center">
          <Link
            to="/pricing"
            className="inline-flex items-center gap-2 bg-accent text-white font-semibold px-8 py-3.5 rounded-none hover:bg-accent-strong shadow-md shadow-accent/20 transition-all"
          >
            Voir les plans IncoKalk
            <ArrowRight size={16} />
          </Link>
          <p className="text-sm text-ink-soft mt-3">
            Essai gratuit 14 jours sur tous les plans payants.
          </p>
        </div>
      </div>
      <StickyMobileCta label="Voir les plans" to="/pricing" />
    </div>
  );
}
