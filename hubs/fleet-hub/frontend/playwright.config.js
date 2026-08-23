import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5199',
    trace: 'on-first-retry'
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 5'] } }
  ],
  webServer: [
    {
      command: 'cd ../backend && mvn spring-boot:run -q',
      url: 'http://localhost:8090/actuator/health',
      reuseExistingServer: true,
      timeout: 120000
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5199',
      reuseExistingServer: true,
      timeout: 120000
    }
  ]
})
