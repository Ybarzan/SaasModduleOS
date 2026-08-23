import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Mail, Inbox, Plus, Trash2, RefreshCw, CheckCircle, XCircle, AlertTriangle,
  Loader2, Settings, Eye, EyeOff, History, ScanText, FileText,
} from 'lucide-react';
import RelatedTools, { type RelatedTool } from '../components/RelatedTools';

const RELATED_TOOLS: RelatedTool[] = [
  { to: '/document-parser', label: 'Extraction de documents', icon: ScanText },
  { to: '/documents', label: 'Génération de documents', icon: FileText },
];

interface EmailIntake {
  id: string;
  email: string;
  imapHost: string;
  imapPort: number;
  username: string;
  protocol: 'IMAP' | 'POP3';
  sslEnabled: boolean;
  folder: string;
  autoImport: boolean;
  deleteAfterImport: boolean;
  targetDocumentType: string;
  isActive: boolean;
  lastCheckAt?: string;
  lastError?: string;
  createdAt: string;
  updatedAt: string;
}

interface EmailIntakePayload {
  email: string;
  imapHost: string;
  imapPort: number;
  username: string;
  password?: string;
  folder: string;
  protocol: 'IMAP' | 'POP3';
  sslEnabled: boolean;
  autoImport: boolean;
  targetDocumentType: string;
  deleteAfterImport: boolean;
  isActive: boolean;
}

interface EmailIntakeLog {
  id: string;
  emailIntakeId: string;
  status: string;
  message: string;
  processedCount: number;
  errorCount: number;
  startedAt: string;
  completedAt?: string;
}

const protocolColors: Record<string, string> = {
  IMAP: 'bg-accent-soft text-accent-strong',
  POP3: 'bg-accent-soft text-accent-strong',
};

const logStatusColors: Record<string, string> = {
  SUCCESS: 'bg-success/10 text-success',
  PARTIAL: 'bg-warning/10 text-warning',
  FAILED: 'bg-danger/10 text-danger',
  RUNNING: 'bg-accent-soft text-accent-strong',
};

const logStatusLabels: Record<string, string> = {
  SUCCESS: 'Succès',
  PARTIAL: 'Partiel',
  FAILED: 'Échoué',
  RUNNING: 'En cours',
};

const DOCUMENT_TYPES = [
  { value: 'SHIPMENT_ORDER', label: 'Ordre d\'expédition' },
  { value: 'INVOICE', label: 'Facture' },
  { value: 'PACKING_LIST', label: 'Liste de colisage' },
  { value: 'BILL_OF_LADING', label: 'Connaissement' },
  { value: 'CUSTOMS_DOC', label: 'Document douanier' },
  { value: 'CERTIFICATE', label: 'Certificat' },
  { value: 'OTHER', label: 'Autre' },
];

