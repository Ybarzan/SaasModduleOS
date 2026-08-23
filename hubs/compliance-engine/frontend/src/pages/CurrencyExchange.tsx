import { useState, useEffect, useCallback } from 'react';
import { DollarSign, ArrowRightLeft, Loader2, RefreshCw, Activity } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import { formatNumber } from '../lib/formatNumber';

const CURRENCY_GROUPS = [
  {
    label: 'Majeures',
    currencies: [
      { code: 'EUR', name: 'Euro', flag: '\u{1F1EA}\u{1F1FA}' },
      { code: 'USD', name: 'Dollar US', flag: '\u{1F1FA}\u{1F1F8}' },
      { code: 'GBP', name: 'Livre sterling', flag: '\u{1F1EC}\u{1F1E7}' },
      { code: 'JPY', name: 'Yen japonais', flag: '\u{1F1EF}\u{1F1F5}' },
      { code: 'CHF', name: 'Franc suisse', flag: '\u{1F1E8}\u{1F1ED}' },
      { code: 'CAD', name: 'Dollar canadien', flag: '\u{1F1E8}\u{1F1E6}' },
      { code: 'AUD', name: 'Dollar australien', flag: '\u{1F1E6}\u{1F1FA}' },
      { code: 'CNY', name: 'Yuan chinois', flag: '\u{1F1E8}\u{1F1F3}' },
    ],
  },
  {
    label: 'Afrique',
    currencies: [
      { code: 'MAD', name: 'Dirham marocain', flag: '\u{1F1F2}\u{1F1E6}' },
      { code: 'TND', name: 'Dinar tunisien', flag: '\u{1F1F9}\u{1F1F3}' },
      { code: 'DZD', name: 'Dinar alg\u00E9rien', flag: '\u{1F1E9}\u{1F1FF}' },
      { code: 'EGP', name: 'Livre \u00E9gyptienne', flag: '\u{1F1EA}\u{1F1EC}' },
      { code: 'NGN', name: 'Naira nig\u00E9rian', flag: '\u{1F1F3}\u{1F1EC}' },
      { code: 'GHS', name: 'Cedi gh\u00E9an\u00E9en', flag: '\u{1F1EC}\u{1F1ED}' },
      { code: 'XOF', name: 'CFA franc BCEAO', flag: '\u{1F1EB}\u{1F1F7}' },
      { code: 'XAF', name: 'CFA franc BEAC', flag: '\u{1F1E8}\u{1F1EC}' },
      { code: 'ZAR', name: 'Rand sud-africain', flag: '\u{1F1FF}\u{1F1E6}' },
      { code: 'KES', name: 'Shilling k\u00E9nyan', flag: '\u{1F1F0}\u{1F1EA}' },
      { code: 'ETB', name: 'Birr \u00E9thiopien', flag: '\u{1F1EA}\u{1F1F9}' },
    ],
  },
  {
    label: 'Asie',
    currencies: [
      { code: 'INR', name: 'Roupie indienne', flag: '\u{1F1EE}\u{1F1F3}' },
      { code: 'KRW', name: 'Won sud-cor\u00E9en', flag: '\u{1F1F0}\u{1F1F7}' },
      { code: 'SGD', name: 'Dollar singapourien', flag: '\u{1F1F8}\u{1F1EC}' },
      { code: 'THB', name: 'Baht tha\u00EFlandais', flag: '\u{1F1F9}\u{1F1ED}' },
      { code: 'MYR', name: 'Ringgit malais', flag: '\u{1F1F2}\u{1F1FE}' },
      { code: 'VND', name: 'Dong vietnamien', flag: '\u{1F1FB}\u{1F1F3}' },
      { code: 'IDR', name: 'Rupiah indon\u00E9sien', flag: '\u{1F1EE}\u{1F1E9}' },
      { code: 'PHP', name: 'Peso philippin', flag: '\u{1F1F5}\u{1F1ED}' },
      { code: 'PKR', name: 'Roupie pakistanaise', flag: '\u{1F1F5}\u{1F1F0}' },
      { code: 'BDT', name: 'Taka bangladais', flag: '\u{1F1E7}\u{1F1E9}' },
      { code: 'LKR', name: 'Roupie srilankaise', flag: '\u{1F1F1}\u{1F1F0}' },
      { code: 'TWD', name: 'Dollar ta\u00EFwanais', flag: '\u{1F1F9}\u{1F1FC}' },
      { code: 'HKD', name: 'Dollar de Hong Kong', flag: '\u{1F1ED}\u{1F1F0}' },
    ],
  },
  {
    label: 'Am\u00E9riques',
    currencies: [
      { code: 'BRL', name: 'R\u00E9al br\u00E9silien', flag: '\u{1F1E7}\u{1F1F7}' },
      { code: 'MXN', name: 'Peso mexicain', flag: '\u{1F1F2}\u{1F1FD}' },
      { code: 'ARS', name: 'Peso argentin', flag: '\u{1F1E6}\u{1F1F7}' },
      { code: 'CLP', name: 'Peso chilien', flag: '\u{1F1E8}\u{1F1F1}' },
      { code: 'COP', name: 'Peso colombien', flag: '\u{1F1E8}\u{1F1F4}' },
      { code: 'PEN', name: 'Sol p\u00E9ruvien', flag: '\u{1F1F5}\u{1F1EA}' },
    ],
  },
  {
    label: 'Europe',
    currencies: [
      { code: 'PLN', name: 'Zloty polonais', flag: '\u{1F1F5}\u{1F1F1}' },
      { code: 'CZK', name: 'Couronne tch\u00E8que', flag: '\u{1F1E8}\u{1F1FF}' },
      { code: 'HUF', name: 'Forint hongrois', flag: '\u{1F1ED}\u{1F1FA}' },
      { code: 'RON', name: 'Leu roumain', flag: '\u{1F1F7}\u{1F1F4}' },
      { code: 'BGN', name: 'Lev bulgare', flag: '\u{1F1E7}\u{1F1EC}' },
      { code: 'HRK', name: 'Kuna croate', flag: '\u{1F1ED}\u{1F1F7}' },
      { code: 'SEK', name: 'Couronne su\u00E9doise', flag: '\u{1F1F8}\u{1F1EA}' },
      { code: 'NOK', name: 'Couronne norv\u00E9gienne', flag: '\u{1F1F3}\u{1F1F4}' },
      { code: 'DKK', name: 'Couronne danoise', flag: '\u{1F1E9}\u{1F1F0}' },
      { code: 'ISK', name: 'Couronne islandaise', flag: '\u{1F1EE}\u{1F1F8}' },
      { code: 'UAH', name: 'Hryvnia ukrainienne', flag: '\u{1F1FA}\u{1F1E6}' },
      { code: 'TRY', name: 'Lire turque', flag: '\u{1F1F9}\u{1F1F7}' },
    ],
  },
  {
    label: 'Oc\u00E9anie',
    currencies: [
      { code: 'NZD', name: 'Dollar n\u00E9o-z\u00E9landais', flag: '\u{1F1F3}\u{1F1FF}' },
      { code: 'FJD', name: 'Dollar des Fidji', flag: '\u{1F1EB}\u{1F1EF}' },
    ],
  },
];

