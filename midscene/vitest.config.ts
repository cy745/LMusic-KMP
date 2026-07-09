import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    testTimeout: 120_000,
    hookTimeout: 60_000,
    retry: 1,
    setupFiles: [],
  },
  envPrefix: ['MIDSCENE_', 'ANDROID_'],
});
