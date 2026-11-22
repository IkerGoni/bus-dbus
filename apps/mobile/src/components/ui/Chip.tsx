import { ButtonHTMLAttributes, forwardRef } from 'react'

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected?: boolean
  variant?: 'day' | 'line' | 'recurrence'
  onClick?: () => void
}

export const Chip = forwardRef<HTMLButtonElement, ChipProps>(
  ({ selected = false, variant = 'day', onClick, children, className = '', disabled, ...props }, ref) => {
    const baseClasses = 'inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium transition-colors min-h-[48px] min-w-[48px] touch-manipulation disabled:opacity-50 disabled:cursor-not-allowed'
    
    const variants = {
      day: selected ? 'bg-primary text-white' : 'bg-bg-secondary text-text hover:bg-border',
      line: selected ? 'bg-primary text-white' : 'bg-bg-secondary text-text hover:bg-border',
      recurrence: selected ? 'bg-primary text-white' : 'bg-bg-secondary text-text hover:bg-border'
    }
    
    return (
      <button
        ref={ref}
        type="button"
        className={`${baseClasses} ${variants[variant]} ${className}`}
        onClick={onClick}
        disabled={disabled}
        aria-pressed={selected}
        {...props}
      >
        {children}
      </button>
    )
  }
)

Chip.displayName = 'Chip'