const ALL_CURRENCIES = CURRENCY_GROUPS.flatMap(g => g.currencies);

const POPULAR_PAIRS = [
  { from: 'EUR', to: 'USD' },
  { from: 'EUR', to: 'GBP' },
  { from: 'EUR', to: 'CNY' },
  { from: 'EUR', to: 'MAD' },
  { from: 'EUR', to: 'JPY' },
  { from: 'USD', to: 'EUR' },
];

const CurrencyExchange = () => {
  const [from, setFrom] = useState('EUR');
  const [to, setTo] = useState('USD');
  const [amount, setAmount] = useState(10000);
  const [convertedValue, setConvertedValue] = useState<string | null>(null);
  const [usedRate, setUsedRate] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [euroRates, setEuroRates] = useState<Record<string, number>>({});
  const [ratesLoading, setRatesLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<string>('');

  const getFlag = (code: string) => ALL_CURRENCIES.find(c => c.code === code)?.flag || '';

  const fetchRates = useCallback(async () => {
    setRatesLoading(true);
    try {
      const res = await incokalkAPI.currency.getRates('EUR');
      const { rates, supported } = res.data;

      const ratesMap: Record<string, number> = {};
      if (rates) {
        Object.entries(rates).forEach(([k, v]) => {
          ratesMap[k] = v as number;
        });
      }
      ratesMap['EUR'] = 1;

      setEuroRates(ratesMap);

      if (supported && supported.length > 0) {
        setLastUpdated(new Date().toLocaleDateString('fr-FR'));
      }
    } catch {
      toast.error('Erreur lors du chargement des taux');
    } finally {
      setRatesLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRates();
  }, [fetchRates]);

  const handleConvert = async () => {
    if (from === to) {
      setConvertedValue(formatNumber(amount, { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
      setUsedRate(1);
      return;
    }
    setLoading(true);
    setConvertedValue(null);
    setUsedRate(null);
    try {
      const res = await incokalkAPI.currency.convert(amount, from, to);
      const { convertedAmount, rate } = res.data;
      setUsedRate(rate);
      setConvertedValue(formatNumber(convertedAmount, { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
    } catch {
      toast.error('Erreur lors de la conversion');
    } finally {
      setLoading(false);
    }
  };

  const swap = () => {
    setFrom(to);
    setTo(from);
    setConvertedValue(null);
    setUsedRate(null);
  };

  const getPopularDisplayRate = (p: { from: string; to: string }) => {
    if (p.from === 'EUR' && p.to === 'EUR') return 1;
    if (p.from === 'EUR') return euroRates[p.to];
    if (p.to === 'EUR') {
      const usdRate = euroRates['USD'];
      if (usdRate && p.from === 'USD') return 1 / usdRate;
      return undefined;
    }
    return undefined;
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-warning via-white to-warning">
      <div className="max-w-5xl mx-auto px-4 py-12">
        {/* Header */}
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-warning/10 mb-4">
            <DollarSign size={32} className="text-warning" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            Taux de change
          </h1>
          <p className="text-ink-soft max-w-xl mx-auto">
            Convertissez vos montants entre devises avec les taux en temps réel.
          </p>
          <div className="flex items-center justify-center gap-2 mt-3">
            <span className="relative flex h-2.5 w-2.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-success/10 opacity-75" />
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-success" />
            </span>
            <span className="text-xs font-medium text-success">En direct</span>
            <span className="text-xs text-ink-soft ml-1">Taux en temps réel</span>
            {lastUpdated && (
              <span className="text-xs text-ink-soft ml-1">Mis à jour : {lastUpdated}</span>
            )}
          </div>
        </div>

        {/* Converter */}
        <div className="bg-surface rounded-2xl shadow-sm border border-line p-6 mb-6">
          <div className="grid grid-cols-[1fr,auto,1fr] gap-4 items-end">
            <div>
              <label className="text-xs text-ink-soft mb-1 block">De</label>
              <select
                value={from}
                onChange={e => { setFrom(e.target.value); setConvertedValue(null); setUsedRate(null); }}
                className="w-full px-3 py-2.5 border border-line rounded-lg text-sm bg-bg focus:outline-none focus:ring-2 focus:ring-warning"
              >
                {CURRENCY_GROUPS.map(group => (
                  <optgroup key={group.label} label={group.label}>
                    {group.currencies.map(c => (
                      <option key={c.code} value={c.code}>{c.flag} {c.code} — {c.name}</option>
                    ))}
                  </optgroup>
                ))}
              </select>
              <input
                type="number"
                value={amount}
                onChange={e => setAmount(Number(e.target.value))}
                className="w-full mt-2 px-3 py-2.5 border border-line rounded-lg text-lg font-bold bg-bg focus:outline-none focus:ring-2 focus:ring-warning"
              />
            </div>
            <button
              onClick={swap}
              className="p-3 rounded-full bg-surface-2 hover:bg-warning/10 hover:text-warning transition-colors mb-1 group"
            >
              <ArrowRightLeft size={20} className="text-ink-soft group-hover:text-warning transition-colors" />
            </button>
            <div>
              <label className="text-xs text-ink-soft mb-1 block">Vers</label>
              <select
                value={to}
                onChange={e => { setTo(e.target.value); setConvertedValue(null); setUsedRate(null); }}
                className="w-full px-3 py-2.5 border border-line rounded-lg text-sm bg-bg focus:outline-none focus:ring-2 focus:ring-warning"
              >
                {CURRENCY_GROUPS.map(group => (
                  <optgroup key={group.label} label={group.label}>
                    {group.currencies.map(c => (
                      <option key={c.code} value={c.code}>{c.flag} {c.code} — {c.name}</option>
                    ))}
                  </optgroup>
                ))}
              </select>
              {convertedValue != null && (
                <div className="mt-2 px-3 py-2.5 border border-success/40 bg-success/10 rounded-lg text-lg font-bold text-success">
                  {convertedValue} {to}
                </div>
              )}
            </div>
          </div>
          <button
            onClick={handleConvert}
            disabled={loading}
            className="mt-4 w-full bg-warning text-white py-3 rounded-xl font-semibold hover:bg-warning/90 disabled:opacity-50 flex items-center justify-center gap-2 text-sm transition-colors"
          >
            {loading ? <><Loader2 className="animate-spin" /> Conversion en cours...</> : 'Convertir'}
          </button>
          {usedRate != null && (
            <div className="mt-3 text-center text-sm text-ink-soft">
              Taux : 1 {from} = <span className="font-bold text-ink">{usedRate.toFixed(6)}</span> {to}
              <span className="inline-flex items-center gap-1 ml-2">
                <span className="h-1.5 w-1.5 rounded-full bg-success inline-block" />
                <span className="text-success text-xs">En direct</span>
              </span>
            </div>
          )}
        </div>

        {/* Taux du moment */}
        <div className="bg-surface rounded-2xl shadow-sm border border-line p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">
                Taux du moment
              </h2>
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-success/10 opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-success" />
              </span>
            </div>
            <button
              onClick={fetchRates}
              className="text-ink-soft hover:text-warning transition-colors"
            >
              <RefreshCw size={14} />
            </button>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {POPULAR_PAIRS.map((p, i) => {
              const rate = getPopularDisplayRate(p);
              return (
                <div key={i} className="bg-bg rounded-xl p-3 text-center">
                  <div className="text-xs text-ink-soft mb-1">
                    {getFlag(p.from)} {p.from} → {getFlag(p.to)} {p.to}
                  </div>
                  <div className="text-xl font-bold text-ink">
                    {rate != null && rate > 0 ? rate.toFixed(4) : '\u2014'}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Table de référence */}
        <div className="bg-surface rounded-2xl shadow-sm border border-line p-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Activity size={16} className="text-warning" />
              <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">
                Table de référence — Taux vs EUR
              </h2>
            </div>
            <button
              onClick={fetchRates}
              disabled={ratesLoading}
              className="text-ink-soft hover:text-warning transition-colors disabled:opacity-50"
            >
              <RefreshCw size={14} className={ratesLoading ? 'animate-spin' : ''} />
            </button>
          </div>
          <p className="text-xs text-ink-soft mb-4">
            Tous les taux sont exprimés par rapport à 1 EUR. Source : Taux en temps réel.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2">
            {ALL_CURRENCIES.map(c => {
              const rate = c.code === 'EUR' ? 1 : euroRates[c.code];
              return (
                <div
                  key={c.code}
                  className="flex items-center justify-between px-3 py-2 bg-bg rounded-lg hover:bg-warning/10 transition-colors"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-lg">{c.flag}</span>
                    <div>
                      <span className="text-sm font-semibold text-ink">{c.code}</span>
                      <span className="text-xs text-ink-soft ml-1.5 hidden sm:inline">{c.name}</span>
                    </div>
                  </div>
                  <span className="text-sm font-bold text-ink tabular-nums">
                    {rate != null ? (c.code === 'EUR' ? '1.0000' : rate.toFixed(4)) : '\u2014'}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CurrencyExchange;
