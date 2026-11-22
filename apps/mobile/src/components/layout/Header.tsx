import { Link } from 'wouter'
import { useTranslation } from 'react-i18next'
import { Bus, Bell, Settings, Menu, X } from 'lucide-react'
import { useSignal, useComputed } from '@preact/signals-react'
import { alerts } from '../../store'

export function Header() {
  const { t } = useTranslation()
  const showMenu = useSignal(false)
  
  const activeAlertsCount = useComputed(() => 
    alerts.value.filter((a: any) => a.active && !a.silenced).length
  )
  
  const toggleMenu = () => { showMenu.value = !showMenu.value }
  
  return (
    <header className="sticky top-0 z-40 bg-surface/95 backdrop-blur-sm border-b border-border safe-top">
      <div className="flex items-center justify-between px-4 py-3">
        <Link href="/" className="flex items-center gap-2" onClick={() => showMenu.value = false}>
          <Bus className="h-6 w-6 text-primary" aria-hidden="true" />
          <span className="font-semibold text-lg text-text">Dbus</span>
        </Link>
        
        <nav className="flex items-center gap-1">
          <Link 
            href="/alerts" 
            className="relative p-2 rounded-lg text-text-muted hover:text-text hover:bg-bg-secondary touch-manipulation min-h-[48px] min-w-[48px]"
            onClick={() => showMenu.value = false}
            aria-label={t('home.actions.viewDetail')}
          >
            <Bell className="h-5 w-5" aria-hidden="true" />
            {activeAlertsCount.value > 0 && (
              <span className="absolute -top-1 -right-1 h-5 w-5 rounded-full bg-error text-white text-xs font-medium flex items-center justify-center">
                {activeAlertsCount.value > 9 ? '9+' : activeAlertsCount.value}
              </span>
            )}
          </Link>
          
          <Link 
            href="/settings" 
            className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-bg-secondary touch-manipulation min-h-[48px] min-w-[48px]"
            onClick={() => showMenu.value = false}
            aria-label={t('settings.title')}
          >
            <Settings className="h-5 w-5" aria-hidden="true" />
          </Link>
          
          <button
            onClick={toggleMenu}
            className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-bg-secondary touch-manipulation min-h-[48px] min-w-[48px] md:hidden"
            aria-label={showMenu.value ? 'Cerrar menú' : 'Abrir menú'}
            aria-expanded={showMenu.value}
          >
            {showMenu.value ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </nav>
      </div>
      
      {showMenu.value && (
        <div className="md:hidden px-4 pb-4 animate-slide-down border-t border-border">
          <Link 
            href="/stops" 
            className="block py-3 text-text hover:text-primary"
            onClick={() => showMenu.value = false}
          >
            {t('stopSelector.title')}
          </Link>
          <Link 
            href="/alerts" 
            className="block py-3 text-text hover:text-primary"
            onClick={() => showMenu.value = false}
          >
            {t('alertsList.title')} ({activeAlertsCount.value})
          </Link>
          <Link 
            href="/settings" 
            className="block py-3 text-text hover:text-primary"
            onClick={() => showMenu.value = false}
          >
            {t('settings.title')}
          </Link>
        </div>
      )}
    </header>
  )
}
