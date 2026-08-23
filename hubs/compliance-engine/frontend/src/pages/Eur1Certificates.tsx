import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FileText, Plus, Download, CheckCircle, XCircle, Ban, Globe, Loader2 } from 'lucide-react';
import { api } from '../lib/api';

interface Eur1Certificate {
  id: string;
  certificateNumber: string;
  agreementCode: string;
  originCountry: string;
  importerName: string;
  exporterName: string;
  hsCode: string;
  goodsDescription?: string;
  netWeightKg?: number;
  grossWeightKg?: number;
  originCriteria?: string;
  productionMethod?: string;
  status: 'ISSUED' | 'USED' | 'EXPIRED' | 'REVOKED';
  issueDate: string;
  validUntil?: string;
  issuerName?: string;
  notes?: string;
}

const STATUS_CONFIG: Record<string, { label: string; icon: typeof CheckCircle; color: string; bg: string }> = {
  ISSUED: { label: 'Émis', icon: CheckCircle, color: 'text-success', bg: 'bg-success/10' },
  USED: { label: 'Utilisé', icon: FileText, color: 'text-accent-strong', bg: 'bg-accent-soft' },
  EXPIRED: { label: 'Expiré', icon: XCircle, color: 'text-warning', bg: 'bg-warning/10' },
  REVOKED: { label: 'Révoqué', icon: Ban, color: 'text-danger', bg: 'bg-danger/10' },
};

const ORIGIN_CRITERIA = ['WO', 'WH', 'PE', 'CTH', 'CTSH'];

const emptyForm = {
  agreementCode: '',
  originCountry: '',
  importerName: '',
  exporterName: '',
  hsCode: '',
  goodsDescription: '',
  netWeightKg: '',
  originCriteria: 'WH',
  validUntil: '',
};

const Eur1Certificates = () => {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const { data: certificatesData, isLoading } = useQuery({
    queryKey: ['eur1-certificates'],
    queryFn: async () => {
      const res = await api.get('/v1/eur1');
      return res.data as Eur1Certificate[];
    },
  });

  const { data: agreementsData } = useQuery({
    queryKey: ['trade-agreements'],
    queryFn: async () => {
      const res = await api.get('/v1/trade-agreements');
      return res.data as { code: string; name: string }[];
    },
  });

  const certificates = Array.isArray(certificatesData) ? certificatesData : [];
  const agreements = Array.isArray(agreementsData) ? agreementsData : [];

  const createMutation = useMutation({
    mutationFn: async (data: typeof form) =>
      api.post('/v1/eur1', {
        agreementCode: data.agreementCode,
        originCountry: data.originCountry,
        importerName: data.importerName,
        exporterName: data.exporterName,
        hsCode: data.hsCode,
        goodsDescription: data.goodsDescription || undefined,
        netWeightKg: data.netWeightKg ? parseFloat(data.netWeightKg) : undefined,
        originCriteria: data.originCriteria,
        validUntil: data.validUntil || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['eur1-certificates'] });
      setShowForm(false);
      setForm(emptyForm);
    },
  });

  const formatDate = (d?: string) => {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-ink">Certificats EUR.1</h1>
          <p className="text-ink-soft mt-1">Certificats d'origine préférentielle</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-strong transition-colors"
        >
          <Plus size={16} />
          Nouveau certificat
        </button>
      </div>

      {showForm && (
        <div className="bg-surface rounded-xl border border-line p-6 mb-6">
          <h3 className="text-sm font-semibold text-ink mb-4">Créer un certificat EUR.1</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-4">
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Accord préférentiel
              </label>
              <select
                value={form.agreementCode}
                onChange={(e) => setForm({ ...form, agreementCode: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              >
                <option value="">Sélectionner</option>
                {agreements.map((a) => (
                  <option key={a.code} value={a.code}>{a.code} — {a.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Pays d'origine
              </label>
              <input
                type="text"
                value={form.originCountry}
                onChange={(e) => setForm({ ...form, originCountry: e.target.value.toUpperCase() })}
                placeholder="FR"
                maxLength={2}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Exportateur
              </label>
              <input
                type="text"
                value={form.exporterName}
                onChange={(e) => setForm({ ...form, exporterName: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Importateur
              </label>
              <input
                type="text"
                value={form.importerName}
                onChange={(e) => setForm({ ...form, importerName: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Code SH
              </label>
              <input
                type="text"
                value={form.hsCode}
                onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
                placeholder="8471.30.00"
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Critère d'origine
              </label>
              <select
                value={form.originCriteria}
                onChange={(e) => setForm({ ...form, originCriteria: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              >
                {ORIGIN_CRITERIA.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Poids net (kg)
              </label>
              <input
                type="number"
                value={form.netWeightKg}
                onChange={(e) => setForm({ ...form, netWeightKg: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Valide jusqu'au (optionnel, 1 an par défaut)
              </label>
              <input
                type="date"
                value={form.validUntil}
                onChange={(e) => setForm({ ...form, validUntil: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
            <div className="md:col-span-2 lg:col-span-3">
              <label className="block text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">
                Description des marchandises
              </label>
              <textarea
                value={form.goodsDescription}
                onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
                placeholder="Description des produits..."
                rows={2}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              />
            </div>
          </div>
          {createMutation.isError && (
            <p className="text-sm text-danger mb-3">
              Erreur lors de la création du certificat. Vérifiez que tous les champs obligatoires sont renseignés.
            </p>
          )}
          <button
            onClick={() => createMutation.mutate(form)}
            disabled={createMutation.isPending || !form.agreementCode || !form.originCountry || !form.importerName || !form.exporterName || !form.hsCode}
            className="inline-flex items-center gap-2 px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-strong transition-colors disabled:opacity-50"
          >
            {createMutation.isPending ? 'Création...' : 'Créer le certificat'}
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : certificates.length === 0 ? (
        <div className="py-12 text-center text-ink-soft">
          <Globe size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucun certificat EUR.1</p>
          <p className="text-xs mt-1">Créez un nouveau certificat pour commencer</p>
        </div>
      ) : (
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line bg-bg">
                <th className="text-left px-4 py-3 font-medium text-ink-soft">Certificat</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">Accord</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">Origine</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">Exportateur</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">Statut</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">Émis le</th>
                <th className="text-right px-4 py-3 font-medium text-ink-soft">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {certificates.map((cert) => {
                const cfg = STATUS_CONFIG[cert.status] || STATUS_CONFIG.ISSUED;
                const Icon = cfg.icon;
                return (
                  <tr key={cert.id} className="hover:bg-bg transition-colors">
                    <td className="px-4 py-3">
                      <span className="font-mono text-xs font-semibold text-ink">{cert.certificateNumber}</span>
                    </td>
                    <td className="px-4 py-3 text-ink-soft text-xs">{cert.agreementCode}</td>
                    <td className="px-4 py-3 text-ink">{cert.originCountry}</td>
                    <td className="px-4 py-3 text-ink">{cert.exporterName}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${cfg.bg} ${cfg.color}`}>
                        <Icon size={12} />
                        {cfg.label}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-ink-soft text-xs">{formatDate(cert.issueDate)}</td>
                    <td className="px-4 py-3 text-right">
                      <button className="inline-flex items-center gap-1 px-2 py-1 text-xs text-accent hover:text-accent-strong transition-colors">
                        <Download size={12} />
                        PDF
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default Eur1Certificates;
