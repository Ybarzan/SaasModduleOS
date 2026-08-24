import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Bell, Truck, Package, Search, WifiOff, Wifi, CheckCheck, Archive,
  Trash2, Clock, Mail, Webhook, Eye, Loader2,
} from 'lucide-react';
import type { Notification } from '../types';
import { timeAgo } from '@/lib/utils';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

type FilterTab = 'ALL' | 'UNREAD' | 'READ' | 'ARCHIVED';

const TABS: { key: FilterTab; label: string }[] = [
  { key: 'ALL', label: 'Tous' },
  { key: 'UNREAD', label: 'Non lus' },
  { key: 'READ', label: 'Lus' },
  { key: 'ARCHIVED', label: 'Archivés' },
];

const EVENT_ICONS: Record<string, typeof Truck> = {
  SHIPMENT_STATUS_CHANGE: Truck,
  SHIPMENT_CREATED: Package,
  QUOTE_RECEIVED: Search,
  PROVIDER_DOWN: WifiOff,
  PROVIDER_RECOVERED: Wifi,
};

const EVENT_COLORS: Record<string, string> = {
  SHIPMENT_STATUS_CHANGE: 'bg-accent-soft text-accent',
  SHIPMENT_CREATED: 'bg-success/10 text-success',
  QUOTE_RECEIVED: 'bg-accent-soft text-accent',
  PROVIDER_DOWN: 'bg-danger/10 text-danger',
  PROVIDER_RECOVERED: 'bg-success/10 text-success',
};

const CHANNEL_BADGES: Record<string, string> = {
  IN_APP: 'bg-surface-2 text-ink',
  EMAIL: 'bg-accent-soft text-accent-strong',
  WEBHOOK: 'bg-accent-soft text-accent-strong',
};

const CHANNEL_ICONS: Record<string, typeof Mail> = {
  IN_APP: Eye,
  EMAIL: Mail,
  WEBHOOK: Webhook,
};

const CHANNEL_LABELS: Record<string, string> = {
  IN_APP: 'In-App',
  EMAIL: 'Email',
  WEBHOOK: 'Webhook',
};

