import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Calculator, Trash2, Package, TrendingUp, TrendingDown, Loader2, ArrowRight, Receipt, BarChart3, GitCompare, Share2, Copy, Check, Truck, ShieldCheck } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import InfoTooltip from '../components/InfoTooltip';
import { formatNumber } from '../lib/formatNumber';

interface LandedCost {
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
  portCharges: number;
  customsFees: number;
  handlingFees: number;
  lastMileCost: number;
  dutyAmount: number;
  dutyRate: number;
  vatAmount: number;
  vatRate: number;
  totalLandedCost: number;
  unitCount: number;
  totalLandedCostPerUnit: number;
  margin: number;
  marginPercent: number;
  sellingPrice: number;
  notes: string;
  createdAt: string;
}

interface LandedCostStats {
  total: number;
  avgTotalLandedCost: number;
  avgMargin: number;
}

const COUNTRIES = [
  { code: 'FR', name: 'France' },
  { code: 'DE', name: 'Allemagne' },
  { code: 'IT', name: 'Italie' },
  { code: 'ES', name: 'Espagne' },
  { code: 'NL', name: 'Pays-Bas' },
  { code: 'BE', name: 'Belgique' },
  { code: 'PT', name: 'Portugal' },
  { code: 'PL', name: 'Pologne' },
  { code: 'AT', name: 'Autriche' },
  { code: 'IE', name: 'Irlande' },
  { code: 'GB', name: 'Royaume-Uni' },
  { code: 'VN', name: 'Vietnam' },
  { code: 'CN', name: 'Chine' },
  { code: 'IN', name: 'Inde' },
  { code: 'BD', name: 'Bangladesh' },
  { code: 'TR', name: 'Turquie' },
  { code: 'MA', name: 'Maroc' },
  { code: 'TN', name: 'Tunisie' },
  { code: 'JP', name: 'Japon' },
  { code: 'KR', name: 'Corée du Sud' },
  { code: 'US', name: 'États-Unis' },
  { code: 'BR', name: 'Brésil' },
  { code: 'MX', name: 'Mexique' },
];

const INCOTERMS = ['EXW', 'FOB', 'CIF', 'DAP', 'DDP'];
const TRANSPORT_MODES = ['SEA', 'AIR', 'ROAD'];
const CURRENCIES = ['EUR', 'USD', 'GBP'];

const DEFAULT_FORM = {
  calculationName: '',
  originCountry: 'CN',
  destinationCountry: 'FR',
  incoterm: 'CIF',
  hsCode: '',
  transportMode: 'SEA',
  productValue: 0,
  currency: 'EUR',
  freightCost: 0,
  insuranceCost: 0,
  portCharges: 0,
  customsFees: 0,
  handlingFees: 0,
  lastMileCost: 0,
  unitCount: 1,
  sellingPrice: 0,
  notes: '',
};

// Champs saisis par l'utilisateur avant calcul -- distinct de LandedCost
// (le resultat complet renvoye par le backend, avec les champs calcules :
// droits de douane, TVA, marge...).
type LandedCostInput = typeof DEFAULT_FORM;

