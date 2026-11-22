import { HTMLAttributes, forwardRef } from 'react'

interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'text' | 'circular' | 'rectangular'
  lines?: number
}

export const Skeleton = forwardRef<HTMLDivElement, SkeletonProps>(
  ({ variant = 'text', lines = 1, className = '', ...props }, ref) => {
    const baseClasses = 'animate-pulse bg-border rounded'
    
    const variants = {
      text: 'h-4 w-full',
      circular: 'h-10 w-10 rounded-full',
      rectangular: 'h-20 w-full rounded-lg'
    }
    
    if (variant === 'text' && lines > 1) {
      return (
        <div ref={ref} className={`space-y-2 ${className}`} {...props}>
          {Array.from({ length: lines }).map((_, i) => (
            <div key={i} className={`${baseClasses} ${variants.text} ${i === lines - 1 ? 'w-3/4' : ''}`} />
          ))}
        </div>
      )
    }
    
    return (
      <div ref={ref} className={`${baseClasses} ${variants[variant]} ${className}`} {...props} />
    )
  }
)

Skeleton.displayName = 'Skeleton'
