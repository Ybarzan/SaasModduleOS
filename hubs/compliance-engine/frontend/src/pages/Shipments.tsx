import { Fragment, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Package, Plus, MapPin, CheckCircle, Truck,
  Eye, Trash2, ChevronDown, Circle, Loader2, ArrowRight, FileText, Download, Scale, Search, ClipboardList,
  Link2, Copy, ExternalLink, Check,
} from 'lucide-react';
import type { ShipmentOrder, ShipmentFormData, ShipmentItem, TrackingEvent, Carrier, ShippingRate } from '../types';
import LiveTrackingPanel from '../components/LiveTrackingPanel';
import Pagination from '../components/Pagination';
import { STATUS_CONFIG, INCOTERMS, COUNTRIES } from '@/lib/constants';

const PAGE_SIZE = 20;

// Forme des bons de reception renvoyes par /receivings (distincte de
// ShipmentOrder -- l'API precedente castait a tort vers ce dernier).
interface ReceivingOrderSummary {
  id: string;
  orderNumber: string;
  reference?: string;
  status: string;
}

const EMPTY_FORM: ShipmentFormData = {
   shipperName: '',
   shipperAddress: '',
   shipperCity: '',
   shipperCountry: '',
   shipperPostalCode: '',
   consigneeName: '',
   consigneeAddress: '',
   consigneeCity: '',
   consigneeCountry: '',
   consigneePostalCode: '',
   goodsDescription: '',
   goodsValue: 0,
   currency: 'EUR',
   weightKg: 0,
   volumeM3: 0,
   packagesCount: 1,
   hsCode: '',
   incotermCode: '',
   isDangerous: false,
   requestedPickupDate: '',
   items: [],
 };