const LandedCostCalculator = () => {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(DEFAULT_FORM);
  const [lastResult, setLastResult] = useState<LandedCost | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'calculate' | 'what-if'>('calculate');
  const [scenarios, setScenarios] = useState<LandedCostInput[]>([
    { ...DEFAULT_FORM, calculationName: 'Scénario A' },
    { ...DEFAULT_FORM, calculationName: 'Scénario B' },
  ]);
  const [comparisonResults, setComparisonResults] = useState<LandedCost[] | null>(null);
  const [shareUrl, setShareUrl] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const user = useAuthStore((state) => state.user);

  const { data: calculations, isLoading } = useQuery({
    queryKey: ['landed-costs'],
    queryFn: async () => {
      const res = await incokalkAPI.landedCosts.list();
      return res.data as LandedCost[];
    },
    enabled: !!user,
  });

  const { data: stats } = useQuery({
    queryKey: ['landed-costs-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.landedCosts.stats();
      return res.data as LandedCostStats;
    },
    enabled: !!user,
  });

  const calculateMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.landedCosts.calculate(data),
    onSuccess: (res) => {
      toast.success('Calcul effectué avec succès');
      setLastResult(res.data as LandedCost);
      queryClient.invalidateQueries({ queryKey: ['landed-costs'] });
      queryClient.invalidateQueries({ queryKey: ['landed-costs-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du calcul');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.landedCosts.delete(id),
    onSuccess: () => {
      toast.success('Calcul supprimé');
      setDeleteConfirm(null);
      if (lastResult) setLastResult(null);
      queryClient.invalidateQueries({ queryKey: ['landed-costs'] });
      queryClient.invalidateQueries({ queryKey: ['landed-costs-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const whatIfMutation = useMutation({
    mutationFn: (data: LandedCostInput[]) => incokalkAPI.landedCosts.whatIf(data),
    onSuccess: (res) => {
      setComparisonResults(res.data as LandedCost[]);
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la comparaison');
    },
  });

  const shareMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.landedCosts.share(id),
    onSuccess: (res) => {
      const url = window.location.origin + res.data.shareUrl;
      setShareUrl(url);
      toast.success('Lien de partage généré');
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la génération du lien');
    },
  });

  const handleCalculate = (e: React.FormEvent) => {
    e.preventDefault();
    calculateMutation.mutate(form);
  };

  const updateField = (field: string, value: string | number) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const fmt = (n: number) => formatNumber(n, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const r = lastResult;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Landed Cost Calculator
          </h1>
          <p className="text-ink-soft mt-1">Calcul du coût complet débarqué</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab('calculate')}
            className={`px-4 py-2 rounded-none text-sm font-medium transition-colors flex items-center gap-2 ${activeTab === 'calculate' ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-line'}`}
          >
            <Calculator size={16} /> Calculer
          </button>
          <button
            onClick={() => setActiveTab('what-if')}
            className={`px-4 py-2 rounded-none text-sm font-medium transition-colors flex items-center gap-2 ${activeTab === 'what-if' ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-line'}`}
          >
            <GitCompare size={16} /> What-If
          </button>
        </div>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <Calculator size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Total calculs</p>
              <p className="text-2xl font-bold text-ink">{stats?.total ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-warning/10 flex items-center justify-center">
              <Receipt size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Coût moyen</p>
              <p className="text-2xl font-bold text-ink">
                {stats?.avgTotalLandedCost != null ? `${fmt(stats.avgTotalLandedCost)} €` : '—'}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
              <BarChart3 size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Marge moyenne</p>
              <p className="text-2xl font-bold text-ink">
                {stats?.avgMargin != null ? `${fmt(stats.avgMargin)} %` : '—'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {activeTab === 'calculate' ? (
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
        {/* Left: Form */}
        <div className="lg:col-span-2">
          <div className="lg:sticky lg:top-8">
            <div className="bg-surface rounded-none border border-line p-6">
              <h2 className="text-lg font-semibold text-ink mb-4">Nouveau calcul</h2>
              <form onSubmit={handleCalculate} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Nom du calcul</label>
                  <input
                    type="text"
                    value={form.calculationName}
                    onChange={(e) => updateField('calculationName', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="Optionnel"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Pays d'origine</label>
                    <select
                      value={form.originCountry}
                      onChange={(e) => updateField('originCountry', e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      {COUNTRIES.map((c) => (
                        <option key={c.code} value={c.code}>{c.code} / {c.name}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Pays de destination</label>
                    <select
                      value={form.destinationCountry}
                      onChange={(e) => updateField('destinationCountry', e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      {COUNTRIES.map((c) => (
                        <option key={c.code} value={c.code}>{c.code} / {c.name}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Incoterm</label>
                    <select
                      value={form.incoterm}
                      onChange={(e) => updateField('incoterm', e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      {INCOTERMS.map((i) => (
                        <option key={i} value={i}>{i}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Devise</label>
                    <select
                      value={form.currency}
                      onChange={(e) => updateField('currency', e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      {CURRENCIES.map((c) => (
                        <option key={c} value={c}>{c}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Code SH</label>
                    <input
                      type="text"
                      value={form.hsCode}
                      onChange={(e) => updateField('hsCode', e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="ex: 620443"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Mode transport</label>
                    <select
                      value={form.transportMode}
                      onChange={(e) => updateField('transportMode', e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      {TRANSPORT_MODES.map((m) => (
                        <option key={m} value={m}>{m === 'SEA' ? 'Maritime' : m === 'AIR' ? 'Aérien' : 'Routier'}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Valeur marchandise</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.productValue || ''}
                    onChange={(e) => updateField('productValue', parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="0.00"
                    required
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Fret</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.freightCost || ''}
                      onChange={(e) => updateField('freightCost', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="0.00"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Assurance</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.insuranceCost || ''}
                      onChange={(e) => updateField('insuranceCost', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="0.00"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Frais portuaire</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.portCharges || ''}
                      onChange={(e) => updateField('portCharges', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="0.00"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Frais douane</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.customsFees || ''}
                      onChange={(e) => updateField('customsFees', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="0.00"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Frais manutention</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.handlingFees || ''}
                      onChange={(e) => updateField('handlingFees', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="0.00"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Dernier kilomètre</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.lastMileCost || ''}
                      onChange={(e) => updateField('lastMileCost', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="0.00"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Nombre d'unités</label>
                    <input
                      type="number"
                      step="1"
                      min="1"
                      value={form.unitCount || ''}
                      onChange={(e) => updateField('unitCount', parseInt(e.target.value) || 1)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="1"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Prix de vente unitaire</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.sellingPrice || ''}
                      onChange={(e) => updateField('sellingPrice', parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="Optionnel"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Notes</label>
                  <textarea
                    value={form.notes}
                    onChange={(e) => updateField('notes', e.target.value)}
                    rows={3}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm resize-none"
                    placeholder="Optionnel"
                  />
                </div>

                <button
                  type="submit"
                  disabled={calculateMutation.isPending}
                  className="w-full px-4 py-2.5 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {calculateMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Calculator size={16} />}
                  Calculer
                </button>
              </form>
            </div>
          </div>
        </div>

        {/* Right: Results */}
        <div className="lg:col-span-3 space-y-6">
          {r ? (
            <>
              {/* Cost breakdown */}
              <div className="relative bg-surface rounded-none border border-line p-6">
                <span className="hud-corner hud-corner-tl" aria-hidden="true" />
                <span className="hud-corner hud-corner-tr" aria-hidden="true" />
                <span className="hud-corner hud-corner-bl" aria-hidden="true" />
                <span className="hud-corner hud-corner-br" aria-hidden="true" />
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Package size={20} className="text-accent" />
                    <h2 className="text-lg font-semibold text-ink">Ventilation des coûts</h2>
                  </div>
                  {r && (
                    <button
                      onClick={() => shareMutation.mutate(r.id)}
                      disabled={shareMutation.isPending}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-accent/10 text-accent rounded-none text-xs font-medium hover:bg-accent/20 transition-colors"
                    >
                      <Share2 size={14} />
                      Partager
                    </button>
                  )}
                </div>
                {shareUrl && (
                  <div className="mb-4 flex items-center gap-2 bg-accent/10 rounded-none p-3">
                    <input type="text" readOnly value={shareUrl} className="flex-1 text-xs bg-transparent text-accent-strong outline-none" />
                    <button onClick={() => { navigator.clipboard.writeText(shareUrl); setCopied(true); setTimeout(() => setCopied(false), 2000); }} className="p-1 hover:bg-accent/20 rounded">
                      {copied ? <Check size={14} className="text-success" /> : <Copy size={14} className="text-accent" />}
                    </button>
                  </div>
                )}
                {r.hsCode && (
                  <div className="mb-3 flex items-center gap-2 text-xs text-ink-soft">
                    <span>Code SH:</span><span className="font-mono font-medium text-ink-soft">{r.hsCode}</span>
                    <span className="mx-1">•</span>
                    <span>Transport:</span><span className="font-medium text-ink-soft">{r.transportMode === 'SEA' ? 'Maritime' : r.transportMode === 'AIR' ? 'Aérien' : 'Routier'}</span>
                  </div>
                )}
                <div className="space-y-2">
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Valeur produit</span>
                    <span className="font-medium text-ink">{fmt(r.productValue)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Fret</span>
                    <span className="font-medium text-ink">{fmt(r.freightCost)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Assurance</span>
                    <span className="font-medium text-ink">{fmt(r.insuranceCost)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-2 text-sm border-t border-line font-bold">
                    <span className="text-ink">CIF Total</span>
                    <span className="text-ink">{fmt(r.productValue + r.freightCost + r.insuranceCost)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft flex items-center gap-1.5">
                      Droits de douane ({fmt(r.dutyRate)}%)
                      <InfoTooltip text="Taxe due à l'importation, calculée sur la valeur CIF (produit + fret + assurance) au taux applicable au code SH de la marchandise." />
                    </span>
                    <span className="font-medium text-ink">{fmt(r.dutyAmount)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft flex items-center gap-1.5">
                      TVA ({fmt(r.vatRate)}%)
                      <InfoTooltip text="TVA à l'importation, calculée sur la valeur en douane (CIF + droits de douane), généralement récupérable si vous êtes assujetti." />
                    </span>
                    <span className="font-medium text-ink">{fmt(r.vatAmount)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Frais portuaire</span>
                    <span className="font-medium text-ink">{fmt(r.portCharges)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Frais douane</span>
                    <span className="font-medium text-ink">{fmt(r.customsFees)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Frais manutention</span>
                    <span className="font-medium text-ink">{fmt(r.handlingFees)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-1.5 text-sm">
                    <span className="text-ink-soft">Dernier kilomètre</span>
                    <span className="font-medium text-ink">{fmt(r.lastMileCost)} {r.currency}</span>
                  </div>
                  <div className="flex justify-between py-3 text-base border-t-2 border-ink">
                    <span className="font-bold text-ink">Coût total débarqué</span>
                    <span className="font-bold text-ink">{fmt(r.totalLandedCost)} {r.currency}</span>
                  </div>
                  {r.unitCount > 1 && (
                    <div className="flex justify-between py-2 text-sm bg-accent-soft -mx-6 px-6 rounded-none">
                      <span className="font-medium text-accent-strong">Coût par unité</span>
                      <span className="font-bold text-accent-strong">{fmt(r.totalLandedCostPerUnit)} {r.currency}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Margin card */}
              {r.sellingPrice > 0 && (
                <div className="bg-surface rounded-none border border-line p-6">
                  <div className="flex items-center gap-2 mb-4">
                    {r.margin >= 0 ? (
                      <TrendingUp size={20} className="text-success" />
                    ) : (
                      <TrendingDown size={20} className="text-danger" />
                    )}
                    <h2 className="text-lg font-semibold text-ink">Marge</h2>
                  </div>
                  <div className="space-y-2">
                    <div className="flex justify-between py-1.5 text-sm">
                      <span className="text-ink-soft">Prix de vente</span>
                      <span className="font-medium text-ink">{fmt(r.sellingPrice)} {r.currency}</span>
                    </div>
                    <div className="flex justify-between py-1.5 text-sm">
                      <span className="text-ink-soft">Coût total</span>
                      <span className="font-medium text-ink">{fmt(r.totalLandedCost)} {r.currency}</span>
                    </div>
                    <div className="flex justify-between py-2 text-sm border-t border-line">
                      <span className="font-medium text-ink-soft">Marge</span>
                      <span className={`font-bold ${r.margin >= 0 ? 'text-success' : 'text-danger'}`}>
                        {fmt(r.margin)} {r.currency} ({fmt(r.marginPercent)} %)
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {/* Prochaine étape — le coût débarqué calculé, une fois connu, mène
                  naturellement à créer l'expédition ou l'assurer, mais rien dans
                  le produit ne le suggérait avant : le lien logique existait
                  entre les pages, pas dans l'UI. */}
              <div className="bg-surface rounded-none border border-line p-6">
                <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wide mb-3">Prochaine étape</h2>
                <div className="flex flex-col sm:flex-row gap-3">
                  <Link
                    to="/shipments"
                    className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong transition-colors"
                  >
                    <Truck size={16} />
                    Créer l'expédition
                  </Link>
                  <Link
                    to="/assurance-cargo"
                    className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-accent-soft text-accent-strong rounded-none text-sm font-medium hover:bg-accent/20 transition-colors"
                  >
                    <ShieldCheck size={16} />
                    Assurer la marchandise
                  </Link>
                </div>
              </div>
            </>
          ) : (
            <div className="bg-surface rounded-none border border-line p-12 text-center">
              <Calculator size={40} className="mx-auto mb-3 text-ink-soft" />
              <p className="text-ink-soft">Remplissez le formulaire et cliquez sur "Calculer" pour voir les résultats</p>
            </div>
          )}

          {/* Previous calculations table */}
          <div className="bg-surface rounded-none border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line">
              <h2 className="text-lg font-semibold text-ink">Calculs précédents</h2>
            </div>
            {isLoading ? (
              <div className="px-6 py-12 text-center text-ink-soft">
                <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
                Chargement...
              </div>
            ) : !calculations || calculations.length === 0 ? (
              <div className="px-6 py-12 text-center text-ink-soft">
                <Package size={32} className="mx-auto mb-3 text-ink-soft" />
                <p>Aucun calcul enregistré</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="bg-surface-2 border-b border-line">
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom</th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Trajet</th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Incoterm</th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Valeur</th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Coût total</th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marge</th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {calculations.map((calc: LandedCost) => (
                      <tr key={calc.id} className="hover:bg-surface-2 transition-colors">
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {new Date(calc.createdAt).toLocaleDateString('fr-FR')}
                        </td>
                        <td className="px-6 py-4 text-sm font-medium text-ink">
                          {calc.calculationName || '—'}
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          <span className="flex items-center gap-1.5">
                            {calc.originCountry}
                            <ArrowRight size={12} className="text-ink-soft" />
                            {calc.destinationCountry}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{calc.incoterm}</td>
                        <td className="px-6 py-4 text-sm text-ink text-right">{fmt(calc.productValue)} {calc.currency}</td>
                        <td className="px-6 py-4 text-sm font-medium text-ink text-right">{fmt(calc.totalLandedCost)} {calc.currency}</td>
                        <td className="px-6 py-4 text-sm text-right">
                          {calc.sellingPrice > 0 ? (
                            <span className={calc.margin >= 0 ? 'text-success font-medium' : 'text-danger font-medium'}>
                              {fmt(calc.margin)} {calc.currency} ({fmt(calc.marginPercent)}%)
                            </span>
                          ) : '—'}
                        </td>
                        <td className="px-6 py-4 text-right">
                          {deleteConfirm === calc.id ? (
                            <div className="flex items-center justify-end gap-1">
                              <button
                                onClick={() => deleteMutation.mutate(calc.id)}
                                disabled={deleteMutation.isPending}
                                className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                              >
                                {deleteMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Oui'}
                              </button>
                              <button
                                onClick={() => setDeleteConfirm(null)}
                                className="px-2 py-1 text-xs bg-line text-ink-soft rounded hover:bg-line transition-colors"
                              >
                                Non
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => setDeleteConfirm(calc.id)}
                              className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>
      ) : (
      /* What-If Tab */
      <div className="space-y-6">
        <div className="bg-surface rounded-none border border-line p-6">
          <h2 className="text-lg font-semibold text-ink mb-4">Comparaison de scénarios</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {scenarios.map((scenario, idx) => (
              <div key={idx} className="bg-surface-2 rounded-none p-4 space-y-3">
                <h3 className="font-semibold text-ink-soft">{scenario.calculationName || `Scénario ${String.fromCharCode(65 + idx)}`}</h3>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Origine</label>
                    <select value={scenario.originCountry} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], originCountry: e.target.value };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm">
                      {COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.code}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Destination</label>
                    <select value={scenario.destinationCountry} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], destinationCountry: e.target.value };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm">
                      {COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.code}</option>)}
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Incoterm</label>
                    <select value={scenario.incoterm} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], incoterm: e.target.value };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm">
                      {INCOTERMS.map((i) => <option key={i} value={i}>{i}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Code SH</label>
                    <input type="text" value={scenario.hsCode || ''} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], hsCode: e.target.value };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm" placeholder="620443" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Transport</label>
                    <select value={scenario.transportMode} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], transportMode: e.target.value };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm">
                      {TRANSPORT_MODES.map((m) => <option key={m} value={m}>{m}</option>)}
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Valeur produit</label>
                    <input type="number" step="0.01" min="0" value={scenario.productValue || ''} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], productValue: parseFloat(e.target.value) || 0 };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Fret</label>
                    <input type="number" step="0.01" min="0" value={scenario.freightCost || ''} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], freightCost: parseFloat(e.target.value) || 0 };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Assurance</label>
                    <input type="number" step="0.01" min="0" value={scenario.insuranceCost || ''} onChange={(e) => {
                      const newScenarios = [...scenarios];
                      newScenarios[idx] = { ...newScenarios[idx], insuranceCost: parseFloat(e.target.value) || 0 };
                      setScenarios(newScenarios);
                    }} className="w-full px-2 py-1.5 border border-line rounded text-sm" />
                  </div>
                </div>
              </div>
            ))}
          </div>
          <div className="flex items-center gap-2 mt-4">
            {scenarios.length < 4 && (
              <button onClick={() => setScenarios([...scenarios, { ...DEFAULT_FORM, calculationName: `Scénario ${String.fromCharCode(65 + scenarios.length)}` }])} className="px-3 py-1.5 text-sm bg-surface-2 text-ink-soft rounded-none hover:bg-line">
                + Ajouter un scénario
              </button>
            )}
            {scenarios.length > 2 && (
              <button onClick={() => setScenarios(scenarios.slice(0, -1))} className="px-3 py-1.5 text-sm bg-surface-2 text-ink-soft rounded-none hover:bg-line">
                − Retirer
              </button>
            )}
            <button
              onClick={() => whatIfMutation.mutate(scenarios)}
              disabled={whatIfMutation.isPending}
              className="ml-auto px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 flex items-center gap-2"
            >
              {whatIfMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <GitCompare size={16} />}
              Comparer les scénarios
            </button>
          </div>
        </div>

        {comparisonResults && comparisonResults.length > 0 && (
          <div className="bg-surface rounded-none border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line">
              <h3 className="text-lg font-semibold text-ink">Résultats de la comparaison</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-surface-2 border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase px-6 py-3">Scénario</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase px-6 py-3">Trajet</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase px-6 py-3">Incoterm</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase px-6 py-3">SH</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase px-6 py-3">Valeur</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase px-6 py-3">Droits</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase px-6 py-3">TVA</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase px-6 py-3">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {comparisonResults.map((r: LandedCost, idx: number) => (
                    <tr key={idx} className="hover:bg-surface-2">
                      <td className="px-6 py-4 text-sm font-medium text-ink">{scenarios[idx]?.calculationName || `S${idx + 1}`}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{r.originCountry} → {r.destinationCountry}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{r.incoterm}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{r.hsCode || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{fmt(Number(r.productValue))} €</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{fmt(Number(r.dutyAmount))} € <span className="text-xs text-ink-soft">({fmt(Number(r.dutyRate))}%)</span></td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{fmt(Number(r.vatAmount))} € <span className="text-xs text-ink-soft">({fmt(Number(r.vatRate))}%)</span></td>
                      <td className="px-6 py-4 text-sm font-bold text-ink text-right">{fmt(Number(r.totalLandedCost))} €</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
      )}
    </div>
  );
};

export default LandedCostCalculator;
