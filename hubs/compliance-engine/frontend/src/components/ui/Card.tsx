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
        'bg-surface rounded-none border border-line',
        variant === 'default' && 'p-6',
        variant === 'stat' && 'p-6 text-center',
        variant === 'flat' && 'p-4',
        className,
      )}
      whileHover={hover && !reduceMotion ? { y: -4, boxShadow: '0 20px 40px -20px rgba(0,0,0,0.25)' } : undefined}
      transition={{ type: 'spring', stiffness: 400, damping: 28 }}
      {...props}
    >
      {children}
    </motion.div>
  );
};

export default Card;
