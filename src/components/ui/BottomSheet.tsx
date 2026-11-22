import { HTMLAttributes, forwardRef, ReactNode, useEffect } from 'react'
import { X } from 'lucide-react'
import { createPortal } from 'react-dom'

interface BottomSheetProps extends HTMLAttributes<HTMLDivElement> {
  isOpen: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  className?: string
}

export const BottomSheet = forwardRef<HTMLDivElement, BottomSheetProps>(
  ({ isOpen, onClose, title, children, className = '', ...props }, ref) => {
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
    
    const portalContent = (
      <div className="fixed inset-0 z-50" role="dialog" aria-modal="true" aria-labelledby={title ? 'bottom-sheet-title' : undefined}>
        <div 
          className="absolute inset-0 bg-black/50 animate-fade-in"
          onClick={onClose}
          aria-hidden="true"
        />
        <div
          ref={ref}
          className="absolute bottom-0 left-0 right-0 bg-surface rounded-t-2xl shadow-xl animate-slide-up max-h-[85vh] flex flex-col"
          {...props}
        >
          {(title || onClose) && (
            <div className="flex items-center justify-between p-4 border-b border-border">
              {title && <h2 id="bottom-sheet-title" className="text-lg font-semibold text-text">{title}</h2>}
              <button
                onClick={onClose}
                className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-bg-secondary touch-manipulation min-h-[48px] min-w-[48px]"
                aria-label="Cerrar"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
          )}
          <div className="flex-1 overflow-y-auto p-4">
            {children}
          </div>
        </div>
      </div>
    )
    
    return createPortal(portalContent, document.body)
  }
)

BottomSheet.displayName = 'BottomSheet'
