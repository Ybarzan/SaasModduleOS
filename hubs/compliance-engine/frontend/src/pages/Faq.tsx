import { Link } from 'react-router-dom';
import { ArrowRight, Plus, Minus } from 'lucide-react';
import { useState } from 'react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';

const FAQ_ITEMS = [
  {
    q: 'Qu\'est-ce qu\'IncoKalk ?',
    a: 'IncoKalk est un logiciel de calcul de coûts logistiques internationaux. Il simule le coût total de vos expéditions selon les 11 Incoterms 2020, compare les devis de plusieurs transporteurs et centralise le suivi de vos expéditions dans un seul tableau de bord.',
  },
  {
    q: 'Ai-je besoin d\'une carte bancaire pour créer un compte ?',
    a: 'Non. Le plan gratuit est accessible immédiatement après inscription, sans carte bancaire. Vous pouvez passer à un plan payant à tout moment depuis la page Abonnement.',
  },
  {
    q: 'Quels Incoterms 2020 sont supportés ?',
    a: 'Les 11 Incoterms 2020 sont supportés : EXW, FCA, FAS, FOB, CFR, CIF, CPT, CIP, DAP, DPU et DDP, avec calcul automatique des coûts de transport, droits de douane, assurance et TVA associés à chacun.',
  },
  {
    q: 'IncoKalk peut-il se connecter à mon ERP existant ?',
    a: 'Oui. IncoKalk propose des intégrations avec les principaux ERP (Odoo, SAP, Microsoft Dynamics) ainsi qu\'avec les plateformes e-commerce (WooCommerce, Magento, Shopify) pour synchroniser automatiquement vos données logistiques.',
  },
  {
    q: 'Comment fonctionne la comparaison de devis transporteurs ?',
    a: 'Renseignez les caractéristiques de votre expédition (poids, volume, origine, destination) et IncoKalk interroge simultanément plusieurs transporteurs pour comparer tarifs, délais et niveaux de service en quelques secondes.',
  },
  {
    q: 'Puis-je changer de plan à tout moment ?',
    a: 'Oui, vous pouvez passer d\'un plan à un autre à tout moment depuis la page Abonnement. Le changement est proratisé automatiquement sur votre prochaine facture.',
  },
  {
    q: 'Mes données sont-elles sécurisées ?',
    a: 'Toutes les données sont chiffrées en transit (TLS) et au repos. Consultez notre politique de confidentialité pour le détail du traitement de vos données personnelles et professionnelles.',
  },
  {
    q: 'Proposez-vous un accompagnement pour la mise en route ?',
    a: 'Oui, notre équipe peut vous accompagner dans la configuration initiale (transporteurs, intégrations ERP, import de données). Contactez-nous à contact@incokalk.com pour en discuter.',
  },
];

const FAQ_SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'FAQPage',
  mainEntity: FAQ_ITEMS.map((item) => ({
    '@type': 'Question',
    name: item.q,
    acceptedAnswer: { '@type': 'Answer', text: item.a },
  })),
};

function FaqRow({ q, a }: { q: string; a: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="border border-line rounded-none bg-surface overflow-hidden">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="w-full flex items-center justify-between gap-4 text-left px-6 py-5 hover:bg-bg transition-colors"
      >
        <span className="font-bold text-ink">{q}</span>
        {open ? (
          <Minus size={20} className="text-accent shrink-0" />
        ) : (
          <Plus size={20} className="text-accent shrink-0" />
        )}
      </button>
      {open && (
        <div className="px-6 pb-5 text-ink-soft leading-relaxed">{a}</div>
      )}
    </div>
  );
}

export default function Faq() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="FAQ — Questions fréquentes"
        description="Toutes les réponses à vos questions sur IncoKalk : Incoterms 2020, intégrations ERP, comparaison de transporteurs, sécurité des données et abonnement."
        path="/faq"
        jsonLd={FAQ_SCHEMA}
      />
      <div className="container mx-auto px-4 py-10 max-w-3xl">
        <Breadcrumbs items={[{ label: 'FAQ' }]} />

        <div className="text-center my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Questions <span className="text-accent">fréquentes</span>
          </h1>
          <p className="text-lg text-ink-soft max-w-xl mx-auto">
            Tout ce qu'il faut savoir sur IncoKalk avant de vous lancer.
          </p>
        </div>

        <div className="space-y-3">
          {FAQ_ITEMS.map((item) => (
            <FaqRow key={item.q} q={item.q} a={item.a} />
          ))}
        </div>

        <div className="mt-12 text-center bg-surface border border-line rounded-none p-8">
          <h2 className="text-xl font-bold text-ink mb-2">Une autre question ?</h2>
          <p className="text-ink-soft mb-6">
            Consultez nos <Link to="/pricing" className="text-accent font-semibold hover:underline">plans et tarifs</Link>,
            notre <Link to="/guide-incoterms" className="text-accent font-semibold hover:underline">guide Incoterms 2020</Link>{' '}
            ou contactez-nous directement.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
            <a href="mailto:contact@incokalk.com" className="btn-outline-white !text-ink !border-ink-soft hover:!bg-bg">
              contact@incokalk.com
            </a>
            <Link to="/register" className="btn-primary inline-flex items-center gap-2">
              Créer un compte gratuit
              <ArrowRight size={18} />
            </Link>
          </div>
        </div>
      </div>
      <StickyMobileCta />
    </div>
  );
}
