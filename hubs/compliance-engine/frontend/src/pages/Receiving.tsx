import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { ClipboardList, Plus, Loader2, ScanLine, Trash2, CheckCircle, TriangleAlert, PackageX, Truck, Box, Package, ExternalLink } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface Warehouse {
  id: string;
  name: string;
  code?: string;
}

interface Shipment {
  id: string;
  orderNumber: string;
  status: string;
  goodsDescription?: string;
}

interface ShipmentBrief {
  id: string;
  orderNumber: string;
  status: string;
  goodsDescription?: string;
  packagesCount?: number;
  goodsValue?: number;
  currency?: string;
}

interface InventoryItem {
  id: string;
  name: string;
  sku?: string;
  unit?: string;
}

interface ReceivingOrder {
  id: string;
  orderNumber: string;
  reference?: string;
  warehouseId: string;
  shipmentId?: string;
  status: 'DRAFT' | 'RECEIVING' | 'COMPLETED' | 'CANCELLED';
  notes?: string;
  createdAt?: string;
}

interface ReceivingLine {
  id: string;
  itemId: string;
  quantityExpected: number;
  quantityReceived: number;
  quantityDamaged: number;
  unit?: string;
}

interface ReceivingScan {
  id: string;
  itemId: string;
  barcode?: string;
  quantity: number;
  lotNumber?: string;
  expiryDate?: string;
  serialNumber?: string;
  notes?: string;
  scannedAt?: string;
}

interface Discrepancy {
  id: string;
  receivingOrderId: string;
  itemId: string;
  type: 'OVER' | 'SHORT' | 'DAMAGED' | 'UNEXPECTED';
  expectedQty: number;
  actualQty: number;
  difference: number;
  resolutionStatus: 'OPEN' | 'RESOLVED' | 'CANCELLED';
  notes?: string;
  createdAt?: string;
}

interface OrderDetail {
  order: ReceivingOrder;
  lines: ReceivingLine[];
  scans: ReceivingScan[];
  discrepancies: Discrepancy[];
  totalExpected: number;
  totalReceived: number;
  openDiscrepancyCount: number;
  remaining: number;
  shipment?: ShipmentBrief;
}

const statusLabels: Record<string, string> = {
  DRAFT: 'Brouillon',
  RECEIVING: 'En réception',
  COMPLETED: 'Clôturé',
  CANCELLED: 'Annulé',
};

