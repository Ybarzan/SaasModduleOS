import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Search, DollarSign, Clock, Leaf, Trophy, Zap, Truck, Ship, Plane, Loader2, Download
} from 'lucide-react';
import type { QuoteRequest, QuoteResponse } from '../types';

const COUNTRIES: { name: string; flag: string }[] = [
  { name: 'France', flag: '🇫🇷' },
  { name: 'Allemagne', flag: '🇩🇪' },
  { name: 'Belgique', flag: '🇧🇪' },
  { name: 'Pays-Bas', flag: '🇳🇱' },
  { name: 'Espagne', flag: '🇪🇸' },
  { name: 'Italie', flag: '🇮🇹' },
  { name: 'Portugal', flag: '🇵🇹' },
  { name: 'Royaume-Uni', flag: '🇬🇧' },
  { name: 'Chine', flag: '🇨🇳' },
  { name: 'États-Unis', flag: '🇺🇸' },
  { name: 'Japon', flag: '🇯🇵' },
  { name: 'Corée du Sud', flag: '🇰🇷' },
  { name: 'Inde', flag: '🇮🇳' },
  { name: 'Vietnam', flag: '🇻🇳' },
  { name: 'Thaïlande', flag: '🇹🇭' },
  { name: 'Singapour', flag: '🇸🇬' },
  { name: 'Maroc', flag: '🇲🇦' },
  { name: 'Tunisie', flag: '🇹🇳' },
  { name: 'Turquie', flag: '🇹🇷' },
  { name: 'Émirats Arabes Unis', flag: '🇦🇪' },
  { name: 'Brésil', flag: '🇧🇷' },
  { name: 'Mexique', flag: '🇲🇽' },
  { name: 'Canada', flag: '🇨🇦' },
  { name: 'Australie', flag: '🇦🇺' },
];

const TRANSPORT_MODES = [
  { value: '', label: 'Tous' },
  { value: 'SEA', label: 'Maritime' },
  { value: 'AIR', label: 'Aérien' },
  { value: 'ROAD', label: 'Routier' },
];

const DISPLAY_CURRENCIES = [
  { value: 'EUR', label: 'EUR — Euro' },
  { value: 'USD', label: 'USD — Dollar US' },
  { value: 'GBP', label: 'GBP — Livre Sterling' },
  { value: 'MAD', label: 'MAD — Dirham Marocain' },
  { value: 'CHF', label: 'CHF — Franc Suisse' },
  { value: 'CAD', label: 'CAD — Dollar Canadien' },
  { value: 'CNY', label: 'CNY — Yuan Chinois' },
  { value: 'JPY', label: 'JPY — Yen Japonais' },
  { value: 'TND', label: 'TND — Dinar Tunisien' },
  { value: 'TRY', label: 'TRY — Lire Turque' },
];

const modeColors: Record<string, string> = {
  SEA: 'bg-accent-soft text-accent-strong',
  AIR: 'bg-accent-soft text-accent-strong',
  ROAD: 'bg-warning/10 text-warning',
};

const modeLabels: Record<string, string> = {
  SEA: 'Maritime',
  AIR: 'Aérien',
  ROAD: 'Routier',
};

const modeIcons: Record<string, typeof Ship> = {
  SEA: Ship,
  AIR: Plane,
  ROAD: Truck,
};

const CountrySelect = ({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
}) => (
  <select
    value={value}
    onChange={(e) => onChange(e.target.value)}
    className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
  >
    <option value="">{placeholder}</option>
    {COUNTRIES.map((c) => (
      <option key={c.name} value={c.name}>
        {c.flag} {c.name}
      </option>
    ))}
  </select>
);