const ShipmentReceivingSection = ({ shipmentId }: { shipmentId: string }) => {
  const { data, isLoading } = useQuery({
    queryKey: ['receivings', shipmentId],
    queryFn: async () => {
      const res = await incokalkAPI.receivings.list({ shipmentId });
      return (res?.data ?? []) as ReceivingOrderSummary[];
    },
  });
  const orders = Array.isArray(data) ? data : [];
  return (
    <div className="mt-4 pt-4 border-t border-line">
      <div className="flex items-center justify-between mb-3">
        <h4 className="font-semibold text-ink flex items-center space-x-2">
          <ClipboardList size={16} />
          <span>Réception</span>
          {orders.length > 0 && (
            <span className="bg-accent-soft text-accent-strong text-xs px-2 py-0.5 rounded-full">{orders.length}</span>
          )}
        </h4>
        <Link
          to={`/receivings?shipmentId=${shipmentId}&openCreate=1`}
          className="inline-flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium bg-accent text-white rounded-md hover:bg-accent-strong transition-colors"
        >
          <Plus size={12} />
          <span>Créer un bon de réception</span>
        </Link>
      </div>
      {isLoading ? (
        <Loader2 className="h-4 w-4 animate-spin text-ink-soft" />
      ) : orders.length === 0 ? (
        <p className="text-xs text-ink-soft">Aucun bon de réception lié à cette expédition.</p>
      ) : (
        <ul className="space-y-1.5">
          {orders.map((o) => (
            <li key={o.id}>
              <Link
                to="/receivings"
                className="flex items-center justify-between gap-3 px-3 py-2 bg-surface border border-line rounded-lg hover:border-accent/60 transition-colors"
              >
                <span className="font-mono text-xs font-medium text-ink">{o.orderNumber}</span>
                <span className="text-xs text-ink-soft">
                  {o.status}
                  {o.reference ? ` · ${o.reference}` : ''}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

const CountrySelect = ({
  value,
  onChange,
}: {
  value: string;
  onChange: (v: string) => void;
}) => (
  <select
    value={value}
    onChange={(e) => onChange(e.target.value)}
    className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
  >
    <option value="">Pays</option>
    {COUNTRIES.map((c) => (
      <option key={c} value={c}>{c}</option>
    ))}
  </select>
);

const AddressSection = ({
  title,
  name,
  address,
  city,
  country,
  postalCode,
  onNameChange,
  onAddressChange,
  onCityChange,
  onCountryChange,
  onPostalCodeChange,
}: {
  title: string;
  name: string;
  address: string;
  city: string;
  country: string;
  postalCode: string;
  onNameChange: (v: string) => void;
  onAddressChange: (v: string) => void;
  onCityChange: (v: string) => void;
  onCountryChange: (v: string) => void;
  onPostalCodeChange: (v: string) => void;
}) => (
  <div className="space-y-3">
    <h4 className="font-semibold text-ink flex items-center space-x-2">
      <MapPin size={16} />
      <span>{title}</span>
    </h4>
    <input
      type="text"
      value={name}
      onChange={(e) => onNameChange(e.target.value)}
      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
      placeholder="Nom / Société"
    />
    <input
      type="text"
      value={address}
      onChange={(e) => onAddressChange(e.target.value)}
      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
      placeholder="Adresse"
    />
    <div className="grid grid-cols-3 gap-2">
      <input
        type="text"
        value={city}
        onChange={(e) => onCityChange(e.target.value)}
        className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
        placeholder="Ville"
      />
      <CountrySelect value={country} onChange={onCountryChange} />
      <input
        type="text"
        value={postalCode}
        onChange={(e) => onPostalCodeChange(e.target.value)}
        className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
        placeholder="Code postal"
      />
    </div>
  </div>
);

const TrackingTimeline = ({ events }: { events: TrackingEvent[] }) => (
  <div className="pl-4 border-l-2 border-line space-y-4 mt-4">
    {events.map((event, idx) => {
      const isLast = idx === events.length - 1;
      const config = STATUS_CONFIG[event.status as keyof typeof STATUS_CONFIG];
      return (
        <div key={event.id} className="relative">
          <div
            className={`absolute -left-[21px] w-4 h-4 rounded-full border-2 border-surface ${
              isLast ? 'bg-accent' : 'bg-surface-2'
            }`}
          />
          <div className="ml-4">
            <div className="flex items-center space-x-2">
              <span
                className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                  config?.bg || 'bg-surface-2'
                } ${config?.color || 'text-ink'}`}
              >
                {config?.label || event.status}
              </span>
              <span className="text-xs text-ink-soft">
                {new Date(event.eventTime).toLocaleString('fr-FR')}
              </span>
              {event.dataSource === 'MANUAL' && (
                <span
                  title="Saisi manuellement, pas confirmé par un transporteur ou un flux de tracking"
                  className="text-[10px] uppercase tracking-wide text-ink-soft/70 border border-line rounded px-1.5 py-0.5 shrink-0"
                >
                  Manuel
                </span>
              )}
            </div>
            {event.location && (
              <div className="text-sm text-ink-soft flex items-center space-x-1 mt-1">
                <MapPin size={12} />
                <span>{event.location}</span>
              </div>
            )}
            {event.description && (
              <p className="text-sm text-ink-soft mt-1">{event.description}</p>
            )}
          </div>
        </div>
      );
    })}
    {events.length === 0 && (
      <p className="text-sm text-ink-soft ml-4">Aucun événement de suivi</p>
    )}
  </div>
);

const Shipments = () => {
  const queryClient = useQueryClient();
   const [showForm, setShowForm] = useState(false);
   const [form, setForm] = useState<ShipmentFormData>({ ...EMPTY_FORM });
   const [expandedId, setExpandedId] = useState<string | null>(null);
   const [deleteId, setDeleteId] = useState<string | null>(null);
   const [showRateSearch, setShowRateSearch] = useState(false);
   const [showItemsForm, setShowItemsForm] = useState(false);
   const [items, setItems] = useState<ShipmentItem[]>([]);
   const [page, setPage] = useState(0);
   const [clientLinks, setClientLinks] = useState<Record<string, string>>({});
   const [copiedLinkId, setCopiedLinkId] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['shipments', page],
    queryFn: async () => {
      const res = await incokalkAPI.shipments.getPage(page, PAGE_SIZE);
      return res.data;
    },
  });

  const { data: carriers = [] } = useQuery({
    queryKey: ['carriers'],
    queryFn: async () => {
      const res = await incokalkAPI.carriers.getAll();
      return res.data as Carrier[];
    },
  });

  const canSearchRates = form.shipperCountry && form.consigneeCountry && showRateSearch;
  const { data: allRates = [], isLoading: loadingRates } = useQuery({
    queryKey: ['shipping-rates'],
    queryFn: async () => {
      const res = await incokalkAPI.shippingRates.getAll();
      return (res.data as ShippingRate[]) || [];
    },
  });
  const matchingRates = canSearchRates
    ? allRates.filter((r: ShippingRate) =>
        r.originCountry === form.shipperCountry &&
        r.destinationCountry === form.consigneeCountry &&
        r.active &&
        (!form.weightKg || !r.maxWeightKg || form.weightKg <= r.maxWeightKg)
      )
    : [];

  const shipments: ShipmentOrder[] = Array.isArray(data) ? data : (data?.content ?? []);
  const totalPages: number = Array.isArray(data) ? 1 : (data?.totalPages ?? 1);

  const totalShipments = shipments.length;
  const inTransit = shipments.filter((s) => s.status === 'IN_TRANSIT').length;
  const delivered = shipments.filter((s) => s.status === 'DELIVERED').length;
  const drafts = shipments.filter((s) => s.status === 'DRAFT').length;

  const createMutation = useMutation({
    mutationFn: (d: ShipmentFormData) => incokalkAPI.shipments.create(d),
     onSuccess: () => {
       queryClient.invalidateQueries({ queryKey: ['shipments'] });
       toast.success('Expédition créée');
       setShowForm(false);
       setForm({ ...EMPTY_FORM });
       setItems([]);
     },
    onError: () => toast.error('Erreur lors de la création'),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      incokalkAPI.shipments.updateStatus(id, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipments'] });
      toast.success('Statut mis à jour');
    },
    onError: () => toast.error('Erreur lors de la mise à jour du statut'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.shipments.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipments'] });
      toast.success('Expédition supprimée');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const exportLabelMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.export.shippingLabelPdf(id),
    onSuccess: (res, id) => {
      const shipment = (data ?? []).find((s: ShipmentOrder) => s.id === id);
      const orderNumber = shipment?.orderNumber ?? id;
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `etiquette-${orderNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Étiquette PDF exportée');
    },
    onError: () => toast.error('Erreur lors de l\'export PDF'),
  });

  const exportCmrMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.export.cmrPdf(id),
    onSuccess: (res, id) => {
      const shipment = (data ?? []).find((s: ShipmentOrder) => s.id === id);
      const orderNumber = shipment?.orderNumber ?? id;
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `cmr-${orderNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('CMR exporté');
    },
    onError: () => toast.error('Erreur lors de l\'export CMR'),
  });

  const exportDgdMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.export.dgdPdf(id),
    onSuccess: (res, id) => {
      const shipment = (data ?? []).find((s: ShipmentOrder) => s.id === id);
      const orderNumber = shipment?.orderNumber ?? id;
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `dgd-${orderNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('DGD exportée');
    },
    onError: () => toast.error('Erreur lors de l\'export DGD'),
  });

  const exportCertOrigineMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.export.certificateOfOriginPdf(id),
    onSuccess: (res, id) => {
      const shipment = (data ?? []).find((s: ShipmentOrder) => s.id === id);
      const orderNumber = shipment?.orderNumber ?? id;
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `certificat-origine-${orderNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Certificat d\'origine exporté');
    },
    onError: () => toast.error('Erreur lors de l\'export du certificat d\'origine'),
  });

  // Générer et copier un lien de suivi client depuis l'expédition elle-même --
  // jusqu'ici il fallait aller sur /shared-links et coller l'UUID à la main
  // dans un champ texte brut, sans lien direct depuis la liste des expéditions.
  const shareLinkMutation = useMutation({
    mutationFn: (shipmentId: string) => incokalkAPI.sharedLinks.create({ shipmentId }),
    onSuccess: (res, shipmentId) => {
      const url = `${window.location.origin}${res.data.url}`;
      setClientLinks((prev) => ({ ...prev, [shipmentId]: url }));
      navigator.clipboard.writeText(url);
      setCopiedLinkId(shipmentId);
      setTimeout(() => setCopiedLinkId((current) => (current === shipmentId ? null : current)), 2000);
      toast.success('Lien client copié !');
    },
    onError: () => toast.error('Erreur lors de la création du lien client'),
  });

  const toggleExpand = (id: string) => {
    setExpandedId(expandedId === id ? null : id);
  };

   const handleSubmit = (e: React.FormEvent) => {
     e.preventDefault();
     if (!form.shipperName.trim() || !form.consigneeName.trim()) {
       toast.error('Le nom de l\'expéditeur et du destinataire sont obligatoires');
       return;
     }
     createMutation.mutate({ ...form, items: items.length > 0 ? items : undefined });
   };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement des expéditions...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="bg-danger/10 border border-danger/40 text-danger px-6 py-4 rounded">
          Erreur lors du chargement des expéditions
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-8 gap-4">
          <div>
            <h1 className="text-4xl font-bold text-ink mb-2">Expéditions</h1>
            <p className="text-ink-soft">Gérez vos envois et suivez leur progression</p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={async () => {
                try {
                  const res = await incokalkAPI.export.csv.shipments();
                  const blob = new Blob([res.data], { type: 'text/csv' });
                  const url = window.URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = 'shipments_export.csv';
                  a.click();
                  window.URL.revokeObjectURL(url);
                  toast.success('Export téléchargé');
                } catch { toast.error('Erreur export'); }
              }}
              className="border border-line text-ink px-4 py-2 rounded-lg hover:bg-bg transition-colors flex items-center space-x-2"
            >
              <Download size={18} />
              <span>Exporter CSV</span>
            </button>
            <button
              onClick={() => { setShowForm(!showForm); setForm({ ...EMPTY_FORM }); }}
              className="bg-accent text-white px-4 py-2 rounded-lg hover:bg-accent-strong transition-colors flex items-center space-x-2 tap-target"
            >
              <Plus size={20} />
              <span>Nouvelle expédition</span>
            </button>
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 mb-8">
          <div className="bg-surface rounded-lg shadow-lg p-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-3xl font-bold text-accent">{totalShipments}</div>
                <div className="text-ink-soft">Total</div>
              </div>
              <Package className="h-8 w-8 text-accent" />
            </div>
          </div>
          <div className="bg-surface rounded-lg shadow-lg p-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-3xl font-bold text-warning">{inTransit}</div>
                <div className="text-ink-soft">En transit</div>
              </div>
              <Truck className="h-8 w-8 text-warning" />
            </div>
          </div>
          <div className="bg-surface rounded-lg shadow-lg p-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-3xl font-bold text-success">{delivered}</div>
                <div className="text-ink-soft">Livrées</div>
              </div>
              <CheckCircle className="h-8 w-8 text-success" />
            </div>
          </div>
          <div className="bg-surface rounded-lg shadow-lg p-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-3xl font-bold text-ink-soft">{drafts}</div>
                <div className="text-ink-soft">Brouillons</div>
              </div>
              <Circle className="h-8 w-8 text-ink-soft" />
            </div>
          </div>
        </div>

        {/* Create Form */}
        {showForm && (
          <div className="bg-surface rounded-lg shadow-lg p-6 mb-8">
            <h2 className="text-xl font-bold text-ink mb-6">Nouvelle expédition</h2>
            <form onSubmit={handleSubmit}>
              <div className="space-y-6">
                <AddressSection
                  title="Expéditeur"
                  name={form.shipperName}
                  address={form.shipperAddress}
                  city={form.shipperCity}
                  country={form.shipperCountry}
                  postalCode={form.shipperPostalCode}
                  onNameChange={(v) => setForm({ ...form, shipperName: v })}
                  onAddressChange={(v) => setForm({ ...form, shipperAddress: v })}
                  onCityChange={(v) => setForm({ ...form, shipperCity: v })}
                  onCountryChange={(v) => setForm({ ...form, shipperCountry: v })}
                  onPostalCodeChange={(v) => setForm({ ...form, shipperPostalCode: v })}
                />
                <div className="flex items-center justify-center text-ink-soft">
                  <ArrowRight size={24} />
                </div>
                <AddressSection
                  title="Destinataire"
                  name={form.consigneeName}
                  address={form.consigneeAddress}
                  city={form.consigneeCity}
                  country={form.consigneeCountry}
                  postalCode={form.consigneePostalCode}
                  onNameChange={(v) => setForm({ ...form, consigneeName: v })}
                  onAddressChange={(v) => setForm({ ...form, consigneeAddress: v })}
                  onCityChange={(v) => setForm({ ...form, consigneeCity: v })}
                  onCountryChange={(v) => setForm({ ...form, consigneeCountry: v })}
                  onPostalCodeChange={(v) => setForm({ ...form, consigneePostalCode: v })}
                />

                <div className="space-y-3">
                  <h4 className="font-semibold text-ink flex items-center space-x-2">
                    <Package size={16} />
                    <span>Marchandises</span>
                  </h4>
                  <div className="grid md:grid-cols-2 gap-4">
                    <input
                      type="text"
                      value={form.goodsDescription}
                      onChange={(e) => setForm({ ...form, goodsDescription: e.target.value })}
                      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                      placeholder="Description des marchandises"
                    />
                    <div className="grid grid-cols-2 gap-2">
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        value={form.goodsValue || ''}
                        onChange={(e) => setForm({ ...form, goodsValue: parseFloat(e.target.value) || 0 })}
                        className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                        placeholder="Valeur (€)"
                      />
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        value={form.weightKg || ''}
                        onChange={(e) => setForm({ ...form, weightKg: parseFloat(e.target.value) || 0 })}
                        className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                        placeholder="Poids (kg)"
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={form.volumeM3 || ''}
                      onChange={(e) => setForm({ ...form, volumeM3: parseFloat(e.target.value) || 0 })}
                      className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                      placeholder="Volume (m³)"
                    />
                    <input
                      type="number"
                      min="1"
                      value={form.packagesCount || ''}
                      onChange={(e) => setForm({ ...form, packagesCount: parseInt(e.target.value) || 1 })}
                      className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                      placeholder="Colis"
                    />
                    <input
                      type="text"
                      value={form.hsCode || ''}
                      onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
                      className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                      placeholder="Code SH"
                    />
                    <select
                      value={form.incotermCode || ''}
                      onChange={(e) => setForm({ ...form, incotermCode: e.target.value })}
                      className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                    >
                      <option value="">Incoterm</option>
                      {INCOTERMS.map((i) => (
                        <option key={i} value={i}>{i}</option>
                      ))}
                    </select>
                  </div>
                  <div className="flex items-center space-x-4">
                    <label className="flex items-center space-x-2 text-sm">
                      <input
                        type="checkbox"
                        checked={form.isDangerous || false}
                        onChange={(e) => setForm({ ...form, isDangerous: e.target.checked })}
                        className="rounded border-line"
                      />
                      <span>Marchandises dangereuses</span>
                    </label>
                    <input
                      type="date"
                      value={form.requestedPickupDate || ''}
                      onChange={(e) => setForm({ ...form, requestedPickupDate: e.target.value })}
                      className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                    />
                  </div>
                 </div>

                 {/* Articles section */}
                 <div className="space-y-3">
                   <div className="flex items-center justify-between">
                     <h4 className="font-semibold text-ink flex items-center space-x-2">
                       <Package size={16} />
                       <span>Articles</span>
                     </h4>
                     <button
                       type="button"
                       onClick={() => setShowItemsForm(!showItemsForm)}
                       className="text-accent hover:text-accent-strong text-sm font-medium flex items-center space-x-1"
                     >
                       <Plus size={14} />
                       <span>{showItemsForm ? 'Fermer' : 'Ajouter un article'}</span>
                     </button>
                   </div>
                   {showItemsForm && (
                     <div className="bg-bg rounded-lg p-4 border border-line space-y-3">
                       <div className="grid md:grid-cols-5 gap-3">
                         <input
                           type="text"
                           placeholder="SKU"
                           value={form.items?.[form.items.length - 1]?.sku ?? ''}
                           onChange={(e) => {
                             const last = form.items?.length ? form.items.length - 1 : 0;
                             const updated = [...(form.items ?? [])];
                             if (!updated[last]) updated.push({} as ShipmentItem);
                             updated[last] = { ...updated[last], sku: e.target.value };
                             setForm({ ...form, items: updated });
                           }}
                           className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                         />
                         <input
                           type="text"
                           placeholder="Nom"
                           value={form.items?.[form.items.length - 1]?.name ?? ''}
                           onChange={(e) => {
                             const last = form.items?.length ? form.items.length - 1 : 0;
                             const updated = [...(form.items ?? [])];
                             if (!updated[last]) updated.push({} as ShipmentItem);
                             updated[last] = { ...updated[last], name: e.target.value };
                             setForm({ ...form, items: updated });
                           }}
                           className="md:col-span-2 border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                         />
                         <input
                           type="number"
                           step="0.01"
                           min="0"
                           placeholder="Qté"
                           value={form.items?.[form.items.length - 1]?.quantity ?? 1}
                           onChange={(e) => {
                             const last = form.items?.length ? form.items.length - 1 : 0;
                             const updated = [...(form.items ?? [])];
                             if (!updated[last]) updated.push({} as ShipmentItem);
                             updated[last] = { ...updated[last], quantity: parseFloat(e.target.value) || 1 };
                             setForm({ ...form, items: updated });
                           }}
                           className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                         />
                         <select
                           value={form.items?.[form.items.length - 1]?.unit ?? 'PCS'}
                           onChange={(e) => {
                             const last = form.items?.length ? form.items.length - 1 : 0;
                             const updated = [...(form.items ?? [])];
                             if (!updated[last]) updated.push({} as ShipmentItem);
                             updated[last] = { ...updated[last], unit: e.target.value };
                             setForm({ ...form, items: updated });
                           }}
                           className="border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                         >
                           <option value="PCS">PCS</option>
                           <option value="KG">KG</option>
                           <option value="L">L</option>
                           <option value="M">M</option>
                           <option value="M2">M2</option>
                           <option value="CRT">CRT</option>
                         </select>
                         <button
                           type="button"
                           onClick={() => {
                             const updated = [...(form.items ?? []), {} as ShipmentItem];
                             setForm({ ...form, items: updated });
                           }}
                           className="bg-accent text-white rounded-lg px-3 py-2 text-sm hover:bg-accent-strong transition-colors"
                         >
                           + Ligne
                         </button>
                       </div>
                       {items.length > 0 && (
                         <div className="space-y-2">
                           {(form.items ?? []).map((item, idx) => (
                             <div key={idx} className="flex items-center space-x-2 bg-surface rounded-lg px-3 py-2 border border-line">
                               <span className="text-sm font-mono text-ink">{item.sku || '—'}</span>
                               <span className="text-sm text-ink flex-1">{item.name || '—'}</span>
                               <span className="text-sm text-ink-soft">{item.quantity ?? 1} {item.unit || 'PCS'}</span>
                               <button
                                 type="button"
                                 onClick={() => {
                                   const updated = (form.items ?? []).filter((_, i) => i !== idx);
                                   setForm({ ...form, items: updated });
                                 }}
                                 className="text-danger hover:text-danger"
                               >
                                 ✕
                               </button>
                             </div>
                           ))}
                         </div>
                       )}
                     </div>
                   )}
                 </div>

                 {/* Rate Search Section */}
                {form.shipperCountry && form.consigneeCountry && (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <h4 className="font-semibold text-ink flex items-center space-x-2">
                        <Scale size={16} />
                        <span>Tarif négocié (optionnel)</span>
                      </h4>
                      {!showRateSearch ? (
                        <button
                          type="button"
                          onClick={() => setShowRateSearch(true)}
                          className="text-accent hover:text-accent-strong text-sm font-medium flex items-center space-x-1"
                        >
                          <Search size={14} />
                          <span>Rechercher un tarif</span>
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => { setShowRateSearch(false); }}
                          className="text-ink-soft hover:text-ink text-sm"
                        >
                          Fermer
                        </button>
                      )}
                    </div>

                    {showRateSearch && (
                      <div className="bg-bg rounded-lg p-4 border border-line">
                        {loadingRates ? (
                          <div className="flex items-center justify-center py-4">
                            <Loader2 className="h-6 w-6 animate-spin text-accent mr-2" />
                            <span className="text-sm text-ink-soft">Recherche des tarifs pour {form.shipperCountry} → {form.consigneeCountry}...</span>
                          </div>
                        ) : matchingRates.length > 0 ? (
                          <div className="space-y-2">
                            <p className="text-xs text-ink-soft mb-2">{matchingRates.length} tarif(s) trouvé(s) — cliquez pour sélectionner</p>
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
                              {matchingRates.map((rate: ShippingRate) => {
                                const isSelected = form.shippingRateId === rate.id;
                                const estimatedCost = rate.baseRate + (rate.ratePerKg * (form.weightKg || 0));
                                return (
                                  <button
                                    key={rate.id}
                                    type="button"
                                    onClick={() => {
                                      setForm({
                                        ...form,
                                        shippingRateId: rate.id,
                                        carrierId: rate.carrierId || '',
                                      });
                                    }}
                                    className={`text-left p-3 rounded-lg border-2 transition-all ${
                                      isSelected
                                        ? 'border-success/40 bg-success/10 ring-1 ring-success'
                                        : 'border-line hover:border-line bg-surface'
                                    }`}
                                  >
                                    <div className="flex items-center justify-between mb-1">
                                      <span className="font-medium text-sm text-ink">{rate.carrierName || rate.name}</span>
                                      <span className="font-bold text-success">{estimatedCost.toFixed(2)} €</span>
                                    </div>
                                    <div className="text-xs text-ink-soft space-x-2">
                                      <span>{rate.transportMode === 'SEA' ? 'Maritime' : rate.transportMode === 'AIR' ? 'Aérien' : 'Routier'}</span>
                                      {rate.transitDaysMin && rate.transitDaysMax && (
                                        <span>• {rate.transitDaysMin}-{rate.transitDaysMax}j</span>
                                      )}
                                      {rate.ratePerKg > 0 && <span>• {rate.ratePerKg}€/kg</span>}
                                    </div>
                                    {isSelected && (
                                      <div className="mt-1 text-xs text-success font-medium flex items-center space-x-1">
                                        <CheckCircle size={12} />
                                        <span>Tarif sélectionné</span>
                                      </div>
                                    )}
                                  </button>
                                );
                              })}
                            </div>
                          </div>
                        ) : (
                          <div className="text-center py-4">
                            <p className="text-sm text-ink-soft">Aucun tarif trouvé pour cette route.</p>
                            <p className="text-xs text-ink-soft mt-1">Ajoutez des tarifs dans <span className="font-medium">Gestion tarifs</span> ou sélectionnez un transporteur manuellement.</p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {carriers.length > 0 && (
                  <div className="space-y-3">
                    <h4 className="font-semibold text-ink flex items-center space-x-2">
                      <Truck size={16} />
                      <span>Transporteur (optionnel)</span>
                    </h4>
                    <div className="grid md:grid-cols-3 gap-3">
                      {carriers.map((c) => (
                        <label
                          key={c.id}
                          className={`flex items-center space-x-3 p-3 border-2 rounded-lg cursor-pointer transition-colors ${
                            form.carrierId === c.id
                              ? 'border-accent/40 bg-accent-soft'
                              : 'border-line hover:border-line'
                          }`}
                        >
                          <input
                            type="radio"
                            name="carrierId"
                            value={c.id}
                            checked={form.carrierId === c.id}
                            onChange={(e) => setForm({ ...form, carrierId: e.target.value })}
                            className="text-accent"
                          />
                          <div>
                            <div className="text-sm font-medium text-ink">{c.name}</div>
                            <div className="text-xs text-ink-soft">{c.transportModes}</div>
                          </div>
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                <div className="flex justify-end space-x-3 pt-4 border-t border-line">
                  <button
                    type="button"
                    onClick={() => setShowForm(false)}
                    className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
                  >
                    Annuler
                  </button>
                  <button
                    type="submit"
                    disabled={createMutation.isPending}
                    className="px-4 py-2 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2"
                  >
                    {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    <span>Créer l'expédition</span>
                  </button>
                </div>
              </div>
            </form>
          </div>
        )}

        {/* Shipments Table */}
        {shipments.length === 0 ? (
          <div className="bg-surface rounded-lg shadow-lg p-12 text-center">
            <Package className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink mb-2">Aucune expédition</h3>
            <p className="text-ink-soft mb-6">
              Créez votre première expédition pour commencer
            </p>
            <button
              onClick={() => { setShowForm(true); setForm({ ...EMPTY_FORM }); }}
              className="bg-accent text-white px-6 py-3 rounded-lg hover:bg-accent-strong transition-colors inline-flex items-center space-x-2"
            >
              <Plus size={20} />
              <span>Nouvelle expédition</span>
            </button>
          </div>
        ) : (
          <div className="bg-surface rounded-lg shadow-lg overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b">
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      N° Commande
                    </th>
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      Statut
                    </th>
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      Itinéraire
                    </th>
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      Transporteur
                    </th>
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      Coût
                    </th>
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      Date
                    </th>
                    <th className="text-left py-3 px-4 font-semibold text-sm text-ink-soft">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {shipments.map((shipment) => {
                    const status = STATUS_CONFIG[shipment.status] || STATUS_CONFIG.DRAFT;
                    const isExpanded = expandedId === shipment.id;
                    return (
                      <Fragment key={shipment.id}>
                        <tr
                          className="border-b hover:bg-bg cursor-pointer"
                          onClick={() => toggleExpand(shipment.id)}
                        >
                          <td className="py-3 px-4">
                            <span className="font-mono text-sm font-medium text-ink">
                              {shipment.orderNumber}
                            </span>
                          </td>
                          <td className="py-3 px-4">
                            <span
                              className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${status.bg} ${status.color}`}
                            >
                              {status.label}
                            </span>
                          </td>
                          <td className="py-3 px-4">
                            <div className="flex items-center space-x-2 text-sm text-ink">
                              <span>{shipment.shipperCity || '—'}</span>
                              <ArrowRight size={14} className="text-ink-soft" />
                              <span>{shipment.consigneeCity || '—'}</span>
                            </div>
                          </td>
                          <td className="py-3 px-4 text-sm text-ink">
                            {shipment.carrierName || '—'}
                          </td>
                          <td className="py-3 px-4 text-sm font-medium text-ink">
                            {shipment.finalCost != null
                              ? `${shipment.finalCost.toFixed(2)} €`
                              : shipment.quotedCost != null
                              ? `${shipment.quotedCost.toFixed(2)} €`
                              : '—'}
                          </td>
                          <td className="py-3 px-4 text-sm text-ink-soft">
                            {new Date(shipment.createdAt).toLocaleDateString('fr-FR')}
                          </td>
                          <td className="py-3 px-4">
                            <div className="flex items-center space-x-1" onClick={(e) => e.stopPropagation()}>
                              {shipment.status === 'DRAFT' && (
                                <button
                                  onClick={() => statusMutation.mutate({ id: shipment.id, status: 'BOOKED' })}
                                  className="p-1.5 text-accent hover:bg-accent/20 rounded transition-colors"
                                  title="Réserver"
                                >
                                  <Truck size={14} />
                                </button>
                              )}
                              {shipment.status === 'BOOKED' && (
                                <button
                                  onClick={() => statusMutation.mutate({ id: shipment.id, status: 'IN_TRANSIT' })}
                                  className="p-1.5 text-warning hover:bg-warning/10 rounded transition-colors"
                                  title="En transit"
                                >
                                  <Package size={14} />
                                </button>
                              )}
                              {shipment.status === 'IN_TRANSIT' && (
                                <button
                                  onClick={() => statusMutation.mutate({ id: shipment.id, status: 'DELIVERED' })}
                                  className="p-1.5 text-success hover:bg-success/10 rounded transition-colors"
                                  title="Livré"
                                >
                                  <CheckCircle size={14} />
                                </button>
                              )}
                              <button
                                onClick={() => toggleExpand(shipment.id)}
                                className="p-1.5 text-ink-soft hover:bg-surface-2 rounded transition-colors"
                                title="Détails"
                              >
                                <Eye size={14} />
                              </button>
                              <button
                                onClick={() => exportLabelMutation.mutate(shipment.id)}
                                disabled={exportLabelMutation.isPending}
                                className="p-1.5 text-accent hover:bg-accent-soft rounded transition-colors"
                                title="Étiquette PDF"
                              >
                                <FileText size={14} />
                              </button>
                              <button
                                onClick={() => setDeleteId(shipment.id)}
                                className="p-1.5 text-danger hover:bg-danger/10 rounded transition-colors"
                                title="Supprimer"
                              >
                                <Trash2 size={14} />
                              </button>
                              <ChevronDown
                                size={14}
                                className={`text-ink-soft transition-transform ${
                                  isExpanded ? 'rotate-180' : ''
                                }`}
                              />
                            </div>
                          </td>
                        </tr>
                        {isExpanded && (
                          <tr>
                            <td colSpan={7} className="px-4 py-4 bg-bg">
                              <div className="grid md:grid-cols-2 gap-6">
                                <div>
                                  <h4 className="font-semibold text-ink mb-2">Détails</h4>                                  <dl className="text-sm space-y-1">
                                    <div className="flex">
                                      <dt className="text-ink-soft w-40">Expéditeur:</dt>
                                      <dd className="text-ink">{shipment.shipperName} — {shipment.shipperCity}</dd>
                                    </div>
                                    <div className="flex">
                                      <dt className="text-ink-soft w-40">Destinataire:</dt>
                                      <dd className="text-ink">{shipment.consigneeName} — {shipment.consigneeCity}</dd>
                                    </div>
                                    <div className="flex">
                                      <dt className="text-ink-soft w-40">Marchandises:</dt>
                                      <dd className="text-ink">{shipment.goodsDescription || '—'}</dd>
                                    </div>
                                    <div className="flex">
                                      <dt className="text-ink-soft w-40">Poids / Volume:</dt>
                                      <dd className="text-ink">{shipment.weightKg || '—'} kg / {shipment.volumeM3 || '—'} m³</dd>
                                    </div>
                                    <div className="flex">
                                      <dt className="text-ink-soft w-40">Incoterm:</dt>
                                      <dd className="text-ink">{shipment.incotermCode || '—'}</dd>
                                    </div>
                                  </dl>

                                  <div className="mt-4 pt-4 border-t border-line">
                                    <h4 className="font-semibold text-ink mb-3 flex items-center space-x-2">
                                      <FileText size={16} />
                                      <span>Documents</span>
                                    </h4>
                                    <div className="flex flex-wrap gap-2">
                                      <button
                                        onClick={() => exportCmrMutation.mutate(shipment.id)}
                                        disabled={exportCmrMutation.isPending}
                                        className="inline-flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium border border-line rounded-md bg-surface hover:bg-bg transition-colors disabled:opacity-50 text-ink"
                                      >
                                        <FileText size={12} />
                                        <span>CMR</span>
                                        {exportCmrMutation.isPending && <Loader2 className="h-3 w-3 animate-spin" />}
                                      </button>
                                      <button
                                        onClick={() => {
                                          if (!shipment.isDangerous) {
                                            toast('Cette expédition ne contient pas de marchandises dangereuses', { icon: '⚠️' });
                                          }
                                          exportDgdMutation.mutate(shipment.id);
                                        }}
                                        disabled={exportDgdMutation.isPending}
                                        className="inline-flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium border border-line rounded-md bg-surface hover:bg-bg transition-colors disabled:opacity-50 text-ink"
                                      >
                                        <FileText size={12} />
                                        <span>DGD</span>
                                        {exportDgdMutation.isPending && <Loader2 className="h-3 w-3 animate-spin" />}
                                      </button>
                                      <button
                                        onClick={() => exportCertOrigineMutation.mutate(shipment.id)}
                                        disabled={exportCertOrigineMutation.isPending}
                                        className="inline-flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium border border-line rounded-md bg-surface hover:bg-bg transition-colors disabled:opacity-50 text-ink"
                                      >
                                        <FileText size={12} />
                                        <span>Certificat d'origine</span>
                                        {exportCertOrigineMutation.isPending && <Loader2 className="h-3 w-3 animate-spin" />}
                                      </button>
                                    </div>
                                  </div>

                                  <div className="mt-4 pt-4 border-t border-line">
                                    <h4 className="font-semibold text-ink mb-3 flex items-center space-x-2">
                                      <Link2 size={16} />
                                      <span>Portail client</span>
                                    </h4>
                                    <button
                                      onClick={() => shareLinkMutation.mutate(shipment.id)}
                                      disabled={shareLinkMutation.isPending}
                                      className="inline-flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium border border-line rounded-md bg-surface hover:bg-bg transition-colors disabled:opacity-50 text-ink"
                                    >
                                      <Link2 size={12} />
                                      <span>Générer un lien de suivi client</span>
                                      {shareLinkMutation.isPending && <Loader2 className="h-3 w-3 animate-spin" />}
                                    </button>
                                    {clientLinks[shipment.id] && (
                                      <div className="mt-2 flex items-center gap-2 bg-accent-soft rounded-lg px-3 py-2">
                                        <input
                                          type="text"
                                          readOnly
                                          value={clientLinks[shipment.id]}
                                          className="flex-1 text-xs bg-transparent text-accent-strong outline-none"
                                        />
                                        <button
                                          onClick={() => {
                                            navigator.clipboard.writeText(clientLinks[shipment.id]);
                                            setCopiedLinkId(shipment.id);
                                            setTimeout(() => setCopiedLinkId((c) => (c === shipment.id ? null : c)), 2000);
                                          }}
                                          className="p-1 hover:bg-accent/20 rounded"
                                          title="Copier"
                                        >
                                          {copiedLinkId === shipment.id ? (
                                            <Check size={14} className="text-success" />
                                          ) : (
                                            <Copy size={14} className="text-accent" />
                                          )}
                                        </button>
                                        <a
                                          href={clientLinks[shipment.id]}
                                          target="_blank"
                                          rel="noopener noreferrer"
                                          className="p-1 hover:bg-accent/20 rounded"
                                          title="Voir comme le client"
                                        >
                                          <ExternalLink size={14} className="text-accent" />
                                        </a>
                                      </div>
                                    )}
                                  </div>
                                </div>
                                <div>
                                  <h4 className="font-semibold text-ink mb-2">Suivi</h4>
                                  <LiveTrackingPanel
                                    shipmentId={shipment.id}
                                    mode={shipment.carrierName?.includes('Air') ? 'AIR' : shipment.carrierName?.includes('Sea') || shipment.carrierName?.includes('Maritime') ? 'MARITIME' : 'ROAD'}
                                  />
                                   <div className="mt-4">
                                     <h4 className="font-semibold text-ink mb-2 text-sm">Événements de suivi</h4>
                                     <TrackingTimeline events={shipment.trackingEvents || []} />
                                   </div>
                                 </div>
                               </div>
                               {shipment.items && shipment.items.length > 0 && (
                                 <div className="mt-4 pt-4 border-t border-line">
                                   <h4 className="font-semibold text-ink mb-2 text-sm flex items-center space-x-2">
                                     <Package size={14} />
                                     <span>Articles ({shipment.items.reduce((s, it) => s + (it.quantity ?? 1), 0)})</span>
                                   </h4>
                                   <ul className="space-y-1">
                                     {shipment.items.map((item, idx) => (
                                       <li key={idx} className="flex items-center justify-between text-xs bg-surface rounded px-2 py-1 border border-line">
                                         <span className="font-mono text-ink">{item.sku || '—'}</span>
                                         <span className="text-ink-soft flex-1 ml-2 truncate">{item.name || ''}</span>
                                         <span className="text-ink-soft ml-2">{item.quantity} {item.unit}</span>
                                       </li>
                                     ))}
                                   </ul>
                                 </div>
                               )}
                               <ShipmentReceivingSection shipmentId={shipment.id} />
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {!Array.isArray(data) && totalPages > 1 && (
          <div className="bg-surface rounded-lg shadow-lg mt-4 py-3">
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          </div>
        )}

        {/* Delete Confirmation */}
        {deleteId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4 p-6">
              <h3 className="text-lg font-bold text-ink mb-4">Confirmer la suppression</h3>
              <p className="text-ink-soft mb-6">
                Êtes-vous sûr de vouloir supprimer cette expédition ? Cette action est irréversible.
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

export default Shipments;
