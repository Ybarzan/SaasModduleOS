import { Shield, Users, Settings, Crown } from 'lucide-react';

const roleConfig = {
  OWNER: { label: 'Propriétaire', color: 'bg-warning/10 text-warning', icon: Crown },
  ADMIN: { label: 'Administrateur', color: 'bg-accent-soft text-accent-strong', icon: Shield },
  MANAGER: { label: 'Manager', color: 'bg-accent/10 text-accent-strong', icon: Settings },
  USER: { label: 'Utilisateur', color: 'bg-accent/10 text-accent-strong', icon: Users },
};

const RoleBadge = ({ role, size = 'sm' }: { role?: string; size?: 'sm' | 'md' }) => {
  const c = roleConfig[role as keyof typeof roleConfig] || roleConfig.USER;
  const Icon = c.icon;
  return (
    <span className={`inline-flex items-center space-x-1 px-2 py-0.5 rounded-full font-medium ${c.color} ${size === 'md' ? 'text-sm' : 'text-xs'}`}>
      <Icon size={size === 'md' ? 14 : 12} />
      <span>{c.label}</span>
    </span>
  );
};

export default RoleBadge;
