/**
 * Midscene Shared Device Configuration for LMusic-KMP
 *
 * Provides a unified AndroidDevice + AndroidAgent factory
 * so every test script uses the same setup.
 */

import { agentFromAdbDevice, type AndroidAgent } from '@midscene/android';
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
    // dismiss system dialogs / permission pop-ups before each action
    aiActionContext: `
      你正在测试 LMusic 音乐播放器应用（包名: ${APP_PACKAGE}）。
      请先确保 LMusic 应用在前台运行。
      如果出现权限弹窗（如存储权限、通知权限），请点击"允许"或"拒绝"以继续。
    `,
  });

  console.log('[device] Agent created');
  return { agent };
}
