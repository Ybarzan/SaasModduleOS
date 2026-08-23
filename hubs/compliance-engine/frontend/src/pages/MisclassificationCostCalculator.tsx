import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, Calculator, Info } from 'lucide-react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';
import { formatEur } from '../lib/formatNumber';

export default function MisclassificationCostCalculator() {
  const [shipmentsPerMonth, setShipmentsPerMonth] = useState(20);
  const [avgValue, setAvgValue] = useState(8000);
  const [dutyGapPoints, setDutyGapPoints] = useState(3);

  const monthlyCost = shipmentsPerMonth * avgValue * (dutyGapPoints / 100);
  const annualCost = monthlyCost * 12;

  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="Calculateur : coût d'une erreur de classification douanière"
        description="Estimez ce qu'un mauvais code SH (classification douanière) vous coûte réellement en droits de douane mal calculés, à partir de vos propres volumes d'expédition."
        path="/cout-erreur-douane"
      />
      <div className="container mx-auto px-4 py-10 max-w-4xl">
        <Breadcrumbs items={[{ label: "Coût d'une erreur douanière" }]} />

        <div className="text-center my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            Combien vous coûte une <span className="text-accent">erreur de classification douanière</span> ?
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            Chaque produit importé est rattaché à un code SH (Système Harmonisé) qui détermine le
            taux de droits de douane appliqué. Deux codes plausibles pour le même produit peuvent
            avoir des taux très différents — l'écart se paie à chaque expédition, silencieusement.
          </p>
        </div>

        <div className="bg-surface rounded-2xl border border-line p-6 sm:p-8 mb-8">
          <h2 className="text-lg font-bold text-ink mb-6 flex items-center gap-2">
            <Calculator size={20} className="text-accent" />
            Estimez votre exposition
          </h2>

          <div className="grid sm:grid-cols-3 gap-6 mb-6">
            <div>
              <label className="block text-sm font-medium text-ink mb-2">
                Expéditions importées / mois
              </label>
              <input
                type="number"
                min={0}
                value={shipmentsPerMonth}
                onChange={(e) => setShipmentsPerMonth(Math.max(0, Number(e.target.value)))}
                className="w-full px-4 py-2.5 border border-line rounded-xl focus:ring-2 focus:ring-accent focus:border-transparent text-sm bg-surface text-ink"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-2">
                Valeur déclarée moyenne (€)
              </label>
              <input
                type="number"
                min={0}
                value={avgValue}
                onChange={(e) => setAvgValue(Math.max(0, Number(e.target.value)))}
                className="w-full px-4 py-2.5 border border-line rounded-xl focus:ring-2 focus:ring-accent focus:border-transparent text-sm bg-surface text-ink"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-2">
                Écart de droits estimé (points de %)
              </label>
              <input
                type="number"
                min={0}
                step={0.5}
                value={dutyGapPoints}
                onChange={(e) => setDutyGapPoints(Math.max(0, Number(e.target.value)))}
                className="w-full px-4 py-2.5 border border-line rounded-xl focus:ring-2 focus:ring-accent focus:border-transparent text-sm bg-surface text-ink"
              />
            </div>
          </div>

          <div className="grid sm:grid-cols-2 gap-4 mb-6">
            <div className="bg-accent-soft rounded-xl p-5 text-center">
              <p className="text-xs font-semibold text-ink-soft uppercase tracking-wide mb-1">Coût mensuel estimé</p>
              <p className="text-3xl font-extrabold text-accent-strong">{formatEur(monthlyCost)}</p>
            </div>
            <div className="bg-accent-soft rounded-xl p-5 text-center">
              <p className="text-xs font-semibold text-ink-soft uppercase tracking-wide mb-1">Coût annuel estimé</p>
              <p className="text-3xl font-extrabold text-accent-strong">{formatEur(annualCost)}</p>
            </div>
          </div>

          <div className="flex items-start gap-3 text-sm text-ink-soft bg-warning/10 border border-warning/20 rounded-xl p-4">
            <Info size={16} className="text-warning shrink-0 mt-0.5" />
            <p>
              Estimation basée sur les valeurs que vous saisissez, pas sur un taux moyen du marché —
              nous n'inventons pas de statistique sectorielle. L'écart réel de droits dépend du code
              SH exact retenu pour votre produit, du pays d'origine et du pays de destination : à
              vérifier au cas par cas avec l'outil de classification HS d'IncoKalk.
            </p>
          </div>
        </div>

        <div className="bg-surface border border-line rounded-2xl p-6 sm:p-8 mb-8 flex items-start gap-4">
          <AlertTriangle size={22} className="text-warning shrink-0 mt-0.5" />
          <div>
            <h2 className="font-bold text-ink mb-1">Ce n'est pas qu'un manque à gagner</h2>
            <p className="text-sm text-ink-soft">
              Une classification erronée expose aussi à un redressement douanier a posteriori
              (rappel de droits, intérêts, pénalités) si l'administration retient un code différent
              lors d'un contrôle — l'écart calculé ci-dessus peut se matérialiser rétroactivement sur
              plusieurs années d'expéditions passées.
            </p>
          </div>
        </div>

        <div className="text-center bg-surface border border-line rounded-2xl p-8">
          <h2 className="text-xl font-bold text-ink mb-2">Vérifiez votre classification HS</h2>
          <p className="text-ink-soft mb-6">
            IncoKalk classe vos produits, calcule les droits associés et alerte en cas d'écart avec
            l'historique de vos déclarations.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/simulation" className="btn-primary inline-flex items-center justify-center gap-2">
              Essayer le calculateur
              <ArrowRight size={18} />
            </Link>
            <Link
              to="/register"
              className="px-6 py-3 rounded-lg font-semibold border border-line text-ink hover:bg-surface-2 transition-colors inline-flex items-center justify-center gap-2"
            >
              Créer un compte gratuit
            </Link>
          </div>
        </div>
      </div>
      <StickyMobileCta />
    </div>
  );
}
