/**
 * Vitest lifecycle hooks for Midscene tests.
 *
 * Usage in a test file:
 *   import { describeWithDevice } from '../helpers/setup';
 *
 *   describeWithDevice('Home page', ({ agent }) => {
 *     it('should display the home screen', async () => {
 *       await agent.aiAction('打开 LMusic 的首页');
 *     });
 *   });
 */

import { describe, beforeAll, afterAll } from 'vitest';
import { createDeviceContext, type DeviceContext } from '../config/device';
import 'dotenv/config';

// ── Helper: describeWithDevice ───────────────────────────────

/**
 * Wrapper around describe() that automatically connects to the Android device
 * before all tests and cleans up after.
 */
export function describeWithDevice(
  title: string,
  fn: (ctx: DeviceContext) => void,
): void {
  describe(title, () => {
    let ctx: DeviceContext | undefined;

    beforeAll(async () => {
      ctx = await createDeviceContext();
    }, 60_000);

    afterAll(() => {
      ctx?.agent.destroy();
    });

    fn(ctx!);
  });
}
