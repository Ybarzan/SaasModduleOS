import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  FileText, Truck, MapPin, AlertTriangle, Award, Search, Package,
  Loader2, Download, CheckCircle2, ArrowRight, ScanText, Mail,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import type { ShipmentOrder } from '../types';
import { STATUS_CONFIG } from '@/lib/constants';

type ShipmentDocType = 'shipping-label' | 'cmr' | 'dgd' | 'certificate-of-origin';

interface ShipmentDocCard {
  id: ShipmentDocType;
  title: string;
  description: string;
  icon: typeof Truck;
  color: 'blue' | 'emerald' | 'amber' | 'red' | 'purple';
  filenamePrefix: string;
}

const SHIPMENT_DOCUMENTS: ShipmentDocCard[] = [
  { id: 'shipping-label', title: "Étiquette d'expédition", description: 'Adresses, poids, colis et code-barres scannable', icon: Truck, color: 'emerald', filenamePrefix: 'etiquette' },
  { id: 'cmr', title: 'CMR (lettre de voiture)', description: 'Convention relative au contrat de transport international', icon: MapPin, color: 'amber', filenamePrefix: 'cmr' },
  { id: 'dgd', title: 'Déclaration marchandises dangereuses', description: 'DGD — Dangerous Goods Declaration', icon: AlertTriangle, color: 'red', filenamePrefix: 'dgd' },
  { id: 'certificate-of-origin', title: "Certificat d'origine", description: "Certifie l'origine des marchandises pour la douane", icon: Award, color: 'purple', filenamePrefix: 'certificat-origine' },
];

const COLOR_CLASSES: Record<ShipmentDocCard['color'], { bg: string; icon: string; border: string }> = {
  blue: { bg: 'bg-accent/10', icon: 'text-accent', border: 'border-accent/40' },
  emerald: { bg: 'bg-success/10', icon: 'text-success', border: 'border-success/40' },
  amber: { bg: 'bg-warning/10', icon: 'text-warning', border: 'border-warning/40' },
  red: { bg: 'bg-danger/10', icon: 'text-danger', border: 'border-danger/40' },
  purple: { bg: 'bg-accent/10', icon: 'text-accent', border: 'border-accent/40' },
};

const EXPORTERS: Record<ShipmentDocType, (id: string) => Promise<{ data: ArrayBuffer }>> = {
  'shipping-label': (id) => incokalkAPI.export.shippingLabelPdf(id),
  cmr: (id) => incokalkAPI.export.cmrPdf(id),
  dgd: (id) => incokalkAPI.export.dgdPdf(id),
  'certificate-of-origin': (id) => incokalkAPI.export.certificateOfOriginPdf(id),
};

