import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, BookOpen, Download, Loader2, X } from 'lucide-react';
import { incokalkAPI } from '../lib/api';

interface TaricLine {
  id: string;
  hsCode: string;
  description: string;
  dutyRate: string;
  thirdCountryDuty: string;
  origin: string;
  measures: string;
  chapter: string;
  supplementaryDuties?: string;
  quotas?: string;
  suspensions?: string;
}

const CHAPTERS = [
  '01-05 Animaux vivants et produits',
  '06-14 Produits végétaux',
  '15-23 Graisses, huiles, cires',
  '24-27 Produits alimentaires, boissons, tabac',
  '28-38 Produits chimiques',
  '39-40 Plastiques et caoutchouc',
  '41-43 Cuir, maroquinerie',
  '44-49 Bois, papier, imprimés',
  '50-63 Textiles et vêtements',
  '64-68 Chaussures, pierre, plâtre',
  '72-83 Métaux et ouvrages',
  '84-85 Machines, appareils électriques',
  '86-89 Matériel de transport',
  '90-97 Instruments, horlogerie, divers',
];

const TaricData = () => {
  const [search, setSearch] = useState('');
  const [chapterFilter, setChapterFilter] = useState('');
  const [countryFilter, setCountryFilter] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data: taricData, isLoading } = useQuery({
    queryKey: ['taric-data', search, chapterFilter, countryFilter],
    queryFn: async () => {
      const params: Record<string, string> = {};
      if (search) params.keyword = search;
      if (chapterFilter) params.chapter = chapterFilter;
      if (countryFilter) params.country = countryFilter;
      const res = await incokalkAPI.customs.search(search, 'EU');
      return (res.data?.results || res.data || []) as TaricLine[];
    },
  });

  const items = Array.isArray(taricData) ? taricData : [];

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">
          <span className="text-accent font-normal" aria-hidden="true">:: </span>
          Données TARIC
        </h1>
        <p className="text-ink-soft mt-1">Consultez le tarif douanier européen</p>
      </div>

      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="relative flex-1">
          <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Code SH ou mot-clé..."
            className="w-full pl-10 pr-4 py-2 border border-line rounded-none text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
          />
        </div>
        <select
          value={chapterFilter}
          onChange={(e) => setChapterFilter(e.target.value)}
          className="px-3 py-2 border border-line rounded-none text-sm bg-surface focus:ring-2 focus:ring-accent focus:border-transparent"
        >
          <option value="">Chapitre</option>
          {CHAPTERS.map((ch) => (
            <option key={ch.split(' ')[0]} value={ch.split(' ')[0]}>
              {ch}
            </option>
          ))}
        </select>
        <select
          value={countryFilter}
          onChange={(e) => setCountryFilter(e.target.value)}
          className="px-3 py-2 border border-line rounded-none text-sm bg-surface focus:ring-2 focus:ring-accent focus:border-transparent"
        >
          <option value="">Pays d'origine</option>
          <option value="CN">Chine</option>
          <option value="US">États-Unis</option>
          <option value="JP">Japon</option>
          <option value="KR">Corée du Sud</option>
          <option value="VN">Viêt Nam</option>
          <option value="IN">Inde</option>
          <option value="GB">Royaume-Uni</option>
          <option value="CH">Suisse</option>
          <option value="TR">Turquie</option>
          <option value="MA">Maroc</option>
        </select>
      </div>

      {isLoading ? (
        <div className="py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : items.length === 0 ? (
        <div className="py-12 text-center text-ink-soft">
          <BookOpen size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucune donnée TARIC trouvée</p>
          <p className="text-xs mt-1">Utilisez la recherche pour consulter le tarif</p>
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((item) => {
            const isSelected = selectedId === item.id;
            return (
              <div key={item.id}>
                <button
                  onClick={() => setSelectedId(isSelected ? null : item.id)}
                  className="w-full text-left bg-surface rounded-none border border-line p-4 hover:shadow-md transition-shadow"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3 min-w-0">
                      <span className="px-2 py-0.5 bg-accent-soft text-accent-strong rounded text-xs font-mono font-semibold shrink-0">
                        {item.hsCode}
                      </span>
                      <span className="text-sm text-ink truncate">{item.description}</span>
                    </div>
                    <div className="flex items-center gap-4 shrink-0 ml-4">
                      <span className="text-sm font-medium text-ink">{item.dutyRate}</span>
                      {isSelected ? <X size={16} className="text-ink-soft" /> : null}
                    </div>
                  </div>
                </button>
                {isSelected && (
                  <div className="bg-bg border border-t-0 border-line rounded-none px-4 py-4 space-y-4">
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Droit de douane</p>
                        <p className="text-sm font-semibold text-ink">{item.dutyRate}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Droit pays tiers</p>
                        <p className="text-sm text-ink">{item.thirdCountryDuty || '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Pays d'origine</p>
                        <p className="text-sm text-ink">{item.origin || '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Mesures</p>
                        <p className="text-sm text-ink">{item.measures || '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Droits additionnels</p>
                        <p className="text-sm text-ink">{item.supplementaryDuties || '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Quotas</p>
                        <p className="text-sm text-ink">{item.quotas || '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-ink-soft uppercase tracking-wider">Suspensions</p>
                        <p className="text-sm text-ink">{item.suspensions || '—'}</p>
                      </div>
                    </div>
                    <button className="inline-flex items-center gap-2 px-3 py-1.5 bg-accent text-white rounded-none text-sm hover:bg-accent-strong transition-colors">
                      <Download size={14} />
                      Exporter
                    </button>
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

export default TaricData;
