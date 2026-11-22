import { useSignal, useComputed, effect } from '@preact/signals-react'
import { useParams, useLocation } from 'wouter'
import { useTranslation } from 'react-i18next'
import { MapPin, Clock, Users, CreditCard, AlertCircle, ChevronRight, Star, Bell, Star as StarIcon, ChevronLeft } from 'lucide-react'
import { rtData } from '../../store'
import { getStop, getRoutesForStop } from '../../lib/db/queries'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/ToastContainer'

export function StopDetail() {
  const { t } = useTranslation()
  const params = useParams()
  const [, navigate] = useLocation()
  const stopId = params.stopId
  const { addToast } = useToast()
  const stop = useSignal<any | null>(null)
  const routes = useSignal<string[]>([])
  const loading = useSignal(true)
  const isFavorite = useSignal(false)
  
  effect(() => {
    if (stopId) loadStopDetail(stopId)
  }, [stopId])
  
  const loadStopDetail = async (id: string) => {
    loading.value = true
    try {
      const [stopData, routeIds] = await Promise.all([
        getStop(id),
        getRoutesForStop(id)
      ])
      if (stopData) {
        stop.value = stopData
        routes.value = routeIds
      }
    } catch (err) {
      console.error('Error loading stop detail:', err)
      addToast({ type: 'error', title: t('common.error'), message: 'No se pudo cargar la parada' })
    } finally {
      loading.value = false
    }
  }
  
  const arrivals = useComputed(() => {
    if (!rtData.value || !stop.value) return []
    
    const stopId = stop.value.stop_id
    const relevant = rtData.value.tripUpdates?.filter((tu: any) => 
      tu.stopTimeUpdate?.some((stu: any) => stu.stopId === stopId)
    ) || []
    
    return relevant.map((tu: any) => {
      const stu = tu.stopTimeUpdate?.find((s: any) => s.stopId === stopId)
      const arrivalTime = stu?.arrival?.time ? 
        (typeof stu.arrival.time === 'number' ? stu.arrival.time * 1000 : new Date(stu.arrival.time).getTime()) 
        : null
      const vehicle = tu.vehicle
      const isRT = !!vehicle
      
      return {
        lineId: tu.trip?.routeId || '',
        destination: tu.trip?.destination || '',
        arrivalTime,
        minutes: arrivalTime ? Math.max(0, Math.round((arrivalTime - Date.now()) / 1000 / 60)) : null,
        isRT,
        occupancy: vehicle?.occupancyStatus,
        vehicleLabel: vehicle?.label
      }
    })
    .filter((a: any) => a.minutes !== null && a.minutes <= 60)
    .sort((a: any, b: any) => (a.minutes || 999) - (b.minutes || 999))
    .slice(0, 5)
  })
  
  const handleFavoriteToggle = () => {
    isFavorite.value = !isFavorite.value
    addToast({ 
      type: 'info', 
      title: stop.value?.stop_name || '', 
      message: isFavorite.value ? 'Eliminado de favoritas' : 'Añadido a favoritas' 
    })
  }
  
  const handleCreateAlert = () => {
    if (stop.value) {
      // Navigate to alert config with this stop pre-selected
    }
  }
  
  if (loading.value) {
    return (
      <div className="page-container">
        <div className="px-4 py-4 space-y-4" role="status">
          <Card padding="sm">
            <div className="flex items-center gap-3">
              <Skeleton variant="circular" />
              <div className="flex-1 space-y-2">
                <Skeleton variant="text" />
                <Skeleton variant="text" />
              </div>
            </div>
          </Card>
          {[1,2,3].map(i => (
            <Card key={i} padding="sm">
              <div className="flex items-center justify-between">
                <div className="flex-1 space-y-1">
                  <Skeleton variant="text" />
                  <Skeleton variant="text" />
                </div>
                <Skeleton variant="circular" />
              </div>
            </Card>
          ))}
        </div>
      </div>
    )
  }
  
  if (!stop.value) {
    return (
      <div className="page-container px-4 py-8 text-center">
        <AlertCircle className="h-12 w-12 mx-auto text-text-muted mb-4" />
        <h2 className="text-xl font-medium text-text mb-2">{t('common.error')}</h2>
        <p className="text-text-muted mb-4">Parada no encontrada</p>
        <Button variant="primary">
          <Link href="/stops">{t('common.retry')}</Link>
        </Button>
      </div>
    )
  }
  
  const formatOccupancy = (status?: number) => {
    if (!status) return null
    if (status <= 2) return { label: 'Baja', variant: 'occupancy-low' as const }
    if (status <= 4) return { label: 'Media', variant: 'occupancy-medium' as const }
    return { label: 'Alta', variant: 'occupancy-high' as const }
  }
  
  return (
    <div className="page-container">
      <div className="sticky top-16 z-30 bg-surface/95 backdrop-blur-sm border-b border-border safe-top px-4 py-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-text truncate">{stop.value.stop_name}</h1>
            {stop.value.stop_code && (
              <span className="text-sm text-text-muted">{stop.value.stop_code}</span>
            )}
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              onClick={handleFavoriteToggle}
              className={`p-2 rounded-lg touch-manipulation min-h-[48px] min-w-[48px] ${
                isFavorite.value ? 'text-warning bg-warning/10' : 'text-text-muted hover:text-warning hover:bg-bg-secondary'
              }`}
              aria-label={isFavorite.value ? 'Eliminar de favoritas' : 'Añadir a favoritas'}
              aria-pressed={isFavorite.value}
            >
              <StarIcon className={isFavorite.value ? 'fill-current' : ''} />
            </button>
            <Button 
              variant="primary" 
              size="sm" 
              leftIcon={Bell}
              onClick={handleCreateAlert}
            >
              {t('stopDetail.createAlert')}
            </Button>
          </div>
        </div>
      </div>
      
      <div className="px-4 py-4 space-y-6">
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-lg font-medium text-text">{t('stopDetail.lines')}</h2>
            <span className="text-sm text-text-muted">{routes.value.length} {t('common.lines')}</span>
          </div>
          <div className="flex flex-wrap gap-2">
            {routes.value.map(lineId => (
              <Badge key={lineId} variant="default" className="px-3 py-1.5 text-base">
                {lineId}
              </Badge>
            ))}
          </div>
        </section>
        
        <section>
          <h2 className="text-lg font-medium text-text mb-3">{t('stopDetail.nextArrivals')}</h2>
          
          {arrivals.value.length === 0 ? (
            <Card className="text-center py-6">
              <Clock className="h-10 w-10 mx-auto text-text-muted mb-3" />
              <p className="text-text-muted">No hay llegadas programadas en la próxima hora</p>
            </Card>
          ) : (
            <div className="space-y-2" role="list">
              {arrivals.value.map((arrival: any, i: number) => (
                <Card 
                  key={`${arrival.lineId}-${arrival.arrivalTime}`}
                  padding="sm" 
                  role="listitem"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="flex-shrink-0">
                        <Badge variant={arrival.isRT ? 'rt' : 'scheduled'} className="text-xs">
                          {arrival.isRT ? t('home.live') : t('home.scheduled')}
                        </Badge>
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-text">{arrival.lineId}</span>
                          <span className="text-text-muted">→</span>
                          <span className="text-text-muted truncate max-w-[150px]">{arrival.destination}</span>
                        </div>
                        {arrival.vehicleLabel && (
                          <p className="text-xs text-text-muted">{arrival.vehicleLabel}</p>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-3 flex-shrink-0">
                      {arrival.occupancy && formatOccupancy(arrival.occupancy) && (
                        <Badge variant={formatOccupancy(arrival.occupancy)!.variant}>
                          <Users className="h-3 w-3 mr-1" aria-hidden="true" />
                          {formatOccupancy(arrival.occupancy)!.label}
                        </Badge>
                      )}
                      <div className="text-right">
                        <p className="text-xl font-bold text-primary tabular-nums">
                          {arrival.minutes !== null ? `${arrival.minutes}'` : t('common.now')}
                        </p>
                        <p className="text-xs text-text-muted">min</p>
                      </div>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </section>
        
        <section className="grid grid-cols-2 gap-3">
          <Card padding="md" className="text-center">
            <Users className="h-8 w-8 mx-auto text-primary mb-2" />
            <p className="text-sm text-text-muted">{t('stopDetail.occupancy')}</p>
            <p className="text-xl font-semibold text-text">
              {arrivals.value[0]?.occupancy ? formatOccupancy(arrivals.value[0].occupancy)?.label : '—'}
            </p>
          </Card>
          <Card padding="md" className="text-center">
            <CreditCard className="h-8 w-8 mx-auto text-success mb-2" />
            <p className="text-sm text-text-muted">{t('stopDetail.mugiBalance')}</p>
            <p className="text-xl font-semibold text-success">12,50 €</p>
          </Card>
        </section>
      </div>
    </div>
  )
}

import { Link } from 'wouter'
import { Users, CreditCard } from 'lucide-react'
