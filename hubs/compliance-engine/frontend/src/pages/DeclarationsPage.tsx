import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FileText,
  Plus,
  Trash2,
  Send,
  CheckCircle,
  Loader2,
  Package,
  Ship,
  Plane,
  ArrowRight,
  Clock,
  X,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI, downloadAuthedFile } from '../lib/api';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

// --- Types ---

interface DauDeclaration {
  id: string;
  declarationNumber: string;
  declarationType: string;
  status: string;
  customsOffice: string;
  customsRegime: string;
  declaredValue: number;
  currency: string;
  originCountry: string;
  destinationCountry: string;
  hsCode: string;
  goodsDescription: string;
  netWeight: number;
  grossWeight: number;
  packages: number;
  submittedAt: string;
  clearedAt: string;
  rejectedAt: string;
  rejectionReason: string;
  notes: string;
  createdAt: string;
}

interface DebDeclaration {
  id: string;
  declarationNumber: string;
  declarationType: string;
  period: string;
  status: string;
  partnerCountry: string;
  natureOfTransaction: string;
  modeOfTransport: string;
  netMass: number;
  statisticalValue: number;
  hsCode8: string;
  goodsDescription: string;
  submittedAt: string;
  createdAt: string;
}

interface Ics2Declaration {
  id: string;
  declarationNumber: string;
  status: string;
  senderEori: string;
  receiverEori: string;
  vesselName: string;
  voyageNumber: string;
  containerNumber: string;
  hsCode6: string;
  goodsDescription: string;
  grossWeight: number;
  packagesCount: number;
  submittedAt: string;
  respondedAt: string;
  responseMessage: string;
  createdAt: string;
}

interface ExportDeclaration {
  id: string;
  declarationNumber: string;
  declarationType: string;
  status: string;
  exporterEori: string;
  destinationCountry: string;
  goodsDescription: string;
  hsCode: string;
  declaredValue: number;
  currency: string;
  netWeight: number;
  grossWeight: number;
  packagesCount: number;
  submittedAt: string;
  validatedAt: string;
  rejectedAt: string;
  rejectionReason: string;
  createdAt: string;
}

interface DeclarationStats {
  total: number;
  draft: number;
  submitted: number;
  cleared: number;
  rejected: number;
}

// --- Constants ---

type Tab = 'dau' | 'deb' | 'ics2' | 'export';

const TABS: { key: Tab; label: string; icon: typeof FileText }[] = [
  { key: 'dau', label: 'DAU', icon: FileText },
  { key: 'deb', label: 'DEB', icon: Package },
  { key: 'ics2', label: 'ICS2', icon: Ship },
  { key: 'export', label: 'Export', icon: Plane },
];

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-2 text-ink',
  SUBMITTED: 'bg-accent-soft text-accent-strong',
  UNDER_REVIEW: 'bg-warning/10 text-warning',
  CLEARED: 'bg-success/10 text-success',
  RELEASED: 'bg-success/10 text-success',
  REJECTED: 'bg-danger/10 text-danger',
  SENT: 'bg-accent-soft text-accent-strong',
  ACCEPTED: 'bg-success/10 text-success',
  PENDING: 'bg-warning/10 text-warning',
};

const DAU_TYPE_STYLES: Record<string, string> = {
  DAU_IMPORT: 'bg-accent-soft text-accent-strong',
  DAU_EXPORT: 'bg-accent-soft text-accent-strong',
  TRANSIT_T1: 'bg-warning/10 text-warning',
  TRANSIT_T2: 'bg-warning/10 text-warning',
};

const DEB_TYPE_STYLES: Record<string, string> = {
  DEB_EXPEDITION: 'bg-success/10 text-success',
  DEB_INTRODUCTION: 'bg-accent-soft text-accent-strong',
  INTRASTAT_ARRIVAL: 'bg-success/10 text-success',
  INTRASTAT_DEPARTURE: 'bg-warning/10 text-warning',
};

const EXPORT_TYPE_STYLES: Record<string, string> = {
  AES: 'bg-accent-soft text-accent-strong',
  EXS: 'bg-accent-soft text-accent-strong',
};

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'Soumis',
  UNDER_REVIEW: 'En cours d\'examen',
  CLEARED: 'Dédouané',
  RELEASED: 'Livré',
  REJECTED: 'Rejeté',
  SENT: 'Envoyé',
  ACCEPTED: 'Accepté',
  PENDING: 'En attente',
};

const TYPE_LABELS: Record<string, string> = {
  DAU_IMPORT: 'Import',
  DAU_EXPORT: 'Export',
  TRANSIT_T1: 'Transit T1',
  TRANSIT_T2: 'Transit T2',
  DEB_EXPEDITION: 'Expédition',
  DEB_INTRODUCTION: 'Introduction',
  INTRASTAT_ARRIVAL: 'Intrastat arrivée',
  INTRASTAT_DEPARTURE: 'Intrastat départ',
  AES: 'AES',
  EXS: 'EXS',
};

const COUNTRY_NAMES: Record<string, string> = {
  FR: 'France',
  DE: 'Allemagne',
  ES: 'Espagne',
  IT: 'Italie',
  NL: 'Pays-Bas',
  BE: 'Belgique',
  PL: 'Pologne',
  PT: 'Portugal',
  AT: 'Autriche',
  IE: 'Irlande',
  RO: 'Roumanie',
  BG: 'Bulgarie',
  CZ: 'Tchéquie',
  DK: 'Danemark',
  FI: 'Finlande',
  GR: 'Grèce',
  HU: 'Hongrie',
  SE: 'Suède',
  HR: 'Croatie',
  SK: 'Slovaquie',
  SI: 'Slovénie',
  LT: 'Lituanie',
  LV: 'Lettonie',
  EE: 'Estonie',
  LU: 'Luxembourg',
  MT: 'Malte',
  CY: 'Chypre',
  GB: 'Royaume-Uni',
  CH: 'Suisse',
  NO: 'Norvège',
  US: 'États-Unis',
  CN: 'Chine',
  JP: 'Japon',
  KR: 'Corée du Sud',
  IN: 'Inde',
  TR: 'Turquie',
  MA: 'Maroc',
  TN: 'Tunisie',
  DZ: 'Algérie',
  VN: 'Viêt Nam',
  TH: 'Thaïlande',
  SG: 'Singapour',
  HK: 'Hong Kong',
  TW: 'Taïwan',
  AU: 'Australie',
  CA: 'Canada',
  MX: 'Mexique',
  BR: 'Brésil',
  AR: 'Argentine',
  CL: 'Chili',
  CO: 'Colombie',
  PE: 'Pérou',
  ZA: 'Afrique du Sud',
  NG: 'Nigeria',
  EG: 'Égypte',
  SA: 'Arabie Saoudite',
  AE: 'Émirats arabes unis',
  QA: 'Qatar',
  KW: 'Koweït',
  IL: 'Israël',
  PH: 'Philippines',
  ID: 'Indonésie',
  MY: 'Malaisie',
  PK: 'Pakistan',
  BD: 'Bangladesh',
  LK: 'Sri Lanka',
};

