import { useSignal, useComputed, effect } from '@preact/signals-react'
import { Link, useLocation } from 'wouter'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, Bell, Moon, AlertCircle, Check, Trash2, Edit, ToggleLeft, ToggleRight, Clock } from 'lucide-react'
import { alerts } from '../../store'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { useToast } from '../components/ui/ToastContainer'

export function AlertsList() {
  const { t } = useTranslation()
  const [, navigate] = useLocation()
  const { addToast } = useToast()
  
  const activeAlerts = useComputed(() => alerts.value.filter((a: any) => a.active))
  const inactiveAlerts = useComputed(() => alerts.value.filter((a: any) => !a.active))
  
  const toggleAlert = (alert: any) => {
    alerts.value = alerts.value.map((a: any) => 
      a.alertId === alert.alertId ? { ...a, active: !a.active, updatedAt: Date.now() } : a
    )
    addToast({ 
      type: 'info', 
      title: alert.active ? 'Alerta desactivada' : 'Alerta activada',
      message: `${alert.lineName} → ${alert.destination}`
    })
  }
  
  const editAlert = (alert: any) => {
    navigate(`/alert/${alert.alertId}/edit`)
  }
  
  const deleteAlert = (alert: any) => {
    alerts.value = alerts.value.filter((a: any) => a.alertId !== alert.alertId)
    addToast({ type: 'info', title: t('common.delete'), message: 'Alerta eliminada' })
  }
  
  const isSilenced = (alert: any) => {
    if (!alert.silenceEnabled) return false
    const now = new Date()
    const currentTime = now.toTimeString().slice(0, 5)
    return currentTime >= alert.silenceFrom || currentTime <= alert.silenceTo
  }
  
  const renderAlertCard = (alert: any) => {
    const silenced = isSilenced(alert)
    
    return (
      <Card 
        key={alert.alertId} 
        padding="md" 
        className={silenced ? 'opacity-70' : ''}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-medium text-text">{alert.lineName}</span>
              <span className="text-text-muted">→</span>
              <span className="text-text-muted truncate">{alert.destination}</span>
              <Badge variant={alert.active ? 'rt' : 'default'}>
                {alert.active ? (silenced ? '🌙 ' : '') + t('alertsList.status.active') : t('alertsList.status.inactive')}
              </Badge>
              {silenced && <Moon className="h-3 w-3 text-warning" aria-hidden="true" />}
            </div>
            <p className="text-sm text-text-muted mt-1">
              <Clock className="h-3 w-3 inline mr-1" aria-hidden="true" />
              {t('home.arrivingIn', { minutes: alert.minutesAhead })} • {alert.stopName}
            </p>
            <div className="flex flex-wrap gap-1 mt-2 text-xs text-text-muted">
              {alert.days.map((d: number) => ['D','L','M','X','J','V','S'][d]).join(' ')}
              <span>•</span>
              {t(`alertConfig.recurrenceOptions.${alert.recurrence}`)}
            </div>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={alert.active}
                onChange={() => toggleAlert(alert)}
                className="sr-only peer"
              />
              <div className="w-10 h-6 bg-border peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
            </label>
            <button
              onClick={() => editAlert(alert)}
              className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-bg-secondary touch-manipulation min-h-[44px] min-w-[44px]"
              aria-label={t('common.edit')}
            >
              <Edit className="h-4 w-4" />
            </button>
            <button
              onClick={() => deleteAlert(alert)}
              className="p-2 rounded-lg text-text-muted hover:text-error hover:bg-red-50 dark:hover:bg-red-900/20 touch-manipulation min-h-[44px] min-w-[44px]"
              aria-label={t('common.delete')}
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      </Card>
    )
  }
  
  return (
    <div className="page-container">
      <div className="sticky top-16 z-30 bg-surface/95 backdrop-blur-sm border-b border-border safe-top px-4 py-3">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-semibold text-text">{t('alertsList.title')}</h1>
          <Button variant="primary" size="sm" leftIcon={Bell}>
            <Link href="/alert/new">{t('alertsList.addNew')}</Link>
          </Button>
        </div>
      </div>
      
      <div className="px-4 py-4 space-y-6">
        {activeAlerts.value.length === 0 && inactiveAlerts.value.length === 0 ? (
          <Card className="text-center py-12">
            <Bell className="h-12 w-12 mx-auto text-text-muted mb-4" />
            <h2 className="text-xl font-medium text-text mb-2">{t('alertsList.empty')}</h2>
            <Button variant="primary" leftIcon={Bell} size="lg">
              <Link href="/alert/new">{t('alertsList.addNew')}</Link>
            </Button>
          </Card>
        ) : (
          <>
            {activeAlerts.value.length > 0 && (
              <section>
                <h2 className="text-lg font-medium text-text mb-3">
                  {t('alertsList.status.active')} ({activeAlerts.value.length})
                </h2>
                <div className="space-y-3" role="list">
                  {activeAlerts.value.map(renderAlertCard)}
                </div>
              </section>
            )}
            
            {inactiveAlerts.value.length > 0 && (
              <section>
                <h2 className="text-lg font-medium text-text mb-3">
                  {t('alertsList.status.inactive')} ({inactiveAlerts.value.length})
                </h2>
                <div className="space-y-3" role="list">
                  {inactiveAlerts.value.map(renderAlertCard)}
                </div>
              </section>
            )}
          </>
        )}
      </div>
    </div>
  )
}
