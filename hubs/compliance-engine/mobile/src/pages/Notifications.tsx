import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2, CheckCheck } from 'lucide-react';
import { mobileApi } from '../lib/api';
import type { MobileNotification } from '../types';

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "à l'instant";
  if (minutes < 60) return `il y a ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `il y a ${hours} h`;
  return `il y a ${Math.floor(hours / 24)} j`;
}

const Notifications = () => {
  const queryClient = useQueryClient();

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-notifications'],
    queryFn: async () => (await mobileApi.notifications.list()).data as MobileNotification[],
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) => mobileApi.notifications.markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mobile-notifications'] }),
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => mobileApi.notifications.markAllRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mobile-notifications'] }),
  });

  const unreadCount = data.filter((n) => !n.isRead).length;

  return (
    <>
      <div className="header-bar row-between">
        <h1 className="title" style={{ margin: 0 }}>Notifications</h1>
        {unreadCount > 0 && (
          <button
            onClick={() => markAllReadMutation.mutate()}
            className="row text-sm"
            style={{ background: 'none', border: 'none', color: 'rgb(var(--c-accent))', cursor: 'pointer', fontWeight: 600 }}
          >
            <CheckCheck size={14} /> Tout marquer lu
          </button>
        )}
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}

        {isError && <p className="error-text">Impossible de charger les notifications.</p>}

        {!isLoading && data.length === 0 && (
          <div className="empty-state">Aucune notification pour le moment.</div>
        )}

        {data.map((n) => (
          <button
            key={n.id}
            onClick={() => !n.isRead && markReadMutation.mutate(n.id)}
            className="card"
            style={{ textAlign: 'left', width: '100%', opacity: n.isRead ? 0.65 : 1 }}
          >
            <div className="row" style={{ alignItems: 'flex-start' }}>
              {!n.isRead && <span className="unread-dot" style={{ marginTop: 6 }} />}
              <div style={{ flex: 1 }}>
                <p style={{ fontWeight: 700, margin: '0 0 4px' }}>{n.title}</p>
                <p className="text-sm text-soft" style={{ margin: '0 0 6px' }}>{n.body}</p>
                <p className="text-sm text-soft" style={{ margin: 0 }}>{timeAgo(n.sentAt)}</p>
              </div>
            </div>
          </button>
        ))}
      </div>
    </>
  );
};

export default Notifications;
