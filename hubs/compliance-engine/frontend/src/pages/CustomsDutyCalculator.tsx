import { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Shield, Loader2, AlertCircle, CheckCircle, Search } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { ORIGIN_COUNTRIES, DEST_COUNTRIES } from '../lib/countries';
import toast from 'react-hot-toast';
import { formatEur } from '../lib/formatNumber';

interface SearchResult {
  hsCode: string;
  description: string;
  origin: string;
  dutyRate: number;
  dutyType: string;
  isPrefential: boolean;
  agreementCode: string;
}

interface SearchResponse {
  keyword: string;
  destination: string;
  hsCodesFound: number;
  hsCodes: string[];
  rates: SearchResult[];
}

interface DutyCalculation {
  hsCode: string;
  origin: string;
  destination: string;
  dutyRate: number;
  dutyAmount: number;
  isPrefential: boolean;
  mfnRate: number;
  savings: number;
  agreementCode?: string;
  agreementName?: string;
  cifValue: number;
  notes?: string;
}

const CustomsDutyCalculator = () => {
  const [hsCode, setHsCode] = useState('8471');
  const [customHs, setCustomHs] = useState('');
  const [origin, setOrigin] = useState('CN');
  const [dest, setDest] = useState('FR');
  const [goodsValue, setGoodsValue] = useState(10000);
  const [freight, setFreight] = useState(2000);
  const [insurance, setInsurance] = useState(100);
  const [result, setResult] = useState<DutyCalculation | null>(null);
  const [loading, setLoading] = useState(false);

  const [hsSearch, setHsSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [hsDropdownOpen, setHsDropdownOpen] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(null);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => setDebouncedSearch(hsSearch), 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [hsSearch]);

  const { data: agreementsData } = useQuery({
    queryKey: ['trade-agreements-all'],
    queryFn: async () => {
      const res = await incokalkAPI.tradeAgreements.list();
      return res.data as Array<{ partnerCountry: string; code: string }>;
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

  const { data: searchData, isFetching: searchLoading } = useQuery<SearchResponse>({
    queryKey: ['taric-search', debouncedSearch, dest],
    queryFn: async () => {
      const res = await incokalkAPI.customs.search(debouncedSearch, dest);
      return res.data;
    },
    enabled: debouncedSearch.length >= 2,
    staleTime: 60_000,
  });

  const searchResults = searchData?.rates ?? [];
  const uniqueHsCodes = searchData?.hsCodes ?? [];

  const selectedLabel = (() => {
    const match = searchResults.find(r => r.hsCode === hsCode && r.origin === origin);
    return match?.description ?? '';
  })();

  const handleSelectHs = (code: string, _description: string) => {
    setHsCode(code);
    setCustomHs('');
    setHsSearch('');
    setHsDropdownOpen(false);
  };

  const handleCalculate = async () => {
    const code = customHs || hsCode;
    if (!code) { toast.error('Sélectionnez ou saisissez un code SH'); return; }
    setLoading(true);
    try {
      const res = await incokalkAPI.customs.getDuty(code, origin, dest, goodsValue, freight, insurance);
      setResult(res.data);
    } catch {
      toast.error('Erreur lors du calcul');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-accent via-white to-accent">
      <div className="max-w-5xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-accent-soft mb-4">
            <Shield size={32} className="text-accent" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Calculateur de droits de douane
          </h1>
          <p className="text-ink-soft max-w-xl mx-auto">Recherchez un produit par nom ou code SH, puis estimez les droits à l'import.</p>
        </div>

        <div className="grid lg:grid-cols-2 gap-6">
          <div className="bg-surface rounded-none shadow-sm border border-line p-6 space-y-5">
            <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">Paramètres</h2>

            <div className="relative">
              <label className="text-xs text-ink-soft mb-1 block">Recherche TARIC</label>
              <div className="relative">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                <input
                  placeholder="Ex: chaussures, smartphones, café..."
                  value={hsSearch}
                  onFocus={() => setHsDropdownOpen(true)}
                  onChange={e => { setHsSearch(e.target.value); setHsDropdownOpen(true); }}
                  onBlur={() => setTimeout(() => setHsDropdownOpen(false), 200)}
                  className="w-full pl-9 pr-3 py-2.5 border border-line rounded-none text-sm bg-bg"
                />
                {searchLoading && <Loader2 size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-accent animate-spin" />}
              </div>

              {hsDropdownOpen && debouncedSearch.length >= 2 && (
                <div className="absolute z-30 mt-1 w-full max-h-64 overflow-y-auto rounded-none border border-line bg-surface shadow-lg">
                  {uniqueHsCodes.length === 0 && !searchLoading && (
                    <div className="px-3 py-3 text-xs text-ink-soft text-center">Aucun résultat pour "{debouncedSearch}"</div>
                  )}
                  {uniqueHsCodes.map(code => {
                    const rate = searchResults.find(r => r.hsCode === code);
                    return (
                      <button
                        key={code}
                        type="button"
                        onMouseDown={e => e.preventDefault()}
                        onClick={() => handleSelectHs(code, rate?.description ?? '')}
                        className={`w-full text-left px-3 py-2.5 text-sm hover:bg-accent/20 flex items-center justify-between gap-3 ${hsCode === code ? 'bg-accent/10 text-accent-strong font-semibold' : 'text-ink'}`}
                      >
                        <div className="min-w-0">
                          <div className="font-medium">{code}</div>
                          <div className="text-[11px] text-ink-soft truncate">{rate?.description ?? '—'}</div>
                        </div>
                        <div className="flex items-center gap-2 flex-shrink-0">
                          {rate?.isPrefential && (
                            <span className="px-1.5 py-0.5 bg-success/10 text-success rounded text-[10px] font-medium">Préf.</span>
                          )}
                          <span className="text-xs text-ink-soft">
                            {rate ? `${rate.dutyRate.toFixed(1)}%` : '—'}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}

              {hsDropdownOpen && debouncedSearch.length < 2 && (
                <div className="absolute z-30 mt-1 w-full rounded-none border border-line bg-surface shadow-lg p-3">
                  <p className="text-[11px] text-ink-soft text-center">Tapez au moins 2 caractères pour rechercher dans les données TARIC</p>
                </div>
              )}

              {selectedLabel && !hsSearch && (
                <div className="mt-1.5 text-xs text-ink-soft">
                  Sélectionné : <span className="font-medium text-accent">{hsCode} — {selectedLabel}</span>
                </div>
              )}

              <input
                placeholder="ou saisissez un code SH manuellement..."
                value={customHs}
                onChange={e => setCustomHs(e.target.value)}
                className="w-full mt-2 px-3 py-2 border border-line rounded-none text-sm bg-bg"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Origine</label>
                <select value={origin} onChange={e => setOrigin(e.target.value)} className="w-full px-3 py-2 border border-line rounded-none text-sm bg-bg">
                  {ORIGIN_COUNTRIES.map(c => (
                    <option key={c.code} value={c.code}>
                      {c.name}{agreementsByCountry.has(c.code) ? ` (${agreementsByCountry.get(c.code)})` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Destination</label>
                <select value={dest} onChange={e => setDest(e.target.value)} className="w-full px-3 py-2 border border-line rounded-none text-sm bg-bg">
                  {DEST_COUNTRIES.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}
                </select>
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <label className="text-sm font-medium text-ink">Valeur marchandises (€)</label>
                <input type="number" value={goodsValue} onChange={e => setGoodsValue(Number(e.target.value))} className="w-28 px-2 py-1 border border-line rounded text-right text-sm bg-bg" />
              </div>
              <input type="range" value={goodsValue} onChange={e => setGoodsValue(Number(e.target.value))} className="w-full h-1.5 bg-surface-2 rounded-none appearance-none cursor-pointer accent-accent" min="0" max="500000" step="100" />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Fret (€)</label>
                <input type="number" value={freight} onChange={e => setFreight(Number(e.target.value))} className="w-full px-3 py-2 border border-line rounded-none text-sm bg-bg" />
              </div>
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Assurance (€)</label>
                <input type="number" value={insurance} onChange={e => setInsurance(Number(e.target.value))} className="w-full px-3 py-2 border border-line rounded-none text-sm bg-bg" />
              </div>
            </div>

            <button onClick={handleCalculate} disabled={loading} className="w-full bg-accent text-white py-3 rounded-none font-semibold hover:bg-accent-strong disabled:opacity-50 flex items-center justify-center gap-2 text-sm transition-colors">
              {loading ? <><Loader2 className="animate-spin" /> Calcul...</> : 'Calculer les droits de douane'}
            </button>
          </div>

          <div className="space-y-4">
            {result ? (
              <>
                <div className="relative bg-surface rounded-none shadow-sm border border-line p-6">
                  <span className="hud-corner hud-corner-tl" aria-hidden="true" />
                  <span className="hud-corner hud-corner-tr" aria-hidden="true" />
                  <span className="hud-corner hud-corner-bl" aria-hidden="true" />
                  <span className="hud-corner hud-corner-br" aria-hidden="true" />
                  <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Résultat</h2>
                  <div className="grid grid-cols-2 gap-4 mb-4">
                    <div className="bg-accent/10 rounded-none p-4 text-center">
                      <div className="text-3xl font-bold text-accent-strong">{result.dutyRate > 0 ? result.dutyRate.toFixed(1) : '0'}%</div>
                      <div className="text-xs text-ink-soft">Taux de droit</div>
                    </div>
                    <div className="bg-success/10 rounded-none p-4 text-center">
                      <div className="text-3xl font-bold text-success">{formatEur(result.dutyAmount)}</div>
                      <div className="text-xs text-ink-soft">Droits à payer</div>
                    </div>
                  </div>
                  {result.savings > 0 && (
                    <div className="mb-4 bg-success/10 border border-success/40 rounded-none px-4 py-3 flex items-center gap-3">
                      <div className="flex items-center gap-1.5">
                        <CheckCircle size={16} className="text-success" />
                        <span className="text-sm font-semibold text-success">Économie</span>
                      </div>
                      <span className="text-sm text-success">
                        — {formatEur(result.savings)} vs taux MFN ({result.mfnRate.toFixed(1)}%)
                      </span>
                    </div>
                  )}
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Code SH</span><span className="font-medium">{result.hsCode}</span></div>
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Origine</span><span className="font-medium">{result.origin}</span></div>
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Destination</span><span className="font-medium">{result.destination}</span></div>
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Type</span><span className="font-medium">{result.isPrefential ? 'Préférentiel' : 'MFN'}</span></div>
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Taux MFN</span><span className="font-medium">{result.mfnRate.toFixed(1)}%</span></div>
                    {result.agreementCode && (
                      <div className="flex justify-between py-1"><span className="text-ink-soft">Accord</span><span className="font-medium">{result.agreementName || result.agreementCode}</span></div>
                    )}
                    <div className="flex justify-between py-1 border-t pt-2"><span className="text-ink-soft">Valeur CIF</span><span className="font-bold">{formatEur(result.cifValue)}</span></div>
                  </div>
                </div>
                {result.agreementName && (
                  <div className="bg-success/10 border border-success/40 rounded-none p-4 flex items-start gap-3">
                    <CheckCircle size={20} className="text-success mt-0.5 flex-shrink-0" />
                    <div>
                      <div className="text-sm font-bold text-success">Accord préférentiel applicable</div>
                      <div className="text-xs text-success mt-0.5">{result.agreementName} — Droits réduits ou nuls</div>
                    </div>
                  </div>
                )}
                {result.notes && (
                  <div className="bg-bg rounded-none p-4 flex items-start gap-3">
                    <AlertCircle size={18} className="text-ink-soft mt-0.5 flex-shrink-0" />
                    <p className="text-xs text-ink-soft">{result.notes}</p>
                  </div>
                )}
              </>
            ) : (
              <div className="bg-surface rounded-none shadow-sm border border-line p-12 text-center">
                <Shield className="h-10 w-10 mx-auto mb-3 text-ink-soft" />
                <p className="text-sm text-ink-soft">Remplissez le formulaire pour estimer les droits de douane</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CustomsDutyCalculator;
