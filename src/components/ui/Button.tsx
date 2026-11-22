import { ButtonHTMLAttributes, forwardRef } from 'react'
import { LucideIcon } from 'lucide-react'

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'leftIcon' | 'rightIcon' | 'loading' | 'fullWidth'> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  leftIcon?: LucideIcon
  rightIcon?: LucideIcon
  loading?: boolean
  fullWidth?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (props, ref) => {
    const { 
      variant = 'primary', 
      size = 'md', 
      leftIcon, 
      rightIcon, 
      loading, 
      fullWidth, 
      children, 
      className = '', 
      disabled, 
      ...restProps 
    } = props
    
    const baseClasses = 'inline-flex items-center justify-center gap-2 font-medium rounded-md transition-colors touch-manipulation min-h-[48px] disabled:opacity-50 disabled:cursor-not-allowed'
    
    const variants = {
      primary: 'bg-primary text-white hover:bg-primary-hover',
      secondary: 'bg-bg-secondary text-text hover:bg-border',
      ghost: 'bg-transparent text-primary hover:bg-bg-secondary',
      danger: 'bg-error text-white hover:bg-red-700'
    }
    
    const sizes = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2.5 text-base',
      lg: 'px-6 py-3 text-lg'
    }
    
    const widthClass = fullWidth ? 'w-full' : ''
    
    return (
      <button
        ref={ref}
        className={`inline-flex items-center justify-center gap-2 font-medium rounded-md transition-colors touch-manipulation min-h-[48px] disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${widthClass} ${className}`}
        disabled={disabled || loading}
        {...restProps}
      >
        {loading ? (
          <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
        ) : leftIcon ? (
          <leftIcon className="h-4 w-4" aria-hidden="true" />
        ) : null}
        {children}
        {!loading && rightIcon && <rightIcon className="h-4 w-4" aria-hidden="true" />}
      </button>
    )
  }
)

Button.displayName = 'Button'
