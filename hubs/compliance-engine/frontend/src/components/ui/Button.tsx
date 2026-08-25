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
  primary: 'bg-accent text-white hover:bg-accent-strong shadow-md shadow-accent/20 hover:shadow-lg hover:shadow-accent/30',
  secondary: 'bg-surface text-ink border-2 border-line hover:bg-accent-soft hover:border-accent/40',
  ghost: 'bg-transparent text-ink-soft hover:text-ink hover:bg-surface-2',
  'outline-white': 'border-2 border-surface/30 text-white hover:bg-surface/10',
};

const sizeClasses: Record<Size, string> = {
  sm: 'px-4 py-2 text-sm rounded-none gap-1.5',
  md: 'px-6 py-3 text-sm rounded-none gap-2',
  lg: 'px-8 py-3.5 text-base rounded-none gap-2',
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
        whileHover={reduceMotion ? undefined : { y: -3 }}
        whileTap={reduceMotion ? undefined : { y: -1, scale: 0.96 }}
        transition={{ type: 'spring', stiffness: 500, damping: 25 }}
        {...props}
      >
        {children}
      </motion.button>
    );
  },
);
Button.displayName = 'Button';

export default Button;