const Notifications = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', page],
    queryFn: async () => {
      const res = await incokalkAPI.notifications.getPage(page, PAGE_SIZE);
      return res.data;
    },
    refetchInterval: 15000,
  });

  const notifications: Notification[] = Array.isArray(data) ? data : (data?.content ?? []);
  const totalPages: number = Array.isArray(data) ? 1 : (data?.totalPages ?? 1);

  const markReadMutation = useMutation({
    mutationFn: (ids: string[]) => incokalkAPI.notifications.markRead(ids),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
      toast.success('Notification marquée comme lue');
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => incokalkAPI.notifications.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
      toast.success('Toutes les notifications marquées comme lues');
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const archiveMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.notifications.archive(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
      toast.success('Notification archivée');
    },
    onError: () => toast.error("Erreur lors de l'archivage"),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.notifications.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
      toast.success('Notification supprimée');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const filtered = notifications.filter((n) => {
    if (activeTab === 'ALL') return true;
    return n.status === activeTab;
  });

  const unreadCount = notifications.filter((n) => n.status === 'UNREAD').length;

  if (isLoading) {
    return (
      <div className="min-h-screen bg-bg py-12">
        <div className="container mx-auto px-4">
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-surface rounded-none shadow-lg p-6 animate-pulse">
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 bg-surface-2 rounded-full" />
                  <div className="flex-1 space-y-3">
                    <div className="h-4 bg-surface-2 rounded w-1/3" />
                    <div className="h-3 bg-surface-2 rounded w-2/3" />
                    <div className="h-3 bg-surface-2 rounded w-1/4" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-4xl font-bold text-ink mb-2">
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Centre de notifications
            </h1>
            <p className="text-ink-soft">
              {unreadCount > 0 ? `${unreadCount} non lu(s)` : 'Toutes vos notifications sont lues'}
            </p>
          </div>
          {unreadCount > 0 && (
            <button
              onClick={() => markAllReadMutation.mutate()}
              disabled={markAllReadMutation.isPending}
              className="bg-accent text-white px-4 py-2 rounded-none hover:bg-accent-strong transition-colors flex items-center space-x-2 disabled:opacity-50"
            >
              {markAllReadMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CheckCheck size={18} />
              )}
              <span>Tout marquer lu</span>
            </button>
          )}
        </div>

        {/* Tabs */}
        <div className="flex gap-1 mb-6 bg-surface rounded-none shadow-sm p-1 inline-flex">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-4 py-2 rounded-none text-sm font-medium transition-colors ${
                activeTab === tab.key
                  ? 'bg-accent text-white'
                  : 'text-ink-soft hover:bg-surface-2'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Notifications list */}
        {filtered.length === 0 ? (
          <div className="bg-surface rounded-none shadow-lg p-12 text-center">
            <Bell className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink mb-2">
              {activeTab === 'UNREAD'
                ? 'Aucune notification non lue'
                : activeTab === 'READ'
                ? 'Aucune notification lue'
                : activeTab === 'ARCHIVED'
                ? 'Aucune notification archivée'
                : 'Aucune notification'}
            </h3>
            <p className="text-ink-soft">
              Les nouvelles notifications apparaîtront ici
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {filtered.map((notif) => {
              const Icon = EVENT_ICONS[notif.eventType] || Bell;
              const colorClass = EVENT_COLORS[notif.eventType] || 'bg-surface-2 text-ink-soft';
              const channelBadge = CHANNEL_BADGES[notif.channel] || 'bg-surface-2 text-ink';
              const ChannelIcon = CHANNEL_ICONS[notif.channel] || Eye;
              const isUnread = notif.status === 'UNREAD';

              return (
                <div
                  key={notif.id}
                  className={`bg-surface rounded-none shadow-lg p-5 hover:shadow-xl transition-shadow flex items-start gap-4 ${isUnread ? 'border-l-4 border-accent' : ''}`}
                >
                  <div className={`w-12 h-12 rounded-full flex items-center justify-center flex-shrink-0 ${colorClass}`}>
                    <Icon size={22} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <h3 className={`text-sm ${isUnread ? 'font-bold text-ink' : 'font-semibold text-ink'}`}>
                          {notif.title}
                        </h3>
                        <p className="text-sm text-ink-soft mt-1 line-clamp-2">{notif.message}</p>
                        {notif.entityType && notif.entityId && (
                          <p className="text-xs text-ink-soft mt-1.5">
                            {notif.entityType} : {notif.entityId}
                          </p>
                        )}
                      </div>
                      <div className="flex items-center gap-2 flex-shrink-0">
                        <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium ${channelBadge}`}>
                          <ChannelIcon size={12} />
                          {CHANNEL_LABELS[notif.channel] || notif.channel}
                        </span>
                        {isUnread && (
                          <span className="w-2.5 h-2.5 bg-accent rounded-full" title="Non lu" />
                        )}
                        {notif.status === 'ARCHIVED' && (
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-warning/10 text-warning">
                            Archivé
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center justify-between mt-3 pt-3 border-t border-line">
                      <div className="flex items-center gap-1.5 text-xs text-ink-soft">
                        <Clock size={12} />
                        {timeAgo(notif.sentAt)}
                      </div>
                      <div className="flex items-center gap-1">
                        {isUnread && (
                          <button
                            onClick={() => markReadMutation.mutate([notif.id])}
                            disabled={markReadMutation.isPending}
                            className="p-1.5 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-none transition-colors disabled:opacity-50"
                            title="Marquer comme lu"
                          >
                            <Eye size={14} />
                          </button>
                        )}
                        {notif.status !== 'ARCHIVED' && (
                          <button
                            onClick={() => archiveMutation.mutate(notif.id)}
                            disabled={archiveMutation.isPending}
                            className="p-1.5 text-ink-soft hover:text-warning hover:bg-warning/10 rounded-none transition-colors disabled:opacity-50"
                            title="Archiver"
                          >
                            <Archive size={14} />
                          </button>
                        )}
                        <button
                          onClick={() => setDeleteId(notif.id)}
                          className="p-1.5 text-ink-soft hover:text-danger hover:bg-danger/10 rounded-none transition-colors"
                          title="Supprimer"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

        {/* Delete Confirmation */}
        {deleteId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-none shadow-xl w-full max-w-md mx-4 p-6">
              <h3 className="text-lg font-bold text-ink mb-4">Confirmer la suppression</h3>
              <p className="text-ink-soft mb-6">
                Êtes-vous sûr de vouloir supprimer cette notification ? Cette action est irréversible.
              </p>
              <div className="flex justify-end space-x-3">
                <button
                  onClick={() => setDeleteId(null)}
                  className="px-4 py-2 text-ink bg-surface-2 rounded-none hover:bg-surface-2 transition-colors"
                >
                  Annuler
                </button>
                <button
                  onClick={() => deleteMutation.mutate(deleteId)}
                  disabled={deleteMutation.isPending}
                  className="px-4 py-2 bg-danger text-white rounded-none hover:bg-danger/90 transition-colors disabled:opacity-50 flex items-center space-x-2"
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

export default Notifications;
