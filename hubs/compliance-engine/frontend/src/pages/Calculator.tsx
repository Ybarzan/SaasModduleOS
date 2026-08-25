import type { AxiosError } from 'axios';
import { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import toast from 'react-hot-toast';
import { Calculator as CalculatorIcon, ArrowLeft, Loader2, Layers, X, AlertCircle, AlertTriangle, Info, Package, Plus, Trash2 } from 'lucide-react';
import { CostChart } from '../components/CostChart';
import type { ComplianceAlert, Incoterm, SimulationParams, SimulationResponse } from '../types';
import { formatEur } from '../lib/formatNumber';

type TransportMode = 'SEA' | 'AIR' | 'ROAD';

interface SimulationApiData {
  incotermFullName?: string;
  buyerRiskScore?: number;
  riskLevel?: string;
  estimatedDays?: number;
  buyerCosts?: Record<string, number>;
  sellerCosts?: Record<string, number>;
  totalBuyerCost?: number;
  totalSellerCost?: number;
  responsibilities?: Record<string, boolean>;
  recommendations?: string[];
  warnings?: string[];
  buyerRisks?: string[];
  complianceAlerts?: ComplianceAlert[];
  comparison?: unknown[];
  logistics?: {
    totalBoxes?: number;
    totalVolumeM3?: number;
    totalWeightKg?: number;
    utilizationPercent?: number;
    recommendedMode?: string;
    modeReason?: string;
    totalPackageVolumeM3?: number;
  };
}

interface TruckingOption {
  mode: string;
  label: string;
  description: string;
  recommended?: boolean;
  transitDays: number;
  costPerPallet: number;
  co2Kg: number;
  costEur: number;
}

interface TruckingRates {
  options: TruckingOption[];
}


const WhatIfForm = ({
  initialParams,
  onRun,
  onCancel
}: {
  initialParams: SimulationParams,
  onRun: (params: SimulationParams) => void,
  onCancel: () => void
}) => {
  const [params, setParams] = useState<SimulationParams>(initialParams);

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-ink-soft mb-1">Valeur marchandise (€)</label>
        <input
          type="number"
          value={params.productValue}
          onChange={(e) => setParams({ ...params, productValue: Number(e.target.value) })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-warning focus:border-transparent"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-ink-soft mb-1">Poids (kg)</label>
        <input
          type="number"
          value={params.weight}
          onChange={(e) => setParams({ ...params, weight: Number(e.target.value) })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-warning focus:border-transparent"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-ink-soft mb-1">Mode de transport</label>
        <select
          value={params.transportMode}
          onChange={(e) => setParams({ ...params, transportMode: e.target.value as TransportMode })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-warning focus:border-transparent"
        >
          <option value="SEA">Maritime</option>
          <option value="AIR">Aérien</option>
          <option value="ROAD">Routier</option>
        </select>
      </div>
      <div className="flex space-x-3 pt-4">
        <button
          onClick={() => onRun(params)}
          className="flex-1 bg-warning text-white py-2 px-4 rounded-none hover:bg-warning/90 font-medium"
        >
          Lancer le What-if
        </button>
        <button
          onClick={onCancel}
          className="flex-1 bg-surface-2 text-ink-soft py-2 px-4 rounded-none hover:bg-line font-medium"
        >
          Annuler
        </button>
      </div>
    </div>
  );
};

const Calculator = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const preselectedIncotermId = location.state?.incotermId;
  const preselectedMode = location.state?.transportMode as 'SEA' | 'AIR' | 'ROAD' | undefined;

  const [selectedIncoterm, setSelectedIncoterm] = useState<Incoterm | null>(null);
  const [formData, setFormData] = useState({
    productValue: 10000,
    currency: 'EUR',
    originCountry: 'CN',
    destinationCountry: 'FR',
    weight: 100,
    transportMode: 'SEA' as 'SEA' | 'AIR' | 'ROAD',
  });

  const [packagingItems, setPackagingItems] = useState<Array<{
    sku: string; lengthCm: number; widthCm: number; heightCm: number; weightKg: number; quantity: number;
  }>>([]);

  const [result, setResult] = useState<SimulationResponse | null>(null);
  const [pinnedResults, setPinnedResults] = useState<SimulationResponse[]>([]);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [isComparing, setIsComparing] = useState(false);
  const [branchingParams, setBranchingParams] = useState<SimulationParams | null>(null);
  const [isBranching, setIsBranching] = useState(false);
  const [newItem, setNewItem] = useState({ sku: '', lengthCm: 30, widthCm: 20, heightCm: 15, weightKg: 2, quantity: 1 });
  const [truckingRates, setTruckingRates] = useState<TruckingRates | null>(null);
  const [loadingTrucking, setLoadingTrucking] = useState(false);

  const transformResult = (data: SimulationApiData, incotermCode: string): SimulationResponse => ({
    incoterm: incotermCode,
    incotermFullName: data.incotermFullName || '',
    buyerRiskScore: data.buyerRiskScore || 0,
    riskLevel: data.riskLevel || '',
    estimatedDays: data.estimatedDays || 0,
    buyerCosts: {
      goodsValue: data.buyerCosts?.goodsValue || 0,
      exportCustoms: data.buyerCosts?.exportCustoms || 0,
      originHandling: data.buyerCosts?.originHandling || 0,
      originDocumentation: data.buyerCosts?.originDocumentation || 0,
      freight: data.buyerCosts?.freight || 0,
      insurance: data.buyerCosts?.insurance || 0,
      destinationHandling: data.buyerCosts?.destinationHandling || 0,
      destinationDocumentation: data.buyerCosts?.destinationDocumentation || 0,
      importDuties: data.buyerCosts?.importDuties || 0,
      importVat: data.buyerCosts?.importVat || 0,
      lastMileDelivery: data.buyerCosts?.lastMileDelivery || 0,
    },
    sellerCosts: {
      goodsValue: data.sellerCosts?.goodsValue || 0,
      exportCustoms: data.sellerCosts?.exportCustoms || 0,
      originHandling: data.sellerCosts?.originHandling || 0,
      originDocumentation: data.sellerCosts?.originDocumentation || 0,
      freight: data.sellerCosts?.freight || 0,
      insurance: data.sellerCosts?.insurance || 0,
      destinationHandling: data.sellerCosts?.destinationHandling || 0,
      destinationDocumentation: data.sellerCosts?.destinationDocumentation || 0,
      importDuties: data.sellerCosts?.importDuties || 0,
      importVat: data.sellerCosts?.importVat || 0,
      lastMileDelivery: data.sellerCosts?.lastMileDelivery || 0,
    },
    totalBuyerCost: data.totalBuyerCost || 0,
    totalSellerCost: data.totalSellerCost || 0,
    responsibilities: {
      sellerExportClearance: data.responsibilities?.sellerExportClearance || false,
      sellerOriginCharges: data.responsibilities?.sellerOriginCharges || false,
      sellerMainFreight: data.responsibilities?.sellerMainFreight || false,
      sellerInsurance: data.responsibilities?.sellerInsurance || false,
      sellerDestinationCharges: data.responsibilities?.sellerDestinationCharges || false,
      sellerImportDuties: data.responsibilities?.sellerImportDuties || false,
      sellerVat: data.responsibilities?.sellerVat || false,
    },
    recommendations: data.recommendations || [],
    warnings: data.warnings || [],
    buyerRisks: data.buyerRisks || [],
    complianceAlerts: data.complianceAlerts || [],
    comparison: data.comparison || [],
    logistics: data.logistics ? {
      totalBoxes: data.logistics.totalBoxes || 0,
      totalVolumeM3: data.logistics.totalVolumeM3 || 0,
      totalWeightKg: data.logistics.totalWeightKg || 0,
      utilizationPercent: data.logistics.utilizationPercent || 0,
      recommendedMode: data.logistics.recommendedMode || '',
      modeReason: data.logistics.modeReason || '',
      totalPackageVolumeM3: data.logistics.totalPackageVolumeM3 || 0,
    } : undefined,
  });

  const pinResult = () => {
    if (result && !pinnedResults.find(r => r.incoterm === result.incoterm)) {
      setPinnedResults([...pinnedResults, result]);
    } else {
      toast.error('Cette simulation est déjà épinglée');
    }
  };

  const unpinResult = (index: number) => {
    setPinnedResults(pinnedResults.filter((_, i) => i !== index));
  };

  const buildRequest = (overrides?: { transportMode?: 'SEA' | 'AIR' | 'ROAD' }) => ({
    incoterm: selectedIncoterm!.code,
    goodsValue: formData.productValue,
    weightKg: formData.weight,
    volumeM3: 1,
    currency: formData.currency,
    originCountry: formData.originCountry,
    destinationCountry: formData.destinationCountry,
    transportMode: overrides?.transportMode ?? formData.transportMode,
    insuranceLevel: 'STANDARD' as const,
    compareWithOthers: false,
    packagingItems: packagingItems.length > 0 ? packagingItems : undefined,
  });

  const compareAllModes = async () => {
    if (!selectedIncoterm) {
      toast.error('Veuillez sélectionner un Incoterm');
      return;
    }

    setIsComparing(true);
    const modes: ('SEA' | 'AIR' | 'ROAD')[] = ['SEA', 'AIR', 'ROAD'];
    const newPinnedResults: SimulationResponse[] = [];

    try {
      for (const mode of modes) {
        const response = await incokalkAPI.simulation.calculate(buildRequest({ transportMode: mode }));
        const data = response.data;

        const calculationResult = transformResult(data, selectedIncoterm.code);
        calculationResult.params = {
          incotermId: selectedIncoterm.id as unknown as number,
          incotermCode: selectedIncoterm.code,
          productValue: formData.productValue,
          currency: formData.currency,
          originCountry: formData.originCountry,
          destinationCountry: formData.destinationCountry,
          weight: formData.weight,
          transportMode: mode,
        };
        newPinnedResults.push(calculationResult);
      }

      setPinnedResults(prev => [...prev, ...newPinnedResults]);
      toast.success('Comparaison des modes effectuée !');
    } catch (error) {
      console.error('Erreur lors de la comparaison:', error);
      toast.error('Erreur lors de la comparaison automatique des modes');
    } finally {
      setIsComparing(false);
    }
  };

  const handleBranch = (params: SimulationParams) => {
    setBranchingParams(params);
    setIsBranching(true);
  };

  const handleRunWhatIf = async (newParams: SimulationParams) => {
    if (!selectedIncoterm) {
      toast.error('Veuillez sélectionner un Incoterm');
      return;
    }

    setIsBranching(false);

    try {
      const response = await incokalkAPI.simulation.calculate({
        incoterm: selectedIncoterm.code,
        goodsValue: newParams.productValue,
        weightKg: newParams.weight,
        volumeM3: newParams.volume || 1,
        currency: newParams.currency,
        originCountry: newParams.originCountry,
        destinationCountry: newParams.destinationCountry,
        transportMode: newParams.transportMode,
        insuranceLevel: 'STANDARD' as const,
        compareWithOthers: false,
      });
      const data = response.data;

      const calculationResult = transformResult(data, selectedIncoterm.code);
      calculationResult.params = newParams;
      calculationResult.isWhatIf = true;

      setResult(calculationResult);
      toast.success('Scénario What-if calculé !');
    } catch (error) {
      console.error('Erreur lors du calcul What-if:', error);
      toast.error('Erreur lors du calcul du scénario');
    }
  };

  // Auto-calculate when formData changes (Debounced)
  useEffect(() => {
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);

    debounceTimerRef.current = setTimeout(() => {
      if (selectedIncoterm) {
        handleCalculate();
      }
    }, 500);

    return () => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
    };
  }, [formData, selectedIncoterm]);

  // Fetch real incoterms
  const { data: incoterms = [], isLoading: loadingIncoterms } = useQuery({
    queryKey: ['incoterms'],
    queryFn: async () => {
      const response = await incokalkAPI.incoterms.getAll();
      return response.data as Incoterm[];
    },
  });

  // Calculate mutation
  const calculateMutation = useMutation({
    mutationFn: async (data: ReturnType<typeof buildRequest>) => {
      const response = await incokalkAPI.simulation.calculate(data);
      return response.data;
    },
    onSuccess: (data) => {
      if (!selectedIncoterm) return;
      const calculationResult = transformResult(data, selectedIncoterm.code);
      calculationResult.params = {
        incotermId: selectedIncoterm.id as unknown as number,
        incotermCode: selectedIncoterm.code,
        productValue: formData.productValue,
        currency: formData.currency,
        originCountry: formData.originCountry,
        destinationCountry: formData.destinationCountry,
        weight: formData.weight,
        transportMode: formData.transportMode,
      };
      setResult(calculationResult);
      toast.success('Calcul effectué avec succès !');
    },
    onError: (error: AxiosError<{ message?: string; details?: unknown }>) => {
      console.error('Erreur de calcul:', error);
      toast.error(error.response?.data?.message || 'Erreur lors du calcul');
    },
  });

  // Pre-select incoterm and transport mode if coming from simulation page
  useEffect(() => {
    if (preselectedIncotermId && incoterms.length > 0) {
      const incoterm = incoterms.find((i) => i.id === preselectedIncotermId);
      if (incoterm) {
        setSelectedIncoterm(incoterm);
        if (preselectedMode) {
          setFormData(prev => ({ ...prev, transportMode: preselectedMode }));
        }
      }
    }
  }, [preselectedIncotermId, incoterms, preselectedMode]);

  const handleCalculate = () => {
    if (!selectedIncoterm) {
      toast.error('Veuillez sélectionner un Incoterm');
      return;
    }
    calculateMutation.mutate(buildRequest());
  };

  const handleSave = () => {
    if (!result || !user) {
      toast.error('Résultat manquant ou utilisateur non connecté');
      return;
    }

    // Note: The simulation is already saved by the backend during the calculation call.
    // We just redirect to the dashboard to see it in the history.
    toast.success('Simulation enregistrée !');
    navigate('/dashboard');
  };

  const fetchTruckingRates = async () => {
    setLoadingTrucking(true);
    try {
      const response = await incokalkAPI.logistics.calculateTrucking({
        originCountry: formData.originCountry,
        destinationCountry: formData.destinationCountry,
        weightKg: formData.weight,
        volumeM3: result?.logistics?.totalVolumeM3 || 1,
        palletCount: result?.logistics?.totalBoxes || undefined,
      });
      setTruckingRates(response.data);
    } catch {
      toast.error('Erreur lors du calcul des tarifs transport');
    } finally {
      setLoadingTrucking(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg">
      {/* Header */}
      <div className="bg-surface border-b border-line px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <button onClick={() => navigate('/simulation')} className="flex items-center space-x-2 text-ink-soft hover:text-ink transition-colors">
              <ArrowLeft size={18} />
              <span className="text-sm">Retour</span>
            </button>
            <div className="h-6 w-px bg-line" />
            <h1 className="text-xl font-bold text-ink flex items-center space-x-2">
              <CalculatorIcon className="h-5 w-5 text-accent" />
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              <span>Calculateur Incoterms</span>
            </h1>
          </div>
          {result && user && (
            <div className="flex items-center gap-2">
              <button onClick={pinResult} className="text-sm bg-accent-soft text-accent-strong px-3 py-1.5 rounded-none hover:bg-accent-soft transition-colors font-medium">Épingler</button>
              <button onClick={() => handleBranch(result.params!)} className="text-sm bg-warning/10 text-warning px-3 py-1.5 rounded-none hover:bg-warning/20 transition-colors font-medium flex items-center gap-1"><Layers size={13} /> Branch</button>
              <button onClick={handleSave} className="text-sm bg-success text-white px-3 py-1.5 rounded-none hover:bg-success transition-colors font-medium">Historique</button>
            </div>
          )}
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid lg:grid-cols-5 gap-6">
          {/* ── Colonne formulaire (2/5) ── */}
          <div className="lg:col-span-2 space-y-4">
            {/* Incoterm */}
            <div className="bg-surface rounded-none border border-line p-5">
              <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-3">Incoterm</h2>
              {loadingIncoterms ? (
                <div className="flex items-center gap-2 text-ink-soft"><Loader2 className="h-4 w-4 animate-spin" /><span className="text-sm">Chargement...</span></div>
              ) : (
                <select
                  value={selectedIncoterm?.id || ''}
                  onChange={(e) => { const incoterm = incoterms.find(i => i.id === e.target.value); setSelectedIncoterm(incoterm || null); }}
                  className="w-full px-3 py-2.5 border border-line rounded-none text-sm focus:ring-2 focus:ring-accent focus:border-transparent bg-bg"
                >
                  <option value="">Sélectionner un Incoterm</option>
                  {incoterms.map((incoterm) => (
                    <option key={incoterm.id} value={incoterm.id}>{incoterm.code} — {incoterm.fullName}</option>
                  ))}
                </select>
              )}
            </div>

            {/* Paramètres marchandise */}
            <div className="bg-surface rounded-none border border-line p-5">
              <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-3">Marchandise</h2>
              <div className="space-y-4">
                <div>
                  <div className="flex justify-between items-center mb-1.5">
                    <label className="text-sm font-medium text-ink-soft">Valeur (€)</label>
                    <input type="number" value={formData.productValue} onChange={(e) => setFormData({ ...formData, productValue: Number(e.target.value) })} className="w-28 px-2 py-1 border border-line rounded text-right text-sm bg-bg focus:ring-2 focus:ring-accent" min="0" step="100" />
                  </div>
                  <input type="range" value={formData.productValue} onChange={(e) => setFormData({ ...formData, productValue: Number(e.target.value) })} className="w-full h-1.5 bg-line rounded-none appearance-none cursor-pointer accent-accent" min="0" max="1000000" step="100" />
                </div>
                <div>
                  <div className="flex justify-between items-center mb-1.5">
                    <label className="text-sm font-medium text-ink-soft">Poids (kg)</label>
                    <input type="number" value={formData.weight} onChange={(e) => setFormData({ ...formData, weight: Number(e.target.value) })} className="w-28 px-2 py-1 border border-line rounded text-right text-sm bg-bg focus:ring-2 focus:ring-accent" min="0" step="1" />
                  </div>
                  <input type="range" value={formData.weight} onChange={(e) => setFormData({ ...formData, weight: Number(e.target.value) })} className="w-full h-1.5 bg-line rounded-none appearance-none cursor-pointer accent-accent" min="0" max="10000" step="1" />
                </div>
              </div>
            </div>

            {/* Transport & Route */}
            <div className="bg-surface rounded-none border border-line p-5">
              <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-3">Transport & Route</h2>
              <div className="space-y-3">
                <select value={formData.transportMode} onChange={(e) => setFormData({ ...formData, transportMode: e.target.value as TransportMode })} className="w-full px-3 py-2.5 border border-line rounded-none text-sm bg-bg focus:ring-2 focus:ring-accent">
                  <option value="SEA">Maritime</option><option value="AIR">Aérien</option><option value="ROAD">Routier</option>
                </select>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-ink-soft mb-1 block">Origine</label>
                    <select value={formData.originCountry} onChange={(e) => setFormData({ ...formData, originCountry: e.target.value })} className="w-full px-3 py-2 border border-line rounded-none text-sm bg-bg focus:ring-2 focus:ring-accent">
                      <option value="CN">Chine</option><option value="US">États-Unis</option><option value="DE">Allemagne</option><option value="JP">Japon</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs text-ink-soft mb-1 block">Destination</label>
                    <select value={formData.destinationCountry} onChange={(e) => setFormData({ ...formData, destinationCountry: e.target.value })} className="w-full px-3 py-2 border border-line rounded-none text-sm bg-bg focus:ring-2 focus:ring-accent">
                      <option value="FR">France</option><option value="US">États-Unis</option><option value="DE">Allemagne</option><option value="GB">Royaume-Uni</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>

            {/* Packaging */}
            <div className="bg-surface rounded-none border border-line p-5">
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider flex items-center gap-2"><Package size={14} /> Packaging</h2>
                {packagingItems.length > 0 && <span className="bg-surface-2 text-ink text-xs px-2 py-0.5 rounded-full font-medium">{packagingItems.length}</span>}
              </div>
              {packagingItems.length > 0 && (
                <div className="space-y-1.5 mb-3">
                  {packagingItems.map((item, idx) => (
                    <div key={idx} className="flex items-center gap-2 text-xs bg-bg rounded-none px-3 py-2 border border-line">
                      <span className="text-ink-soft font-mono w-4">{idx + 1}</span>
                      <span className="font-medium text-ink flex-1 truncate">{item.sku}</span>
                      <span className="text-ink-soft">{item.quantity}×</span>
                      <span className="text-ink-soft">{item.lengthCm}×{item.widthCm}×{item.heightCm}</span>
                      <span className="text-ink-soft">{item.weightKg}kg</span>
                      <button onClick={() => setPackagingItems(packagingItems.filter((_, i) => i !== idx))} className="text-ink-soft hover:text-danger transition-colors"><Trash2 size={12} /></button>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex gap-1.5 items-end">
                <input placeholder="SKU" value={newItem.sku} onChange={(e) => setNewItem({ ...newItem, sku: e.target.value })} className="flex-1 min-w-0 px-2 py-1.5 border border-line rounded text-xs bg-bg focus:ring-2 focus:ring-accent" />
                <input type="number" placeholder="L" value={newItem.lengthCm} onChange={(e) => setNewItem({ ...newItem, lengthCm: Number(e.target.value) })} className="w-14 px-1.5 py-1.5 border border-line rounded text-xs bg-bg text-center" />
                <input type="number" placeholder="l" value={newItem.widthCm} onChange={(e) => setNewItem({ ...newItem, widthCm: Number(e.target.value) })} className="w-14 px-1.5 py-1.5 border border-line rounded text-xs bg-bg text-center" />
                <input type="number" placeholder="H" value={newItem.heightCm} onChange={(e) => setNewItem({ ...newItem, heightCm: Number(e.target.value) })} className="w-14 px-1.5 py-1.5 border border-line rounded text-xs bg-bg text-center" />
                <input type="number" placeholder="kg" value={newItem.weightKg} onChange={(e) => setNewItem({ ...newItem, weightKg: Number(e.target.value) })} className="w-14 px-1.5 py-1.5 border border-line rounded text-xs bg-bg text-center" />
                <input type="number" placeholder="Qté" value={newItem.quantity} onChange={(e) => setNewItem({ ...newItem, quantity: Math.max(1, Number(e.target.value)) })} className="w-12 px-1.5 py-1.5 border border-line rounded text-xs bg-bg text-center" />
                <button onClick={() => { setPackagingItems([...packagingItems, newItem]); setNewItem({ sku: '', lengthCm: 30, widthCm: 20, heightCm: 15, weightKg: 2, quantity: 1 }); }} className="bg-accent text-white p-1.5 rounded hover:bg-accent-strong flex-shrink-0 transition-colors"><Plus size={14} /></button>
              </div>
              <p className="text-[10px] text-ink-soft mt-2">L × l × H en cm, poids en kg</p>
            </div>

            {/* Actions */}
            <div className="flex gap-2">
              <button onClick={handleCalculate} disabled={calculateMutation.isPending || !selectedIncoterm} className="flex-1 bg-accent text-white py-3 rounded-none font-semibold hover:bg-accent-strong disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 text-sm transition-colors shadow-sm">
                {calculateMutation.isPending ? <><Loader2 className="h-4 w-4 animate-spin" /> Calcul...</> : <><CalculatorIcon className="h-4 w-4" /> Calculer</>}
              </button>
              <button onClick={compareAllModes} disabled={isComparing || !selectedIncoterm || calculateMutation.isPending} className="flex-1 bg-surface-2 text-ink-soft py-3 rounded-none font-semibold hover:bg-line disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 text-sm transition-colors">
                {isComparing ? <><Loader2 className="h-4 w-4 animate-spin" /> Comparaison...</> : <><Layers className="h-4 w-4" /> Comparer 3 modes</>}
              </button>
            </div>
          </div>

          {/* ── Colonne résultats (3/5) ── */}
          <div className="lg:col-span-3 space-y-4">
            {/* What-if modal */}
            {isBranching && branchingParams && (
              <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 p-4">
                <div className="bg-surface rounded-none shadow-2xl w-full max-w-md overflow-hidden">
                  <div className="flex justify-between items-center p-4 border-b bg-bg">
                    <h3 className="text-lg font-bold text-ink flex items-center space-x-2"><Layers className="text-warning" size={20} /><span>Scénario What-if</span></h3>
                    <button onClick={() => setIsBranching(false)} className="text-ink-soft hover:text-ink"><X size={20} /></button>
                  </div>
                  <div className="p-6"><WhatIfForm initialParams={branchingParams} onRun={handleRunWhatIf} onCancel={() => setIsBranching(false)} /></div>
                </div>
              </div>
            )}

            {/* Comparaison épinglée */}
            {pinnedResults.length > 0 && (
              <div className="bg-surface rounded-none border border-line p-5">
                <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4 flex items-center gap-2">Comparaison ({pinnedResults.length})</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
                  {(() => {
                    const minCost = Math.min(...pinnedResults.map(r => r.totalBuyerCost));
                    return pinnedResults.map((pinned, index) => (
                      <div key={index} className={`relative rounded-none p-4 border-2 ${pinned.totalBuyerCost === minCost ? 'border-success bg-success/10' : 'border-line bg-bg'}`}>
                        {pinned.totalBuyerCost === minCost && <span className="absolute -top-2 -right-2 bg-success text-white text-[9px] font-bold px-2 py-0.5 rounded-full shadow">MEILLEUR PRIX</span>}
                        <button onClick={() => handleBranch(pinned.params!)} className="absolute top-2 left-2 text-warning hover:text-warning" title="Brancher"><Layers size={13} /></button>
                        <button onClick={() => unpinResult(index)} className="absolute top-2 right-2 text-ink-soft hover:text-danger" title="Retirer"><span className="text-xs font-bold">✕</span></button>
                        <div className="text-[10px] font-bold text-accent uppercase tracking-widest mb-2">{pinned.incoterm}{pinned.isWhatIf && <span className="ml-1 bg-warning/10 text-warning px-1 rounded">WHAT-IF</span>}</div>
                        <div className="text-2xl font-bold text-ink mb-3">{formatEur(pinned.totalBuyerCost, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                        <CostChart data={[
                          { name: 'Douanes Export', value: pinned.buyerCosts.exportCustoms },
                          { name: 'Manutention Origine', value: pinned.buyerCosts.originHandling },
                          { name: 'Doc. Origine', value: pinned.buyerCosts.originDocumentation },
                          { name: 'Fret', value: pinned.buyerCosts.freight },
                          { name: 'Assurance', value: pinned.buyerCosts.insurance },
                          { name: 'Manutention Dest.', value: pinned.buyerCosts.destinationHandling },
                          { name: 'Doc. Destination', value: pinned.buyerCosts.destinationDocumentation },
                          { name: 'Douanes Import', value: pinned.buyerCosts.importDuties },
                          { name: 'TVA Import', value: pinned.buyerCosts.importVat },
                          { name: 'Dernier km', value: pinned.buyerCosts.lastMileDelivery },
                        ]} />
                      </div>
                    ));
                  })()}
                </div>
              </div>
            )}

            {/* Résultat principal */}
            {result ? (
              <div className="space-y-4">
                {/* Incoterm + Coût total */}
                <div className="relative bg-surface rounded-none border border-line p-5">
                  <span className="hud-corner hud-corner-tl" aria-hidden="true" />
                  <span className="hud-corner hud-corner-tr" aria-hidden="true" />
                  <span className="hud-corner hud-corner-bl" aria-hidden="true" />
                  <span className="hud-corner hud-corner-br" aria-hidden="true" />
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="text-[10px] font-bold text-accent uppercase tracking-widest">{result.incoterm}</div>
                      <div className="text-sm text-ink-soft mt-0.5">{result.incotermFullName}</div>
                    </div>
                    <div className="text-right">
                      <div className="text-3xl font-bold text-ink">{formatEur(result.totalBuyerCost, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                      <div className="text-xs text-ink-soft">Coût total acheteur</div>
                    </div>
                  </div>
                  <div className="flex gap-4 mt-3 pt-3 border-t border-line text-xs text-ink-soft">
                    <span>Poids: {formData.weight} kg</span>
                    <span>Valeur: {formatEur(formData.productValue)}</span>
                    <span>Mode: {formData.transportMode === 'SEA' ? 'Maritime' : formData.transportMode === 'AIR' ? 'Aérien' : 'Routier'}</span>
                  </div>
                </div>

                {/* Alertes conformité */}
                {result.complianceAlerts && result.complianceAlerts.length > 0 && (() => {
                  const criticals = result.complianceAlerts.filter(a => a.severity === 'CRITICAL');
                  const warnings = result.complianceAlerts.filter(a => a.severity === 'WARNING');
                  const infos = result.complianceAlerts.filter(a => a.severity === 'INFO');
                  const hasCritical = criticals.length > 0;

                  return (
                    <div className={`rounded-none shadow-sm border p-5 ${hasCritical ? 'bg-danger/10 border-danger/40' : 'bg-surface border-line'}`}>
                      <div className="flex items-center justify-between mb-3">
                        <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider">Alertes conformité</h3>
                        <div className="flex gap-1.5">
                          {criticals.length > 0 && <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold bg-danger/10 text-danger"><AlertCircle className="h-3 w-3" />{criticals.length}</span>}
                          {warnings.length > 0 && <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold bg-warning/10 text-warning"><AlertTriangle className="h-3 w-3" />{warnings.length}</span>}
                          {infos.length > 0 && <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold bg-surface-2 text-ink"><Info className="h-3 w-3" />{infos.length}</span>}
                        </div>
                      </div>
                      <div className="space-y-2">
                        {[...criticals, ...warnings, ...infos].map((alert, index) => {
                          const severityStyles: Record<string, string> = { CRITICAL: 'bg-danger/10 border-danger/40 text-danger', WARNING: 'bg-warning/10 border-warning/40 text-warning', INFO: 'bg-surface-2 border-line text-ink' };
                          const severityIconMap: Record<string, typeof AlertCircle> = { CRITICAL: AlertCircle, WARNING: AlertTriangle, INFO: Info };
                          const categoryLabels: Record<string, string> = { INCOTERM: 'Incoterm', COUNTRY: 'Pays', HS_CODE: 'Code SH', TRANSPORT: 'Transport' };
                          const Icon = severityIconMap[alert.severity] || Info;
                          return (
                            <div key={index} className={`flex items-start gap-2.5 p-3 border rounded-none text-sm ${severityStyles[alert.severity] || severityStyles.INFO} ${alert.severity === 'CRITICAL' ? 'ring-1 ring-danger' : ''}`}>
                              <Icon className="h-4 w-4 mt-0.5 flex-shrink-0" />
                              <div className="flex-1">
                                <div className="flex items-center gap-2 mb-0.5">
                                  <span className="font-bold text-xs">{categoryLabels[alert.category] || alert.category}</span>
                                  <span className={`text-[10px] font-semibold px-1.5 py-0 rounded ${alert.severity === 'CRITICAL' ? 'bg-danger/10 text-danger' : alert.severity === 'WARNING' ? 'bg-warning/10 text-warning' : 'bg-surface-2 text-ink-soft'}`}>{alert.severity}</span>
                                </div>
                                <div className="text-xs opacity-90 leading-relaxed">{alert.message}</div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })()}

                {/* Répartition des frais */}
                <div className="bg-surface rounded-none border border-line p-5">
                  <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider mb-4">Répartition des frais acheteur</h3>
                  <CostChart data={[
                    { name: 'Douanes Export', value: result.buyerCosts.exportCustoms },
                    { name: 'Manutention Origine', value: result.buyerCosts.originHandling },
                    { name: 'Doc. Origine', value: result.buyerCosts.originDocumentation },
                    { name: 'Fret', value: result.buyerCosts.freight },
                    { name: 'Assurance', value: result.buyerCosts.insurance },
                    { name: 'Manutention Dest.', value: result.buyerCosts.destinationHandling },
                    { name: 'Doc. Destination', value: result.buyerCosts.destinationDocumentation },
                    { name: 'Douanes Import', value: result.buyerCosts.importDuties },
                    { name: 'TVA Import', value: result.buyerCosts.importVat },
                    { name: 'Dernier km', value: result.buyerCosts.lastMileDelivery },
                  ]} />
                  <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 mt-4 pt-3 border-t border-line">
                    {[
                      ['Douanes export', result.buyerCosts.exportCustoms],
                      ['Manutention origine', result.buyerCosts.originHandling],
                      ['Documentation origine', result.buyerCosts.originDocumentation],
                      ['Fret principal', result.buyerCosts.freight],
                      ['Assurance', result.buyerCosts.insurance],
                      ['Manutention destination', result.buyerCosts.destinationHandling],
                      ['Documentation destination', result.buyerCosts.destinationDocumentation],
                      ['Droits de douane import', result.buyerCosts.importDuties],
                      ['TVA import', result.buyerCosts.importVat],
                      ['Dernier kilomètre', result.buyerCosts.lastMileDelivery],
                    ].map(([label, value]) => (
                      <div key={label as string} className="flex justify-between text-xs py-1">
                        <span className="text-ink-soft">{label}</span>
                        <span className="font-medium text-ink">{(value as number).toFixed(2)} €</span>
                      </div>
                    ))}
                  </div>
                  <div className="flex justify-between items-center pt-3 mt-3 border-t border-line">
                    <span className="text-sm font-bold text-ink">Coût total</span>
                    <span className="text-xl font-bold text-accent">{result.totalBuyerCost.toFixed(2)} €</span>
                  </div>
                </div>

                {/* Logistique */}
                {result.logistics && (
                  <div className="bg-surface rounded-none border border-line p-5">
                    <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider mb-3 flex items-center gap-2"><Package size={14} /> Logistique</h3>
                    <div className="grid grid-cols-4 gap-3">
                      <div className="bg-surface-2 rounded-none p-3 text-center">
                        <div className="text-2xl font-bold text-ink">{result.logistics.totalBoxes}</div>
                        <div className="text-[10px] text-ink-soft uppercase">Colis</div>
                      </div>
                      <div className="bg-success/10 rounded-none p-3 text-center">
                        <div className="text-2xl font-bold text-success">{result.logistics.totalWeightKg}</div>
                        <div className="text-[10px] text-ink-soft uppercase">Kg</div>
                      </div>
                      <div className="bg-accent-soft rounded-none p-3 text-center">
                        <div className="text-2xl font-bold text-accent-strong">{result.logistics.totalVolumeM3}</div>
                        <div className="text-[10px] text-ink-soft uppercase">m³</div>
                      </div>
                      <div className="bg-warning/10 rounded-none p-3 text-center">
                        <div className="text-2xl font-bold text-warning">{result.logistics.utilizationPercent}%</div>
                        <div className="text-[10px] text-ink-soft uppercase">Rempli</div>
                      </div>
                    </div>
                    <div className="mt-3 p-2.5 bg-success/10 rounded-none border border-success/15">
                      <div className="text-xs font-bold text-success">Mode recommandé : {result.logistics.recommendedMode}</div>
                      {result.logistics.modeReason && <div className="text-[11px] text-success mt-0.5">{result.logistics.modeReason}</div>}
                    </div>
                    {!truckingRates ? (
                      <button onClick={fetchTruckingRates} disabled={loadingTrucking} className="mt-3 w-full bg-success text-white py-2.5 rounded-none text-sm font-semibold hover:bg-success disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                        {loadingTrucking ? <><Loader2 className="h-4 w-4 animate-spin" /> Calcul...</> : 'Voir les tarifs LTL / FTL / Express'}
                      </button>
                    ) : (
                      <div className="mt-3 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-ink-soft uppercase tracking-wider">Tarifs transport</span>
                          <button onClick={() => setTruckingRates(null)} className="text-[10px] text-ink-soft hover:text-ink">Masquer</button>
                        </div>
                        {truckingRates.options.map((opt: TruckingOption) => (
                          <div key={opt.mode} className={`flex items-center justify-between p-3 rounded-none text-sm border ${opt.recommended ? 'bg-success/10 border-success/40' : 'bg-bg border-line'}`}>
                            <div className="flex-1">
                              <div className="flex items-center gap-2">
                                <span className="font-bold text-ink">{opt.label}</span>
                                {opt.recommended && <span className="bg-success text-white text-[9px] font-bold px-2 py-0.5 rounded-full">RECOMMANDÉ</span>}
                              </div>
                              <p className="text-[11px] text-ink-soft mt-0.5">{opt.description}</p>
                              <div className="flex gap-3 mt-1 text-[11px] text-ink-soft">
                                <span>⏱ {opt.transitDays}j</span><span>💰 {opt.costPerPallet} €/pal</span><span>🌱 {opt.co2Kg} kg CO₂</span>
                              </div>
                            </div>
                            <div className="text-right ml-4">
                              <div className="text-lg font-bold text-success">{formatEur(opt.costEur)}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ) : (
              <div className="bg-surface rounded-none shadow-sm border border-line p-12 text-center">
                <CalculatorIcon className="h-10 w-10 mx-auto mb-3 text-ink-soft" />
                <p className="text-sm text-ink-soft">Sélectionnez un Incoterm et cliquez sur « Calculer »</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Calculator;
