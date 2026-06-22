import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@tabler/icons-webfont/dist/tabler-icons.min.css'
import './styles/tokens.css'
import './styles/animations.css'
import { App } from './App'
import { AuthProvider } from './auth/AuthContext'

// Restore the saved theme before first paint.
const theme = localStorage.getItem('auvex.theme') === 'dark' ? 'dark' : 'light'
document.body.className = theme

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
