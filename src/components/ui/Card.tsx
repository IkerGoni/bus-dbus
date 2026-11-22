import { HTMLAttributes, forwardRef, ReactNode } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  padding?: 'none' | 'sm' | 'md' | 'lg'
  hoverable?: boolean
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  ({ children, padding = 'md', hoverable, className = '', ...props }, ref) => {
    const paddings = {
      none: '',
      sm: 'p-3',
      md: 'p-4',
      lg: 'p-6'
    }
    
    const hoverClass = hoverable ? 'hover:shadow-md transition-shadow cursor-pointer' : ''
    
    return (
      <div
        ref={ref}
        className={`bg-surface rounded-lg border border-border shadow-sm ${paddings[padding]} ${hoverClass} ${className}`}
        {...props}
      >
        {children}
      </div>
    )
  }
)

Card.displayName = 'Card'
