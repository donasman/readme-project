import { defineConfig, devices } from "@playwright/test"

// 가짜 서버(npm run dev)에서 돈다. 진짜 서버 화면(5173)과 겹치지 않게 포트를 따로 쓰고, 이미 떠 있으면 그 서버를 쓴다.
const URL = "http://127.0.0.1:5175"

export default defineConfig({
  testDir: "./e2e",
  forbidOnly: !!process.env.CI,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL: URL, trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 800 } } },
    { name: "mobile", use: { ...devices["Pixel 7"] } },
  ],
  webServer: {
    command: "npm run dev -- --host 127.0.0.1 --port 5175 --strictPort",
    url: URL,
    reuseExistingServer: !process.env.CI,
  },
})
