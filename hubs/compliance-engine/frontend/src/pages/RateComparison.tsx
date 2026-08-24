import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Truck, ChevronDown, ChevronUp, AlertTriangle, Loader2,
  ArrowRight, Filter, X, Ship, Plane, Download
} from 'lucide-react';
import type { Carrier, ShippingRate } from '../types';
import { COUNTRIES } from '@/lib/constants';

interface RateComparisonResult {
  rate: ShippingRate;
  carrierName: string;
  carrierCode: string;
  estimatedCost: number;
  transitDaysAvg: number;
  co2EstimateKg: number;
}

const TRANSPORT_MODES = [
  { value: 'SEA', label: 'Maritime', icon: Ship, color: 'bg-accent-soft text-accent-strong' },
  { value: 'AIR', label: 'Aérien', icon: Plane, color: 'bg-accent-soft text-accent-strong' },
  { value: 'ROAD', label: 'Routier', icon: Truck, color: 'bg-warning/10 text-warning' },
];

const EMPTY_FORM = {
  originCountry: '',
  destinationCountry: '',
  transportMode: '',
  weightKg: '',
  volumeM3: '',
};

const RateComparison = () => {
  const [form, setForm] = useState(EMPTY_FORM);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data: carriers = [] } = useQuery({
    queryKey: ['carriers'],
    queryFn: async () => {
      const res = await incokalkAPI.carriers.getAll();
      return res.data || [];
    },
  });

  const activeCarriers = carriers.filter((c: Carrier) => c.active);

  const { data: comparison, isLoading, error, refetch } = useQuery({
    queryKey: ['rate-comparison', form.originCountry, form.destinationCountry, form.transportMode, form.weightKg],
    queryFn: async () => {
      if (!form.originCountry || !form.destinationCountry || !form.transportMode) return null;
      const res = await incokalkAPI.shippingRates.compare(
        form.originCountry,
        form.destinationCountry,
        form.transportMode,
        form.weightKg ? parseFloat(form.weightKg) : undefined
      );
      return (res.data as RateComparisonResult[]) || [];
    },
    enabled: !!form.originCountry && !!form.destinationCountry && !!form.transportMode,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.originCountry || !form.destinationCountry || !form.transportMode) {
      toast.error('Veuillez remplir les champs obligatoires');
      return;
    }
    refetch();
  };

  const clearForm = () => {
    setForm(EMPTY_FORM);
    setShowAdvanced(false);
  };

  const modeConfig = TRANSPORT_MODES.find(m => m.value === form.transportMode);

  const exportPdfMutation = useMutation({
    mutationFn: () => incokalkAPI.export.quotesPdf({
      originCountry: form.originCountry,
      destinationCountry: form.destinationCountry,
      transportMode: form.transportMode,
      weightKg: form.weightKg ? parseFloat(form.weightKg) : 10,
      volumeM3: form.volumeM3 ? parseFloat(form.volumeM3) : 1,
      goodsValue: 0,
    }),
    onSuccess: (res) => {
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `devis-${form.originCountry}-${form.destinationCountry}-${new Date().toISOString().split('T')[0]}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('PDF téléchargé');
    },
    onError: () => toast.error('Erreur lors de la génération du PDF'),
  });

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Comparaison des tarifs en cours...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="bg-danger/10 border border-danger/40 text-danger px-6 py-4 rounded">
          Erreur lors de la comparaison des tarifs
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-ink mb-2">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Comparateur de tarifs multi-transporteurs
          </h1>
          <p className="text-ink-soft">
            Comparez les tarifs de vos transporteurs pour un trajet donné et trouvez la meilleure option
          </p>
        </div>

        {/* Search Form */}
        <div className="bg-surface rounded-none shadow-lg p-6 mb-8">
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays d'origine *</label>
                <select
                  value={form.originCountry}
                  onChange={(e) => setForm({ ...form, originCountry: e.target.value })}
                  className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                >
                  <option value="">Sélectionner...</option>
                  {COUNTRIES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div className="flex items-center justify-center text-ink-soft">
                <ArrowRight size={24} />
              </div>

              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays de destination *</label>
                <select
                  value={form.destinationCountry}
                  onChange={(e) => setForm({ ...form, destinationCountry: e.target.value })}
                  className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                >
                  <option value="">Sélectionner...</option>
                  {COUNTRIES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-ink mb-1">Mode de transport *</label>
                <select
                  value={form.transportMode}
                  onChange={(e) => setForm({ ...form, transportMode: e.target.value })}
                  className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                >
                  <option value="">Sélectionner...</option>
                  {TRANSPORT_MODES.map((m) => (
                    <option key={m.value} value={m.value}>{m.label}</option>
                  ))}
                </select>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setShowAdvanced(!showAdvanced)}
              className="text-accent hover:text-accent-strong text-sm font-medium flex items-center space-x-1"
            >
              <span>{showAdvanced ? 'Masquer' : 'Afficher'} les options avancées</span>
              {showAdvanced ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>

            {showAdvanced && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-line">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Poids (kg)</label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    value={form.weightKg}
                    onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                    placeholder="Ex: 100"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Volume (m³)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.volumeM3}
                    onChange={(e) => setForm({ ...form, volumeM3: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                    placeholder="Ex: 2.5"
                  />
                </div>
              </div>
            )}

            <div className="flex items-center space-x-4">
              <button
                type="submit"
                className="bg-accent text-white px-6 py-3 rounded-none hover:bg-accent-strong transition-colors flex items-center space-x-2 font-medium"
              >
                <Filter size={20} />
                <span>Comparer les tarifs</span>
              </button>
              <button
                type="button"
                onClick={clearForm}
                className="text-ink-soft hover:text-ink px-4 py-2 border border-line rounded-none transition-colors"
              >
                <X size={18} className="mr-1" />
                Effacer
              </button>
            </div>
          </form>
        </div>

        {/* Results */}
        {comparison && comparison.length > 0 && (
          <div className="space-y-4">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-2xl font-bold text-ink">
                {comparison.length} tarif{comparison.length > 1 ? 's' : ''} trouvé{comparison.length > 1 ? 's' : ''}
              </h2>
              <div className="flex items-center space-x-2 text-sm text-ink-soft mt-1">
                {modeConfig && (
                  <span className={`inline-flex items-center px-2 py-1 rounded-full ${modeConfig.color}`}>
                    <modeConfig.icon size={14} className="mr-1" />
                    {modeConfig.label}
                  </span>
                )}
                <span className="text-ink-soft">
                  {form.originCountry} → {form.destinationCountry}
                </span>
              </div>
            </div>
            <button
              onClick={() => exportPdfMutation.mutate()}
              disabled={exportPdfMutation.isPending}
              className="bg-surface border border-line text-ink px-4 py-2 rounded-none hover:bg-bg transition-colors flex items-center space-x-2 text-sm font-medium shadow-sm disabled:opacity-50"
            >
              {exportPdfMutation.isPending ? (
                <Loader2 size={16} className="animate-spin" />
              ) : (
                <Download size={16} />
              )}
              <span>Exporter PDF</span>
            </button>
          </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {comparison.map((result, index) => (
                <RateCard
                  key={result.rate.id}
                  result={result}
                  rank={index + 1}
                  expandedId={expandedId}
                  onToggleExpand={setExpandedId}
                />
              ))}
            </div>
          </div>
        )}

        {comparison && comparison.length === 0 && form.originCountry && (
          <div className="bg-surface rounded-none shadow-lg p-12 text-center">
            <AlertTriangle className="h-16 w-16 text-warning mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink mb-2">Aucun tarif trouvé</h3>
            <p className="text-ink-soft mb-6">
              Aucun tarif ne correspond à vos critères pour ce trajet.
            </p>
            <p className="text-sm text-ink-soft">
              Vérifiez que vos transporteurs ont configuré des tarifs pour cette route et ce mode de transport.
            </p>
          </div>
        )}

        {activeCarriers.length === 0 && !comparison && (
          <div className="bg-surface rounded-none shadow-lg p-12 text-center">
            <Truck className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink mb-2">Aucun transporteur configuré</h3>
            <p className="text-ink-soft mb-6">
              Ajoutez d'abord vos transporteurs et leurs tarifs pour pouvoir comparer.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

interface RateCardProps {
  result: RateComparisonResult;
  rank: number;
  expandedId: string | null;
  onToggleExpand: (id: string | null) => void;
}

const RateCard = ({ result, rank, expandedId, onToggleExpand }: RateCardProps) => {
  const isExpanded = expandedId === result.rate.id;
  const isBest = rank === 1;

  return (
    <div className={`relative bg-surface rounded-none shadow-lg p-6 ${isBest ? 'ring-2 ring-warning' : ''} transition-shadow hover:shadow-xl`}>
      {isBest && (
        <>
          <span className="hud-corner hud-corner-tl" aria-hidden="true" />
          <span className="hud-corner hud-corner-tr" aria-hidden="true" />
          <span className="hud-corner hud-corner-bl" aria-hidden="true" />
          <span className="hud-corner hud-corner-br" aria-hidden="true" />
        </>
      )}
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 bg-accent-soft rounded-full flex items-center justify-center">
            <Truck className="h-6 w-6 text-accent" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h3 className="font-semibold text-ink">{result.carrierName}</h3>
              {isBest && (
                <span className="bg-warning/10 text-warning text-xs px-2 py-0.5 rounded-full font-medium">
                  Meilleur prix
                </span>
              )}
            </div>
            <span className="text-sm text-ink-soft font-mono">{result.carrierCode}</span>
          </div>
        </div>
        <div className="text-right">
          <div className="text-3xl font-bold text-ink">
            {result.estimatedCost.toFixed(2)} €
          </div>
          <div className="text-sm text-ink-soft"># {rank}</div>
        </div>
      </div>

      {/* Quick Stats */}
      <div className="grid grid-cols-3 gap-4 mb-4 p-4 bg-bg rounded-none">
        <div className="text-center">
          <div className="text-2xl font-bold text-ink">{result.transitDaysAvg} j</div>
          <div className="text-xs text-ink-soft">Délai moyen</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold text-ink">
            {result.co2EstimateKg > 0 ? `${result.co2EstimateKg} kg` : '—'}
          </div>
          <div className="text-xs text-ink-soft">CO₂ estimé</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold text-ink">
            {result.rate.transportMode}
          </div>
          <div className="text-xs text-ink-soft">Mode</div>
        </div>
      </div>

      {/* Expandable Details */}
      <button
        onClick={() => onToggleExpand(isExpanded ? null : result.rate.id)}
        className="w-full flex items-center justify-between text-sm text-accent hover:text-accent-strong mb-4"
      >
        <span>{isExpanded ? 'Masquer les détails' : 'Voir les détails'}</span>
        {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>

      {isExpanded && (
        <div className="space-y-3 pt-4 border-t border-line animate-slide-down">
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-ink-soft">Tarif de base :</span>
              <span className="font-medium ml-2">{result.rate.baseRate.toFixed(2)} €</span>
            </div>
            <div>
              <span className="text-ink-soft">Devise :</span>
              <span className="font-medium ml-2">{result.rate.currency}</span>
            </div>
            <div>
              <span className="text-ink-soft">Prix au kg :</span>
              <span className="font-medium ml-2">{result.rate.ratePerKg?.toFixed(2) || '—'} €/kg</span>
            </div>
            <div>
              <span className="text-ink-soft">Prix au m³ :</span>
              <span className="font-medium ml-2">{result.rate.ratePerCbm?.toFixed(2) || '—'} €/m³</span>
            </div>
            <div>
              <span className="text-ink-soft">Délai min/max :</span>
              <span className="font-medium ml-2">
                {result.rate.transitDaysMin || '—'} - {result.rate.transitDaysMax || '—'} jours
              </span>
            </div>
            <div>
              <span className="text-ink-soft">Poids min/max :</span>
              <span className="font-medium ml-2">
                {result.rate.minWeightKg || '—'} - {result.rate.maxWeightKg || '—'} kg
              </span>
            </div>
          </div>

          <div className="flex items-center space-x-2 text-sm text-ink-soft">
            <span className={`inline-flex items-center px-2 py-0.5 rounded-full ${
              result.rate.active ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink-soft'
            }`}>
              {result.rate.active ? 'Actif' : 'Inactif'}
            </span>
            <span className="text-xs">
              Valide du {result.rate.validFrom ? new Date(result.rate.validFrom).toLocaleDateString('fr-FR') : '—'}
              au {result.rate.validUntil ? new Date(result.rate.validUntil).toLocaleDateString('fr-FR') : '—'}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

export default RateComparison;