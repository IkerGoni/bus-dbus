import { HTMLAttributes, forwardRef } from 'react'

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: 'rt' | 'scheduled' | 'occupancy-low' | 'occupancy-medium' | 'occupancy-high' | 'silenced' | 'default'
}

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(
  ({ variant = 'default', children, className = '', ...props }, ref) => {
    const variants = {
      rt: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
      scheduled: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200',
      'occupancy-low': 'bg-green-100 text-green-800',
      'occupancy-medium': 'bg-yellow-100 text-yellow-800',
      'occupancy-high': 'bg-red-100 text-red-800',
      silenced: 'bg-orange-100 text-orange-800',
      default: 'bg-border text-text-muted'
    }
    
    return (
      <span
        ref={ref}
        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${variants[variant]} ${className}`}
        {...props}
      >
        {children}
      </span>
    )
  }
)

Badge.displayName = 'Badge'
