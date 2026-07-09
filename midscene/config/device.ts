/**
 * Midscene Shared Device Configuration for LMusic-KMP
 *
 * Provides a unified AndroidDevice + AndroidAgent factory
 * so every test script uses the same setup.
 */

import { agentFromAdbDevice, type AndroidAgent } from '@midscene/android';
import { LMUSIC_UI_CONTEXT } from '../helpers/gestures';
import 'dotenv/config';

export const APP_PACKAGE = 'com.lalilu.lmusic.kmp';

export interface DeviceContext {
  agent: AndroidAgent;
}

/** Sleep helper (Midscene doesn't expose waitForIdle) */
export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * One-shot factory: connect to the first device + create agent.
 */
export async function createDeviceContext(): Promise<DeviceContext> {
  const agent = await agentFromAdbDevice(undefined, {
    aiActionContext: LMUSIC_UI_CONTEXT,
  });

  console.log('[device] Agent created');
  return { agent };
}