const formatCountry = (code: string) => COUNTRY_NAMES[code] || code;

const formatCurrency = (value: number, currency: string) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: currency || 'EUR' }).format(value);

const formatWeight = (kg: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'decimal', maximumFractionDigits: 2 }).format(kg) + ' kg';

const formatDate = (d: string) => (d ? new Date(d).toLocaleDateString('fr-FR') : '—');

const formatEnum = (value: string) => TYPE_LABELS[value] || value;

const formatStatus = (status: string) => STATUS_LABELS[status] || status;

const getStatusColor = (status: string) => STATUS_STYLES[status] || 'bg-surface-2 text-ink-soft';

// --- Badge Components ---

const StatusBadge = ({ status }: { status: string }) => (
  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[status] || 'bg-surface-2 text-ink-soft'}`}>
    {STATUS_LABELS[status] || status}
  </span>
);

const TypeBadge = ({ type, styles }: { type: string; styles: Record<string, string> }) => (
  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${styles[type] || 'bg-surface-2 text-ink-soft'}`}>
    {TYPE_LABELS[type] || type}
  </span>
);

// --- Empty State ---

const EmptyState = ({ icon: Icon, message }: { icon: typeof FileText; message: string }) => (
  <div className="px-6 py-12 text-center text-ink-soft">
    <Icon size={32} className="mx-auto mb-3 text-ink-soft" />
    <p>{message}</p>
  </div>
);

// --- Loading ---

const LoadingSpinner = () => (
  <div className="px-6 py-12 text-center text-ink-soft">
    <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
    Chargement...
  </div>
);

// --- Stats Card ---

const StatCard = ({ label, value, icon: Icon, color }: { label: string; value: number | string; icon: typeof FileText; color: string }) => (
  <div className="bg-surface rounded-none border border-line p-5">
    <div className="flex items-center gap-3">
      <div className={`w-10 h-10 rounded-none ${color} flex items-center justify-center`}>
        <Icon size={20} />
      </div>
      <div>
        <p className="text-sm text-ink-soft">{label}</p>
        <p className="text-2xl font-bold text-ink">{value}</p>
      </div>
    </div>
  </div>
);

// --- DAU Form ---

interface DeclarationDetail {
  id: string;
  status: string;
  createdAt: string;
  declarationNumber?: string;
  declarationType?: string;
  hsCode6?: string;
  hsCode8?: string;
  hsCode?: string;
  customsOffice?: string;
  customsRegime?: string;
  customsCode?: string;
  declaredValue?: number;
  currency?: string;
  originCountry?: string;
  destinationCountry?: string;
  partnerCountry?: string;
  period?: string;
  netWeight?: number;
  grossWeight?: number;
  netMass?: number;
  statisticalValue?: number;
  packagesCount?: number;
  senderEori?: string;
  receiverEori?: string;
  exporterEori?: string;
  vesselName?: string;
  voyageNumber?: string;
  containerNumber?: string;
  natureOfTransaction?: string;
  modeOfTransport?: string;
  goodsDescription?: string;
  notes?: string;
  submittedAt?: string;
  clearedAt?: string;
  validatedAt?: string;
  rejectedAt?: string;
  respondedAt?: string;
}

interface DauFormProps {
  onSubmit: (data: Record<string, unknown>) => void;
  isPending: boolean;
  onClose: () => void;
}

