import { AlertCircle, Package, Truck, CheckCircle, type LucideIcon } from 'lucide-react';

export const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  DRAFT: { label: 'Brouillon', color: 'text-ink-soft', bg: 'bg-surface-2' },
  QUOTED: { label: 'Devisé', color: 'text-accent-strong', bg: 'bg-accent-soft' },
  BOOKED: { label: 'Réservé', color: 'text-accent', bg: 'bg-accent-soft' },
  IN_TRANSIT: { label: 'En transit', color: 'text-warning', bg: 'bg-warning/10' },
  DELIVERED: { label: 'Livré', color: 'text-success', bg: 'bg-success/10' },
  CANCELLED: { label: 'Annulé', color: 'text-danger', bg: 'bg-danger/10' },
};

export const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  QUOTED: 'Devisé',
  BOOKED: 'Réservé',
  IN_TRANSIT: 'En transit',
  DELIVERED: 'Livré',
  CANCELLED: 'Annulé',
};

export const CLIENT_STATUS_CONFIG: Record<string, { label: string; color: string; icon: LucideIcon }> = {
  DRAFT: { label: 'Brouillon', color: 'bg-surface-2 text-ink-soft', icon: AlertCircle },
  QUOTED: { label: 'Devisé', color: 'bg-accent-soft text-accent-strong', icon: Package },
  BOOKED: { label: 'Réservé', color: 'bg-accent-soft text-accent', icon: Package },
  IN_TRANSIT: { label: 'En transit', color: 'bg-warning/10 text-warning', icon: Truck },
  DELIVERED: { label: 'Livré', color: 'bg-success/10 text-success', icon: CheckCircle },
  CANCELLED: { label: 'Annulé', color: 'bg-danger/10 text-danger', icon: AlertCircle },
};

export const TRACKING_STATUS_COLORS: Record<string, string> = {
  IN_TRANSIT: 'bg-warning/10 text-warning',
  DEPARTED: 'bg-accent-soft text-accent-strong',
  ARRIVED: 'bg-success/10 text-success',
  DELIVERED: 'bg-success/10 text-success',
  CREATED: 'bg-surface-2 text-ink-soft',
};

export const INCOTERMS = [
  'EXW', 'FCA', 'FAS', 'FOB', 'CFR', 'CIF', 'CPT', 'CIP',
  'DAP', 'DPU', 'DDP',
];

export const COUNTRIES = [
  'France', 'Allemagne', 'Belgique', 'Pays-Bas', 'Espagne', 'Italie',
  'Portugal', 'Royaume-Uni', 'Chine', 'États-Unis', 'Japon', 'Corée du Sud',
  'Inde', 'Vietnam', 'Thaïlande', 'Singapour', 'Maroc', 'Tunisie',
  'Turquie', 'Émirats Arabes Unis', 'Brésil', 'Mexique', 'Canada', 'Australie',
];