function downloadPdfBlob(data: ArrayBuffer, filename: string) {
  const blob = new Blob([data], { type: 'application/pdf' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

const DocumentsPage = () => {
  const [search, setSearch] = useState('');
  const [selectedShipment, setSelectedShipment] = useState<ShipmentOrder | null>(null);
  const [generatingDoc, setGeneratingDoc] = useState<ShipmentDocType | null>(null);
  const hasMinimumRole = useAuthStore((s) => s.hasMinimumRole);

  const { data: shipments = [], isLoading } = useQuery({
    queryKey: ['documents-shipments'],
    queryFn: async () => {
      const res = await incokalkAPI.shipments.getAll();
      return (res.data as ShipmentOrder[]) || [];
    },
  });

  const filteredShipments = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return shipments.slice(0, 8);
    return shipments
      .filter((s) =>
        s.orderNumber.toLowerCase().includes(q) ||
        s.shipperName?.toLowerCase().includes(q) ||
        s.consigneeName?.toLowerCase().includes(q) ||
        s.consigneeCountry?.toLowerCase().includes(q)
      )
      .slice(0, 8);
  }, [shipments, search]);

  const generateMutation = useMutation({
    mutationFn: async ({ type, shipment }: { type: ShipmentDocType; shipment: ShipmentOrder }) => {
      const res = await EXPORTERS[type](shipment.id);
      return { res, type, shipment };
    },
    onMutate: ({ type }) => setGeneratingDoc(type),
    onSuccess: ({ res, type, shipment }) => {
      const doc = SHIPMENT_DOCUMENTS.find((d) => d.id === type)!;
      downloadPdfBlob(res.data, `${doc.filenamePrefix}-${shipment.orderNumber}.pdf`);
      toast.success(`${doc.title} téléchargé`);
    },
    onError: () => toast.error('Erreur lors de la génération du document'),
    onSettled: () => setGeneratingDoc(null),
  });

  return (
    <div className="min-h-screen bg-bg">
      <div className="max-w-4xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-surface-2 mb-4">
            <FileText size={32} className="text-ink-soft" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Documents
          </h1>
          <p className="text-ink-soft max-w-xl mx-auto">
            Générez les documents de transport à partir des données réelles de vos expéditions.
          </p>
        </div>

        {/* Devis — pointe vers la vraie recherche de tarifs (déjà construite sur /quotes) */}
        <Link
          to="/quotes"
          className="flex items-center gap-4 bg-surface rounded-none shadow-sm border border-line p-6 mb-6 hover:border-accent/40 transition-colors group"
        >
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-none bg-accent/10 shrink-0">
            <FileText size={24} className="text-accent" />
          </div>
          <div className="flex-1">
            <h3 className="font-bold text-ink mb-1">Devis de transport</h3>
            <p className="text-sm text-ink-soft">
              Comparez les tarifs transporteurs réels et exportez le devis en PDF
            </p>
          </div>
          <ArrowRight size={18} className="text-ink-soft group-hover:text-accent transition-colors shrink-0" />
        </Link>

        {/* Extraction/Import — mêmes cartes que ci-dessus, visibles seulement si le
            rôle de l'utilisateur donne accès à ces pages (MANAGER / ADMIN). */}
        {hasMinimumRole('MANAGER') && (
          <Link
            to="/document-parser"
            className="flex items-center gap-4 bg-surface rounded-none shadow-sm border border-line p-6 mb-6 hover:border-accent/40 transition-colors group"
          >
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-none bg-accent/10 shrink-0">
              <ScanText size={24} className="text-accent" />
            </div>
            <div className="flex-1">
              <h3 className="font-bold text-ink mb-1">Extraction de documents</h3>
              <p className="text-sm text-ink-soft">
                Extrayez automatiquement les données d'une facture, d'un B/L ou d'un certificat scanné
              </p>
            </div>
            <ArrowRight size={18} className="text-ink-soft group-hover:text-accent transition-colors shrink-0" />
          </Link>
        )}
        {hasMinimumRole('ADMIN') && (
          <Link
            to="/email-intake"
            className="flex items-center gap-4 bg-surface rounded-none shadow-sm border border-line p-6 mb-6 hover:border-accent/40 transition-colors group"
          >
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-none bg-accent/10 shrink-0">
              <Mail size={24} className="text-accent" />
            </div>
            <div className="flex-1">
              <h3 className="font-bold text-ink mb-1">Import Email</h3>
              <p className="text-sm text-ink-soft">
                Configurez une boîte email pour créer des brouillons d'expédition automatiquement
              </p>
            </div>
            <ArrowRight size={18} className="text-ink-soft group-hover:text-accent transition-colors shrink-0" />
          </Link>
        )}

        {/* Sélection d'expédition — alimente les 4 documents ci-dessous */}
        <div className="bg-surface rounded-none shadow-sm border border-line p-6 mb-6">
          <h2 className="font-bold text-ink mb-1">Sélectionnez une expédition</h2>
          <p className="text-sm text-ink-soft mb-4">
            Les documents ci-dessous sont générés à partir des données réelles de l'expédition choisie.
          </p>

          {selectedShipment ? (
            <div className="flex items-center justify-between bg-accent-soft border border-accent/30 rounded-none px-4 py-3">
              <div className="flex items-center gap-3 min-w-0">
                <CheckCircle2 size={18} className="text-accent shrink-0" />
                <div className="min-w-0">
                  <p className="font-semibold text-ink truncate">{selectedShipment.orderNumber}</p>
                  <p className="text-xs text-ink-soft truncate">
                    {selectedShipment.shipperCountry || selectedShipment.shipperCity || '—'}
                    {' → '}
                    {selectedShipment.consigneeCountry || selectedShipment.consigneeCity || '—'}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedShipment(null)}
                className="text-xs font-semibold text-accent hover:underline shrink-0 ml-3"
              >
                Changer
              </button>
            </div>
          ) : (
            <>
              <div className="relative mb-3">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Rechercher par n° de commande, expéditeur, destinataire..."
                  className="w-full pl-9 pr-4 py-2.5 border border-line rounded-none text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-transparent bg-surface"
                />
              </div>

              {isLoading ? (
                <div className="flex items-center justify-center py-8 text-ink-soft">
                  <Loader2 size={20} className="animate-spin" />
                </div>
              ) : shipments.length === 0 ? (
                <div className="text-center py-8">
                  <Package size={28} className="mx-auto text-ink-soft mb-2" />
                  <p className="text-sm text-ink-soft mb-3">Aucune expédition pour le moment.</p>
                  <Link to="/shipments" className="text-sm font-semibold text-accent hover:underline">
                    Créer une expédition
                  </Link>
                </div>
              ) : filteredShipments.length === 0 ? (
                <p className="text-sm text-ink-soft text-center py-6">Aucune expédition ne correspond à cette recherche.</p>
              ) : (
                <div className="space-y-1 max-h-64 overflow-y-auto">
                  {filteredShipments.map((s) => {
                    const status = STATUS_CONFIG[s.status] ?? STATUS_CONFIG.DRAFT;
                    return (
                      <button
                        key={s.id}
                        onClick={() => setSelectedShipment(s)}
                        className="w-full flex items-center justify-between gap-3 px-3 py-2.5 rounded-none hover:bg-surface-2 transition-colors text-left"
                      >
                        <div className="min-w-0">
                          <p className="font-semibold text-ink text-sm truncate">{s.orderNumber}</p>
                          <p className="text-xs text-ink-soft truncate">
                            {s.shipperName || '—'} → {s.consigneeName || '—'}
                          </p>
                        </div>
                        <span className={`shrink-0 text-xs font-semibold px-2 py-1 rounded-full ${status.bg} ${status.color}`}>
                          {status.label}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </div>

        <div className="grid sm:grid-cols-2 gap-4">
          {SHIPMENT_DOCUMENTS.map((doc) => {
            const Icon = doc.icon;
            const colors = COLOR_CLASSES[doc.color];
            const isGenerating = generatingDoc === doc.id;

            return (
              <div key={doc.id} className={`bg-surface rounded-none shadow-sm border border-line p-6 flex flex-col ${colors.border} transition-colors`}>
                <div className={`inline-flex items-center justify-center w-12 h-12 rounded-none ${colors.bg} mb-4`}>
                  <Icon size={24} className={colors.icon} />
                </div>
                <h3 className="font-bold text-ink mb-1">{doc.title}</h3>
                <p className="text-sm text-ink-soft flex-1 mb-4">{doc.description}</p>

                <button
                  onClick={() => selectedShipment && generateMutation.mutate({ type: doc.id, shipment: selectedShipment })}
                  disabled={!selectedShipment || isGenerating}
                  className="bg-accent text-white py-2.5 rounded-none text-sm font-semibold hover:bg-accent-strong disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
                >
                  {isGenerating ? (
                    <><Loader2 className="h-4 w-4 animate-spin" /> Génération...</>
                  ) : (
                    <><Download size={14} /> {selectedShipment ? 'Télécharger PDF' : 'Sélectionnez une expédition'}</>
                  )}
                </button>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default DocumentsPage;
