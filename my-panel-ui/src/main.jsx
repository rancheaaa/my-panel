import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'

const renderApp = () => {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

// 灵活开启 Mock
if (import.meta.env.VITE_USE_MOCK === 'true') {
  import('./mock').then(() => {
    renderApp()
  })
} else {
  renderApp()
}
