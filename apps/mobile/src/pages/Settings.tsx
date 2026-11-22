import { useSignal, useComputed, effect } from '@preact/signals-react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, Moon, Sun, Monitor, Bell, Database, Trash2, Info, Coffee, ExternalLink, Github } from 'lucide-react'
import { settings } from '../../store'
import { Button } from '../components/ui/Button'
import { Chip } from '../components/ui/Chip'
import { Card } from '../components/ui/Card'
import { useToast } from '../components/ui/ToastContainer'
import { clearAllData } from '../../lib/db'

export function Settings() {
  const { t, i18n } = useTranslation()
  const { addToast } = useToast()
  const showClearConfirm = useSignal(false)
  
  const theme = useComputed(() => settings.value.theme)
  const lang = useComputed(() => settings.value.lang)
  const notificationsEnabled = useComputed(() => settings.value.notificationsEnabled)
  
  const handleThemeChange = (newTheme: 'system' | 'light' | 'dark') => {
    settings.value = { ...settings.value, theme: newTheme }
    const root = document.documentElement
    if (newTheme === 'dark' || (newTheme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
      root.classList.add('dark')
    } else {
      root.classList.remove('dark')
    }
  }
  
  const handleLangChange = (newLang: 'es' | 'eu') => {
    settings.value = { ...settings.value, lang: newLang }
    i18n.changeLanguage(newLang)
  }
  
  const handleNotificationsToggle = (enabled: boolean) => {
    settings.value = { ...settings.value, notificationsEnabled: enabled }
  }
  
  const handleClearCache = async () => {
    try {
      await clearAllData()
      addToast({ type: 'success', title: t('common.success'), message: t('settings.clearCache') })
      showClearConfirm.value = false
      window.location.reload()
    } catch (err) {
      addToast({ type: 'error', title: t('common.error'), message: 'No se pudo limpiar la caché' })
    }
  }
  
  return (
    <div className="page-container">
      <div className="sticky top-16 z-30 bg-surface/95 backdrop-blur-sm border-b border-border safe-top px-4 py-3">
        <h1 className="text-lg font-semibold text-text">{t('settings.title')}</h1>
      </div>
      
      <div className="px-4 py-4 space-y-6">
        <section>
          <h2 className="text-lg font-medium text-text mb-3">{t('settings.appearance')}</h2>
          <div className="flex gap-2">
            {(['system', 'light', 'dark'] as const).map(tm => (
              <Chip
                key={tm}
                selected={theme.value === tm}
                onClick={() => handleThemeChange(tm)}
              >
                {tm === 'system' ? <Monitor className="h-4 w-4" /> : tm === 'light' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                {t(`settings.theme.${tm}`)}
              </Chip>
            ))}
          </div>
        </section>
        
        <section>
          <label className="block text-sm font-medium text-text mb-3">{t('settings.language')}</label>
          <div className="flex gap-2">
            {(['es', 'eu'] as const).map(l => (
              <Chip
                key={l}
                selected={lang.value === l}
                onClick={() => handleLangChange(l)}
              >
                {l.toUpperCase()}
              </Chip>
            ))}
          </div>
        </section>
        
        <section>
          <div className="flex items-center justify-between mb-4">
            <label className="flex items-center gap-3 text-text">
              <Bell className="h-5 w-5 text-primary" />
              <span className="font-medium">{t('settings.notifications')}</span>
            </label>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={notificationsEnabled.value}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleNotificationsToggle(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-border peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
            </label>
          </div>
          
          {notificationsEnabled.value && (
            <div className="space-y-3 text-sm">
              <p className="text-text-muted flex items-center gap-2">
                <Check className="h-4 w-4 text-success" />
                {t('settings.permissionGranted')}
              </p>
              <p className="text-text-muted">
                {t('settings.lastUpdate', { time: 'hace 2 min' })}
              </p>
            </div>
          )}
        </section>
        
        <section>
          <h2 className="text-lg font-medium text-text mb-3">{t('settings.data')}</h2>
          <Card padding="md">
            <div className="flex items-center justify-between mb-4">
              <div>
                <p className="font-medium text-text">{t('settings.cacheSize', { size: '2.3' })}</p>
                <p className="text-sm text-text-muted">{t('settings.lastUpdate', { time: 'hace 15s' })}</p>
              </div>
              <Button variant="secondary" size="sm" leftIcon={Trash2} onClick={() => showClearConfirm.value = true}>
                {t('settings.clearCache')}
              </Button>
            </div>
            <div className="text-sm text-text-muted">
              <p>{t('settings.lastUpdate', { time: 'hace 15s' })}</p>
              <p>GTFS estático: v2026.11</p>
            </div>
          </Card>
        </section>
        
        <section>
          <h2 className="text-lg font-medium text-text mb-3">{t('settings.about')}</h2>
          <Card padding="md" className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-text">{t('settings.version', { version: '0.1.0-beta' })}</p>
                <p className="text-sm text-text-muted">{t('settings.license')}</p>
              </div>
            </div>
            <div className="pt-4 border-t border-border">
              <p className="text-sm text-text-muted mb-2">{t('settings.dataSource')}</p>
              <div className="flex gap-2">
                <Button variant="ghost" size="sm" leftIcon={ExternalLink}>
                  <a href="https://opendata.euskadi.eus" target="_blank" rel="noopener">Open Data Euskadi</a>
                </Button>
                <Button variant="ghost" size="sm" leftIcon={Github}>
                  <a href="https://github.com/IkerGoni/bus-dbus" target="_blank" rel="noopener">GitHub</a>
                </Button>
              </div>
            </div>
            <div className="pt-4 border-t border-border text-center">
              <Button variant="secondary" leftIcon={Coffee}>
                <a href="https://buymeacoffee.com" target="_blank" rel="noopener">{t('settings.donate')}</a>
              </Button>
            </div>
          </Card>
        </section>
        
        {showClearConfirm.value && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" role="dialog" aria-modal="true">
            <Card className="w-full max-w-md p-6">
              <h3 className="text-lg font-semibold text-text mb-2">¿Limpiar toda la caché?</h3>
              <p className="text-text-muted mb-6">Se eliminarán todos los datos guardados (paradas, alertas, configuración). La app volverá a descargar los datos necesarios.</p>
              <div className="flex gap-3">
                <Button variant="secondary" fullWidth onClick={() => showClearConfirm.value = false}>
                  {t('common.cancel')}
                </Button>
                <Button variant="danger" fullWidth onClick={handleClearCache}>
                  {t('settings.clearCache')}
                </Button>
              </div>
            </Card>
          </div>
        )}
      </div>
    </div>
  )
}
