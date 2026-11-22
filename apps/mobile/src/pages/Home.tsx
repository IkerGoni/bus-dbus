import { useSignal, useComputed, effect } from '@preact/signals-react'
import { Link, useLocation } from 'wouter'
import { useTranslation } from 'react-i18next'
import { MapPin, Clock, AlertCircle, ChevronRight, Bell, Moon, Sun } from 'lucide-react'
import { rtData, alerts, settings, countdown, nextArrival } from '../../store'
import { formatDistanceToNow } from 'date-fns'
import { es, eu } from 'date-fns/locale'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/ToastContainer'

export function Home() {
  const { t, i18n } = useTranslation()
  const [, navigate] = useLocation()
  const { addToast } = useToast()
  const showSnooze = useSignal(false)
  
  const activeAlert = useComputed(() => 
    alerts.value.find((a: any) => a.active && !a.silenced)
  )
  
  const isStale = useComputed(() => 
    rtData.value && Date.now() - rtData.value.timestamp > 60000
  )
  
  const locale = i18n.language === 'eu' ? eu : es
  
  const handleSnooze = () => {
    if (activeAlert.value) {
      showSnooze.value = true
      setTimeout(() => showSnooze.value = false, 5 * 60 * 1000)
      addToast({
        type: 'info',
        title: t('home.actions.snooze'),
        message: t('home.actions.snooze') + ' 5 min'
      })
    }
  }
  
  const handleDisable = () => {
    if (activeAlert.value) {
      addToast({
        type: 'info',
        title: t('home.actions.disable'),
        message: 'Alerta desactivada'
      })
    }
  }
  
  if (!activeAlert.value) {
    return (
      <div className="page-container px-4 py-6">
        <Card className="text-center py-12">
          <MapPin className="h-12 w-12 mx-auto text-text-muted mb-4" aria-hidden="true" />
          <h1 className="text-xl font-semibold text-text mb-2">{t('home.emptyTitle')}</h1>
          <p className="text-text-muted mb-6">{t('onboarding.subtitle')}</p>
          <Button 
            fullWidth
            size="lg"
            leftIcon={MapPin}
          >
            <Link href="/stops">{t('home.emptyAction')}</Link>
          </Button>
        </Card>
        
        <div className="mt-6 grid grid-cols-3 gap-3 text-center">
          <div className="p-4 bg-bg-secondary rounded-lg">
            <Bell className="h-6 w-6 mx-auto text-primary mb-2" />
            <p className="text-sm text-text-muted">{t('common.notifications')}</p>
          </div>
          <div className="p-4 bg-bg-secondary rounded-lg">
            <Clock className="h-6 w-6 mx-auto text-primary mb-2" />
            <p className="text-sm text-text-muted">{t('alertConfig.minutesAhead')}</p>
          </div>
          <div className="p-4 bg-bg-secondary rounded-lg">
            <Moon className="h-6 w-6 mx-auto text-primary mb-2" />
            <p className="text-sm text-text-muted">{t('alertConfig.silence')}</p>
          </div>
        </div>
      </div>
    )
  }
  
  const alert = activeAlert.value
  const arrival = nextArrival.value
  const minutes = countdown.value
  
  return (
    <div className="page-container px-4 py-6">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Badge variant={rtData.value ? 'rt' : 'scheduled'}>
            {rtData.value ? t('home.live') : t('home.scheduled')}
          </Badge>
          {isStale.value && (
            <Badge variant="default">{t('home.stale')}</Badge>
          )}
        </div>
        <div className="text-sm text-text-muted">
          {rtData.value && formatDistanceToNow(rtData.value.timestamp, { 
            addSuffix: true, 
            locale 
          })}
        </div>
      </div>
      
      <Card className="mb-4">
        <div className="flex items-start justify-between mb-3">
          <div>
            <p className="text-sm text-text-muted">{t('home.nextArrival')}</p>
            <h2 className="text-2xl font-bold text-text flex items-center gap-2">
              {alert?.lineName} 
              <span className="text-lg font-normal text-text-muted">→</span>
              <span className="text-lg font-normal">{alert?.destination}</span>
            </h2>
            <p className="text-sm text-text-muted mt-1">
              <MapPin className="h-4 w-4 inline mr-1" aria-hidden="true" />
              {alert?.stopName}
            </p>
          </div>
          <div className="text-right">
            <p className="text-4xl font-bold text-primary tabular-nums">
              {minutes !== null ? `${minutes}'` : t('common.now')}
            </p>
            <p className="text-sm text-text-muted">
              {t('home.arrivingIn', { minutes: minutes || 0 })}
            </p>
          </div>
        </div>
        
        <div className="h-2 bg-border rounded-full overflow-hidden mb-4">
          <div 
            className="h-full bg-primary transition-all duration-1000 ease-out"
            style={{ width: arrival ? `${Math.min(100, Math.max(0, 100 - (minutes || 0) * 10))}%` : '0%' }}
          />
        </div>
        
        <div className="flex gap-2">
          <Button 
            variant="ghost" 
            fullWidth
            leftIcon={Clock}
            onClick={handleSnooze}
            disabled={showSnooze.value}
          >
            {showSnooze.value ? t('common.snooze') + '...' : t('home.actions.snooze')}
          </Button>
          <Button 
            variant="secondary" 
            fullWidth
            leftIcon={MapPin}
          >
            <Link href={`/stop/${alert?.stopId}`}>{t('home.actions.viewDetail')}</Link>
          </Button>
          <Button 
            variant="danger" 
            fullWidth
            leftIcon={AlertCircle}
            onClick={handleDisable}
          >
            {t('home.actions.disable')}
          </Button>
        </div>
      </Card>
      
      <div className="grid grid-cols-3 gap-3 mb-6">
        <Card padding="sm" className="text-center">
          <p className="text-2xl font-bold text-primary">{alerts.value.filter((a: any) => a.active).length}</p>
          <p className="text-xs text-text-muted">{t('alertsList.title')}</p>
        </Card>
        <Card padding="sm" className="text-center">
          <p className="text-2xl font-bold text-success">{rtData.value ? '✓' : '✗'}</p>
          <p className="text-xs text-text-muted">{t('common.live')}</p>
        </Card>
        <Card padding="sm" className="text-center">
          <p className="text-2xl font-bold text-warning">{settings.value.notificationsEnabled ? '🔔' : '🔕'}</p>
          <p className="text-xs text-text-muted">{t('settings.notifications')}</p>
        </Card>
      </div>
      
      <Button variant="ghost" fullWidth className="mt-4">
        <Link href="/alerts">
          {t('alertsList.title')} ({alerts.value.filter((a: any) => a.active).length})
        </Link>
      </Button>
    </div>
  )
}
