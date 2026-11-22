import { signal } from '@preact/signals-react'
import { X } from 'lucide-react'

interface Toast {
  id: string
  type: 'success' | 'error' | 'info' | 'warning'
  title: string
  message?: string
  action?: { label: string; onClick: () => void }
  duration?: number
}

const toasts = signal<Toast[]>([])

export function useToast() {
  const addToast = (toast: Omit<Toast, 'id'>) => {
    const id = Math.random().toString(36).slice(2)
    toasts.value = [...toasts.value, { ...toast, id }]
    if (toast.duration !== 0) {
      setTimeout(() => removeToast(id), toast.duration || 5000)
    }
    return id
  }
  
  const removeToast = (id: string) => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }
  
  return { toasts, addToast, removeToast }
}

interface ToastContainerProps {
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left'
}

export function ToastContainer({ position = 'bottom-right' }: ToastContainerProps = {}) {
  const { toasts, removeToast } = useToast()
  
  const typeStyles = {
    success: 'border-green-500 bg-green-50 dark:bg-green-900/20',
    error: 'border-red-500 bg-red-50 dark:bg-red-900/20',
    info: 'border-primary bg-primary/10 dark:bg-primary/20',
    warning: 'border-yellow-500 bg-yellow-50 dark:bg-yellow-900/20'
  }
  
  const icons = {
    success: '✓',
    error: '✕',
    info: 'ℹ',
    warning: '⚠'
  }
  
  const positions = {
    'top-right': 'fixed top-4 right-4',
    'top-left': 'fixed top-4 left-4',
    'bottom-right': 'fixed bottom-4 right-4',
    'bottom-left': 'fixed bottom-4 left-4'
  }
  
  return (
    <div className={`${positions[position]} z-50 flex flex-col gap-2 w-full max-w-xs md:max-w-sm pointer-events-none`}>
      {toasts.value.map(toast => (
        <div 
          key={toast.id} 
          className={`pointer-events-auto animate-slide-up flex items-start gap-3 p-4 rounded-lg border shadow-lg ${typeStyles[toast.type]}`}
          role="alert"
        >
          <span className="flex-shrink-0 text-lg">{icons[toast.type]}</span>
          <div className="flex-1 min-w-0">
            <p className="font-medium text-text">{toast.title}</p>
            {toast.message && <p className="text-sm text-text-muted mt-0.5">{toast.message}</p>}
            {toast.action && (
              <button
                onClick={() => { toast.action!.onClick(); removeToast(toast.id) }}
                className="mt-2 text-sm font-medium text-primary hover:underline"
              >
                {toast.action.label}
              </button>
            )}
          </div>
          <button
            onClick={() => removeToast(toast.id)}
            className="flex-shrink-0 text-text-muted hover:text-text p-1"
            aria-label="Cerrar"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  )
}

export { useToast }
