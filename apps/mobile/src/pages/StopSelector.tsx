import { useSignal, useComputed, effect } from '@preact/signals-react'
import { Link, useLocation } from 'wouter'
import { useTranslation } from 'react-i18next'
import { MapPin, Search, Star, Loader2, Navigation, Locate, ChevronRight, X } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/ToastContainer'
import { getNearestStops, searchStops } from '../../lib/db/queries'
import { useGeolocation } from '../../hooks/useGeolocation'

export function StopSelector() {
  const { t } = useTranslation()
  const [, navigate] = useLocation()
  const { addToast } = useToast()
  const query = useSignal('')
  const stops = useSignal<any[]>([])
  const loading = useSignal(false)
  const activeTab = useSignal<'nearby' | 'search' | 'favorites'>('nearby')
  const { location, error: geoError, requestLocation } = useGeolocation()
  
  const filteredStops = useComputed(() => {
    if (activeTab.value === 'search' && query.value.trim()) {
      return stops.value.filter((s: any) => 
        s.stop_name.toLowerCase().includes(query.value.toLowerCase()) ||
        s.stop_code?.includes(query.value)
      )
    }
    return stops.value
  })
  
  effect(() => {
    if (activeTab.value === 'nearby' && location) {
      loadNearbyStops(location.latitude, location.longitude)
    } else if (activeTab.value === 'favorites') {
      loadFavorites()
    }
  }, [activeTab.value, location])
  
  const loadNearbyStops = async (lat: number, lon: number) => {
    loading.value = true
    try {
      const nearby = await getNearestStops(lat, lon, 20)
      stops.value = nearby
    } catch (err) {
      console.error('Error loading nearby stops:', err)
      addToast({ type: 'error', title: t('common.error'), message: t('stopSelector.loading') })
    } finally {
      loading.value = false
    }
  }
  
  const loadFavorites = async () => {
    stops.value = []
  }
  
  const handleSearch = async (searchQuery: string) => {
    query.value = searchQuery
    activeTab.value = 'search'
    if (!searchQuery.trim()) return
    
    loading.value = true
    try {
      const results = await searchStops(searchQuery, 20)
      stops.value = results
    } catch (err) {
      console.error('Error searching stops:', err)
    } finally {
      loading.value = false
    }
  }
  
  const handleStopSelect = (stop: any) => {
    navigate(`/stop/${stop.stop_id}`)
  }
  
  const handleFavoriteToggle = (stop: any, e: React.MouseEvent) => {
    e.stopPropagation()
    e.preventDefault()
    addToast({ 
      type: 'info', 
      title: stop.stop_name, 
      message: 'Añadido a favoritas' 
    })
  }
  
  return (
    <div className="page-container">
      <div className="sticky top-16 z-30 bg-surface/95 backdrop-blur-sm border-b border-border safe-top px-4 py-3">
        <div className="relative max-w-xl mx-auto">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-text-muted" aria-hidden="true" />
          <Input
            type="search"
            placeholder={t('stopSelector.searchPlaceholder')}
            value={query.value}
            onChange={(e) => handleSearch(e.target.value)}
            className="pl-10 pr-10"
          />
          {query.value && (
            <button
              onClick={() => { query.value = ''; handleSearch(''); }}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-text-muted hover:text-text"
              aria-label={t('common.clear')}
            >
              <X className="h-5 w-5" />
            </button>
          )}
        </div>
      </div>
      
      <div className="sticky top-16 z-20 bg-surface border-b border-border flex overflow-x-auto">
        <button
          onClick={() => { activeTab.value = 'nearby'; query.value = ''; }}
          className={`flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab.value === 'nearby' 
              ? 'text-primary border-b-2 border-primary' 
              : 'text-text-muted'
          }`}
        >
          <Navigation className="h-4 w-4" aria-hidden="true" />
          {t('stopSelector.nearby')}
        </button>
        <button
          onClick={() => { activeTab.value = 'search'; }}
          className={`flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab.value === 'search' 
              ? 'text-primary border-b-2 border-primary' 
              : 'text-text-muted'
          }`}
        >
          <Search className="h-4 w-4" aria-hidden="true" />
          {t('common.search')}
        </button>
        <button
          onClick={() => { activeTab.value = 'favorites'; query.value = ''; }}
          className={`flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors ${
            activeTab.value === 'favorites' 
              ? 'text-primary border-b-2 border-primary' 
              : 'text-text-muted'
          }`}
        >
          <Star className="h-4 w-4" aria-hidden="true" />
          {t('stopSelector.favorites')}
        </button>
      </div>
      
      <div className="px-4 py-4">
        {activeTab.value === 'nearby' && !location && (
          <Card className="text-center py-8">
            <Locate className="h-12 w-12 mx-auto text-text-muted mb-4" aria-hidden="true" />
            <h3 className="text-lg font-medium text-text mb-2">{t('stopSelector.useLocation')}</h3>
            <p className="text-text-muted mb-4">
              {geoError 
                ? 'No se pudo obtener tu ubicación. Activa los permisos de geolocalización.'
                : 'Permite el acceso a tu ubicación para ver paradas cercanas.'}
            </p>
            <Button 
              variant="primary" 
              leftIcon={Locate}
              onClick={requestLocation}
              disabled={loading.value}
            >
              {loading.value ? t('common.loading') : t('stopSelector.useLocation')}
            </Button>
          </Card>
        )}
        
        {loading.value && (
          <div className="space-y-3" role="status" aria-label={t('common.loading')}>
            {[1,2,3,4,5].map(i => (
              <Card key={i} padding="sm">
                <div className="flex items-center gap-3">
                  <Skeleton variant="circular" />
                  <div className="flex-1 space-y-2">
                    <Skeleton variant="text" />
                    <Skeleton variant="text" />
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
        
        {!loading.value && filteredStops.value.length === 0 && (
          <Card className="text-center py-8">
            <Search className="h-12 w-12 mx-auto text-text-muted mb-4" aria-hidden="true" />
            <h3 className="text-lg font-medium text-text mb-2">
              {activeTab.value === 'search' 
                ? 'No se encontraron paradas' 
                : activeTab.value === 'favorites'
                ? 'No tienes favoritas'
                : 'No hay paradas cercanas'}
            </h3>
            <p className="text-text-muted">
              {activeTab.value === 'search' 
                ? 'Intenta con otro término de búsqueda'
                : activeTab.value === 'favorites'
                ? 'Marca paradas como favoritas desde el detalle'
                : 'Prueba a activar la geolocalización'}
            </p>
          </Card>
        )}
        
        {!loading.value && filteredStops.value.length > 0 && (
          <div className="space-y-3" role="list" aria-label={t('stopSelector.nearby')}>
            {filteredStops.value.map((stop: any) => (
              <Card 
                key={stop.stop_id} 
                padding="sm" 
                hoverable
                onClick={() => handleStopSelect(stop)}
                role="listitem"
                tabIndex={0}
                onKeyDown={(e: React.KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') handleStopSelect(stop) }}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <h4 className="font-medium text-text truncate">{stop.stop_name}</h4>
                      {stop.stop_code && (
                        <span className="text-xs text-text-muted px-2 py-0.5 rounded bg-bg-secondary">
                          {stop.stop_code}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-text-muted truncate mt-1">
                      {stop.stop_desc || `Lat: ${stop.stop_lat.toFixed(4)}, Lon: ${stop.stop_lon.toFixed(4)}`}
                    </p>
                  </div>
                  <button
                    onClick={(e: React.MouseEvent) => handleFavoriteToggle(stop, e)}
                    className="p-2 rounded-lg text-text-muted hover:text-warning hover:bg-bg-secondary touch-manipulation min-h-[48px] min-w-[48px] flex-shrink-0"
                    aria-label="Añadir a favoritas"
                    aria-pressed="false"
                  >
                    <Star className="h-5 w-5" aria-hidden="true" />
                  </button>
                  <ChevronRight className="h-5 w-5 text-text-muted flex-shrink-0" aria-hidden="true" />
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
