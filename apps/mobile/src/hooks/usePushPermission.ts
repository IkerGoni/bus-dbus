import { useSignal } from '@preact/signals-react'
import { useEffect } from 'react'

type PermissionState = 'default' | 'granted' | 'denied'

export function usePushPermission() {
  const permission = useSignal<PermissionState>('default')
  const supported = useSignal(false)
  
  useEffect(() => {
    if ('Notification' in window) {
      supported.value = true
      permission.value = Notification.permission
    }
  }, [])
  
  const requestPermission = async (): Promise<PermissionState> => {
    if (!supported.value) return 'denied'
    
    try {
      const result = await Notification.requestPermission()
      permission.value = result
      return result
    } catch (err) {
      console.error('Permission request failed:', err)
      return 'denied'
    }
  }
  
  return {
    permission,
    supported,
    requestPermission,
    isGranted: permission.value === 'granted',
    isDenied: permission.value === 'denied'
  }
}
