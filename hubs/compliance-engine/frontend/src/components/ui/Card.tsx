import { motion, useReducedMotion, type HTMLMotionProps } from 'framer-motion';
import clsx from 'clsx';

interface CardProps extends HTMLMotionProps<'div'> {
  variant?: 'default' | 'stat' | 'flat';
  hover?: boolean;
}

const Card = ({ variant = 'default', hover = true, className, children, ...props }: CardProps) => {
  const reduceMotion = useReducedMotion();
  return (
    <motion.div
      className={clsx(
        'bg-surface rounded-lg border border-line',
        variant === 'default' && 'p-6',
        variant === 'stat' && 'p-6 text-center',
        variant === 'flat' && 'p-4',
        className,
      )}
      whileHover={hover && !reduceMotion ? { borderColor: 'rgb(var(--c-accent) / 0.4)' } : undefined}
      transition={{ duration: 0.15 }}
      {...props}
    >
      {children}
    </motion.div>
  );
};

export default Card;
