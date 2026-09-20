import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, timeout: 420_000,
  expect: { timeout: 20_000 }, reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://localhost:25173', actionTimeout: 20_000, navigationTimeout: 30_000, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
})