const DauForm = ({ onSubmit, isPending, onClose }: DauFormProps) => {
  const [form, setForm] = useState({
    declarationType: 'DAU_IMPORT',
    customsOffice: '',
    customsRegime: '',
    declaredValue: '',
    currency: 'EUR',
    originCountry: '',
    destinationCountry: '',
    hsCode: '',
    goodsDescription: '',
    netWeight: '',
    grossWeight: '',
    packages: '',
    notes: '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      ...form,
      declaredValue: parseFloat(form.declaredValue) || 0,
      netWeight: parseFloat(form.netWeight) || 0,
      grossWeight: parseFloat(form.grossWeight) || 0,
      packages: parseInt(form.packages) || 0,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Type de déclaration</label>
          <select
            value={form.declarationType}
            onChange={(e) => setForm({ ...form, declarationType: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          >
            <option value="DAU_IMPORT">Import</option>
            <option value="DAU_EXPORT">Export</option>
            <option value="TRANSIT_T1">Transit T1</option>
            <option value="TRANSIT_T2">Transit T2</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Bureau douanier</label>
          <input
            type="text"
            value={form.customsOffice}
            onChange={(e) => setForm({ ...form, customsOffice: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: Paris-CDG"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Régime douanier</label>
          <input
            type="text"
            value={form.customsRegime}
            onChange={(e) => setForm({ ...form, customsRegime: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: 4000"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Valeur déclarée</label>
          <input
            type="number"
            value={form.declaredValue}
            onChange={(e) => setForm({ ...form, declaredValue: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Pays d'origine</label>
          <input
            type="text"
            value={form.originCountry}
            onChange={(e) => setForm({ ...form, originCountry: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Code pays (ex: CN)"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Pays de destination</label>
          <input
            type="text"
            value={form.destinationCountry}
            onChange={(e) => setForm({ ...form, destinationCountry: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Code pays (ex: FR)"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Code SH</label>
          <input
            type="text"
            value={form.hsCode}
            onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: 8471.30"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Nombre de colis</label>
          <input
            type="number"
            value={form.packages}
            onChange={(e) => setForm({ ...form, packages: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Poids net (kg)</label>
          <input
            type="number"
            step="0.01"
            value={form.netWeight}
            onChange={(e) => setForm({ ...form, netWeight: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Poids brut (kg)</label>
          <input
            type="number"
            step="0.01"
            value={form.grossWeight}
            onChange={(e) => setForm({ ...form, grossWeight: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
          />
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-ink mb-1">Description des marchandises</label>
        <textarea
          value={form.goodsDescription}
          onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          rows={3}
          placeholder="Description détaillée..."
          required
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-ink mb-1">Notes</label>
        <textarea
          value={form.notes}
          onChange={(e) => setForm({ ...form, notes: e.target.value })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          rows={2}
          placeholder="Notes internes..."
        />
      </div>
      <div className="flex gap-3 pt-2">
        <button type="button" onClick={onClose} className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors">
          Annuler
        </button>
        <button type="submit" disabled={isPending} className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2">
          {isPending && <Loader2 size={14} className="animate-spin" />}
          Créer la déclaration
        </button>
      </div>
    </form>
  );
};

// --- DEB Form ---

const DebForm = ({ onSubmit, isPending, onClose }: DauFormProps) => {
  const [form, setForm] = useState({
    declarationType: 'DEB_EXPEDITION',
    period: '',
    partnerCountry: '',
    natureOfTransaction: '',
    modeOfTransport: '',
    netMass: '',
    statisticalValue: '',
    hsCode8: '',
    goodsDescription: '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      ...form,
      netMass: parseFloat(form.netMass) || 0,
      statisticalValue: parseFloat(form.statisticalValue) || 0,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Type</label>
          <select
            value={form.declarationType}
            onChange={(e) => setForm({ ...form, declarationType: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          >
            <option value="DEB_EXPEDITION">Expédition</option>
            <option value="DEB_INTRODUCTION">Introduction</option>
            <option value="INTRASTAT_ARRIVAL">Intrastat arrivée</option>
            <option value="INTRASTAT_DEPARTURE">Intrastat départ</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Période</label>
          <input
            type="month"
            value={form.period}
            onChange={(e) => setForm({ ...form, period: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Pays partenaire</label>
          <input
            type="text"
            value={form.partnerCountry}
            onChange={(e) => setForm({ ...form, partnerCountry: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Code pays (ex: DE)"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Nature de la transaction</label>
          <input
            type="text"
            value={form.natureOfTransaction}
            onChange={(e) => setForm({ ...form, natureOfTransaction: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: Achat-vente"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Mode de transport</label>
          <input
            type="text"
            value={form.modeOfTransport}
            onChange={(e) => setForm({ ...form, modeOfTransport: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: Route"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Valeur statistique (€)</label>
          <input
            type="number"
            value={form.statisticalValue}
            onChange={(e) => setForm({ ...form, statisticalValue: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Masse nette (kg)</label>
          <input
            type="number"
            step="0.01"
            value={form.netMass}
            onChange={(e) => setForm({ ...form, netMass: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Code SH à 8 chiffres</label>
          <input
            type="text"
            value={form.hsCode8}
            onChange={(e) => setForm({ ...form, hsCode8: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: 84713000"
            required
          />
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-ink mb-1">Description des marchandises</label>
        <textarea
          value={form.goodsDescription}
          onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          rows={3}
          placeholder="Description détaillée..."
          required
        />
      </div>
      <div className="flex gap-3 pt-2">
        <button type="button" onClick={onClose} className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors">
          Annuler
        </button>
        <button type="submit" disabled={isPending} className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2">
          {isPending && <Loader2 size={14} className="animate-spin" />}
          Créer la déclaration
        </button>
      </div>
    </form>
  );
};

// --- ICS2 Form ---

const Ics2Form = ({ onSubmit, isPending, onClose }: DauFormProps) => {
  const [form, setForm] = useState({
    senderEori: '',
    receiverEori: '',
    vesselName: '',
    voyageNumber: '',
    containerNumber: '',
    hsCode6: '',
    goodsDescription: '',
    grossWeight: '',
    packagesCount: '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      ...form,
      grossWeight: parseFloat(form.grossWeight) || 0,
      packagesCount: parseInt(form.packagesCount) || 0,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-ink mb-1">EORI expéditeur</label>
          <input
            type="text"
            value={form.senderEori}
            onChange={(e) => setForm({ ...form, senderEori: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: FR12345678900"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">EORI destinataire</label>
          <input
            type="text"
            value={form.receiverEori}
            onChange={(e) => setForm({ ...form, receiverEori: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: DE12345678900"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Nom du navire</label>
          <input
            type="text"
            value={form.vesselName}
            onChange={(e) => setForm({ ...form, vesselName: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: MAERSK SELETAR"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">N° de voyage</label>
          <input
            type="text"
            value={form.voyageNumber}
            onChange={(e) => setForm({ ...form, voyageNumber: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: VOY2026-001"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">N° de conteneur</label>
          <input
            type="text"
            value={form.containerNumber}
            onChange={(e) => setForm({ ...form, containerNumber: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: MSKU1234567"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Code SH à 6 chiffres</label>
          <input
            type="text"
            value={form.hsCode6}
            onChange={(e) => setForm({ ...form, hsCode6: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: 847130"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Poids brut (kg)</label>
          <input
            type="number"
            step="0.01"
            value={form.grossWeight}
            onChange={(e) => setForm({ ...form, grossWeight: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Nombre de colis</label>
          <input
            type="number"
            value={form.packagesCount}
            onChange={(e) => setForm({ ...form, packagesCount: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0"
          />
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-ink mb-1">Description des marchandises</label>
        <textarea
          value={form.goodsDescription}
          onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          rows={3}
          placeholder="Description détaillée..."
          required
        />
      </div>
      <div className="flex gap-3 pt-2">
        <button type="button" onClick={onClose} className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors">
          Annuler
        </button>
        <button type="submit" disabled={isPending} className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2">
          {isPending && <Loader2 size={14} className="animate-spin" />}
          Créer la déclaration
        </button>
      </div>
    </form>
  );
};

// --- Export Form ---

const ExportForm = ({ onSubmit, isPending, onClose }: DauFormProps) => {
  const [form, setForm] = useState({
    declarationType: 'AES',
    exporterEori: '',
    destinationCountry: '',
    hsCode: '',
    declaredValue: '',
    currency: 'EUR',
    netWeight: '',
    grossWeight: '',
    packagesCount: '',
    goodsDescription: '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      ...form,
      declaredValue: parseFloat(form.declaredValue) || 0,
      netWeight: parseFloat(form.netWeight) || 0,
      grossWeight: parseFloat(form.grossWeight) || 0,
      packagesCount: parseInt(form.packagesCount) || 0,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Type</label>
          <select
            value={form.declarationType}
            onChange={(e) => setForm({ ...form, declarationType: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          >
            <option value="AES">AES</option>
            <option value="EXS">EXS</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">EORI exportateur</label>
          <input
            type="text"
            value={form.exporterEori}
            onChange={(e) => setForm({ ...form, exporterEori: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: FR12345678900"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Pays de destination</label>
          <input
            type="text"
            value={form.destinationCountry}
            onChange={(e) => setForm({ ...form, destinationCountry: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Code pays (ex: US)"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Valeur déclarée (€)</label>
          <input
            type="number"
            value={form.declaredValue}
            onChange={(e) => setForm({ ...form, declaredValue: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Code SH</label>
          <input
            type="text"
            value={form.hsCode}
            onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="Ex: 8471.30"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Nombre de colis</label>
          <input
            type="number"
            value={form.packagesCount}
            onChange={(e) => setForm({ ...form, packagesCount: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Poids net (kg)</label>
          <input
            type="number"
            step="0.01"
            value={form.netWeight}
            onChange={(e) => setForm({ ...form, netWeight: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink mb-1">Poids brut (kg)</label>
          <input
            type="number"
            step="0.01"
            value={form.grossWeight}
            onChange={(e) => setForm({ ...form, grossWeight: e.target.value })}
            className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            placeholder="0.00"
          />
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-ink mb-1">Description des marchandises</label>
        <textarea
          value={form.goodsDescription}
          onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
          className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          rows={3}
          placeholder="Description détaillée..."
          required
        />
      </div>
      <div className="flex gap-3 pt-2">
        <button type="button" onClick={onClose} className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors">
          Annuler
        </button>
        <button type="submit" disabled={isPending} className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2">
          {isPending && <Loader2 size={14} className="animate-spin" />}
          Créer la déclaration
        </button>
      </div>
    </form>
  );
};

// --- Main Component ---

const DeclarationsPage = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<Tab>('dau');
  const [formOpen, setFormOpen] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [selectedDeclaration, setSelectedDeclaration] = useState<DeclarationDetail | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [dauPage, setDauPage] = useState(0);

  const openDetail = (declaration: DeclarationDetail) => {
    setSelectedDeclaration(declaration);
    setDetailModalOpen(true);
  };

  // --- DAU ---
  const { data: dauData, isLoading: dauLoading } = useQuery({
    queryKey: ['declarations-dau', dauPage],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.dau.getPage(dauPage, PAGE_SIZE);
      return res.data;
    },
    enabled: activeTab === 'dau',
  });
  const daus: DauDeclaration[] = Array.isArray(dauData) ? dauData : (dauData as { content?: DauDeclaration[] })?.content || [];
  const dauTotalPages: number = Array.isArray(dauData) ? 1 : ((dauData as { totalPages?: number })?.totalPages ?? 1);

  const { data: dauStats } = useQuery({
    queryKey: ['declarations-dau-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.dau.stats();
      return res.data as DeclarationStats;
    },
    enabled: activeTab === 'dau',
  });

  const createDauMutation = useMutation({
    mutationFn: (data: unknown) => incokalkAPI.declarations.dau.create(data),
    onSuccess: () => {
      toast.success('Déclaration DAU créée');
      setFormOpen(false);
      queryClient.invalidateQueries({ queryKey: ['declarations-dau'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-dau-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la création'),
  });

  const updateDauStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => incokalkAPI.declarations.dau.updateStatus(id, { status }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['declarations-dau'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-dau-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour'),
  });

  const deleteDauMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.declarations.dau.delete(id),
    onSuccess: () => {
      toast.success('Déclaration supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['declarations-dau'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-dau-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  // --- DEB ---
  const { data: debData, isLoading: debLoading } = useQuery({
    queryKey: ['declarations-deb'],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.debp.list();
      return res.data as DebDeclaration[] | { data: DebDeclaration[] };
    },
    enabled: activeTab === 'deb',
  });
  const debs: DebDeclaration[] = Array.isArray(debData) ? debData : (debData as { data?: DebDeclaration[] })?.data || [];

  const createDebMutation = useMutation({
    mutationFn: (data: unknown) => incokalkAPI.declarations.debp.create(data),
    onSuccess: () => {
      toast.success('Déclaration DEB créée');
      setFormOpen(false);
      queryClient.invalidateQueries({ queryKey: ['declarations-deb'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la création'),
  });

  const updateDebStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => incokalkAPI.declarations.debp.updateStatus(id, { status }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['declarations-deb'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour'),
  });

  const deleteDebMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.declarations.debp.delete(id),
    onSuccess: () => {
      toast.success('Déclaration supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['declarations-deb'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  // --- ICS2 ---
  const { data: ics2Data, isLoading: ics2Loading } = useQuery({
    queryKey: ['declarations-ics2'],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.ics2.list();
      return res.data as Ics2Declaration[] | { data: Ics2Declaration[] };
    },
    enabled: activeTab === 'ics2',
  });
  const ics2s: Ics2Declaration[] = Array.isArray(ics2Data) ? ics2Data : (ics2Data as { data?: Ics2Declaration[] })?.data || [];

  const { data: ics2Stats } = useQuery({
    queryKey: ['declarations-ics2-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.ics2.stats();
      return res.data as DeclarationStats;
    },
    enabled: activeTab === 'ics2',
  });

  const createIcs2Mutation = useMutation({
    mutationFn: (data: unknown) => incokalkAPI.declarations.ics2.create(data),
    onSuccess: () => {
      toast.success('Déclaration ICS2 créée');
      setFormOpen(false);
      queryClient.invalidateQueries({ queryKey: ['declarations-ics2'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-ics2-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la création'),
  });

  const updateIcs2StatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => incokalkAPI.declarations.ics2.updateStatus(id, { status }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['declarations-ics2'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-ics2-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour'),
  });

  const deleteIcs2Mutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.declarations.ics2.delete(id),
    onSuccess: () => {
      toast.success('Déclaration supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['declarations-ics2'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-ics2-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  // --- Export ---
  const { data: exportData, isLoading: exportLoading } = useQuery({
    queryKey: ['declarations-export'],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.exportd.list();
      return res.data as ExportDeclaration[] | { data: ExportDeclaration[] };
    },
    enabled: activeTab === 'export',
  });
  const exports: ExportDeclaration[] = Array.isArray(exportData) ? exportData : (exportData as { data?: ExportDeclaration[] })?.data || [];

  const { data: exportStats } = useQuery({
    queryKey: ['declarations-export-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.declarations.exportd.stats();
      return res.data as DeclarationStats;
    },
    enabled: activeTab === 'export',
  });

  const createExportMutation = useMutation({
    mutationFn: (data: unknown) => incokalkAPI.declarations.exportd.create(data),
    onSuccess: () => {
      toast.success('Déclaration d\'export créée');
      setFormOpen(false);
      queryClient.invalidateQueries({ queryKey: ['declarations-export'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-export-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la création'),
  });

  const updateExportStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => incokalkAPI.declarations.exportd.updateStatus(id, { status }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['declarations-export'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-export-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour'),
  });

  const deleteExportMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.declarations.exportd.delete(id),
    onSuccess: () => {
      toast.success('Déclaration supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['declarations-export'] });
      queryClient.invalidateQueries({ queryKey: ['declarations-export-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  // --- Helpers ---

  const getLoading = () => {
    if (activeTab === 'dau') return dauLoading;
    if (activeTab === 'deb') return debLoading;
    if (activeTab === 'ics2') return ics2Loading;
    return exportLoading;
  };

  const getNextStatus = (currentStatus: string): string | null => {
    const transitions: Record<string, string> = {
      DRAFT: 'SUBMITTED',
      SUBMITTED: 'UNDER_REVIEW',
      UNDER_REVIEW: 'CLEARED',
    };
    return transitions[currentStatus] || null;
  };

  const getNextStatusLabel = (currentStatus: string): string => {
    const labels: Record<string, string> = {
      DRAFT: 'Soumettre',
      SUBMITTED: 'Examiner',
      UNDER_REVIEW: 'Dédouaner',
    };
    return labels[currentStatus] || '';
  };

  const getDebNextStatus = (currentStatus: string): string | null => {
    const transitions: Record<string, string> = {
      DRAFT: 'VALIDATED',
      VALIDATED: 'SUBMITTED',
    };
    return transitions[currentStatus] || null;
  };

  const getDebNextStatusLabel = (currentStatus: string): string => {
    const labels: Record<string, string> = {
      DRAFT: 'Valider',
      VALIDATED: 'Soumettre',
    };
    return labels[currentStatus] || '';
  };

  const getExportNextStatus = (currentStatus: string): string | null => {
    const transitions: Record<string, string> = {
      DRAFT: 'SUBMITTED',
      SUBMITTED: 'ACCEPTED',
    };
    return transitions[currentStatus] || null;
  };

  const getExportNextStatusLabel = (currentStatus: string): string => {
    const labels: Record<string, string> = {
      DRAFT: 'Soumettre',
      SUBMITTED: 'Valider',
    };
    return labels[currentStatus] || '';
  };

  const handleStatusUpdate = (id: string, currentStatus: string) => {
    if (activeTab === 'dau') {
      const next = getNextStatus(currentStatus);
      if (next) updateDauStatusMutation.mutate({ id, status: next });
    } else if (activeTab === 'deb') {
      const next = getDebNextStatus(currentStatus);
      if (next) updateDebStatusMutation.mutate({ id, status: next });
    } else if (activeTab === 'ics2') {
      const next = getNextStatus(currentStatus);
      if (next) updateIcs2StatusMutation.mutate({ id, status: next });
    } else if (activeTab === 'export') {
      const next = getExportNextStatus(currentStatus);
      if (next) updateExportStatusMutation.mutate({ id, status: next });
    }
  };

  const handleDelete = (id: string) => {
    if (activeTab === 'dau') deleteDauMutation.mutate(id);
    else if (activeTab === 'deb') deleteDebMutation.mutate(id);
    else if (activeTab === 'ics2') deleteIcs2Mutation.mutate(id);
    else deleteExportMutation.mutate(id);
  };

  const handleCreate = (data: Record<string, unknown>) => {
    if (activeTab === 'dau') createDauMutation.mutate(data);
    else if (activeTab === 'deb') createDebMutation.mutate(data);
    else if (activeTab === 'ics2') createIcs2Mutation.mutate(data);
    else createExportMutation.mutate(data);
  };


  const tabCounts: Record<Tab, number> = {
    dau: daus.length,
    deb: debs.length,
    ics2: ics2s.length,
    export: exports.length,
  };

  const stats = activeTab === 'dau' ? dauStats : activeTab === 'ics2' ? ics2Stats : activeTab === 'export' ? exportStats : null;

  const renderForm = () => {
    if (activeTab === 'dau') return <DauForm onSubmit={handleCreate} isPending={createDauMutation.isPending} onClose={() => setFormOpen(false)} />;
    if (activeTab === 'deb') return <DebForm onSubmit={handleCreate} isPending={createDebMutation.isPending} onClose={() => setFormOpen(false)} />;
    if (activeTab === 'ics2') return <Ics2Form onSubmit={handleCreate} isPending={createIcs2Mutation.isPending} onClose={() => setFormOpen(false)} />;
    return <ExportForm onSubmit={handleCreate} isPending={createExportMutation.isPending} onClose={() => setFormOpen(false)} />;
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Déclarations en douane
          </h1>
          <p className="text-ink-soft mt-1">DAU, DEB/Intrastat, ICS2 et déclarations d'export</p>
        </div>
        <button
          onClick={() => setFormOpen(true)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Nouvelle déclaration
        </button>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-line mb-6 overflow-x-auto">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              onClick={() => {
                setActiveTab(tab.key);
                setFormOpen(false);
                setDeleteConfirm(null);
              }}
              className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                isActive
                  ? 'border-accent/40 text-accent'
                  : 'border-transparent text-ink-soft hover:text-ink hover:border-line'
              }`}
            >
              <Icon size={16} />
              {tab.label}
              <span className={`ml-1 px-2 py-0.5 rounded-full text-xs ${isActive ? 'bg-accent-soft text-accent-strong' : 'bg-surface-2 text-ink-soft'}`}>
                {tabCounts[tab.key]}
              </span>
            </button>
          );
        })}
      </div>

      {/* Stats cards */}
      {stats && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          <StatCard
            label="Total"
            value={stats.total ?? '—'}
            icon={FileText}
            color="bg-accent-soft"
          />
          <StatCard
            label="Brouillons"
            value={stats.draft ?? '—'}
            icon={Clock}
            color="bg-bg"
          />
          <StatCard
            label="Soumis"
            value={stats.submitted ?? '—'}
            icon={Send}
            color="bg-warning/10"
          />
          <StatCard
            label="Dédouanés"
            value={stats.cleared ?? '—'}
            icon={CheckCircle}
            color="bg-success/10"
          />
        </div>
      )}

      {/* Table */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">
            {activeTab === 'dau' && 'Documents Administratifs Uniques'}
            {activeTab === 'deb' && 'Déclarations d\'Échanges de Biens'}
            {activeTab === 'ics2' && 'Import Control System 2'}
            {activeTab === 'export' && 'Déclarations d\'export (AES/EXS)'}
          </h2>
        </div>

        {getLoading() ? (
          <LoadingSpinner />
        ) : activeTab === 'dau' && daus.length === 0 ? (
          <EmptyState icon={FileText} message="Aucune déclaration DAU" />
        ) : activeTab === 'deb' && debs.length === 0 ? (
          <EmptyState icon={Package} message="Aucune déclaration DEB" />
        ) : activeTab === 'ics2' && ics2s.length === 0 ? (
          <EmptyState icon={Ship} message="Aucune déclaration ICS2" />
        ) : activeTab === 'export' && exports.length === 0 ? (
          <EmptyState icon={Plane} message="Aucune déclaration d'export" />
        ) : (
          <div className="overflow-x-auto">
            {/* DAU Table */}
            {activeTab === 'dau' && (
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N° déclaration</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Type</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Bureau douanier</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Régime</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Valeur déclarée</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Origine → Dest.</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date création</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {daus.map((d) => (
                    <tr key={d.id} className="cursor-pointer hover:bg-bg transition-colors" onClick={() => openDetail(d)}>
                      <td className="px-6 py-4 text-sm font-medium text-ink">{d.declarationNumber}</td>
                      <td className="px-6 py-4"><TypeBadge type={d.declarationType} styles={DAU_TYPE_STYLES} /></td>
                      <td className="px-6 py-4"><StatusBadge status={d.status} /></td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.customsOffice || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.customsRegime || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {d.declaredValue ? formatCurrency(d.declaredValue, d.currency) : '—'}
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {d.originCountry || d.destinationCountry ? (
                          <span className="flex items-center gap-1">
                            {formatCountry(d.originCountry)}
                            <ArrowRight size={12} className="text-ink-soft" />
                            {formatCountry(d.destinationCountry)}
                          </span>
                        ) : '—'}
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(d.createdAt)}</td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {getNextStatus(d.status) && (
                            <button
                              onClick={() => handleStatusUpdate(d.id, d.status)}
                              className="px-3 py-1 text-xs bg-accent text-white rounded-none hover:bg-accent-strong transition-colors flex items-center gap-1"
                            >
                              <Send size={12} />
                              {getNextStatusLabel(d.status)}
                            </button>
                          )}
                          {d.status === 'DRAFT' && (
                            deleteConfirm === d.id ? (
                              <div className="flex items-center gap-1">
                                <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                                <button
                                  onClick={() => handleDelete(d.id)}
                                  className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                                >
                                  Oui
                                </button>
                                <button
                                  onClick={() => setDeleteConfirm(null)}
                                  className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                                >
                                  Non
                                </button>
                              </div>
                            ) : (
                              <button
                                onClick={() => setDeleteConfirm(d.id)}
                                className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                                title="Supprimer"
                              >
                                <Trash2 size={16} />
                              </button>
                            )
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {activeTab === 'dau' && <Pagination page={dauPage} totalPages={dauTotalPages} onPageChange={setDauPage} />}

            {/* DEB Table */}
            {activeTab === 'deb' && (
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N°</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Type</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Période</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Pays partenaire</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Valeur statistique</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Masse net</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code SH 8</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date création</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {debs.map((d) => (
                    <tr key={d.id} className="cursor-pointer hover:bg-bg transition-colors" onClick={() => openDetail(d)}>
                      <td className="px-6 py-4 text-sm font-medium text-ink">{d.declarationNumber}</td>
                      <td className="px-6 py-4"><TypeBadge type={d.declarationType} styles={DEB_TYPE_STYLES} /></td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.period || '—'}</td>
                      <td className="px-6 py-4"><StatusBadge status={d.status} /></td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatCountry(d.partnerCountry)}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {d.statisticalValue ? formatCurrency(d.statisticalValue, 'EUR') : '—'}
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {d.netMass ? formatWeight(d.netMass) : '—'}
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.hsCode8 || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(d.createdAt)}</td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {getDebNextStatus(d.status) && (
                            <button
                              onClick={() => handleStatusUpdate(d.id, d.status)}
                              className="px-3 py-1 text-xs bg-accent text-white rounded-none hover:bg-accent-strong transition-colors flex items-center gap-1"
                            >
                              <Send size={12} />
                              {getDebNextStatusLabel(d.status)}
                            </button>
                          )}
                          {d.status === 'DRAFT' && (
                            deleteConfirm === d.id ? (
                              <div className="flex items-center gap-1">
                                <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                                <button
                                  onClick={() => handleDelete(d.id)}
                                  className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                                >
                                  Oui
                                </button>
                                <button
                                  onClick={() => setDeleteConfirm(null)}
                                  className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                                >
                                  Non
                                </button>
                              </div>
                            ) : (
                              <button
                                onClick={() => setDeleteConfirm(d.id)}
                                className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                                title="Supprimer"
                              >
                                <Trash2 size={16} />
                              </button>
                            )
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            {/* ICS2 Table */}
            {activeTab === 'ics2' && (
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N°</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">EORI expéditeur</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Navire</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N° voyage</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Conteneur</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code SH 6</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date création</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {ics2s.map((d) => (
                    <tr key={d.id} className="cursor-pointer hover:bg-bg transition-colors" onClick={() => openDetail(d)}>
                      <td className="px-6 py-4 text-sm font-medium text-ink">{d.declarationNumber}</td>
                      <td className="px-6 py-4"><StatusBadge status={d.status} /></td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.senderEori || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.vesselName || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.voyageNumber || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.containerNumber || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.hsCode6 || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(d.createdAt)}</td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {getNextStatus(d.status) && (
                            <button
                              onClick={() => handleStatusUpdate(d.id, d.status)}
                              className="px-3 py-1 text-xs bg-accent text-white rounded-none hover:bg-accent-strong transition-colors flex items-center gap-1"
                            >
                              <Send size={12} />
                              {getNextStatusLabel(d.status)}
                            </button>
                          )}
                          {d.status === 'DRAFT' && (
                            deleteConfirm === d.id ? (
                              <div className="flex items-center gap-1">
                                <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                                <button
                                  onClick={() => handleDelete(d.id)}
                                  className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                                >
                                  Oui
                                </button>
                                <button
                                  onClick={() => setDeleteConfirm(null)}
                                  className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                                >
                                  Non
                                </button>
                              </div>
                            ) : (
                              <button
                                onClick={() => setDeleteConfirm(d.id)}
                                className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                                title="Supprimer"
                              >
                                <Trash2 size={16} />
                              </button>
                            )
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            {/* Export Table */}
            {activeTab === 'export' && (
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N°</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Type</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">EORI exportateur</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Destination</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Valeur déclarée</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code SH</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date création</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {exports.map((d) => (
                    <tr key={d.id} className="cursor-pointer hover:bg-bg transition-colors" onClick={() => openDetail(d)}>
                      <td className="px-6 py-4 text-sm font-medium text-ink">{d.declarationNumber}</td>
                      <td className="px-6 py-4"><TypeBadge type={d.declarationType} styles={EXPORT_TYPE_STYLES} /></td>
                      <td className="px-6 py-4"><StatusBadge status={d.status} /></td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.exporterEori || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatCountry(d.destinationCountry)}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {d.declaredValue ? formatCurrency(d.declaredValue, d.currency) : '—'}
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{d.hsCode || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(d.createdAt)}</td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {getExportNextStatus(d.status) && (
                            <button
                              onClick={() => handleStatusUpdate(d.id, d.status)}
                              className="px-3 py-1 text-xs bg-accent text-white rounded-none hover:bg-accent-strong transition-colors flex items-center gap-1"
                            >
                              <Send size={12} />
                              {getExportNextStatusLabel(d.status)}
                            </button>
                          )}
                          {d.status === 'DRAFT' && (
                            deleteConfirm === d.id ? (
                              <div className="flex items-center gap-1">
                                <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                                <button
                                  onClick={() => handleDelete(d.id)}
                                  className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                                >
                                  Oui
                                </button>
                                <button
                                  onClick={() => setDeleteConfirm(null)}
                                  className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                                >
                                  Non
                                </button>
                              </div>
                            ) : (
                              <button
                                onClick={() => setDeleteConfirm(d.id)}
                                className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                                title="Supprimer"
                              >
                                <Trash2 size={16} />
                              </button>
                            )
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>

      {/* Create Modal */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setFormOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">
              {activeTab === 'dau' && 'Nouvelle déclaration DAU'}
              {activeTab === 'deb' && 'Nouvelle déclaration DEB'}
              {activeTab === 'ics2' && 'Nouvelle déclaration ICS2'}
              {activeTab === 'export' && 'Nouvelle déclaration d\'export'}
            </h3>
            {renderForm()}
          </div>
        </div>
      )}

      {detailModalOpen && selectedDeclaration && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setDetailModalOpen(false)}>
          <div className="bg-surface rounded-none shadow-2xl max-w-2xl w-full mx-4 max-h-[85vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between p-6 border-b border-line">
              <div>
                <h2 className="text-lg font-bold text-ink">{selectedDeclaration.declarationNumber}</h2>
                <p className="text-sm text-ink-soft">
                  {selectedDeclaration.declarationType && formatEnum(selectedDeclaration.declarationType ?? '')}
                  {selectedDeclaration.hsCode6 && ` \u2022 HS ${selectedDeclaration.hsCode6}`}
                  {selectedDeclaration.hsCode8 && ` \u2022 HS ${selectedDeclaration.hsCode8}`}
                  {selectedDeclaration.hsCode && ` \u2022 HS ${selectedDeclaration.hsCode}`}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => {
                    const resource = activeTab === 'dau' ? 'customs-declarations' : activeTab === 'deb' ? 'deb-declarations' : activeTab === 'ics2' ? 'ics2-declarations' : 'export-declarations';
                    downloadAuthedFile(`/v1/${resource}/${selectedDeclaration.id}/pdf`, `${selectedDeclaration.declarationNumber || activeTab}.pdf`)
                      .catch(() => toast.error('Erreur lors du téléchargement du PDF'));
                  }}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-danger/10 text-danger rounded-none text-sm font-medium hover:bg-danger/10 transition-colors"
                >
                  <FileText size={14} />
                  PDF
                </button>
                <button onClick={() => setDetailModalOpen(false)} className="p-2 hover:bg-surface-2 rounded-none">
                  <X size={20} className="text-ink-soft" />
                </button>
              </div>
            </div>
            <div className="p-6 space-y-4">
              <div className="flex items-center gap-3">
                <span className="text-sm text-ink-soft">Statut :</span>
                <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${getStatusColor(selectedDeclaration.status)}`}>
                  {formatStatus(selectedDeclaration.status)}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-4 text-sm">
                {selectedDeclaration.customsOffice && (
                  <div><span className="text-ink-soft">Bureau</span><p className="font-medium">{selectedDeclaration.customsOffice}</p></div>
                )}
                {selectedDeclaration.customsRegime && (
                  <div><span className="text-ink-soft">Régime</span><p className="font-medium">{selectedDeclaration.customsRegime}</p></div>
                )}
                {selectedDeclaration.customsCode && (
                  <div><span className="text-ink-soft">Code douanier</span><p className="font-medium">{selectedDeclaration.customsCode}</p></div>
                )}
                {selectedDeclaration.declaredValue != null && (
                  <div><span className="text-ink-soft">Valeur déclarée</span><p className="font-medium">{formatCurrency(selectedDeclaration.declaredValue ?? 0, selectedDeclaration.currency ?? 'EUR')}</p></div>
                )}
                {selectedDeclaration.originCountry && (
                  <div><span className="text-ink-soft">Pays d'origine</span><p className="font-medium">{formatCountry(selectedDeclaration.originCountry)}</p></div>
                )}
                {selectedDeclaration.destinationCountry && (
                  <div><span className="text-ink-soft">Pays de destination</span><p className="font-medium">{formatCountry(selectedDeclaration.destinationCountry)}</p></div>
                )}
                {selectedDeclaration.partnerCountry && (
                  <div><span className="text-ink-soft">Pays partenaire</span><p className="font-medium">{formatCountry(selectedDeclaration.partnerCountry)}</p></div>
                )}
                {selectedDeclaration.period && (
                  <div><span className="text-ink-soft">Période</span><p className="font-medium">{selectedDeclaration.period}</p></div>
                )}
                {selectedDeclaration.netWeight != null && (
                  <div><span className="text-ink-soft">Poids net</span><p className="font-medium">{formatWeight(selectedDeclaration.netWeight ?? 0)}</p></div>
                )}
                {selectedDeclaration.grossWeight != null && (
                  <div><span className="text-ink-soft">Poids brut</span><p className="font-medium">{formatWeight(selectedDeclaration.grossWeight ?? 0)}</p></div>
                )}
                {selectedDeclaration.netMass != null && (
                  <div><span className="text-ink-soft">Masse nette</span><p className="font-medium">{formatWeight(selectedDeclaration.netMass ?? 0)}</p></div>
                )}
                {selectedDeclaration.statisticalValue != null && (
                  <div><span className="text-ink-soft">Valeur statistique</span><p className="font-medium">{formatCurrency(selectedDeclaration.statisticalValue ?? 0, 'EUR')}</p></div>
                )}
                {selectedDeclaration.packagesCount != null && (
                  <div><span className="text-ink-soft">Colis</span><p className="font-medium">{selectedDeclaration.packagesCount}</p></div>
                )}
                {selectedDeclaration.senderEori && (
                  <div><span className="text-ink-soft">EORI expéditeur</span><p className="font-medium">{selectedDeclaration.senderEori}</p></div>
                )}
                {selectedDeclaration.receiverEori && (
                  <div><span className="text-ink-soft">EORI destinataire</span><p className="font-medium">{selectedDeclaration.receiverEori}</p></div>
                )}
                {selectedDeclaration.exporterEori && (
                  <div><span className="text-ink-soft">EORI exportateur</span><p className="font-medium">{selectedDeclaration.exporterEori}</p></div>
                )}
                {selectedDeclaration.vesselName && (
                  <div><span className="text-ink-soft">Navire</span><p className="font-medium">{selectedDeclaration.vesselName}</p></div>
                )}
                {selectedDeclaration.voyageNumber && (
                  <div><span className="text-ink-soft">Numéro de voyage</span><p className="font-medium">{selectedDeclaration.voyageNumber}</p></div>
                )}
                {selectedDeclaration.containerNumber && (
                  <div><span className="text-ink-soft">Conteneur</span><p className="font-medium">{selectedDeclaration.containerNumber}</p></div>
                )}
                {selectedDeclaration.natureOfTransaction && (
                  <div><span className="text-ink-soft">Nature</span><p className="font-medium">{selectedDeclaration.natureOfTransaction}</p></div>
                )}
                {selectedDeclaration.modeOfTransport && (
                  <div><span className="text-ink-soft">Mode de transport</span><p className="font-medium">{selectedDeclaration.modeOfTransport}</p></div>
                )}
              </div>
              {selectedDeclaration.goodsDescription && (
                <div className="border-t border-line pt-4">
                  <span className="text-sm text-ink-soft">Description des marchandises</span>
                  <p className="text-sm text-ink mt-1">{selectedDeclaration.goodsDescription}</p>
                </div>
              )}
              {selectedDeclaration.notes && (
                <div className="border-t border-line pt-4">
                  <span className="text-sm text-ink-soft">Notes</span>
                  <p className="text-sm text-ink mt-1">{selectedDeclaration.notes}</p>
                </div>
              )}
              <div className="border-t border-line pt-4 grid grid-cols-2 gap-3 text-xs text-ink-soft">
                <div>Créé : {new Date(selectedDeclaration.createdAt).toLocaleDateString('fr-FR')}</div>
                {selectedDeclaration.submittedAt && <div>Soumis : {new Date(selectedDeclaration.submittedAt).toLocaleDateString('fr-FR')}</div>}
                {selectedDeclaration.clearedAt && <div>Dédouané : {new Date(selectedDeclaration.clearedAt).toLocaleDateString('fr-FR')}</div>}
                {selectedDeclaration.validatedAt && <div>Validé : {new Date(selectedDeclaration.validatedAt).toLocaleDateString('fr-FR')}</div>}
                {selectedDeclaration.rejectedAt && <div>Rejeté : {new Date(selectedDeclaration.rejectedAt).toLocaleDateString('fr-FR')}</div>}
                {selectedDeclaration.respondedAt && <div>Réponse : {new Date(selectedDeclaration.respondedAt).toLocaleDateString('fr-FR')}</div>}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DeclarationsPage;
