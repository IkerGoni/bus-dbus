import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import es from './locales/es.json'
import eu from './locales/eu.json'

export function initializeI18n() {
  i18n
    .use(initReactI18next)
    .init({
      resources: {
        es: { translation: es },
        eu: { translation: eu }
      },
      lng: navigator.language.startsWith('eu') ? 'eu' : 'es',
      fallbackLng: 'es',
      interpolation: {
        escapeValue: false
      },
      react: {
        useSuspense: false
      }
    })
  
  return i18n
}

export { i18n }
