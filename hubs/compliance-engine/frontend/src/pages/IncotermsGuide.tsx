import { Link } from 'react-router-dom';
import { ArrowRight, Ship, Truck, ShieldCheck } from 'lucide-react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';

interface IncotermRule {
  code: string;
  name: string;
  riskTransfer: string;
  costs: string;
  bestFor: string;
}

// Règles ICC officielles, groupées comme le fait la publication Incoterms 2020 elle-même :
// 7 règles utilisables pour tout mode de transport, 4 réservées au maritime/fluvial.
const ANY_MODE: IncotermRule[] = [
  {
    code: 'EXW',
    name: 'Ex Works — À l\'usine',
    riskTransfer: 'Dès la mise à disposition dans les locaux du vendeur, avant même le chargement.',
    costs: 'L\'acheteur assume tout : chargement, transport principal, export, import, assurance.',
    bestFor: 'Vendeur qui veut le minimum d\'obligations. Peu adapté si l\'acheteur ne maîtrise pas les formalités export du pays vendeur.',
  },
  {
    code: 'FCA',
    name: 'Free Carrier — Franco Transporteur',
    riskTransfer: 'Dès la remise de la marchandise au transporteur désigné par l\'acheteur.',
    costs: 'Vendeur : dédouanement export. Acheteur : transport principal, import.',
    bestFor: 'Le plus flexible du groupe — compatible avec tout mode de transport, y compris les conteneurs remis avant chargement à bord.',
  },
  {
    code: 'CPT',
    name: 'Carriage Paid To — Port payé jusqu\'à',
    riskTransfer: 'Dès la remise au premier transporteur, même si le vendeur paie le transport au-delà.',
    costs: 'Vendeur : transport jusqu\'au lieu convenu. Acheteur : assurance, import, à partir du transfert de risque.',
    bestFor: 'Vendeur qui veut maîtriser la logistique sans assumer le risque en transit — point souvent mal compris par les acheteurs.',
  },
  {
    code: 'CIP',
    name: 'Carriage and Insurance Paid To — Port payé, assurance comprise, jusqu\'à',
    riskTransfer: 'Identique à CPT : dès la remise au premier transporteur.',
    costs: 'Comme CPT, mais le vendeur souscrit une assurance à couverture étendue (Incoterms 2020 a relevé cette exigence par rapport à 2010).',
    bestFor: 'Marchandises de valeur ou trajets à risque, quand l\'acheteur veut une garantie de couverture sans la négocier lui-même.',
  },
  {
    code: 'DAP',
    name: 'Delivered At Place — Rendu au lieu de destination',
    riskTransfer: 'À l\'arrivée au lieu convenu, marchandise prête à être déchargée (non déchargée).',
    costs: 'Vendeur : transport + risques jusqu\'à destination. Acheteur : déchargement, import.',
    bestFor: 'Vendeur qui veut offrir une livraison "clé en main" côté transport sans gérer les droits d\'importation du pays acheteur.',
  },
  {
    code: 'DPU',
    name: 'Delivered at Place Unloaded — Rendu au lieu déchargé',
    riskTransfer: 'Après déchargement au lieu convenu — le seul Incoterm où le vendeur assume le déchargement.',
    costs: 'Vendeur : transport + déchargement. Acheteur : import.',
    bestFor: 'Livraisons sur site nécessitant un déchargement spécialisé (ex : équipement lourd) que le vendeur maîtrise mieux.',
  },
  {
    code: 'DDP',
    name: 'Delivered Duty Paid — Rendu droits acquittés',
    riskTransfer: 'À l\'arrivée au lieu convenu, marchandise dédouanée à l\'import.',
    costs: 'Vendeur : absolument tout, y compris droits de douane et TVA à l\'import.',
    bestFor: 'Maximum de service pour l\'acheteur — mais risque pour le vendeur si les règles douanières du pays de destination changent (souvent sous-estimé).',
  },
];

const SEA_ONLY: IncotermRule[] = [
  {
    code: 'FAS',
    name: 'Free Alongside Ship — Franco le long du navire',
    riskTransfer: 'Dès que la marchandise est placée le long du navire, au port d\'embarquement convenu.',
    costs: 'Vendeur : jusqu\'au quai. Acheteur : chargement à bord, transport, import.',
    bestFor: 'Marchandises en vrac chargées directement depuis le quai (minerais, céréales).',
  },
  {
    code: 'FOB',
    name: 'Free On Board — Franco à bord',
    riskTransfer: 'Dès que la marchandise est chargée à bord du navire.',
    costs: 'Vendeur : chargement à bord. Acheteur : fret maritime, assurance, import.',
    bestFor: 'L\'Incoterm maritime le plus utilisé historiquement — attention à ne pas l\'employer par réflexe pour du fret conteneurisé (FCA est souvent plus adapté).',
  },
  {
    code: 'CFR',
    name: 'Cost and Freight — Coût et fret',
    riskTransfer: 'Comme FOB : dès le chargement à bord, même si le vendeur paie le fret jusqu\'à destination.',
    costs: 'Vendeur : fret maritime. Acheteur : assurance, import — le risque a déjà changé de camp.',
    bestFor: 'Vendeur qui veut négocier le fret maritime sans assumer le risque en mer.',
  },
  {
    code: 'CIF',
    name: 'Cost, Insurance and Freight — Coût, assurance et fret',
    riskTransfer: 'Identique à CFR : dès le chargement à bord.',
    costs: 'Comme CFR, mais le vendeur souscrit une assurance minimale (couverture plus faible que CIP — un point souvent source de litige).',
    bestFor: 'Produits en vrac ou marchandises courantes à faible valeur, où une couverture d\'assurance minimale suffit.',
  },
];

const SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'Article',
  headline: 'Guide complet des Incoterms 2020',
  description:
    'Les 11 règles Incoterms 2020 expliquées : transfert du risque, répartition des coûts entre vendeur et acheteur, et quand utiliser chacune.',
  author: { '@type': 'Organization', name: 'IncoKalk' },
};

function RuleCard({ rule }: { rule: IncotermRule }) {
  return (
    <div className="bg-surface border border-line rounded-2xl p-6">
      <div className="flex items-center gap-3 mb-3">
        <span className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-accent-soft text-accent-strong font-extrabold text-sm shrink-0">
          {rule.code}
        </span>
        <h3 className="font-bold text-ink">{rule.name}</h3>
      </div>
      <dl className="space-y-2 text-sm">
        <div>
          <dt className="font-semibold text-ink-soft uppercase text-xs tracking-wide">Transfert du risque</dt>
          <dd className="text-ink">{rule.riskTransfer}</dd>
        </div>
        <div>
          <dt className="font-semibold text-ink-soft uppercase text-xs tracking-wide">Répartition des coûts</dt>
          <dd className="text-ink">{rule.costs}</dd>
        </div>
        <div>
          <dt className="font-semibold text-ink-soft uppercase text-xs tracking-wide">À privilégier pour</dt>
          <dd className="text-ink-soft">{rule.bestFor}</dd>
        </div>
      </dl>
    </div>
  );
}

export default function IncotermsGuide() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="Guide complet des Incoterms 2020"
        description="Les 11 règles Incoterms 2020 expliquées : transfert du risque, répartition des coûts entre vendeur et acheteur, et quand utiliser chacune."
        path="/guide-incoterms"
        ogType="article"
        jsonLd={SCHEMA}
      />
      <div className="container mx-auto px-4 py-10 max-w-4xl">
        <Breadcrumbs items={[{ label: 'Guide Incoterms 2020' }]} />

        <div className="text-center my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            Le guide des <span className="text-accent">Incoterms 2020</span>
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            Les 11 règles publiées par la Chambre de Commerce Internationale (ICC), pour savoir
            exactement qui — du vendeur ou de l'acheteur — assume le transport, le risque et les
            formalités à chaque étape d'une expédition internationale.
          </p>
        </div>

        <div className="bg-accent-soft border border-accent/20 rounded-2xl p-6 mb-10 flex items-start gap-3">
          <ShieldCheck size={22} className="text-accent-strong mt-0.5 shrink-0" />
          <p className="text-sm text-ink">
            Deux notions à ne jamais confondre : le <strong>transfert du risque</strong> (à partir de
            quand une avarie ou une perte est à la charge de l'acheteur) et la{' '}
            <strong>répartition des coûts</strong> (qui paie quoi). Les deux ne coïncident pas
            toujours au même point du trajet — c'est la source la plus fréquente de litiges
            commerciaux liés aux Incoterms.
          </p>
        </div>

        <section className="mb-12">
          <div className="flex items-center gap-2 mb-6">
            <Truck size={20} className="text-accent" />
            <h2 className="text-2xl font-bold text-ink">Tout mode de transport (7 règles)</h2>
          </div>
          <div className="grid md:grid-cols-2 gap-4">
            {ANY_MODE.map((rule) => (
              <RuleCard key={rule.code} rule={rule} />
            ))}
          </div>
        </section>

        <section className="mb-12">
          <div className="flex items-center gap-2 mb-6">
            <Ship size={20} className="text-accent" />
            <h2 className="text-2xl font-bold text-ink">Maritime et voies navigables intérieures uniquement (4 règles)</h2>
          </div>
          <p className="text-sm text-ink-soft mb-6">
            Réservées au transport par mer ou par voie fluviale, où le point de transfert du risque
            se définit par rapport au navire — inadaptées au fret conteneurisé remis avant
            chargement (FCA est alors le bon choix).
          </p>
          <div className="grid md:grid-cols-2 gap-4">
            {SEA_ONLY.map((rule) => (
              <RuleCard key={rule.code} rule={rule} />
            ))}
          </div>
        </section>

        <div className="text-center bg-surface border border-line rounded-2xl p-8">
          <h2 className="text-xl font-bold text-ink mb-2">Calculez le coût réel selon chaque Incoterm</h2>
          <p className="text-ink-soft mb-6">
            Choisir la bonne règle sur le papier ne suffit pas — le Calculateur Incoterms d'IncoKalk
            simule le coût total (transport, douane, assurance) pour les 11 règles sur votre trajet
            réel.
          </p>
          <Link to="/simulation" className="btn-primary inline-flex items-center gap-2">
            Essayer le calculateur
            <ArrowRight size={18} />
          </Link>
          <p className="mt-4">
            <Link to="/cout-erreur-douane" className="text-sm font-medium text-accent hover:underline">
              Et le choix du code SH ? Estimez le coût d'une erreur de classification →
            </Link>
          </p>
        </div>
      </div>
      <StickyMobileCta />
    </div>
  );
}
