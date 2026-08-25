import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { incokalkAPI } from '../lib/api';
import { Package, TrendingUp, TrendingDown, Loader2, AlertCircle } from 'lucide-react';
import { formatNumber } from '../lib/formatNumber';

interface LandedCostData {
  id: string;
  calculationName: string;
  originCountry: string;
  destinationCountry: string;
  incoterm: string;
  hsCode: string;
  transportMode: string;
  productValue: number;
  currency: string;
  freightCost: number;
  insuranceCost: number;
  dutyAmount: number;
  dutyRate: number;
  vatAmount: number;
  vatRate: number;
  portCharges: number;
  customsFees: number;
  handlingFees: number;
  lastMileCost: number;
  totalLandedCost: number;
  unitCount: number;
  totalLandedCostPerUnit: number;
  margin: number;
  marginPercent: number;
  sellingPrice: number;
  notes: string;
}

const COUNTRY_NAMES: Record<string, string> = {
  FR: 'France', DE: 'Allemagne', IT: 'Italie', ES: 'Espagne', NL: 'Pays-Bas',
  BE: 'Belgique', PT: 'Portugal', PL: 'Pologne', AT: 'Autriche', IE: 'Irlande',
  GB: 'Royaume-Uni', VN: 'Vietnam', CN: 'Chine', IN: 'Inde', BD: 'Bangladesh',
  TR: 'Turquie', MA: 'Maroc', TN: 'Tunisie', JP: 'Japon', KR: 'Corée du Sud',
  US: 'États-Unis', BR: 'Brésil', MX: 'Mexique',
};

const SharedLandedCost = () => {
  const { token } = useParams<{ token: string }>();
  const [data, setData] = useState<LandedCostData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    incokalkAPI.landedCosts.getPublic(token)
      .then((res) => setData(res.data as LandedCostData))
      .catch((err) => setError(err.response?.data?.message || 'Lien invalide ou expiré'))
      .finally(() => setLoading(false));
  }, [token]);

  const fmt = (n: number) => formatNumber(n, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  if (loading) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-accent animate-spin" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <div className="bg-surface rounded-none border border-line p-8 text-center max-w-md">
          <AlertCircle className="w-12 h-12 text-danger/70 mx-auto mb-4" />
          <h2 className="text-lg font-semibold text-ink mb-2">Lien invalide</h2>
          <p className="text-ink-soft">{error || 'Ce lien de partage n\'est pas valide ou a expiré.'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <div className="text-center mb-8">
          <Package className="w-12 h-12 text-accent mx-auto mb-3" />
          <h1 className="text-2xl font-bold text-ink">
            {data.calculationName || 'Landed Cost'}
          </h1>
          <p className="text-ink-soft mt-1">
            {COUNTRY_NAMES[data.originCountry] || data.originCountry} → {COUNTRY_NAMES[data.destinationCountry] || data.destinationCountry}
            {' • '}{data.incoterm}
            {data.hsCode && ` • SH ${data.hsCode}`}
          </p>
        </div>

        <div className="bg-surface rounded-none border border-line p-6 mb-6">
          <h2 className="text-lg font-semibold text-ink mb-4">Ventilation des coûts</h2>
          <div className="space-y-2">
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Valeur produit</span>
              <span className="font-medium">{fmt(data.productValue)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Fret</span>
              <span className="font-medium">{fmt(data.freightCost)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Assurance</span>
              <span className="font-medium">{fmt(data.insuranceCost)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-2 text-sm border-t border-line font-bold">
              <span>CIF Total</span>
              <span>{fmt(data.productValue + data.freightCost + data.insuranceCost)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Droits de douane ({fmt(data.dutyRate)}%)</span>
              <span className="font-medium">{fmt(data.dutyAmount)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">TVA ({fmt(data.vatRate)}%)</span>
              <span className="font-medium">{fmt(data.vatAmount)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Frais portuaire</span>
              <span className="font-medium">{fmt(data.portCharges)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Frais douane</span>
              <span className="font-medium">{fmt(data.customsFees)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Frais manutention</span>
              <span className="font-medium">{fmt(data.handlingFees)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-1.5 text-sm">
              <span className="text-ink-soft">Dernier kilomètre</span>
              <span className="font-medium">{fmt(data.lastMileCost)} {data.currency}</span>
            </div>
            <div className="flex justify-between py-3 text-base border-t-2 border-ink">
              <span className="font-bold">Coût total débarqué</span>
              <span className="font-bold">{fmt(data.totalLandedCost)} {data.currency}</span>
            </div>
            {data.unitCount > 1 && (
              <div className="flex justify-between py-2 text-sm bg-accent-soft -mx-6 px-6 rounded-b-xl">
                <span className="font-medium text-accent-strong">Coût par unité</span>
                <span className="font-bold text-accent-strong">{fmt(data.totalLandedCostPerUnit)} {data.currency}</span>
              </div>
            )}
          </div>
        </div>

        {data.sellingPrice > 0 && (
          <div className="bg-surface rounded-none border border-line p-6">
            <div className="flex items-center gap-2 mb-4">
              {data.margin >= 0 ? <TrendingUp className="w-5 h-5 text-success" /> : <TrendingDown className="w-5 h-5 text-danger" />}
              <h2 className="text-lg font-semibold text-ink">Marge</h2>
            </div>
            <div className="space-y-2">
              <div className="flex justify-between py-1.5 text-sm">
                <span className="text-ink-soft">Prix de vente</span>
                <span className="font-medium">{fmt(data.sellingPrice)} {data.currency}</span>
              </div>
              <div className="flex justify-between py-2 text-sm border-t border-line">
                <span className="font-medium">Marge</span>
                <span className={`font-bold ${data.margin >= 0 ? 'text-success' : 'text-danger'}`}>
                  {fmt(data.margin)} {data.currency} ({fmt(data.marginPercent)}%)
                </span>
              </div>
            </div>
          </div>
        )}

        <p className="text-center text-xs text-ink-soft mt-8">Calculé avec IncoKalk</p>
      </div>
    </div>
  );
};

export default SharedLandedCost;
