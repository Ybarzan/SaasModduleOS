import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Calculator, FileText, Globe, Shield, ArrowRightLeft, Check, Info, TrendingDown, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { ORIGIN_COUNTRIES, DEST_COUNTRIES } from '../lib/countries';
import { formatEur } from '../lib/formatNumber';

interface TariffInfo {
  hsCode: string;
  origin: string;
  destination: string;
  mfnRate: number;
  appliedRate: number;
  isPrefential: boolean;
  agreement: string;
  savings: number;
  notes: string;
  availableAgreements: { code: string; name: string; type: string }[];
}

interface DutyResult {
  dutyAmount: number;
  dutyRate: number;
  dutyType: string;
  isPrefential: boolean;
  agreementCode: string;
  agreementName: string;
  mfnRate: number;
  savings: number;
  notes: string;
}

interface VatResult {
  vatAmount: number;
  vatRate: number;
  vatType: string;
  regime: string;
  reverseCharge: boolean;
  isExempt: boolean;
  notes: string;
}

interface VatRates {
  [country: string]: number;
}


const INCOTERMS = ['EXW', 'FOB', 'CIF', 'DAP', 'DDP'];

const EU_COUNTRY_NAMES: Record<string, string> = {
  FR: 'France', DE: 'Allemagne', IT: 'Italie', ES: 'Espagne', NL: 'Pays-Bas',
  BE: 'Belgique', PT: 'Portugal', PL: 'Pologne', AT: 'Autriche', IE: 'Irlande',
  FI: 'Finlande', SE: 'Suède', DK: 'Danemark', GR: 'Grèce', CZ: 'Tchéquie',
  RO: 'Roumanie', HU: 'Hongrie', BG: 'Bulgarie', HR: 'Croatie', SI: 'Slovénie',
  EE: 'Estonie', LV: 'Lettre', LT: 'Lituanie', CY: 'Chypre', MT: 'Malte', LU: 'Luxembourg',
};

