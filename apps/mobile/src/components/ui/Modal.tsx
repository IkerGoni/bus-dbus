import { HTMLAttributes, forwardRef, ReactNode, useEffect } from 'react'
import { X } from 'lucide-react'
import { createPortal } from 'react-dom'

interface ModalProps extends HTMLAttributes<HTMLDivElement> {
  isOpen: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

export const Modal = forwardRef<HTMLDivElement, ModalProps>(
  ({ isOpen, onClose, title, children, size = 'md', className = '', ...props }, ref) => {
    useEffect(() => {
      const handleKeyDown = (e: KeyboardEvent) => {
        if (e.key === 'Escape') onClose()
      }
      
      if (isOpen) {
        document.addEventListener('keydown', handleKeyDown)
        document.body.style.overflow = 'hidden'
      }
      
      return () => {
        document.removeEventListener('keydown', handleKeyDown)
        document.body.style.overflow = ''
      }
    }, [isOpen, onClose])
    
    if (!isOpen) return null
    
    const sizes = {
      sm: 'max-w-sm',
      md: 'max-w-md',
      lg: 'max-w-lg'
    }
    
    const portalContent = (
      <div className="fixed inset-0 z-50" role="dialog" aria-modal="true" aria-labelledby={title ? 'modal-title' : undefined}>
        <div 
          className="absolute inset-0 bg-black/50 animate-fade-in"
          onClick={onClose}
          aria-hidden="true"
        />
        <div
          ref={ref}
          className={`absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full mx-4 bg-surface rounded-xl shadow-xl animate-scale-in ${sizes[size]} ${className}`}
          {...props}
        >
          {(title || onClose) && (
            <div className="flex items-center justify-between p-4 border-b border-border">
              {title && <h2 id="modal-title" className="text-lg font-semibold text-text">{title}</h2>}
              <button
                onClick={onClose}
                className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-bg-secondary touch-manipulation min-h-[48px] min-w-[48px]"
                aria-label="Cerrar"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
          )}
          <div className="p-4">
            {children}
          </div>
        </div>
      </div>
    )
    
    return createPortal(portalContent, document.body)
  }
)

Modal.displayName = 'Modal'
