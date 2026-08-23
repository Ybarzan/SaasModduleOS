/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        // ---- semantic tokens (current design system, light/dark via CSS vars) ----
        // rgb(var(..) / <alpha-value>) — pas juste var(--x) — pour que les
        // modificateurs d'opacite Tailwind (bg-accent/20, text-ink/60...) fonctionnent.
        bg: 'rgb(var(--c-bg) / <alpha-value>)',
        surface: 'rgb(var(--c-surface) / <alpha-value>)',
        'surface-2': 'rgb(var(--c-surface-2) / <alpha-value>)',
        ink: 'rgb(var(--c-ink) / <alpha-value>)',
        'ink-soft': 'rgb(var(--c-ink-soft) / <alpha-value>)',
        line: 'rgb(var(--c-line) / <alpha-value>)',
        accent: {
          DEFAULT: 'rgb(var(--c-accent) / <alpha-value>)',
          soft: 'rgb(var(--c-accent-soft) / <alpha-value>)',
          strong: 'rgb(var(--c-accent-strong) / <alpha-value>)',
        },
        'accent-2': 'rgb(var(--c-accent-2) / <alpha-value>)',
        success: 'rgb(var(--c-success) / <alpha-value>)',
        warning: 'rgb(var(--c-warning) / <alpha-value>)',
        danger: 'rgb(var(--c-danger) / <alpha-value>)',
        // ---- legacy palette (kept defined, no longer referenced by components) ----
        zellige: {
          50: '#FDF6EC',
          100: '#F5E6CC',
          200: '#EDD4A8',
          300: '#E8C547',
          400: '#D4A843',
          500: '#C8553D',
          600: '#B04530',
          700: '#8B3525',
          800: '#6B2A1D',
          900: '#4A1D14',
        },
        sable: {
          50: '#FEFCF7',
          100: '#FDF6EC',
          200: '#F5E6CC',
          300: '#E8D5B0',
          400: '#D4C09A',
        },
        olive: {
          50: '#F0F5F1',
          100: '#D8E8DA',
          200: '#B5D1BA',
          300: '#8BB894',
          400: '#5B7553',
          500: '#4A6042',
          600: '#3A4D34',
          700: '#2C3A27',
          800: '#1F2A1C',
        },
        medina: {
          50: '#F0F5F9',
          100: '#D1E3F0',
          200: '#A3C7E1',
          300: '#6BA3CC',
          400: '#3B82B5',
          500: '#1B4965',
          600: '#153A52',
          700: '#0F2B3E',
          800: '#0A1D2B',
          900: '#051019',
        },
        terracotta: {
          50: '#FEF2EE',
          100: '#FCDDD5',
          200: '#F5B5A6',
          300: '#E88C77',
          400: '#D67057',
          500: '#C8553D',
          600: '#A84330',
          700: '#8B3525',
        },
      },
      fontFamily: {
        // Praxio v0.2 : une seule famille monospace pour toute l'app (docs/09-design-system.md).
        sans: ['"JetBrains Mono"', 'ui-monospace', '"SFMono-Regular"', 'Consolas', 'monospace'],
      },
      borderRadius: {
        '2xl': '1rem',
        '3xl': '1.5rem',
      },
      animation: {
        'fade-in': 'fadeIn 0.5s ease-out',
        'slide-up': 'slideUp 0.6s ease-out',
        'float': 'float 6s ease-in-out infinite',
        'pulse-soft': 'pulseSoft 3s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(30px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.7' },
        },
      },
    },
  },
  plugins: [],
}
