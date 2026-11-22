import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/tailwind.css'
import { initializeI18n } from './i18n'
import { registerSW } from './lib/sw-registration'
import { startIOSFallback } from './lib/ios-fallback'

// Initialize i18n
initializeI18n()

// Register Service Worker
if ('serviceWorker' in navigator) {
  registerSW().then((registration) => {
    // iOS fallback for background sync
    startIOSFallback(registration)
  }).catch(console.error)
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
