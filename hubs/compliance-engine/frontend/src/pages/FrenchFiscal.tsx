import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { FileText, Euro, Calculator, Save, Loader2 } from 'lucide-react';
import { api } from '../lib/api';

interface VatConfig {
  tvaRate: number;
  vatNumber: string;
  intraEuScheme: string;
}

interface DebConfig {
  frequency: string;
  threshold: number;
}

interface IntrastatConfig {
  dispatchThreshold: number;
  arrivalThreshold: number;
  declarationType: string;
}

const FrenchFiscal = () => {
  const [vatConfig, setVatConfig] = useState<VatConfig>({
    tvaRate: 20,
    vatNumber: '',
    intraEuScheme: 'normal',
  });
  const [debConfig, setDebConfig] = useState<DebConfig>({
    frequency: 'monthly',
    threshold: 460000,
  });
  const [intrastatConfig, setIntrastatConfig] = useState<IntrastatConfig>({
    dispatchThreshold: 460000,
    arrivalThreshold: 460000,
    declarationType: 'simplified',
  });

  const { isLoading } = useQuery({
    queryKey: ['french-fiscal-settings'],
    queryFn: async () => {
      const res = await api.get('/v1/fiscal/french-settings');
      const data = res.data;
      if (data.vat) setVatConfig((prev) => ({ ...prev, ...data.vat }));
      if (data.deb) setDebConfig((prev) => ({ ...prev, ...data.deb }));
      if (data.intrastat) setIntrastatConfig((prev) => ({ ...prev, ...data.intrastat }));
      return data;
    },
  });

  const saveMutation = useMutation({
    mutationFn: async (payload: { section: string; data: unknown }) => {
      return api.post('/v1/fiscal/french-settings', payload);
    },
  });

  const saveVat = () => saveMutation.mutate({ section: 'vat', data: vatConfig });
  const saveDeb = () => saveMutation.mutate({ section: 'deb', data: debConfig });
  const saveIntrastat = () => saveMutation.mutate({ section: 'intrastat', data: intrastatConfig });

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Fiscalité française</h1>
        <p className="text-ink-soft mt-1">Configuration TVA, DEB & Intrastat</p>
      </div>

      {isLoading ? (
        <div className="py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : (
        <div className="space-y-6">
          {/* TVA intracommunautaire */}
          <div className="bg-surface rounded-xl border border-line p-6">
            <div className="flex items-center gap-3 mb-4">
              <Euro size={20} className="text-accent" />
              <h2 className="text-lg font-semibold text-ink">TVA intracommunautaire</h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Taux TVA (%)
                </label>
                <input
                  type="number"
                  value={vatConfig.tvaRate}
                  onChange={(e) => setVatConfig({ ...vatConfig, tvaRate: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Numéro de TVA
                </label>
                <input
                  type="text"
                  value={vatConfig.vatNumber}
                  onChange={(e) => setVatConfig({ ...vatConfig, vatNumber: e.target.value })}
                  placeholder="FRXX999999999"
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Régime intra-UE
                </label>
                <select
                  value={vatConfig.intraEuScheme}
                  onChange={(e) => setVatConfig({ ...vatConfig, intraEuScheme: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface focus:ring-2 focus:ring-accent focus:border-transparent"
                >
                  <option value="normal">Normal (autoliquidation)</option>
                  <option value="franchise">Franchise en base</option>
                  <option value="deduction">Déduction partielle</option>
                </select>
              </div>
            </div>
            <button
              onClick={saveVat}
              disabled={saveMutation.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-strong transition-colors disabled:opacity-50"
            >
              <Save size={14} />
              {saveMutation.isPending ? 'Enregistrement...' : 'Enregistrer TVA'}
            </button>
          </div>

          {/* DEB */}
          <div className="bg-surface rounded-xl border border-line p-6">
            <div className="flex items-center gap-3 mb-4">
              <FileText size={20} className="text-warning" />
              <h2 className="text-lg font-semibold text-ink">DEB (Déclaration d'Échanges de Biens)</h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Fréquence
                </label>
                <select
                  value={debConfig.frequency}
                  onChange={(e) => setDebConfig({ ...debConfig, frequency: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface focus:ring-2 focus:ring-accent focus:border-transparent"
                >
                  <option value="monthly">Mensuelle</option>
                  <option value="quarterly">Trimestrielle</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Seuil (€)
                </label>
                <input
                  type="number"
                  value={debConfig.threshold}
                  onChange={(e) => setDebConfig({ ...debConfig, threshold: parseInt(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                />
              </div>
            </div>
            <button
              onClick={saveDeb}
              disabled={saveMutation.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 bg-warning text-white rounded-lg text-sm hover:bg-warning/90 transition-colors disabled:opacity-50"
            >
              <Save size={14} />
              {saveMutation.isPending ? 'Enregistrement...' : 'Enregistrer DEB'}
            </button>
          </div>

          {/* Intrastat */}
          <div className="bg-surface rounded-xl border border-line p-6">
            <div className="flex items-center gap-3 mb-4">
              <Calculator size={20} className="text-success" />
              <h2 className="text-lg font-semibold text-ink">Intrastat</h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Seuil expéditions (€)
                </label>
                <input
                  type="number"
                  value={intrastatConfig.dispatchThreshold}
                  onChange={(e) =>
                    setIntrastatConfig({ ...intrastatConfig, dispatchThreshold: parseInt(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Seuil arrivées (€)
                </label>
                <input
                  type="number"
                  value={intrastatConfig.arrivalThreshold}
                  onChange={(e) =>
                    setIntrastatConfig({ ...intrastatConfig, arrivalThreshold: parseInt(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                  Type de déclaration
                </label>
                <select
                  value={intrastatConfig.declarationType}
                  onChange={(e) => setIntrastatConfig({ ...intrastatConfig, declarationType: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface focus:ring-2 focus:ring-accent focus:border-transparent"
                >
                  <option value="simplified">Simplifiée</option>
                  <option value="complete">Complète</option>
                </select>
              </div>
            </div>
            <button
              onClick={saveIntrastat}
              disabled={saveMutation.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 bg-success text-white rounded-lg text-sm hover:bg-success/90 transition-colors disabled:opacity-50"
            >
              <Save size={14} />
              {saveMutation.isPending ? 'Enregistrement...' : 'Enregistrer Intrastat'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default FrenchFiscal;