const CustomsDashboard = () => {
  const [activeTab, setActiveTab] = useState<'duty' | 'vat' | 'rates'>('duty');

  const [dutyForm, setDutyForm] = useState({
    hsCode: '',
    origin: 'CN',
    dest: 'FR',
    goodsValue: 10000,
    freight: 2000,
    insurance: 100,
  });

  const [vatForm, setVatForm] = useState({
    origin: 'CN',
    dest: 'FR',
    goodsValue: 10000,
    freight: 2000,
    insurance: 100,
    incoterm: 'FOB',
    b2b: true,
  });

  const [rateSort, setRateSort] = useState<'asc' | 'desc'>('asc');

  const dutyMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.customs.getDuty(
        dutyForm.hsCode,
        dutyForm.origin,
        dutyForm.dest,
        dutyForm.goodsValue,
        dutyForm.freight,
        dutyForm.insurance
      ),
    onError: () => {
      toast.error('Erreur lors du calcul des droits');
    },
  });

  const tariffMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.customs.getTariffInfo(dutyForm.hsCode, dutyForm.origin, dutyForm.dest),
    onError: () => {
      toast.error('Erreur lors de la récupération des informations tarifaires');
    },
  });

  const vatMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.customs.getVat(
        vatForm.origin,
        vatForm.dest,
        vatForm.goodsValue,
        vatForm.freight,
        vatForm.insurance,
        vatForm.incoterm,
        vatForm.b2b
      ),
    onError: () => {
      toast.error('Erreur lors du calcul de la TVA');
    },
  });

  const { data: vatRatesData, isLoading: vatRatesLoading } = useQuery({
    queryKey: ['customs-vat-rates'],
    queryFn: async () => {
      const res = await incokalkAPI.customs.getVatRates();
      return res.data as VatRates;
    },
    enabled: activeTab === 'rates',
  });

  const { data: agreementsData } = useQuery({
    queryKey: ['trade-agreements-all'],
    queryFn: async () => {
      const res = await incokalkAPI.tradeAgreements.list();
      return res.data as Array<{ partnerCountry: string; code: string; name: string }>;
    },
  });

  const agreementsByCountry = new Map<string, string>();
  if (Array.isArray(agreementsData)) {
    for (const a of agreementsData) {
      if (!agreementsByCountry.has(a.partnerCountry)) {
        agreementsByCountry.set(a.partnerCountry, a.code);
      }
    }
  }

  const handleDutyCalculate = () => {
    if (!dutyForm.hsCode) {
      toast.error('Veuillez saisir un code SH');
      return;
    }
    dutyMutation.mutate();
    tariffMutation.mutate();
  };

  const handleVatCalculate = () => {
    vatMutation.mutate();
  };

  const dutyResult = dutyMutation.data?.data as DutyResult | undefined;
  const tariffResult = tariffMutation.data?.data as TariffInfo | undefined;
  const vatResult = vatMutation.data?.data as VatResult | undefined;

  const sortedVatRates = vatRatesData
    ? Object.entries(vatRatesData)
        .filter(([code]) => EU_COUNTRY_NAMES[code])
        .sort((a, b) => (rateSort === 'asc' ? a[1] - b[1] : b[1] - a[1]))
    : [];

  const tabs = [
    { id: 'duty' as const, label: 'Simulateur de droits', icon: Calculator },
    { id: 'vat' as const, label: 'Simulateur TVA', icon: FileText },
    { id: 'rates' as const, label: 'Taux TVA par pays', icon: Globe },
  ];

  const inputClass = 'w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm';
  const selectClass = 'w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm bg-surface';

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Douanes & Réglementation</h1>
          <p className="text-ink-soft mt-1">Calcul de droits, TVA et accords commerciaux</p>
        </div>
        <div className="flex items-center gap-2 bg-accent-soft text-accent-strong px-3 py-1.5 rounded-lg">
          <Shield size={16} />
          <span className="text-sm font-medium">Données TARIC 2026</span>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-2 p-1 rounded-lg mb-8">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition-colors flex-1 justify-center ${
              activeTab === id
                ? 'bg-surface text-accent shadow-sm'
                : 'text-ink-soft hover:text-ink'
            }`}
          >
            <Icon size={16} />
            <span className="hidden sm:inline">{label}</span>
          </button>
        ))}
      </div>

      {/* Section 1: Simulateur de droits */}
      {activeTab === 'duty' && (
        <div className="grid lg:grid-cols-2 gap-6">
          <div className="bg-surface rounded-xl border border-line p-6 space-y-5">
            <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">Paramètres</h2>

            <div>
              <label className="block text-sm font-medium text-ink mb-1">Code SH</label>
              <input
                type="text"
                value={dutyForm.hsCode}
                onChange={(e) => setDutyForm({ ...dutyForm, hsCode: e.target.value })}
                className={inputClass}
                placeholder="Ex: 8471, 9401..."
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays d'origine</label>
                <select
                  value={dutyForm.origin}
                  onChange={(e) => setDutyForm({ ...dutyForm, origin: e.target.value })}
                  className={selectClass}
                >
                  {ORIGIN_COUNTRIES.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.name}{agreementsByCountry.has(c.code) ? ` (${agreementsByCountry.get(c.code)})` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays de destination</label>
                <select
                  value={dutyForm.dest}
                  onChange={(e) => setDutyForm({ ...dutyForm, dest: e.target.value })}
                  className={selectClass}
                >
                  {DEST_COUNTRIES.map((c) => (
                    <option key={c.code} value={c.code}>{c.name}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Valeur marchandise (€)</label>
                <input
                  type="number"
                  value={dutyForm.goodsValue}
                  onChange={(e) => setDutyForm({ ...dutyForm, goodsValue: Number(e.target.value) })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Fret (€)</label>
                <input
                  type="number"
                  value={dutyForm.freight}
                  onChange={(e) => setDutyForm({ ...dutyForm, freight: Number(e.target.value) })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Assurance (€)</label>
                <input
                  type="number"
                  value={dutyForm.insurance}
                  onChange={(e) => setDutyForm({ ...dutyForm, insurance: Number(e.target.value) })}
                  className={inputClass}
                />
              </div>
            </div>

            <button
              onClick={handleDutyCalculate}
              disabled={dutyMutation.isPending || tariffMutation.isPending}
              className="w-full bg-accent text-white px-4 py-2.5 rounded-lg font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
            >
              {(dutyMutation.isPending || tariffMutation.isPending) && <Loader2 size={16} className="animate-spin" />}
              <Calculator size={16} />
              Calculer
            </button>
          </div>

          <div className="space-y-4">
            {dutyResult ? (
              <>
                <div className="bg-surface rounded-xl border border-line p-6">
                  <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Résultat</h2>
                  <div className="grid grid-cols-2 gap-4 mb-4">
                    <div className="bg-accent-soft rounded-xl p-4 text-center">
                      <div className="text-3xl font-bold text-accent-strong">
                        {dutyResult.dutyRate.toFixed(1)}%
                      </div>
                      <div className="text-xs text-ink-soft">Taux appliqué</div>
                    </div>
                    <div className="bg-success/10 rounded-xl p-4 text-center">
                      <div className="text-3xl font-bold text-success">
                        {formatEur(dutyResult.dutyAmount)}
                      </div>
                      <div className="text-xs text-ink-soft">Montant des droits</div>
                    </div>
                  </div>

                  {dutyResult.isPrefential && dutyResult.savings > 0 && (
                    <div className="mb-4 bg-success/10 border border-success/40 rounded-lg px-4 py-3 flex items-center gap-3">
                      <div className="flex items-center gap-1.5">
                        <Check size={16} className="text-success" />
                        <span className="text-sm font-semibold text-success">Préférentiel</span>
                      </div>
                      <span className="text-sm text-success">
                        — Économie de {formatEur(dutyResult.savings)} vs MFN
                      </span>
                    </div>
                  )}

                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between py-1">
                      <span className="text-ink-soft">Type</span>
                      <span className="font-medium">
                        {dutyResult.isPrefential ? (
                          <span className="text-success">Préférentiel</span>
                        ) : (
                          <span className="text-ink">MFN</span>
                        )}
                      </span>
                    </div>
                    <div className="flex justify-between py-1">
                      <span className="text-ink-soft">Taux MFN</span>
                      <span className="font-medium">{dutyResult.mfnRate.toFixed(1)}%</span>
                    </div>
                    {dutyResult.agreementCode && (
                      <div className="flex justify-between py-1">
                        <span className="text-ink-soft">Accord applicable</span>
                        <span className="font-medium">{dutyResult.agreementName || dutyResult.agreementCode}</span>
                      </div>
                    )}
                  </div>
                </div>

                {tariffResult?.availableAgreements && tariffResult.availableAgreements.length > 0 && (
                  <div className="bg-surface rounded-xl border border-line p-6">
                    <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-3">Accords disponibles</h2>
                    <div className="space-y-2">
                      {tariffResult.availableAgreements.map((agreement) => (
                        <div key={agreement.code} className="flex items-center gap-2 text-sm text-ink">
                          <ArrowRightLeft size={14} className="text-accent" />
                          <span className="font-medium">{agreement.name}</span>
                          <span className="text-xs text-ink-soft">({agreement.code})</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {dutyResult.notes && (
                  <div className="bg-bg border border-line rounded-xl p-4 flex items-start gap-3">
                    <Info size={18} className="text-ink-soft mt-0.5 flex-shrink-0" />
                    <p className="text-sm text-ink-soft">{dutyResult.notes}</p>
                  </div>
                )}
              </>
            ) : (
              <div className="bg-surface rounded-xl border border-line p-12 text-center">
                <Calculator size={32} className="mx-auto mb-3 text-ink-soft" />
                <p className="text-sm text-ink-soft">Remplissez le formulaire pour calculer les droits de douane</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Section 2: Simulateur TVA */}
      {activeTab === 'vat' && (
        <div className="grid lg:grid-cols-2 gap-6">
          <div className="bg-surface rounded-xl border border-line p-6 space-y-5">
            <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">Paramètres TVA</h2>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays origine</label>
                <select
                  value={vatForm.origin}
                  onChange={(e) => setVatForm({ ...vatForm, origin: e.target.value })}
                  className={selectClass}
                >
                  {ORIGIN_COUNTRIES.map((c) => (
                    <option key={c.code} value={c.code}>{c.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays destination</label>
                <select
                  value={vatForm.dest}
                  onChange={(e) => setVatForm({ ...vatForm, dest: e.target.value })}
                  className={selectClass}
                >
                  {DEST_COUNTRIES.map((c) => (
                    <option key={c.code} value={c.code}>{c.name}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Valeur marchandise (€)</label>
                <input
                  type="number"
                  value={vatForm.goodsValue}
                  onChange={(e) => setVatForm({ ...vatForm, goodsValue: Number(e.target.value) })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Fret (€)</label>
                <input
                  type="number"
                  value={vatForm.freight}
                  onChange={(e) => setVatForm({ ...vatForm, freight: Number(e.target.value) })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Assurance (€)</label>
                <input
                  type="number"
                  value={vatForm.insurance}
                  onChange={(e) => setVatForm({ ...vatForm, insurance: Number(e.target.value) })}
                  className={inputClass}
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-ink mb-1">Incoterm</label>
              <select
                value={vatForm.incoterm}
                onChange={(e) => setVatForm({ ...vatForm, incoterm: e.target.value })}
                className={selectClass}
              >
                {INCOTERMS.map((inc) => (
                  <option key={inc} value={inc}>{inc}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-ink mb-2">Type</label>
              <div className="flex gap-4">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="vatType"
                    checked={vatForm.b2b === true}
                    onChange={() => setVatForm({ ...vatForm, b2b: true })}
                    className="text-accent focus:ring-accent"
                  />
                  <span className="text-sm text-ink">B2B</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="vatType"
                    checked={vatForm.b2b === false}
                    onChange={() => setVatForm({ ...vatForm, b2b: false })}
                    className="text-accent focus:ring-accent"
                  />
                  <span className="text-sm text-ink">B2C</span>
                </label>
              </div>
            </div>

            <button
              onClick={handleVatCalculate}
              disabled={vatMutation.isPending}
              className="w-full bg-accent text-white px-4 py-2.5 rounded-lg font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
            >
              {vatMutation.isPending && <Loader2 size={16} className="animate-spin" />}
              <Calculator size={16} />
              Calculer la TVA
            </button>
          </div>

          <div className="space-y-4">
            {vatResult ? (
              <>
                <div className="bg-surface rounded-xl border border-line p-6">
                  <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Résultat TVA</h2>
                  <div className="grid grid-cols-2 gap-4 mb-4">
                    <div className="bg-accent-soft rounded-xl p-4 text-center">
                      <div className="text-3xl font-bold text-accent-strong">
                        {formatEur(vatResult.vatAmount)}
                      </div>
                      <div className="text-xs text-ink-soft">Montant TVA</div>
                    </div>
                    <div className="bg-success/10 rounded-xl p-4 text-center">
                      <div className="text-3xl font-bold text-success">
                        {vatResult.vatRate.toFixed(1)}%
                      </div>
                      <div className="text-xs text-ink-soft">Taux appliqué</div>
                    </div>
                  </div>

                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between py-1">
                      <span className="text-ink-soft">Type</span>
                      <span className="font-medium">{vatResult.vatType}</span>
                    </div>
                    <div className="flex justify-between py-1">
                      <span className="text-ink-soft">Régime</span>
                      <span className="font-medium">{vatResult.regime}</span>
                    </div>
                    {vatResult.reverseCharge && (
                      <div className="flex items-center gap-2 bg-warning/10 border border-warning/40 rounded-lg px-3 py-2 mt-2">
                        <ArrowRightLeft size={14} className="text-warning" />
                        <span className="text-sm font-medium text-warning">Reverse charge applicable</span>
                      </div>
                    )}
                    {vatResult.isExempt && (
                      <div className="flex items-center gap-2 bg-success/10 border border-success/40 rounded-lg px-3 py-2 mt-2">
                        <Check size={14} className="text-success" />
                        <span className="text-sm font-medium text-success">Exonéré de TVA</span>
                      </div>
                    )}
                  </div>
                </div>

                {vatResult.notes && (
                  <div className="bg-bg border border-line rounded-xl p-4 flex items-start gap-3">
                    <Info size={18} className="text-ink-soft mt-0.5 flex-shrink-0" />
                    <p className="text-sm text-ink-soft">{vatResult.notes}</p>
                  </div>
                )}
              </>
            ) : (
              <div className="bg-surface rounded-xl border border-line p-12 text-center">
                <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
                <p className="text-sm text-ink-soft">Remplissez le formulaire pour calculer la TVA à l'import</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Section 3: Taux de TVA par pays EU */}
      {activeTab === 'rates' && (
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          <div className="px-6 py-4 border-b border-line flex items-center justify-between">
            <h2 className="text-lg font-semibold text-ink">Taux de TVA standard par pays EU</h2>
            <button
              onClick={() => setRateSort(rateSort === 'asc' ? 'desc' : 'asc')}
              className="flex items-center gap-1.5 text-sm text-ink-soft hover:text-ink transition-colors"
            >
              <TrendingDown size={14} className={rateSort === 'desc' ? 'rotate-180' : ''} />
              Trier par taux {rateSort === 'asc' ? '↑' : '↓'}
            </button>
          </div>

          {vatRatesLoading ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
              Chargement des taux...
            </div>
          ) : sortedVatRates.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Globe size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune donnée de taux disponible</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Pays</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Taux standard</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {sortedVatRates.map(([code, rate]) => (
                    <tr key={code} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm font-medium text-ink">{EU_COUNTRY_NAMES[code]}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{code}</td>
                      <td className="px-6 py-4 text-sm font-semibold text-right">
                        <span className={`px-2 py-0.5 rounded-full text-xs ${
                          rate <= 17 ? 'bg-success/10 text-success' :
                          rate <= 21 ? 'bg-accent-soft text-accent-strong' :
                          'bg-warning/10 text-warning'
                        }`}>
                          {rate}%
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default CustomsDashboard;
