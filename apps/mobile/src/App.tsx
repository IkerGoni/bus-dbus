import { useEffect } from 'react'
import { useSignal, useComputed } from '@preact/signals-react'
import { Router, Route, Switch } from 'wouter'
import { Home } from '@/pages/Home'
import { StopSelector } from '@/pages/StopSelector'
import { StopDetail } from '@/pages/StopDetail'
import { AlertConfig } from '@/pages/AlertConfig'
import { AlertsList } from '@/pages/AlertsList'
import { Settings } from '@/pages/Settings'
import { Offline } from '@/pages/Offline'
import { Header } from '@/components/layout/Header'
import { ToastContainer } from '@/components/ui/ToastContainer'
import { rtData } from '@/store'

function App() {
  const isOnline = useSignal(navigator.onLine)
  
  useEffect(() => {
    const handleOnline = () => { isOnline.value = true }
    const handleOffline = () => { isOnline.value = false }
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  const showOffline = useComputed(() => !isOnline.value && !rtData.value)

  return (
    <>
      <Router>
        <div className="min-h-screen bg-bg">
          {!showOffline.value && <Header />}
          <main className="pb-20 safe-top safe-bottom">
            <Switch>
              <Route path="/" component={Home} />
              <Route path="/stops" component={StopSelector} />
              <Route path="/stop/:stopId" component={StopDetail} />
              <Route path="/alert/new" component={AlertConfig} />
              <Route path="/alert/:alertId/edit" component={AlertConfig} />
              <Route path="/alerts" component={AlertsList} />
              <Route path="/settings" component={Settings} />
              <Route path="/offline" component={Offline} />
            </Switch>
          </main>
        </div>
      </Router>
      <ToastContainer />
      {showOffline.value && <Offline />}
    </>
  )
}

export default App