const statusColors: Record<string, string> = {
  DRAFT: 'bg-surface-2 text-ink-soft',
  RECEIVING: 'bg-accent-soft text-accent-strong',
  COMPLETED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

const discLabels: Record<string, string> = {
  OVER: 'Excédent',
  SHORT: 'Manquant',
  DAMAGED: 'Endommagé',
  UNEXPECTED: 'Imprévu',
};

const discColors: Record<string, string> = {
  OVER: 'bg-warning/10 text-warning',
  SHORT: 'bg-danger/10 text-danger',
  DAMAGED: 'bg-accent-soft text-accent-strong',
  UNEXPECTED: 'bg-accent-soft text-accent-strong',
};

type ApiError = { response?: { data?: { message?: string } } };

const Receiving = () => {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const canManage = useAuthStore((s) => s.hasMinimumRole('MANAGER'));
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(searchParams.get('openCreate') === '1');
  const [createForm, setCreateForm] = useState<{ warehouseId: string; shipmentId: string; lines: { itemId: string; quantityExpected: number }[] }>({
    warehouseId: '',
    shipmentId: searchParams.get('shipmentId') ?? '',
    lines: [{ itemId: '', quantityExpected: 1 }],
  });
  const [reference, setReference] = useState('');
  const [referenceTouched, setReferenceTouched] = useState(false);
  const [scanForm, setScanForm] = useState<{ itemId: string; barcode: string; quantity: number; lotNumber: string; expiryDate: string; serialNumber: string }>({
    itemId: '',
    barcode: '',
    quantity: 1,
    lotNumber: '',
    expiryDate: '',
    serialNumber: '',
  });
  const [damageForm, setDamageForm] = useState<{ itemId: string; quantity: number; notes: string }>({
    itemId: '',
    quantity: 1,
    notes: '',
  });

  const { data: warehousesData } = useQuery({
    queryKey: ['warehouses'],
    queryFn: async () => (await incokalkAPI.warehouses.list())?.data ?? [],
  });
  const warehouses = (Array.isArray(warehousesData) ? warehousesData : []) as Warehouse[];
  const warehouseName = (id: string) => warehouses.find((w) => w.id === id)?.name ?? id;

  const { data: itemsData } = useQuery({
    queryKey: ['inventory-items-all'],
    queryFn: async () => (await incokalkAPI.inventory.items.list())?.data ?? [],
  });
  const items = (Array.isArray(itemsData) ? itemsData : []) as InventoryItem[];
  const itemName = (id: string) => items.find((i) => i.id === id)?.name ?? id;
  const itemSku = (id: string) => items.find((i) => i.id === id)?.sku ?? '';

  const { data: shipmentsData } = useQuery({
    queryKey: ['shipments-all'],
    queryFn: async () => {
      const res = await incokalkAPI.shipments.getAll();
      return Array.isArray(res?.data) ? res.data : [];
    },
  });
  const shipments = (Array.isArray(shipmentsData) ? shipmentsData : []) as Shipment[];

  const selectedShipment = shipments.find((s) => s.id === createForm.shipmentId);
  const autoReference = selectedShipment
    ? selectedShipment.orderNumber + (selectedShipment.goodsDescription ? ` — ${selectedShipment.goodsDescription}` : '')
    : '';
  const effectiveReference = referenceTouched ? reference : autoReference;

  const { data, isLoading } = useQuery({
    queryKey: ['receivings', statusFilter],
    queryFn: async () => {
      const res = await incokalkAPI.receivings.list({ status: statusFilter || undefined });
      return (res?.data ?? []) as ReceivingOrder[];
    },
  });
  const orders = Array.isArray(data) ? data : [];

  const { data: detailData, refetch: refetchDetail } = useQuery({
    queryKey: ['receiving-detail', selectedId],
    queryFn: async () => (await incokalkAPI.receivings.get(selectedId!))?.data as OrderDetail,
    enabled: !!selectedId,
  });

  const { data: discrepanciesData } = useQuery({
    queryKey: ['receiving-discrepancies'],
    queryFn: async () => (await incokalkAPI.receivings.discrepancies())?.data ?? [],
  });
  const discrepancies = (Array.isArray(discrepanciesData) ? discrepanciesData : []) as Discrepancy[];

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['receivings'] });
    queryClient.invalidateQueries({ queryKey: ['receiving-detail'] });
    queryClient.invalidateQueries({ queryKey: ['receiving-discrepancies'] });
    queryClient.invalidateQueries({ queryKey: ['inventory-balances'] });
  };

  const createMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.receivings.create({
        warehouseId: createForm.warehouseId,
        shipmentId: createForm.shipmentId || undefined,
        reference: effectiveReference || undefined,
        lines: createForm.lines
          .filter((l) => l.itemId)
          .map((l) => ({ itemId: l.itemId, quantityExpected: Number(l.quantityExpected) || 0, unit: undefined })),
      }),
    onSuccess: (res) => {
      toast.success('Bon de réception créé');
      setShowCreate(false);
      setSearchParams({}, { replace: true });
      setCreateForm({ warehouseId: '', shipmentId: '', lines: [{ itemId: '', quantityExpected: 1 }] });
      setReference('');
      setReferenceTouched(false);
      setSelectedId(res?.data?.id ?? null);
      invalidateAll();
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de la création'),
  });

  const scanMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.receivings.scan(selectedId!, {
        itemId: scanForm.itemId || undefined,
        barcode: scanForm.barcode || undefined,
        quantity: Number(scanForm.quantity) || 1,
        lotNumber: scanForm.lotNumber || undefined,
        expiryDate: scanForm.expiryDate || undefined,
        serialNumber: scanForm.serialNumber || undefined,
      }),
    onSuccess: () => {
      toast.success('Scan enregistré — stock posté');
      setScanForm({ itemId: '', barcode: '', quantity: 1, lotNumber: '', expiryDate: '', serialNumber: '' });
      invalidateAll();
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors du scan'),
  });

  const damageMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.receivings.damage(selectedId!, {
        itemId: damageForm.itemId,
        quantity: Number(damageForm.quantity) || 1,
        notes: damageForm.notes || undefined,
      }),
    onSuccess: () => {
      toast.success('Marchandise endommagée signalée');
      setDamageForm({ itemId: '', quantity: 1, notes: '' });
      invalidateAll();
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur'),
  });

  const completeMutation = useMutation({
    mutationFn: () => incokalkAPI.receivings.complete(selectedId!),
    onSuccess: () => {
      toast.success('Bon de réception clôturé');
      invalidateAll();
      refetchDetail();
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de la clôture'),
  });

  const cancelMutation = useMutation({
    mutationFn: () => incokalkAPI.receivings.cancel(selectedId!),
    onSuccess: () => {
      toast.success('Bon annulé');
      invalidateAll();
      refetchDetail();
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de l’annulation'),
  });

  const resolveMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.receivings.resolveDiscrepancy(id),
    onSuccess: () => {
      toast.success('Écart résolu');
      invalidateAll();
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur'),
  });

  const openCreate = () => setShowCreate(true);

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Réception marchandises</h1>
          <p className="text-ink-soft mt-1">Bons de réception, scan code-barres et écarts</p>
        </div>
        {canManage && (
          <button
            onClick={openCreate}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Nouveau bon
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1">
          <div className="bg-surface rounded-xl border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line flex items-center justify-between">
              <h2 className="text-lg font-semibold text-ink">Bons de réception</h2>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="text-xs px-2 py-1 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent"
              >
                <option value="">Tous</option>
                {Object.entries(statusLabels).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
            {isLoading ? (
              <div className="px-6 py-12 text-center text-ink-soft">
                <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
                Chargement...
              </div>
            ) : orders.length === 0 ? (
              <div className="px-6 py-12 text-center text-ink-soft">
                <ClipboardList size={32} className="mx-auto mb-3 text-ink-soft" />
                <p>Aucun bon de réception</p>
              </div>
            ) : (
              <ul className="divide-y divide-line max-h-[60vh] overflow-y-auto">
                {orders.map((o) => (
                  <li
                    key={o.id}
                    onClick={() => setSelectedId(o.id)}
                    className={`px-6 py-3 cursor-pointer hover:bg-bg transition-colors ${selectedId === o.id ? 'bg-accent-soft' : ''}`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-mono font-medium text-ink">{o.orderNumber}</span>
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[o.status]}`}>
                        {statusLabels[o.status]}
                      </span>
                    </div>
                    <p className="text-xs text-ink-soft mt-1">
                      {warehouseName(o.warehouseId)}
                      {o.reference ? ` · ${o.reference}` : ''}
                      {o.createdAt ? ` · ${new Date(o.createdAt).toLocaleDateString()}` : ''}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="lg:col-span-2">
          {selectedId && detailData ? (
            <div className="space-y-6">
              <div className="bg-surface rounded-xl border border-line p-6">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold text-ink font-mono">{detailData.order.orderNumber}</h2>
                    <div className="flex items-center gap-3 mt-1">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[detailData.order.status]}`}>
                        {statusLabels[detailData.order.status]}
                      </span>
                      <span className="text-sm text-ink-soft">{warehouseName(detailData.order.warehouseId)}</span>
                    </div>
                    {detailData.order.reference && (
                      <p className="text-sm text-ink-soft mt-1">Réf : {detailData.order.reference}</p>
                    )}
                    {detailData.shipment && (
                      <Link
                        to="/shipments"
                        className="mt-2 inline-flex items-center gap-2 text-xs font-medium bg-accent/10 text-accent-strong border border-accent/40 px-2.5 py-1 rounded-full hover:bg-accent-soft transition-colors"
                      >
                        <Truck size={12} />
                        Expédition {detailData.shipment.orderNumber}
                        <span className="font-normal text-accent">· {detailData.shipment.status}</span>
                        {detailData.shipment.packagesCount != null && (
                          <span className="font-normal text-accent">· {detailData.shipment.packagesCount} colis</span>
                        )}
                        <ExternalLink size={11} />
                      </Link>
                    )}
                  </div>
                  <div className="grid grid-cols-3 gap-4 text-center">
                    <div>
                      <p className="text-xs text-ink-soft uppercase">Attendu</p>
                      <p className="text-lg font-bold text-ink">{detailData.totalExpected}</p>
                    </div>
                    <div>
                      <p className="text-xs text-ink-soft uppercase">Reçu</p>
                      <p className="text-lg font-bold text-success">{detailData.totalReceived}</p>
                    </div>
                    <div>
                      <p className="text-xs text-ink-soft uppercase">Écarts</p>
                      <p className={`text-lg font-bold ${detailData.openDiscrepancyCount > 0 ? 'text-warning' : 'text-ink'}`}>
                        {detailData.openDiscrepancyCount}
                      </p>
                    </div>
                  </div>
                </div>
                {(detailData.order.status === 'DRAFT' || detailData.order.status === 'RECEIVING') && (
                  <div className="mt-6 flex flex-wrap gap-2">
                    <button
                      onClick={() => scanMutation.mutate()}
                      disabled={scanMutation.isPending || !scanForm.itemId && !scanForm.barcode}
                      className="flex items-center gap-2 bg-success text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-success/90 disabled:opacity-50 transition-colors"
                    >
                      {scanMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <ScanLine size={16} />}
                      Scanner / Enregistrer
                    </button>
                    <button
                      onClick={() => setDamageForm({ ...damageForm, itemId: '' })}
                      className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-accent-strong transition-colors"
                    >
                      <PackageX size={16} />
                      Endommagé
                    </button>
                    <button
                      onClick={() => completeMutation.mutate()}
                      disabled={completeMutation.isPending}
                      className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
                    >
                      {completeMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle size={16} />}
                      Clôturer
                    </button>
                    <button
                      onClick={() => cancelMutation.mutate()}
                      disabled={cancelMutation.isPending}
                      className="flex items-center gap-2 bg-danger/10 text-danger px-4 py-2 rounded-lg text-sm font-medium hover:bg-danger/10 disabled:opacity-50 transition-colors"
                    >
                      {cancelMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Trash2 size={16} />}
                      Annuler
                    </button>
                  </div>
                )}
              </div>

              <div className="bg-surface rounded-xl border border-line p-6">
                <h3 className="text-lg font-semibold text-ink mb-4">Scanner un article</h3>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Code-barres</label>
                    <input
                      type="text"
                      value={scanForm.barcode}
                      onChange={(e) => setScanForm({ ...scanForm, barcode: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm font-mono"
                      placeholder="3760123456789"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Article (si code inconnu)</label>
                    <select
                      value={scanForm.itemId}
                      onChange={(e) => setScanForm({ ...scanForm, itemId: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      <option value="">— auto (code-barres) —</option>
                      {items.map((i) => (
                        <option key={i.id} value={i.id}>{i.name}{i.sku ? ` (${i.sku})` : ''}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">Quantité</label>
                    <input
                      type="number"
                      value={scanForm.quantity}
                      onChange={(e) => setScanForm({ ...scanForm, quantity: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      min={1}
                      step={0.01}
                    />
                  </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-3">
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">N° de lot</label>
                    <input
                      type="text"
                      value={scanForm.lotNumber}
                      onChange={(e) => setScanForm({ ...scanForm, lotNumber: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">DLUO</label>
                    <input
                      type="date"
                      value={scanForm.expiryDate}
                      onChange={(e) => setScanForm({ ...scanForm, expiryDate: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-ink-soft mb-1">N° de série</label>
                    <input
                      type="text"
                      value={scanForm.serialNumber}
                      onChange={(e) => setScanForm({ ...scanForm, serialNumber: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                </div>
              </div>

              <div className="bg-surface rounded-xl border border-line overflow-hidden">
                <div className="px-6 py-4 border-b border-line">
                  <h3 className="text-lg font-semibold text-ink">Lignes attendues</h3>
                </div>
                {detailData.lines.length === 0 ? (
                  <p className="px-6 py-8 text-sm text-ink-soft text-center">Aucune ligne attendue.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead>
                        <tr className="bg-bg border-b border-line">
                          <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Article</th>
                          <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Attendu</th>
                          <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Reçu</th>
                          <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Endommagé</th>
                          <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Reste</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-line">
                        {detailData.lines.map((l) => (
                          <tr key={l.id}>
                            <td className="px-6 py-3 text-sm font-medium text-ink">{itemName(l.itemId)}</td>
                            <td className="px-6 py-3 text-right text-sm text-ink-soft">{l.quantityExpected} {l.unit ?? ''}</td>
                            <td className="px-6 py-3 text-right text-sm text-success">{l.quantityReceived}</td>
                            <td className="px-6 py-3 text-right text-sm text-accent">{l.quantityDamaged}</td>
                            <td className="px-6 py-3 text-right text-sm font-semibold text-ink">
                              {l.quantityExpected - l.quantityReceived}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-surface rounded-xl border border-line overflow-hidden">
                  <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                    <ScanLine size={16} className="text-success" />
                    <h3 className="text-lg font-semibold text-ink">Scans ({detailData.scans.length})</h3>
                  </div>
                  {detailData.scans.length === 0 ? (
                    <p className="px-6 py-6 text-sm text-ink-soft text-center">Aucun scan.</p>
                  ) : (
                    <ul className="divide-y divide-line max-h-64 overflow-y-auto">
                      {detailData.scans.map((s) => {
                        const sku = itemSku(s.itemId);
                        return (
                          <li key={s.id} className="px-6 py-3">
                            <div className="flex items-center justify-between">
                              <span className="text-sm font-medium text-ink">{itemName(s.itemId)}</span>
                              <span className="text-sm font-semibold text-success">+{s.quantity}</span>
                            </div>
                            <p className="text-xs text-ink-soft mt-0.5">
                              {[s.barcode, s.lotNumber, s.serialNumber].filter(Boolean).join(' · ')}
                              {s.expiryDate ? ` · DLUO ${s.expiryDate}` : ''}
                              {s.scannedAt ? ` · ${new Date(s.scannedAt).toLocaleString()}` : ''}
                            </p>
                            <div className="mt-1.5 flex items-center gap-1.5">
                              <Link
                                to={`/inventory-items${sku ? `?q=${encodeURIComponent(sku)}` : ''}`}
                                className="inline-flex items-center gap-1 text-[11px] font-medium text-ink-soft hover:text-accent bg-bg hover:bg-accent-soft border border-line px-1.5 py-0.5 rounded transition-colors"
                                title={`Fiche SKU ${sku || ''}`}
                              >
                                <Package size={10} /> SKU
                              </Link>
                              <Link
                                to={`/logistics${sku ? `?sku=${encodeURIComponent(sku)}` : ''}`}
                                className="inline-flex items-center gap-1 text-[11px] font-medium text-ink-soft hover:text-success bg-bg hover:bg-success/20 border border-line px-1.5 py-0.5 rounded transition-colors"
                                title="Packaging & colisage"
                              >
                                <Box size={10} /> Packaging
                              </Link>
                              <Link
                                to={`/inventory${sku ? `?q=${encodeURIComponent(sku)}` : ''}`}
                                className="inline-flex items-center gap-1 text-[11px] font-medium text-ink-soft hover:text-accent bg-bg hover:bg-accent/20 border border-line px-1.5 py-0.5 rounded transition-colors"
                                title="Voir le stock"
                              >
                                <ClipboardList size={10} /> Stock
                              </Link>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>

                <div className="bg-surface rounded-xl border border-line overflow-hidden">
                  <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                    <TriangleAlert size={16} className="text-warning" />
                    <h3 className="text-lg font-semibold text-ink">Écarts du bon ({detailData.discrepancies.length})</h3>
                  </div>
                  {detailData.discrepancies.length === 0 ? (
                    <p className="px-6 py-6 text-sm text-ink-soft text-center">Aucun écart.</p>
                  ) : (
                    <ul className="divide-y divide-line max-h-64 overflow-y-auto">
                      {detailData.discrepancies.map((d) => (
                        <li key={d.id} className="px-6 py-3">
                          <div className="flex items-center justify-between">
                            <div>
                              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${discColors[d.type]}`}>
                                {discLabels[d.type]}
                              </span>
                              <span className="text-sm font-medium text-ink ml-2">{itemName(d.itemId)}</span>
                            </div>
                            <span className={`text-xs font-medium ${d.resolutionStatus === 'OPEN' ? 'text-warning' : 'text-success'}`}>
                              {d.resolutionStatus === 'OPEN' ? 'Ouvert' : 'Résolu'}
                            </span>
                          </div>
                          <p className="text-xs text-ink-soft mt-1">
                            Attendu {d.expectedQty} · Constaté {d.actualQty} · Écart {d.difference > 0 ? '+' : ''}{d.difference}
                          </p>
                          {d.notes && <p className="text-xs text-ink-soft mt-0.5">{d.notes}</p>}
                          {d.resolutionStatus === 'OPEN' && (
                            <button
                              onClick={() => resolveMutation.mutate(d.id)}
                              disabled={resolveMutation.isPending}
                              className="mt-2 text-xs text-accent hover:text-accent-strong font-medium"
                            >
                              Marquer résolu
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="bg-surface rounded-xl border border-line px-6 py-16 text-center text-ink-soft">
              <ClipboardList size={40} className="mx-auto mb-3 text-ink-soft" />
              <p>Sélectionnez un bon de réception pour voir son détail.</p>
            </div>
          )}
        </div>
      </div>

      <div className="mt-8 bg-surface rounded-xl border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line flex items-center gap-2">
          <PackageX size={16} className="text-danger" />
          <h2 className="text-lg font-semibold text-ink">Écarts globaux ({discrepancies.length})</h2>
        </div>
        {discrepancies.length === 0 ? (
          <p className="px-6 py-6 text-sm text-ink-soft text-center">Aucun écart signalé.</p>
        ) : (
          <ul className="divide-y divide-line">
            {discrepancies.map((d) => (
              <li key={d.id} className="px-6 py-3 flex items-center justify-between gap-4">
                <div>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${discColors[d.type]}`}>
                    {discLabels[d.type]}
                  </span>
                  <span className="text-sm font-medium text-ink ml-2">{itemName(d.itemId)}</span>
                  <span className="text-xs text-ink-soft ml-2">
                    {warehouseName(detailData?.order.warehouseId ?? '') || '—'}
                  </span>
                  <p className="text-xs text-ink-soft mt-1">
                    Attendu {d.expectedQty} · Constaté {d.actualQty} · Écart {d.difference > 0 ? '+' : ''}{d.difference}
                    {d.createdAt ? ` · ${new Date(d.createdAt).toLocaleDateString()}` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-medium ${d.resolutionStatus === 'OPEN' ? 'text-warning' : 'text-success'}`}>
                    {d.resolutionStatus === 'OPEN' ? 'Ouvert' : 'Résolu'}
                  </span>
                  {d.resolutionStatus === 'OPEN' && (
                    <button
                      onClick={() => resolveMutation.mutate(d.id)}
                      disabled={resolveMutation.isPending}
                      className="text-xs text-accent hover:text-accent-strong font-medium"
                    >
                      Résoudre
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => { setShowCreate(false); setSearchParams({}, { replace: true }); }}
          />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">Nouveau bon de réception</h3>
            <form
              onSubmit={(e) => { e.preventDefault(); createMutation.mutate(); }}
              className="space-y-4"
            >
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Entrepôt *</label>
                <select
                  value={createForm.warehouseId}
                  onChange={(e) => setCreateForm({ ...createForm, warehouseId: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  required
                >
                  <option value="">— Sélectionner —</option>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>{w.name}{w.code ? ` (${w.code})` : ''}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Expédition liée</label>
                <select
                  value={createForm.shipmentId}
                  onChange={(e) => {
                    setCreateForm({ ...createForm, shipmentId: e.target.value });
                    setReferenceTouched(false);
                  }}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                >
                  <option value="">— Aucune —</option>
                  {shipments.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.orderNumber}{s.goodsDescription ? ` · ${s.goodsDescription}` : ''}
                    </option>
                  ))}
                </select>
                 <p className="text-xs text-ink-soft mt-1">
                   La référence est préremplie depuis l'expédition. Si aucune ligne n'est ajoutée, les lignes attendues seront dérivées automatiquement des articles de l'expédition.
                 </p>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Référence</label>
                <input
                  type="text"
                  value={effectiveReference}
                  onChange={(e) => { setReference(e.target.value); setReferenceTouched(true); }}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Bon de commande, shipment..."
                />
              </div>
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm font-medium text-ink">Lignes attendues</label>
                  <button
                    type="button"
                    onClick={() => setCreateForm({ ...createForm, lines: [...createForm.lines, { itemId: '', quantityExpected: 1 }] })}
                    className="text-xs text-accent hover:text-accent-strong font-medium flex items-center gap-1"
                  >
                    <Plus size={12} /> Ajouter une ligne
                  </button>
                </div>
                <div className="space-y-2">
                  {createForm.lines.map((line, idx) => (
                    <div key={idx} className="flex gap-2">
                      <select
                        value={line.itemId}
                        onChange={(e) => {
                          const lines = [...createForm.lines];
                          lines[idx] = { ...line, itemId: e.target.value };
                          setCreateForm({ ...createForm, lines });
                        }}
                        className="flex-1 px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      >
                        <option value="">— Article —</option>
                        {items.map((i) => (
                          <option key={i.id} value={i.id}>{i.name}{i.sku ? ` (${i.sku})` : ''}</option>
                        ))}
                      </select>
                      <input
                        type="number"
                        value={line.quantityExpected}
                        onChange={(e) => {
                          const lines = [...createForm.lines];
                          lines[idx] = { ...line, quantityExpected: parseFloat(e.target.value) || 0 };
                          setCreateForm({ ...createForm, lines });
                        }}
                        className="w-24 px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                        min={0}
                        step={0.01}
                      />
                      <button
                        type="button"
                        onClick={() => setCreateForm({ ...createForm, lines: createForm.lines.filter((_, i) => i !== idx) })}
                        disabled={createForm.lines.length === 1}
                        className="px-2 py-2 text-ink-soft hover:text-danger disabled:opacity-30 transition-colors"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => { setShowCreate(false); setSearchParams({}, { replace: true }); }}
                  className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || !createForm.warehouseId}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {createMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Créer
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {selectedId && damageForm.itemId !== '' && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDamageForm({ ...damageForm, itemId: '' })} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-1">Marchandise endommagée</h3>
            <p className="text-sm text-ink-soft mb-4">{detailData?.order.orderNumber}</p>
            <form
              onSubmit={(e) => { e.preventDefault(); damageMutation.mutate(); }}
              className="space-y-4"
            >
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Article *</label>
                <select
                  value={damageForm.itemId}
                  onChange={(e) => setDamageForm({ ...damageForm, itemId: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  required
                >
                  <option value="">— Sélectionner —</option>
                  {items.map((i) => (
                    <option key={i.id} value={i.id}>{i.name}{i.sku ? ` (${i.sku})` : ''}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Quantité *</label>
                <input
                  type="number"
                  value={damageForm.quantity}
                  onChange={(e) => setDamageForm({ ...damageForm, quantity: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  min={0.01}
                  step={0.01}
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Notes</label>
                <textarea
                  value={damageForm.notes}
                  onChange={(e) => setDamageForm({ ...damageForm, notes: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  rows={2}
                  placeholder="Casse, humidité..."
                />
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setDamageForm({ ...damageForm, itemId: '' })}
                  className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={damageMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {damageMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Signaler
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Receiving;
