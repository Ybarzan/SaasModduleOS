import { forwardRef } from 'react';
import { motion, useReducedMotion, type HTMLMotionProps } from 'framer-motion';
import clsx from 'clsx';

type Variant = 'primary' | 'secondary' | 'ghost' | 'outline-white';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends HTMLMotionProps<'button'> {
  variant?: Variant;
  size?: Size;
}

const variantClasses: Record<Variant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-strong',
  secondary: 'bg-surface text-ink border border-line hover:bg-surface-2',
  ghost: 'bg-transparent text-ink-soft hover:text-ink hover:bg-surface-2',
  'outline-white': 'border border-surface/30 text-white hover:bg-surface/10',
};

const sizeClasses: Record<Size, string> = {
  sm: 'px-4 py-2 text-sm rounded-lg gap-1.5',
  md: 'px-6 py-3 text-sm rounded-lg gap-2',
  lg: 'px-8 py-3.5 text-base rounded-lg gap-2',
};

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', className, children, ...props }, ref) => {
    const reduceMotion = useReducedMotion();
    return (
      <motion.button
        ref={ref}
        className={clsx(
          'inline-flex items-center justify-center font-semibold transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed',
          variantClasses[variant],
          sizeClasses[size],
          className,
        )}
        whileTap={reduceMotion ? undefined : { scale: 0.98 }}
        transition={{ duration: 0.15 }}
        {...props}
      >
        {children}
      </motion.button>
    );
  },
);
Button.displayName = 'Button';

export default Button;
