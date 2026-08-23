import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { incokalkAPI } from '../lib/api';
import { useClientAuthStore } from '../stores/clientAuth';
import type { ClientShipment } from '../types';
import {
  Package, Truck, CheckCircle, Clock,
  MapPin, ArrowRight, LogOut, RefreshCw
} from 'lucide-react';
import { CLIENT_STATUS_CONFIG } from '@/lib/constants';

const ClientDashboard = () => {
  const client = useClientAuthStore((s) => s.client);
  const logout = useClientAuthStore((s) => s.logout);

  const { data: shipments = [], isLoading, refetch } = useQuery<ClientShipment[]>({
    queryKey: ['client-shipments'],
    queryFn: async () => {
      const res = await incokalkAPI.clientPortal.shipments();
      return res.data;
    },
    refetchInterval: 30000,
  });

  const totalShipments = shipments.length;
  const inTransit = shipments.filter((s) => s.status === 'IN_TRANSIT').length;
  const delivered = shipments.filter((s) => s.status === 'DELIVERED').length;

  return (
    <div className="min-h-screen bg-bg">
      {/* Header */}
      <div className="bg-surface border-b border-line">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-accent-soft rounded-xl flex items-center justify-center">
              <Package className="w-5 h-5 text-accent" />
            </div>
            <div>
              <h1 className="font-bold text-ink">Mon Suivi</h1>
              <p className="text-xs text-ink-soft">{client?.fullName || client?.email}</p>
            </div>
          </div>
          <button
            onClick={() => { logout(); window.location.href = '/client/login'; }}
            className="flex items-center gap-2 text-sm text-ink-soft hover:text-accent-strong transition"
          >
            <LogOut className="w-4 h-4" /> Déconnexion
          </button>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-6 space-y-6">
        {/* KPI Cards */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-surface rounded-xl border border-line p-4 text-center">
            <Package className="w-5 h-5 text-ink-soft mx-auto mb-1" />
            <p className="text-2xl font-bold text-ink">{totalShipments}</p>
            <p className="text-xs text-ink-soft">Total expéditions</p>
          </div>
          <div className="bg-surface rounded-xl border border-line p-4 text-center">
            <Truck className="w-5 h-5 text-warning mx-auto mb-1" />
            <p className="text-2xl font-bold text-warning">{inTransit}</p>
            <p className="text-xs text-ink-soft">En transit</p>
          </div>
          <div className="bg-surface rounded-xl border border-line p-4 text-center">
            <CheckCircle className="w-5 h-5 text-success mx-auto mb-1" />
            <p className="text-2xl font-bold text-success">{delivered}</p>
            <p className="text-xs text-ink-soft">Livrées</p>
          </div>
        </div>

        {/* Refresh */}
        <div className="flex items-center justify-between">
          <h2 className="font-semibold text-ink">Vos expéditions</h2>
          <button onClick={() => refetch()} className="flex items-center gap-1.5 text-sm text-accent hover:text-accent-strong">
            <RefreshCw className="w-4 h-4" /> Actualiser
          </button>
        </div>

        {/* Shipments List */}
        {isLoading ? (
          <div className="text-center py-12 text-ink-soft">Chargement...</div>
        ) : shipments.length === 0 ? (
          <div className="text-center py-12 bg-surface rounded-xl border border-line">
            <Package className="w-12 h-12 text-ink-soft mx-auto mb-3" />
            <p className="text-ink-soft">Aucune expédition partagée pour le moment</p>
          </div>
        ) : (
          <div className="space-y-3">
            {shipments.map((shipment) => {
              const statusCfg = CLIENT_STATUS_CONFIG[shipment.status] || CLIENT_STATUS_CONFIG.DRAFT;
              const StatusIcon = statusCfg.icon;
              return (
                <Link
                  key={shipment.id}
                  to={`/client/shipments/${shipment.id}`}
                  className="block bg-surface rounded-xl border border-line p-4 hover:shadow-md hover:border-accent/30 transition-all"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4 flex-1">
                      <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${statusCfg.color}`}>
                        <StatusIcon className="w-5 h-5" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm font-semibold text-ink">{shipment.orderNumber}</span>
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${statusCfg.color}`}>
                            {statusCfg.label}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 mt-1 text-sm text-ink-soft">
                          {shipment.shipperCity && (
                            <span className="flex items-center gap-1">
                              <MapPin className="w-3 h-3" />
                              {shipment.shipperCity}
                              {shipment.shipperCountry && `, ${shipment.shipperCountry}`}
                            </span>
                          )}
                          {shipment.consigneeCity && (
                            <>
                              <ArrowRight className="w-3 h-3 text-ink-soft" />
                              <span className="flex items-center gap-1">
                                <MapPin className="w-3 h-3" />
                                {shipment.consigneeCity}
                                {shipment.consigneeCountry && `, ${shipment.consigneeCountry}`}
                              </span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="text-right ml-4">
                      {shipment.carrierName && (
                        <p className="text-xs text-ink-soft">{shipment.carrierName}</p>
                      )}
                      {shipment.estimatedDeliveryDate && (
                        <p className="text-xs text-ink-soft flex items-center gap-1 justify-end mt-0.5">
                          <Clock className="w-3 h-3" />
                          {new Date(shipment.estimatedDeliveryDate).toLocaleDateString('fr-FR')}
                        </p>
                      )}
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default ClientDashboard;
