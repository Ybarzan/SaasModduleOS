import React from 'react'
import ReactDOM from 'react-dom/client'
import { Toaster } from 'react-hot-toast'
import App from './App.tsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Toaster
      position="top-right"
      reverseOrder={false}
      toastOptions={{
        success: {
          duration: 3000,
        },
        error: {
          duration: 4000,
        },
      }}
    />
    <App />
  </React.StrictMode>,
)