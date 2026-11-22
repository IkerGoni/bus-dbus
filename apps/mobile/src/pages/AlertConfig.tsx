import { useSignal, useComputed, effect } from '@preact/signals-react'
import { useParams, useLocation } from 'wouter'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, Clock, Moon, Sun, Bell, Check } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Chip } from '../components/ui/Chip'
import { Input } from '../components/ui/Input'
import { Card } from '../components/ui/Card'
import { useToast } from '../components/ui/ToastContainer'
import { alerts } from '../../store'
import type { UserAlert } from '../../types'

const DAYS = [
  { key: 0, short: 'D', label: 'Domingo' },
  { key: 1, short: 'L', label: 'Lunes' },
  { key: 2, short: 'M', label: 'Martes' },
  { key: 3, short: 'X', label: 'Miércoles' },
  { key: 4, short: 'J', label: 'Jueves' },
  { key: 5, short: 'V', label: 'Viernes' },
  { key: 6, short: 'S', label: 'Sábado' }
]

const RECURRENCE_OPTIONS = [
  { value: 'daily', label: 'Diario' },
  { value: 'weekdays', label: 'Laborables' },
  { value: 'weekends', label: 'Fines de semana' }
] as const

export function AlertConfig() {
  const { t } = useTranslation()
  const params = useParams()
  const [, navigate] = useLocation()
  const { addToast } = useToast()
  const alertId = params.alertId
  const isEditing = !!alertId
  
  const lineId = useSignal('')
  const minutesAhead = useSignal(5)
  const days = useSignal<number[]>([1,2,3,4,5])
  const recurrence = useSignal<'daily' | 'weekdays' | 'weekends'>('daily')
  const silenceEnabled = useSignal(true)
  const silenceFrom = useSignal('23:00')
  const silenceTo = useSignal('07:00')
  
  effect(() => {
    if (isEditing) {
      const alert = alerts.value.find((a: any) => a.alertId === alertId)
      if (alert) {
        lineId.value = alert.lineId
        minutesAhead.value = alert.minutesAhead
        days.value = alert.days
        recurrence.value = alert.recurrence
        silenceEnabled.value = alert.silenceEnabled
        silenceFrom.value = alert.silenceFrom
        silenceTo.value = alert.silenceTo
      }
    }
  }, [isEditing, alertId])
  
  const lines = ['5', '16', '21', '25', '33']
  const destinations: Record<string, string> = {
    '5': 'Amara',
    '16': 'Herrera',
    '21': 'Ategorrieta',
    '25': 'Universidades',
    '33': 'Altza'
  }
  
  const previewText = useComputed(() => {
    if (!lineId.value) return ''
    const dest = destinations[lineId.value] || ''
    return `${t('push.title', { line: lineId.value, destination: dest })}
${t('push.body', { minutes: minutesAhead.value, stop: 'Parada seleccionada' })}`
  })
  
  const handleSubmit = () => {
    if (!lineId.value) {
      addToast({ type: 'error', title: t('common.error'), message: 'Selecciona una línea' })
      return
    }
    
    const newAlert: any = {
      alertId: alertId || `alert_${Date.now()}`,
      stopId: '',
      stopName: '',
      lineId: lineId.value,
      lineName: lineId.value,
      destination: destinations[lineId.value] || '',
      minutesAhead: minutesAhead.value,
      days: days.value,
      recurrence: recurrence.value,
      silenceEnabled: silenceEnabled.value,
      silenceFrom: silenceFrom.value,
      silenceTo: silenceTo.value,
      active: true,
      silenced: false,
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    
    if (isEditing) {
      alerts.value = alerts.value.map((a: any) => a.alertId === alertId ? { ...newAlert, alertId } : a)
      addToast({ type: 'success', title: t('common.success'), message: t('alertConfig.save') })
    } else {
      alerts.value = [...alerts.value, newAlert]
      addToast({ type: 'success', title: t('common.success'), message: t('alertConfig.save') })
    }
    
    navigate('/alerts')
  }
  
  const handleDelete = () => {
    if (isEditing) {
      alerts.value = alerts.value.filter((a: any) => a.alertId !== alertId)
      addToast({ type: 'info', title: t('common.delete'), message: 'Alerta eliminada' })
      navigate('/alerts')
    }
  }
  
  return (
    <div className="page-container">
      <div className="sticky top-16 z-30 bg-surface/95 backdrop-blur-sm border-b border-border safe-top px-4 py-3">
        <div className="flex items-center justify-between">
          <Button variant="ghost" size="sm" onClick={() => navigate('/alerts')} aria-label={t('common.back')}>
            <ChevronLeft className="h-5 w-5" />
          </Button>
          <h1 className="text-lg font-semibold text-text">{isEditing ? t('alertConfig.title') : t('alertConfig.title')}</h1>
          <div className="w-10" />
        </div>
      </div>
      
      <div className="px-4 py-4 space-y-6">
        <section>
          <label className="block text-sm font-medium text-text mb-3">{t('alertConfig.line')}</label>
          <div className="flex flex-wrap gap-2">
            {lines.map(l => (
              <Chip
                key={l}
                variant="line"
                selected={lineId.value === l}
                onClick={() => lineId.value = l}
              >
                {l}
              </Chip>
            ))}
          </div>
        </section>
        
        <section>
          <div className="flex items-center justify-between mb-3">
            <label className="text-sm font-medium text-text">{t('alertConfig.minutesAhead')}</label>
            <span className="text-lg font-semibold text-primary">{minutesAhead.value} {t('common.minutes_short')}</span>
          </div>
          <input
            type="range"
            min="1"
            max="30"
            value={minutesAhead.value}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => minutesAhead.value = Number(e.target.value)}
            className="w-full h-8 appearance-none bg-bg-secondary rounded-lg [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-6 [&::-webkit-slider-thumb]:h-6 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-primary [&::-webkit-slider-thumb]:cursor-pointer"
            aria-label={t('alertConfig.minutesAhead')}
          />
        </section>
        
        <section>
          <label className="block text-sm font-medium text-text mb-3">{t('alertConfig.days')}</label>
          <div className="flex gap-1.5 overflow-x-auto pb-2">
            {DAYS.map(day => (
              <Chip
                key={day.key}
                variant="day"
                selected={days.value.includes(day.key)}
                onClick={() => days.value = days.value.includes(day.key) ? days.value.filter(x => x !== day.key) : [...days.value, day.key]}
              >
                {day.short}
              </Chip>
            ))}
          </div>
        </section>
        
        <section>
          <label className="block text-sm font-medium text-text mb-3">{t('alertConfig.recurrence')}</label>
          <div className="flex gap-2">
            {RECURRENCE_OPTIONS.map(opt => (
              <Chip
                key={opt.value}
                variant="recurrence"
                selected={recurrence.value === opt.value}
                onClick={() => recurrence.value = opt.value}
              >
                {t(`alertConfig.recurrenceOptions.${opt.value}`)}
              </Chip>
            ))}
          </div>
        </section>
        
        <section>
          <div className="flex items-center justify-between mb-4">
            <label className="text-sm font-medium text-text flex items-center gap-2">
              <Moon className="h-5 w-5" aria-hidden="true" />
              {t('alertConfig.silence')}
            </label>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={silenceEnabled.value}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => silenceEnabled.value = e.target.checked}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-border peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
            </label>
          </div>
          
          {silenceEnabled.value && (
            <div className="grid grid-cols-2 gap-3">
              <Input
                type="time"
                label={t('alertConfig.silenceFrom')}
                value={silenceFrom.value}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => silenceFrom.value = e.target.value}
              />
              <Input
                type="time"
                label={t('alertConfig.silenceTo')}
                value={silenceTo.value}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => silenceTo.value = e.target.value}
              />
            </div>
          )}
        </section>
        
        <Card className="bg-bg-secondary">
          <div className="flex items-center gap-2 mb-3">
            <Bell className="h-5 w-5 text-primary" aria-hidden="true" />
            <span className="font-medium text-text">{t('alertConfig.preview')}</span>
          </div>
          <div className="font-mono text-sm whitespace-pre-wrap text-text-muted">
            {previewText.value || t('alertConfig.preview')}
          </div>
        </Card>
        
        <div className="space-y-3 pt-4 border-t border-border">
          <Button variant="primary" fullWidth size="lg" onClick={handleSubmit}>
            {t(isEditing ? 'common.save' : 'alertConfig.save')}
          </Button>
          <Button variant="secondary" fullWidth onClick={() => navigate('/alerts')}>
            {t('common.cancel')}
          </Button>
          {isEditing && (
            <Button variant="danger" fullWidth onClick={handleDelete}>
              {t('alertConfig.delete')}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
