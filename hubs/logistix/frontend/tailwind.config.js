/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        // ---- semantic tokens (Praxio v0.2 design system, light/dark via CSS vars) ----
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
      },
      fontFamily: {
        // Praxio v0.2 : une seule famille monospace pour toute l'app (docs/09-design-system.md).
        sans: ['"JetBrains Mono"', 'ui-monospace', '"SFMono-Regular"', 'Consolas', 'monospace'],
      },
      borderRadius: {
        '2xl': '1rem',
        '3xl': '1.5rem',
      },
    },
  },
  plugins: [],
}
