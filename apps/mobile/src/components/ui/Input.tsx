import { InputHTMLAttributes, forwardRef } from 'react'
import { LucideIcon } from 'lucide-react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  helperText?: string
  leftIcon?: LucideIcon
  rightIcon?: LucideIcon
  fullWidth?: boolean
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (props, ref) => {
    const { 
      label, 
      error, 
      helperText, 
      leftIcon, 
      rightIcon, 
      fullWidth, 
      className = '', 
      id, 
      ...restProps 
    } = props
    
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-')
    
    return (
      <div className={`w-full`}>
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-text mb-1.5">
            {label}
          </label>
        )}
        <div className="relative">
          {leftIcon && (
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none">
              <leftIcon className="h-5 w-5" aria-hidden="true" />
            </div>
          )}
          <input
            ref={ref}
            id={inputId}
            className={`w-full px-3 py-2.5 rounded-md border bg-surface text-text placeholder:text-text-muted 
              focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent
              min-h-[48px] touch-manipulation transition-colors
              ${leftIcon ? 'pl-10' : ''} ${rightIcon ? 'pr-10' : ''}
              ${error ? 'border-error focus:ring-error' : 'border-border hover:border-text-muted'}
              ${className}`}
            aria-invalid={error ? 'true' : 'false'}
            aria-describedby={error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined}
            {...restProps}
          />
          {rightIcon && (
            <div className="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none">
              <rightIcon className="h-5 w-5" aria-hidden="true" />
            </div>
          )}
        </div>
        {error && (
          <p id={`${inputId}-error`} className="mt-1.5 text-sm text-error" role="alert">{error}</p>
        )}
        {helperText && !error && (
          <p id={`${inputId}-helper`} className="mt-1.5 text-sm text-text-muted">{helperText}</p>
        )}
      </div>
    )
  }
)

Input.displayName = 'Input'
