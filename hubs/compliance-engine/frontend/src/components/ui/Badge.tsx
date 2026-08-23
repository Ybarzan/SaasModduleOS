import type { HTMLAttributes } from 'react';
import clsx from 'clsx';

type Variant = 'success' | 'warning' | 'neutral' | 'info';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: Variant;
  size?: 'sm' | 'md';
}

const variantClasses: Record<Variant, string> = {
  success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning',
  neutral: 'bg-surface-2 text-ink-soft',
  info: 'bg-accent-soft text-accent',
};

const Badge = ({ variant = 'neutral', size = 'sm', className, children, ...props }: BadgeProps) => {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full font-semibold',
        size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm',
        variantClasses[variant],
        className,
      )}
      {...props}
    >
      {children}
    </span>
  );
};

export default Badge;