const Quotes = () => {
  const [form, setForm] = useState<QuoteRequest>({
    originCountry: '',
    destinationCountry: '',
    transportMode: '',
    weightKg: 0,
    volumeM3: 0,
    goodsValue: 0,
    currency: 'EUR',
    hsCode: '',
  });
  const [results, setResults] = useState<QuoteResponse[]>([]);

  const quoteMutation = useMutation({
    mutationFn: (data: QuoteRequest) => incokalkAPI.quotes.get(data),
    onSuccess: (res) => {
      const quotes = (res.data as QuoteResponse[]) || [];
      setResults(quotes);
      if (quotes.length === 0) {
        toast('Aucun tarif trouvé pour cette recherche', { icon: '📦' });
      } else {
        toast.success(`${quotes.length} tarif(s) trouvé(s)`);
      }
    },
    onError: () => toast.error('Erreur lors de la demande de devis'),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.originCountry || !form.destinationCountry) {
      toast.error('Veuillez sélectionner les pays d\'origine et de destination');
      return;
    }
    if (form.weightKg <= 0 || form.volumeM3 <= 0) {
      toast.error('Le poids et le volume doivent être supérieurs à 0');
      return;
    }
    quoteMutation.mutate(form);
  };

  const cheapest = results.length > 0
    ? results.reduce((min, r) => (r.totalCost < min.totalCost ? r : min), results[0])
    : null;

  const fastest = results.length > 0
    ? results.reduce((min, r) => {
        const minDays = r.transitDaysMin ?? 999;
        const curDays = min.transitDaysMin ?? 999;
        return curDays < minDays ? r : min;
      }, results[0])
    : null;

  const greenest = results.length > 0
    ? results.reduce((min, r) => {
        const minCo2 = r.co2EstimateKg ?? 999999;
        const curCo2 = min.co2EstimateKg ?? 999999;
        return curCo2 < minCo2 ? r : min;
      }, results[0])
    : null;

  const exportPdfMutation = useMutation({
    mutationFn: (data: QuoteRequest) => incokalkAPI.export.quotesPdf(data),
    onSuccess: (res) => {
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `devis-incokalk-${new Date().toISOString().slice(0, 10)}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('PDF exporté avec succès');
    },
    onError: () => toast.error('Erreur lors de l\'export PDF'),
  });

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-ink mb-2">Comparaison de devis</h1>
          <p className="text-ink-soft">Comparez les tarifs de transport entre transporteurs</p>
        </div>

        {/* Search Form */}
        <div className="bg-surface rounded-lg shadow-lg p-6 mb-8">
          <form onSubmit={handleSubmit}>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Pays d'origine *
                </label>
                <CountrySelect
                  value={form.originCountry}
                  onChange={(v) => setForm({ ...form, originCountry: v })}
                  placeholder="Origine"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Pays de destination *
                </label>
                <CountrySelect
                  value={form.destinationCountry}
                  onChange={(v) => setForm({ ...form, destinationCountry: v })}
                  placeholder="Destination"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Mode de transport
                </label>
                <select
                  value={form.transportMode || ''}
                  onChange={(e) => setForm({ ...form, transportMode: e.target.value })}
                  className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                >
                  {TRANSPORT_MODES.map((m) => (
                    <option key={m.value} value={m.value}>{m.label}</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">
                    Poids (kg) *
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    value={form.weightKg || ''}
                    onChange={(e) => setForm({ ...form, weightKg: parseFloat(e.target.value) || 0 })}
                    className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                    placeholder="0"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">
                    Volume (m³) *
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.volumeM3 || ''}
                    onChange={(e) => setForm({ ...form, volumeM3: parseFloat(e.target.value) || 0 })}
                    className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                    placeholder="0"
                  />
                </div>
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Valeur des marchandises (€)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={form.goodsValue || ''}
                  onChange={(e) => setForm({ ...form, goodsValue: parseFloat(e.target.value) || 0 })}
                  className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Code SH (optionnel)
                </label>
                <input
                  type="text"
                  value={form.hsCode || ''}
                  onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
                  className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                  placeholder="Ex: 8471.30"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Devise d'affichage
                </label>
                <div className="relative">
                  <DollarSign size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                  <select
                    value={form.currency || 'EUR'}
                    onChange={(e) => setForm({ ...form, currency: e.target.value })}
                    className="w-full border border-line rounded-lg pl-9 pr-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                  >
                    {DISPLAY_CURRENCIES.map((c) => (
                      <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="flex items-end">
                <button
                  type="submit"
                  disabled={quoteMutation.isPending}
                  className="w-full bg-accent text-white px-6 py-2 rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center justify-center space-x-2"
                >
                  {quoteMutation.isPending ? (
                    <Loader2 className="h-5 w-5 animate-spin" />
                  ) : (
                    <Search size={20} />
                  )}
                  <span>Demander des devis</span>
                </button>
              </div>
            </div>
          </form>
        </div>

        {/* Loading */}
        {quoteMutation.isPending && (
          <div className="grid md:grid-cols-3 gap-6">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-surface rounded-lg shadow-lg p-6 animate-pulse">
                <div className="flex items-center space-x-3 mb-4">
                  <div className="w-12 h-12 bg-surface-2 rounded-full" />
                  <div className="space-y-2">
                    <div className="h-4 bg-surface-2 rounded w-24" />
                    <div className="h-3 bg-surface-2 rounded w-16" />
                  </div>
                </div>
                <div className="space-y-3">
                  <div className="h-3 bg-surface-2 rounded w-full" />
                  <div className="h-3 bg-surface-2 rounded w-3/4" />
                  <div className="h-3 bg-surface-2 rounded w-1/2" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Results */}
        {!quoteMutation.isPending && results.length > 0 && (
          <div className="space-y-4">
            {/* Source summary */}
            {(() => {
              const internalCount = results.filter((q) => !q.providerType || q.providerType === 'INTERNAL').length;
              const shippoCount = results.filter((q) => q.providerType === 'SHIPPO').length;
              const dhlCount = results.filter((q) => q.providerType === 'DHL').length;
              const parts = [];
              if (internalCount > 0) parts.push(`Interne (${internalCount})`);
              if (shippoCount > 0) parts.push(`Shippo (${shippoCount})`);
              if (dhlCount > 0) parts.push(`DHL (${dhlCount})`);
              return (
                <div className="text-sm text-ink-soft mb-2">
                  {results.length} tarif(s) trouvé(s) — sources : {parts.join(', ')}
                </div>
              );
            })()}

            <div className="flex justify-end mb-4">
              <button
                onClick={() => exportPdfMutation.mutate(form)}
                disabled={exportPdfMutation.isPending}
                className="bg-accent text-white px-4 py-2 rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2 text-sm"
              >
                {exportPdfMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Download size={16} />
                )}
                <span>Exporter PDF</span>
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
              {cheapest && (
                <div className="bg-success/10 border-2 border-success/40 rounded-lg p-4 text-center">
                  <Trophy className="h-8 w-8 text-success mx-auto mb-2" />
                  <div className="text-sm font-medium text-success">MEILLEUR TARIF</div>
                  <div className="text-lg font-bold text-success">
                    {cheapest.totalCost.toFixed(2)} €
                  </div>
                  <div className="text-sm text-success">{cheapest.carrierName}</div>
                </div>
              )}
              {fastest && (
                <div className="bg-accent-soft border-2 border-accent/20 rounded-lg p-4 text-center">
                  <Zap className="h-8 w-8 text-accent mx-auto mb-2" />
                  <div className="text-sm font-medium text-accent-strong">LE PLUS RAPIDE</div>
                  <div className="text-lg font-bold text-accent-strong">
                    {fastest.transitDaysMin}-{fastest.transitDaysMax} jours
                  </div>
                  <div className="text-sm text-accent">{fastest.carrierName}</div>
                </div>
              )}
              {greenest && (
                <div className="bg-success/10 border-2 border-success/40 rounded-lg p-4 text-center">
                  <Leaf className="h-8 w-8 text-success mx-auto mb-2" />
                  <div className="text-sm font-medium text-success">LE PLUS ÉCO</div>
                  <div className="text-lg font-bold text-success">
                    {greenest.co2EstimateKg?.toFixed(1)} kg CO₂
                  </div>
                  <div className="text-sm text-success">{greenest.carrierName}</div>
                </div>
              )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {[...results]
                .sort((a, b) => a.totalCost - b.totalCost)
                .map((quote, idx) => {
                  const ModeIcon = modeIcons[quote.transportMode] || Truck;
                  const isCheapest = quote === cheapest;
                  const isFastest = quote === fastest;
                  const isGreenest = quote === greenest;
                  return (
                    <div
                      key={idx}
                      className={`bg-surface rounded-lg shadow-lg p-6 transition-shadow hover:shadow-xl ${
                        isCheapest ? 'ring-2 ring-success' : ''
                      }`}
                    >
                      <div className="flex items-start justify-between mb-4">
                        <div className="flex items-center space-x-3">
                          <div className="w-12 h-12 bg-surface-2 rounded-full flex items-center justify-center overflow-hidden">
                            {quote.carrierLogo ? (
                              <img
                                src={quote.carrierLogo}
                                alt={quote.carrierName}
                                className="w-full h-full object-cover"
                              />
                            ) : (
                              <Truck className="h-6 w-6 text-ink-soft" />
                            )}
                          </div>
                          <div>
                            <div className="font-semibold text-ink">{quote.carrierName}</div>
                            <div className="text-sm text-ink-soft">{quote.rateName}</div>
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-1 justify-end">
                          <span
                            className={`inline-flex items-center space-x-1 px-2 py-1 rounded-full text-xs font-medium ${modeColors[quote.transportMode] || 'bg-surface-2 text-ink'}`}
                          >
                            <ModeIcon size={12} />
                            <span>{modeLabels[quote.transportMode] || quote.transportMode}</span>
                          </span>
                          <span
                            className={`px-2 py-1 rounded-full text-xs font-medium ${
                              quote.providerType === 'SHIPPO'
                                ? 'bg-accent-soft text-accent-strong'
                                : quote.providerType === 'DHL'
                                ? 'bg-warning/10 text-warning'
                                : 'bg-success/10 text-success'
                            }`}
                          >
                            {quote.providerType === 'SHIPPO'
                              ? 'API Shippo'
                              : quote.providerType === 'DHL'
                              ? 'API DHL'
                              : 'Interne'}
                          </span>
                          {isCheapest && (
                            <span className="bg-success/10 text-success px-2 py-1 rounded-full text-xs font-medium">
                              MEILLEUR TARIF
                            </span>
                          )}
                          {isFastest && (
                            <span className="bg-accent-soft text-accent-strong px-2 py-1 rounded-full text-xs font-medium">
                              LE PLUS RAPIDE
                            </span>
                          )}
                          {isGreenest && (
                            <span className="bg-success/10 text-success px-2 py-1 rounded-full text-xs font-medium">
                              LE PLUS ÉCO
                            </span>
                          )}
                        </div>
                      </div>

                      <div className="space-y-3">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center space-x-2 text-ink-soft">
                            <DollarSign size={16} />
                            <span className="text-sm">Coût total</span>
                          </div>
                          <div className="text-right">
                            <span className="text-xl font-bold text-ink">
                              {quote.totalCost.toFixed(2)} {quote.currency}
                            </span>
                            {quote.totalCostConverted != null && quote.displayCurrency && (
                              <div className="text-sm text-accent font-medium">
                                ≈ {quote.totalCostConverted.toFixed(2)} {quote.displayCurrency}
                              </div>
                            )}
                          </div>
                        </div>
                        {quote.transitDaysMin != null && quote.transitDaysMax != null && (
                          <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-2 text-ink-soft">
                              <Clock size={16} />
                              <span className="text-sm">Délai de transport</span>
                            </div>
                            <span className="font-medium text-ink">
                              {quote.transitDaysMin}-{quote.transitDaysMax} jours
                            </span>
                          </div>
                        )}
                        {quote.co2EstimateKg != null && (
                          <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-2 text-ink-soft">
                              <Leaf size={16} />
                              <span className="text-sm">CO₂ estimé</span>
                            </div>
                            <span className="font-medium text-ink">
                              {quote.co2EstimateKg.toFixed(1)} kg
                            </span>
                          </div>
                        )}
                        <div className="flex items-center justify-between pt-2 border-t border-line">
                          <span className="text-sm text-ink-soft">Tarif de base</span>
                          <span className="font-medium text-ink">
                            {quote.baseRate.toFixed(2)} {quote.currency}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })}
            </div>
          </div>
        )}

        {/* Empty State */}
        {!quoteMutation.isPending && results.length === 0 && (
          <div className="bg-surface rounded-lg shadow-lg p-12 text-center">
            <Search className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink mb-2">
              Recherchez des devis de transport
            </h3>
            <p className="text-ink-soft">
              Remplissez le formulaire ci-dessus pour comparer les tarifs entre transporteurs
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Quotes;
