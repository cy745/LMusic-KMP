/**
 * Quick verification script: tests that Midscene can connect to
 * the Android device and interact with the LMusic app.
 *
 * Usage:
 *   cd midscene && npx tsx scripts/verify-connection.ts
 */

import { agentFromAdbDevice } from '@midscene/android';
import 'dotenv/config';

async function main() {
  console.log('[verify] Creating Android agent...');
  const agent = await agentFromAdbDevice();

  console.log('[verify] Agent ready. Taking initial screenshot...');

  // 1. Check what's on screen
  const screenInfo = await agent.aiQuery(`
    当前屏幕上是什么应用？列举所有可见的 UI 元素。
  `);
  console.log('[verify] Current screen:', JSON.stringify(screenInfo, null, 2));

  // 2. Try to open LMusic
  console.log('[verify] Opening LMusic...');
  await agent.aiAction('打开 LMusic 应用');

  // 3. Check home screen
  const homeInfo = await agent.aiQuery(`
    当前是 LMusic 的首页吗？
    请列出首页上可见的主要模块和底部导航 tab 名称。
  `);
  console.log('[verify] Home screen:', homeInfo);
  console.log('[verify] ✅ Connection verified successfully!');

  agent.destroy();
}

main().catch((err) => {
  console.error('[verify] ❌ Failed:', err);
  process.exit(1);
});
