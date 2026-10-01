import { defineConfig } from '@playwright/test'

// Scripted user session with real models; kept out of the default browser suite.
export default defineConfig({
  testDir: './e2e/session', outputDir: 'test-results/user-session', fullyParallel: false, workers: 1, timeout: 45 * 60_000,
  expect: { timeout: 20_000 }, reporter: [['list']],
  use: { baseURL: 'http://localhost:25173', actionTimeout: 20_000, navigationTimeout: 30_000, trace: 'retain-on-failure', locale: 'es-ES' },
})
