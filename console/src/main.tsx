import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@tabler/icons-webfont/dist/tabler-icons.min.css'
import './styles/tokens.css'
import './styles/animations.css'
import './styles/landing.css'
import { App } from './App'
import { AuthProvider } from './auth/AuthContext'
import { I18nProvider } from './lib/i18n'

// Restore the saved theme before first paint — dark-first by design.
const theme = localStorage.getItem('auvex.theme') === 'light' ? 'light' : 'dark'
document.body.className = theme

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <BrowserRouter>
      <I18nProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </I18nProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
