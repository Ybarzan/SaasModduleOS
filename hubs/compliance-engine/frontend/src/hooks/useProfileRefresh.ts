import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore, type PlanId, type UserRole } from '../stores/auth';

interface ProfileResponse {
  plan?: PlanId;
  role?: UserRole;
  company?: string;
  company_id?: string;
}

const REFRESH_INTERVAL_MS = 2 * 60 * 1000;

/**
 * L'auth store ne captait plan/role/company qu'au login : un changement fait
 * côté backend (upgrade de plan, changement de rôle par un admin...) restait
 * invisible tant que l'utilisateur ne se déconnectait pas manuellement. Ce
 * hook rapproche périodiquement /v1/auth/me pour garder ces champs à jour.
 */
export function useProfileRefresh() {
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);
  const currentUser = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  const { data } = useQuery({
    queryKey: ['auth-me-refresh'],
    queryFn: async () => {
      const res = await incokalkAPI.auth.me();
      return res.data as ProfileResponse;
    },
    enabled: isAuthenticated,
    refetchInterval: REFRESH_INTERVAL_MS,
    refetchOnWindowFocus: true,
  });

  useEffect(() => {
    if (!data || !currentUser) return;

    const nextPlan = data.plan ?? currentUser.plan;
    const nextRole = data.role ?? currentUser.role;
    const nextCompany = data.company ?? currentUser.company;
    const nextCompanyId = data.company_id ?? currentUser.companyId;

    const changed =
      nextPlan !== currentUser.plan ||
      nextRole !== currentUser.role ||
      nextCompany !== currentUser.company ||
      nextCompanyId !== currentUser.companyId;
    if (!changed) return;

    if (data.plan && data.plan !== currentUser.plan) {
      toast.success(`Votre plan a été mis à jour : ${data.plan}`);
    }

    setUser({
      ...currentUser,
      plan: nextPlan,
      role: nextRole,
      company: nextCompany,
      companyId: nextCompanyId,
    });
  }, [data, currentUser, setUser]);
}