const EmailIntake = () => {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const [formEmail, setFormEmail] = useState('');
  const [formHost, setFormHost] = useState('');
  const [formPort, setFormPort] = useState('993');
  const [formUsername, setFormUsername] = useState('');
  const [formPassword, setFormPassword] = useState('');
  const [formFolder, setFormFolder] = useState('INBOX');
  const [formProtocol, setFormProtocol] = useState<'IMAP' | 'POP3'>('IMAP');
  const [formSsl, setFormSsl] = useState(true);
  const [formAutoImport, setFormAutoImport] = useState(false);
  const [formDocType, setFormDocType] = useState('SHIPMENT_ORDER');
  const [formDeleteAfter, setFormDeleteAfter] = useState(false);
  const [formActive, setFormActive] = useState(true);

  const { data: intakeData, isLoading } = useQuery({
    queryKey: ['email-intake'],
    queryFn: async () => {
      const res = await incokalkAPI.emailIntake.list();
      return (res.data as EmailIntake[]) || [];
    },
  });

  const { data: logsData } = useQuery({
    queryKey: ['email-intake-logs'],
    queryFn: async () => {
      const res = await incokalkAPI.emailIntake.logs();
      return (res.data as EmailIntakeLog[]) || [];
    },
    refetchInterval: 15000,
  });

  const intakes: EmailIntake[] = intakeData ?? [];
  const syncLogs: EmailIntakeLog[] = logsData ?? [];

  const createMutation = useMutation({
    mutationFn: (data: EmailIntakePayload) => incokalkAPI.emailIntake.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-intake'] });
      toast.success('Boîte email configurée avec succès');
      resetForm();
    },
    onError: () => toast.error('Erreur lors de la configuration'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<EmailIntakePayload> }) => incokalkAPI.emailIntake.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-intake'] });
      toast.success('Configuration mise à jour');
      resetForm();
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.emailIntake.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-intake'] });
      toast.success('Boîte email supprimée');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.emailIntake.test(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-intake'] });
      queryClient.invalidateQueries({ queryKey: ['email-intake-logs'] });
      toast.success('Connexion testée avec succès');
      setTestingId(null);
    },
    onError: () => {
      toast.error('Échec du test de connexion');
      setTestingId(null);
    },
  });

  const toggleActiveMutation = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) =>
      incokalkAPI.emailIntake.update(id, { isActive }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['email-intake'] });
      toast.success('Statut mis à jour');
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const resetForm = () => {
    setShowForm(false);
    setEditingId(null);
    setFormEmail('');
    setFormHost('');
    setFormPort('993');
    setFormUsername('');
    setFormPassword('');
    setFormFolder('INBOX');
    setFormProtocol('IMAP');
    setFormSsl(true);
    setFormAutoImport(false);
    setFormDocType('SHIPMENT_ORDER');
    setFormDeleteAfter(false);
    setFormActive(true);
    setShowPassword(false);
  };

  const openEditForm = (intake: EmailIntake) => {
    setEditingId(intake.id);
    setFormEmail(intake.email);
    setFormHost(intake.imapHost);
    setFormPort(String(intake.imapPort));
    setFormUsername(intake.username);
    setFormFolder(intake.folder);
    setFormProtocol(intake.protocol);
    setFormSsl(intake.sslEnabled);
    setFormAutoImport(intake.autoImport);
    setFormDocType(intake.targetDocumentType);
    setFormDeleteAfter(intake.deleteAfterImport);
    setFormActive(intake.isActive);
    setShowForm(true);
  };

  const handleSubmit = () => {
    if (!formEmail.trim() || !formHost.trim() || !formUsername.trim()) {
      toast.error('Veuillez remplir tous les champs obligatoires');
      return;
    }
    const payload = {
      email: formEmail.trim(),
      imapHost: formHost.trim(),
      imapPort: parseInt(formPort, 10) || 993,
      username: formUsername.trim(),
      password: formPassword || undefined,
      folder: formFolder.trim(),
      protocol: formProtocol,
      sslEnabled: formSsl,
      autoImport: formAutoImport,
      targetDocumentType: formDocType,
      deleteAfterImport: formDeleteAfter,
      isActive: formActive,
    };
    if (editingId) {
      updateMutation.mutate({ id: editingId, data: payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const handleTest = (id: string) => {
    setTestingId(id);
    testMutation.mutate(id);
  };

  const formatDate = (date?: string) => {
    if (!date) return '—';
    return new Date(date).toLocaleString('fr-FR');
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement des boîtes email...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-4xl font-bold text-ink mb-2">Import Email</h1>
            <p className="text-ink-soft">Configurez les boîtes email surveillées pour l'import automatique</p>
          </div>
          <button
            onClick={() => { resetForm(); setShowForm(true); }}
            className="flex items-center space-x-2 px-4 py-2.5 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors font-medium"
          >
            <Plus size={18} />
            <span>Ajouter une boîte</span>
          </button>
        </div>

        <RelatedTools tools={RELATED_TOOLS} />

        {/* Email Intake Cards */}
        {intakes.length > 0 ? (
          <div className="grid md:grid-cols-2 gap-6 mb-10">
            {intakes.map((intake) => (
              <div
                key={intake.id}
                className="bg-surface rounded-lg shadow-lg border-2 border-line hover:shadow-xl transition-shadow overflow-hidden"
              >
                <div className="px-6 py-5 bg-accent-soft flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="w-12 h-12 bg-accent-soft rounded-xl flex items-center justify-center">
                      <Mail className="h-6 w-6 text-accent" />
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-ink">{intake.email}</h3>
                      <p className="text-sm text-ink-soft">
                        {intake.imapHost}:{intake.imapPort}
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={() => toggleActiveMutation.mutate({ id: intake.id, isActive: !intake.isActive })}
                    disabled={toggleActiveMutation.isPending}
                    className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                      intake.isActive ? 'bg-success' : 'bg-line'
                    }`}
                  >
                    <span
                      className={`inline-block h-4 w-4 transform rounded-full bg-surface transition-transform ${
                        intake.isActive ? 'translate-x-6' : 'translate-x-1'
                      }`}
                    />
                  </button>
                </div>

                <div className="px-6 py-4 space-y-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-soft">Dossier</span>
                    <span className="font-medium text-ink-soft">{intake.folder}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-soft">Protocole</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${protocolColors[intake.protocol]}`}>
                      {intake.protocol}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-soft">SSL</span>
                    {intake.sslEnabled ? (
                      <span className="flex items-center space-x-1 text-success">
                        <CheckCircle size={14} />
                        <span>Activé</span>
                      </span>
                    ) : (
                      <span className="flex items-center space-x-1 text-ink-soft">
                        <XCircle size={14} />
                        <span>Désactivé</span>
                      </span>
                    )}
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-soft">Import auto.</span>
                    {intake.autoImport ? (
                      <span className="flex items-center space-x-1 text-success">
                        <CheckCircle size={14} />
                        <span>Activé</span>
                      </span>
                    ) : (
                      <span className="flex items-center space-x-1 text-ink-soft">
                        <XCircle size={14} />
                        <span>Désactivé</span>
                      </span>
                    )}
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-soft">Type de document</span>
                    <span className="font-medium text-ink-soft">
                      {DOCUMENT_TYPES.find((d) => d.value === intake.targetDocumentType)?.label || intake.targetDocumentType}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-soft">Dernière vérification</span>
                    <span className="text-ink-soft">{formatDate(intake.lastCheckAt)}</span>
                  </div>
                  {intake.lastError && (
                    <div className="flex items-start space-x-1 text-xs text-danger bg-danger/10 p-2 rounded-lg">
                      <AlertTriangle size={12} className="mt-0.5 flex-shrink-0" />
                      <span>{intake.lastError}</span>
                    </div>
                  )}
                </div>

                <div className="px-6 py-4 border-t border-line flex items-center space-x-2">
                  <button
                    onClick={() => handleTest(intake.id)}
                    disabled={testingId === intake.id}
                    className="flex-1 px-3 py-2 bg-accent-soft text-accent-strong rounded-lg hover:bg-accent-soft transition-colors text-sm font-medium flex items-center justify-center space-x-1.5"
                  >
                    {testingId === intake.id ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <RefreshCw size={14} />
                    )}
                    <span>Tester</span>
                  </button>
                  <button
                    onClick={() => openEditForm(intake)}
                    className="px-3 py-2 bg-surface-2 text-ink-soft rounded-lg hover:bg-line transition-colors text-sm font-medium"
                    title="Modifier"
                  >
                    <Settings size={14} />
                  </button>
                  <button
                    onClick={() => setDeleteId(intake.id)}
                    className="px-3 py-2 bg-danger/10 text-danger rounded-lg hover:bg-danger/10 transition-colors text-sm font-medium"
                    title="Supprimer"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="bg-surface rounded-lg shadow-lg p-12 mb-10 text-center">
            <Inbox className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink-soft mb-2">Aucune boîte email configurée</h3>
            <p className="text-ink-soft mb-6">
              Ajoutez une boîte email pour surveiller et importer automatiquement les documents d'expédition.
            </p>
            <button
              onClick={() => { resetForm(); setShowForm(true); }}
              className="inline-flex items-center space-x-2 px-4 py-2.5 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors font-medium"
            >
              <Plus size={18} />
              <span>Ajouter une boîte</span>
            </button>
          </div>
        )}

        {/* Sync Logs */}
        {syncLogs.length > 0 && (
          <div className="bg-surface rounded-lg shadow-lg overflow-hidden mb-10">
            <div className="px-6 py-4 border-b border-line flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <History size={20} className="text-ink-soft" />
                <h2 className="text-lg font-bold text-ink">Activité d'import récente</h2>
              </div>
              <button
                onClick={() => queryClient.invalidateQueries({ queryKey: ['email-intake-logs'] })}
                className="p-1.5 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-lg transition-colors"
                title="Rafraîchir"
              >
                <RefreshCw size={16} />
              </button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-surface-2 text-left">
                    <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Statut</th>
                    <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Message</th>
                    <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Traités/Erreurs</th>
                    <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Début</th>
                    <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Fin</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {syncLogs.slice(0, 50).map((log) => (
                    <tr key={log.id} className="hover:bg-surface-2 transition-colors">
                      <td className="px-4 py-3">
                        <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${logStatusColors[log.status] || 'bg-surface-2 text-ink'}`}>
                          {logStatusLabels[log.status] || log.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-ink-soft">{log.message}</td>
                      <td className="px-4 py-3 text-sm text-ink-soft">
                        {log.processedCount}/{log.errorCount}
                        {log.errorCount > 0 && (
                          <span className="ml-2 text-danger text-xs">({log.errorCount} erreur(s))</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-ink-soft">{formatDate(log.startedAt)}</td>
                      <td className="px-4 py-3 text-sm text-ink-soft">{formatDate(log.completedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Add/Edit Form Modal */}
        {showForm && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-lg shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
              <div className="p-6">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-xl font-bold text-ink">
                    {editingId ? 'Modifier la boîte email' : 'Ajouter une boîte email'}
                  </h2>
                  <button
                    onClick={resetForm}
                    className="p-1.5 text-ink-soft hover:text-ink hover:bg-surface-2 rounded-lg transition-colors"
                  >
                    <XCircle size={18} />
                  </button>
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Adresse email *</label>
                    <input
                      type="email"
                      value={formEmail}
                      onChange={(e) => setFormEmail(e.target.value)}
                      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="incoming@exemple.com"
                    />
                  </div>

                  <div className="grid grid-cols-3 gap-3">
                    <div className="col-span-2">
                      <label className="block text-sm font-medium text-ink-soft mb-1">Hôte IMAP *</label>
                      <input
                        type="text"
                        value={formHost}
                        onChange={(e) => setFormHost(e.target.value)}
                        className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder="imap.exemple.com"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">Port</label>
                      <input
                        type="number"
                        value={formPort}
                        onChange={(e) => setFormPort(e.target.value)}
                        className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">Utilisateur *</label>
                      <input
                        type="text"
                        value={formUsername}
                        onChange={(e) => setFormUsername(e.target.value)}
                        className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder="incoming@exemple.com"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">
                        {editingId ? 'Mot de passe (laisser vide pour conserver)' : 'Mot de passe'}
                      </label>
                      <div className="relative">
                        <input
                          type={showPassword ? 'text' : 'password'}
                          value={formPassword}
                          onChange={(e) => setFormPassword(e.target.value)}
                          className="w-full border border-line rounded-lg px-3 pr-10 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                          placeholder="••••••••"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink"
                        >
                          {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Dossier</label>
                    <input
                      type="text"
                      value={formFolder}
                      onChange={(e) => setFormFolder(e.target.value)}
                      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="INBOX"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">Protocole</label>
                      <select
                        value={formProtocol}
                        onChange={(e) => setFormProtocol(e.target.value as 'IMAP' | 'POP3')}
                        className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      >
                        <option value="IMAP">IMAP</option>
                        <option value="POP3">POP3</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">Type de document</label>
                      <select
                        value={formDocType}
                        onChange={(e) => setFormDocType(e.target.value)}
                        className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      >
                        {DOCUMENT_TYPES.map((dt) => (
                          <option key={dt.value} value={dt.value}>{dt.label}</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <label className="flex items-center justify-between">
                      <span className="text-sm font-medium text-ink-soft">SSL activé</span>
                      <button
                        type="button"
                        onClick={() => setFormSsl(!formSsl)}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          formSsl ? 'bg-accent' : 'bg-line'
                        }`}
                      >
                        <span
                          className={`inline-block h-3.5 w-3.5 transform rounded-full bg-surface transition-transform ${
                            formSsl ? 'translate-x-4.5' : 'translate-x-1'
                          }`}
                        />
                      </button>
                    </label>

                    <label className="flex items-center justify-between">
                      <span className="text-sm font-medium text-ink-soft">Import automatique</span>
                      <button
                        type="button"
                        onClick={() => setFormAutoImport(!formAutoImport)}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          formAutoImport ? 'bg-accent' : 'bg-line'
                        }`}
                      >
                        <span
                          className={`inline-block h-3.5 w-3.5 transform rounded-full bg-surface transition-transform ${
                            formAutoImport ? 'translate-x-4.5' : 'translate-x-1'
                          }`}
                        />
                      </button>
                    </label>

                    <label className="flex items-center justify-between">
                      <span className="text-sm font-medium text-ink-soft">Supprimer après import</span>
                      <button
                        type="button"
                        onClick={() => setFormDeleteAfter(!formDeleteAfter)}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          formDeleteAfter ? 'bg-accent' : 'bg-line'
                        }`}
                      >
                        <span
                          className={`inline-block h-3.5 w-3.5 transform rounded-full bg-surface transition-transform ${
                            formDeleteAfter ? 'translate-x-4.5' : 'translate-x-1'
                          }`}
                        />
                      </button>
                    </label>

                    <label className="flex items-center justify-between">
                      <span className="text-sm font-medium text-ink-soft">Actif</span>
                      <button
                        type="button"
                        onClick={() => setFormActive(!formActive)}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          formActive ? 'bg-accent' : 'bg-line'
                        }`}
                      >
                        <span
                          className={`inline-block h-3.5 w-3.5 transform rounded-full bg-surface transition-transform ${
                            formActive ? 'translate-x-4.5' : 'translate-x-1'
                          }`}
                        />
                      </button>
                    </label>
                  </div>
                </div>

                <div className="flex justify-end space-x-3 pt-6">
                  <button
                    onClick={resetForm}
                    className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
                  >
                    Annuler
                  </button>
                  <button
                    onClick={handleSubmit}
                    disabled={createMutation.isPending || updateMutation.isPending}
                    className="px-4 py-2 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2"
                  >
                    {(createMutation.isPending || updateMutation.isPending) && (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    )}
                    <span>{editingId ? 'Mettre à jour' : 'Sauvegarder'}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Delete Confirmation */}
        {deleteId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4 p-6">
              <div className="flex items-center space-x-3 mb-4">
                <div className="w-10 h-10 bg-danger/10 rounded-full flex items-center justify-center">
                  <AlertTriangle className="h-5 w-5 text-danger" />
                </div>
                <h3 className="text-lg font-bold text-ink">Confirmer la suppression</h3>
              </div>
              <p className="text-ink-soft mb-6">
                Êtes-vous sûr de vouloir supprimer cette boîte email ? L'import automatique des documents sera interrompu.
              </p>
              <div className="flex justify-end space-x-3">
                <button
                  onClick={() => setDeleteId(null)}
                  className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
                >
                  Annuler
                </button>
                <button
                  onClick={() => deleteMutation.mutate(deleteId)}
                  disabled={deleteMutation.isPending}
                  className="px-4 py-2 bg-danger text-white rounded-lg hover:bg-danger/90 transition-colors disabled:opacity-50 flex items-center space-x-2"
                >
                  {deleteMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>Supprimer</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default EmailIntake;
