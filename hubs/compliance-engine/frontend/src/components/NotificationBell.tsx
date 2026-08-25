import { useState, useEffect, useRef, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Bell, Truck, Package, Search, WifiOff, Wifi, CheckCheck, X,
} from 'lucide-react';
import type { Notification } from '../types';
import { timeAgo } from '@/lib/utils';

const EVENT_ICONS: Record<string, typeof Truck> = {
  SHIPMENT_STATUS_CHANGE: Truck,
  SHIPMENT_CREATED: Package,
  QUOTE_RECEIVED: Search,
  PROVIDER_DOWN: WifiOff,
  PROVIDER_RECOVERED: Wifi,
};

const NotificationBell = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [pulse, setPulse] = useState(false);
  const prevCountRef = useRef<number>(0);
  const ref = useRef<HTMLDivElement>(null);

  const { data: unreadRes } = useQuery({
    queryKey: ['notifications-unread-count'],
    queryFn: async () => {
      const res = await incokalkAPI.notifications.unreadCount();
      return res.data as number;
    },
    refetchInterval: 60000,
  });

  const unreadCount = unreadRes ?? 0;

  useEffect(() => {
    if (prevCountRef.current > 0 && unreadCount > prevCountRef.current) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- pulse est un ack ponctuel déclenché par un changement externe
      setPulse(true);
      toast('Nouvelle notification', { icon: '🔔', duration: 3000 });
      setTimeout(() => setPulse(false), 1000);
    }
    prevCountRef.current = unreadCount;
  }, [unreadCount]);

  const { data: notifsRes } = useQuery({
    queryKey: ['notifications', 'bell'],
    queryFn: async () => {
      const res = await incokalkAPI.notifications.getAll();
      return (res.data as Notification[]).slice(0, 10);
    },
    enabled: open,
  });

  const notifications: Notification[] = notifsRes ?? [];

  const markAllMutation = useMutation({
    mutationFn: () => incokalkAPI.notifications.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markReadMutation = useMutation({
    mutationFn: (ids: string[]) => incokalkAPI.notifications.markRead(ids),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications-unread-count'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const handleClickOutside = useCallback((e: MouseEvent) => {
    if (ref.current && !ref.current.contains(e.target as Node)) {
      setOpen(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open, handleClickOutside]);

  const handleNotificationClick = (notif: Notification) => {
    if (notif.status === 'UNREAD') {
      markReadMutation.mutate([notif.id]);
    }
    setOpen(false);
    if (notif.entityType === 'SHIPMENT' && notif.entityId) {
      navigate('/shipments');
    } else {
      navigate('/notifications');
    }
  };

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className={`relative p-2 rounded-none hover:bg-accent-soft transition-colors text-ink-soft hover:text-accent-strong ${pulse ? 'animate-bounce' : ''}`}
      >
        <Bell size={20} />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-danger text-white text-[10px] font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1 animate-pulse">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-2 w-[380px] bg-surface rounded-none shadow-2xl border border-line z-50 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-line">
            <h3 className="font-bold text-ink">Notifications</h3>
            <div className="flex items-center gap-2">
              {unreadCount > 0 && (
                <button
                  onClick={() => markAllMutation.mutate()}
                  className="text-xs text-accent hover:text-accent-strong font-medium flex items-center gap-1"
                >
                  <CheckCheck size={14} />
                  Tout marquer lu
                </button>
              )}
              <button
                onClick={() => setOpen(false)}
                className="p-1 text-ink-soft hover:text-ink rounded"
              >
                <X size={16} />
              </button>
            </div>
          </div>

          <div className="max-h-[400px] overflow-y-auto">
            {notifications.length === 0 ? (
              <div className="py-8 text-center text-ink-soft text-sm">
                Aucune notification
              </div>
            ) : (
              notifications.map((notif) => {
                const Icon = EVENT_ICONS[notif.eventType] || Bell;
                const isUnread = notif.status === 'UNREAD';
                return (
                  <button
                    key={notif.id}
                    onClick={() => handleNotificationClick(notif)}
                    className={`w-full text-left px-4 py-3 border-b border-line hover:bg-surface-2 transition-colors flex items-start gap-3 ${isUnread ? 'bg-accent-soft/50' : 'bg-surface'}`}
                  >
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 ${isUnread ? 'bg-accent-soft' : 'bg-surface-2'}`}>
                      <Icon size={16} className={isUnread ? 'text-accent' : 'text-ink-soft'} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className={`text-sm truncate ${isUnread ? 'font-bold text-ink' : 'font-medium text-ink-soft'}`}>
                        {notif.title}
                      </p>
                      <p className="text-xs text-ink-soft truncate mt-0.5">{notif.message}</p>
                      <p className="text-xs text-ink-soft mt-1">{timeAgo(notif.sentAt)}</p>
                    </div>
                    {isUnread && (
                      <div className="w-2 h-2 bg-accent rounded-full flex-shrink-0 mt-2" />
                    )}
                  </button>
                );
              })
            )}
          </div>

          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="block text-center py-3 text-sm font-medium text-accent hover:bg-accent-soft border-t border-line transition-colors"
          >
            Voir tout
          </Link>
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
