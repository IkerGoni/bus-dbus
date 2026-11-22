import { useSignal } from '@preact/signals-react'
import { useEffect } from 'react'

interface GeolocationState {
  location: GeolocationCoordinates | null
  error: GeolocationPositionError | null
  loading: boolean
}

export function useGeolocation(options?: PositionOptions) {
  const state = useSignal<GeolocationState>({
    location: null,
    error: null,
    loading: true
  })
  
  const requestLocation = () => {
    if (!navigator.geolocation) {
      state.value = { 
        location: null, 
        error: { code: 0, message: 'Geolocation not supported', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 }, 
        loading: false 
      }
      return
    }
    
    state.value = { ...state.value, loading: true, error: null }
    
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        state.value = { location: pos.coords, error: null, loading: false }
      },
      (err) => {
        state.value = { location: null, error: err, loading: false }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 300000, ...options }
    )
  }
  
  useEffect(() => {
    requestLocation()
  }, [])
  
  return {
    location: state.value.location,
    error: state.value.error,
    loading: state.value.loading,
    requestLocation
  }
}
