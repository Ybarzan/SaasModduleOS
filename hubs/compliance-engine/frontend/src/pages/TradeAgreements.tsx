import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, ChevronDown, ChevronUp, Loader2, FileText } from 'lucide-react';
import { incokalkAPI } from '../lib/api';

interface TradeAgreement {
  id: string;
  code: string;
  name: string;
  partnerCountry: string;
  partnerName: string;
  description: string;
  type: 'FTA' | 'PTA' | 'CU' | 'PSA';
  hsChaptersCovered: string;
  originRules: string;
  validFrom: string;
  validTo: string;
  active: boolean;
}

const parseChapters = (raw: string | undefined): string[] => {
  if (!raw) return [];
  if (raw.includes('-')) {
    const [start, end] = raw.split('-').map(s => parseInt(s.trim(), 10));
    if (!isNaN(start) && !isNaN(end) && start <= end) {
      return Array.from({ length: end - start + 1 }, (_, i) => String(start + i).padStart(2, '0'));
    }
  }
  return raw.split(',').map(s => s.trim()).filter(Boolean);
};

const COUNTRY_MAP: Record<string, string> = {
  VN: 'Viêt Nam',
  CA: 'Canada',
  JP: 'Japon',
  KR: 'Corée du Sud',
  MA: 'Maroc',
  TN: 'Tunisie',
  TR: 'Turquie',
  GB: 'Royaume-Uni',
  SG: 'Singapour',
  BR: 'Brésil',
  MX: 'Mexique',
  CL: 'Chili',
  ZA: 'Afrique du Sud',
};

const FLAG_MAP: Record<string, string> = {
  VN: '🇻🇳', CA: '🇨🇦', JP: '🇯🇵', KR: '🇰🇷', MA: '🇲🇦',
  TN: '🇹🇳', TR: '🇹🇷', GB: '🇬🇧', SG: '🇸🇬', BR: '🇧🇷',
  MX: '🇲🇽', CL: '🇨🇱', ZA: '🇿🇦', EU: '🇪🇺',
};

const TYPE_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  FTA: { label: 'ALE', color: 'text-success', bg: 'bg-success/10' },
  PTA: { label: 'APT', color: 'text-accent-strong', bg: 'bg-accent-soft' },
  CU: { label: 'CU', color: 'text-accent-strong', bg: 'bg-accent-soft' },
  PSA: { label: 'PSA', color: 'text-warning', bg: 'bg-warning/10' },
};

const TradeAgreements = () => {
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data: agreementsData, isLoading } = useQuery({
    queryKey: ['trade-agreements'],
    queryFn: async () => {
      const res = await incokalkAPI.tradeAgreements.list();
      return res.data as TradeAgreement[];
    },
  });

  const agreements = Array.isArray(agreementsData) ? agreementsData : [];

  const filtered = agreements.filter((a) => {
    const q = search.toLowerCase();
    const matchesSearch =
      !q ||
      a.name.toLowerCase().includes(q) ||
      a.code.toLowerCase().includes(q) ||
      a.partnerCountry.toLowerCase().includes(q) ||
      a.partnerName.toLowerCase().includes(q) ||
      (COUNTRY_MAP[a.partnerCountry] || '').toLowerCase().includes(q);
    const matchesType = !typeFilter || a.type === typeFilter;
    return matchesSearch && matchesType;
  });

  const formatDate = (d: string) => {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Accords commerciaux</h1>
        <p className="text-ink-soft mt-1">Accords de libre-échange et régimes préférentiels UE</p>
      </div>

      {/* Search + Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="relative flex-1">
          <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Rechercher par nom, code ou pays..."
            className="w-full pl-10 pr-4 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
          />
        </div>
        <div className="flex gap-2">
          {(['FTA', 'PTA', 'CU'] as const).map((t) => {
            const cfg = TYPE_CONFIG[t];
            return (
              <button
                key={t}
                onClick={() => setTypeFilter(typeFilter === t ? null : t)}
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  typeFilter === t
                    ? `${cfg.bg} ${cfg.color} ring-1 ring-current`
                    : 'bg-surface-2 text-ink-soft hover:bg-surface-2'
                }`}
              >
                {cfg.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-12 text-center text-ink-soft">
          <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucun accord trouvé</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map((agreement) => {
            const isExpanded = expandedId === agreement.id;
            const countryDisplay = COUNTRY_MAP[agreement.partnerCountry] || agreement.partnerName;
            const flag = FLAG_MAP[agreement.partnerCountry] || '🏳️';
            const typeCfg = TYPE_CONFIG[agreement.type] || { label: agreement.type, color: 'text-ink', bg: 'bg-surface-2' };

            return (
              <div
                key={agreement.id}
                className="bg-surface rounded-xl border border-line overflow-hidden hover:shadow-md transition-shadow"
              >
                {/* Card Header */}
                <button
                  onClick={() => setExpandedId(isExpanded ? null : agreement.id)}
                  className="w-full text-left p-5"
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <span className="text-2xl">{flag}</span>
                      <span className="px-2 py-0.5 rounded text-xs font-medium bg-surface-2 text-ink">
                        {agreement.code}
                      </span>
                    </div>
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${typeCfg.bg} ${typeCfg.color}`}>
                      {typeCfg.label}
                    </span>
                  </div>
                  <h3 className="text-sm font-semibold text-ink mb-1">{agreement.name}</h3>
                  <p className="text-xs text-ink-soft">{countryDisplay}</p>

                  <div className="mt-3 flex items-center gap-4 text-xs text-ink-soft">
                    <span>Chapitres SH : {parseChapters(agreement.hsChaptersCovered).length}</span>
                    <span>{formatDate(agreement.validFrom)} — {formatDate(agreement.validTo)}</span>
                  </div>

                  <div className="mt-3 flex items-center justify-between">
                    <span className={`inline-flex items-center gap-1 text-xs ${agreement.active ? 'text-success' : 'text-ink-soft'}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${agreement.active ? 'bg-success' : 'bg-surface-2'}`} />
                      {agreement.active ? 'Actif' : 'Inactif'}
                    </span>
                    {isExpanded ? (
                      <ChevronUp size={16} className="text-ink-soft" />
                    ) : (
                      <ChevronDown size={16} className="text-ink-soft" />
                    )}
                  </div>
                </button>

                {/* Expanded Details */}
                {isExpanded && (
                  <div className="px-5 pb-5 border-t border-line pt-4 space-y-3">
                    {agreement.description && (
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">Description</p>
                        <p className="text-sm text-ink">{agreement.description}</p>
                      </div>
                    )}
                    {agreement.originRules && (
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">Règles d'origine</p>
                        <p className="text-sm text-ink">{agreement.originRules}</p>
                      </div>
                    )}
                    {parseChapters(agreement.hsChaptersCovered).length > 0 && (
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">Chapitres SH couverts</p>
                        <div className="flex flex-wrap gap-1">
                          {parseChapters(agreement.hsChaptersCovered).map((ch) => (
                            <span key={ch} className="px-1.5 py-0.5 bg-surface-2 rounded text-xs text-ink-soft">
                              {ch}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default TradeAgreements;
