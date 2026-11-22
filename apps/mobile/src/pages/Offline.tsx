import { useTranslation } from 'react-i18next'
import { WifiOff, Database, AlertCircle, RefreshCw, Settings, ExternalLink } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'

export function Offline() {
  const { t } = useTranslation()
  const isOnline = typeof navigator !== 'undefined' ? navigator.onLine : true
  const hasCache = true
  
  const handleRetry = () => {
    window.location.reload()
  }
  
  const handleOpenSettings = () => {
    if (typeof window !== 'undefined') {
      window.open('about:blank', '_self')
    }
  }
  
  if (isOnline) return null
  
  return (
    <div className="fixed inset-0 z-50 bg-surface flex items-center justify-center p-4" role="alertdialog" aria-modal="true">
      <Card className="w-full max-w-md text-center p-8">
        <WifiOff className="h-16 w-16 mx-auto text-text-muted mb-4" />
        <h1 className="text-2xl font-semibold text-text mb-2">{t('offline.noConnection')}</h1>
        
        {hasCache ? (
          <>
            <p className="text-text-muted mb-4">{t('offline.staleData', { time: '15 min' })}</p>
            <div className="mb-6 p-4 bg-warning/10 border border-warning/20 rounded-lg">
              <AlertCircle className="h-5 w-5 mx-auto text-warning mb-2" />
              <p className="text-sm text-text">{t('offline.staleWarning')}</p>
            </div>
            <div className="space-y-3">
              <Button variant="primary" fullWidth onClick={handleRetry} leftIcon={RefreshCw}>
                {t('offline.retry')}
              </Button>
              <Button variant="secondary" fullWidth onClick={handleOpenSettings} leftIcon={Settings}>
                {t('offline.openSettings')}
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="text-text-muted mb-6">{t('offline.noCache')}</p>
            <Button variant="primary" fullWidth onClick={handleRetry} leftIcon={RefreshCw}>
              {t('offline.retry')}
            </Button>
          </>
        )}
      </Card>
    </div>
  )
}
