import clsx from 'clsx';

interface SkeletonProps {
  className?: string;
  variant?: 'text' | 'block' | 'circle';
}

const Skeleton = ({ className, variant = 'block' }: SkeletonProps) => {
  return (
    <div
      className={clsx(
        'animate-pulse bg-surface-2',
        variant === 'text' && 'h-4 rounded-md',
        variant === 'block' && 'rounded-xl',
        variant === 'circle' && 'rounded-full',
        className,
      )}
    />
  );
};

export const SkeletonCard = () => (
  <div className="bg-surface rounded-2xl border border-line p-6 space-y-4">
    <Skeleton variant="text" className="w-1/2" />
    <Skeleton className="h-8 w-2/3" />
    <Skeleton variant="text" className="w-1/3" />
  </div>
);

export default Skeleton;
