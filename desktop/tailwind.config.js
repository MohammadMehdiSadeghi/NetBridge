/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/renderer/index.html', './src/renderer/src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg0: '#0B0F14',
        bg1: '#101722',
        bg2: '#162030',
        stroke: '#1F2B3D',
        ink: '#E8EEF7',
        muted: '#8FA0B8',
        brand: '#22D3EE',
        accent: '#A78BFA',
        ok: '#34D399',
        warn: '#FBBF24',
        danger: '#F87171'
      },
      fontFamily: {
        sans: [
          'Segoe UI',
          'Vazirmatn',
          'Tahoma',
          'system-ui',
          '-apple-system',
          'sans-serif'
        ]
      },
      boxShadow: {
        glow: '0 0 40px rgba(34, 211, 238, 0.25)'
      }
    }
  },
  plugins: []
}
