/**
 * LMusic 特殊手势操作
 *
 * LMusic 的页面结构：
 * - 启动后进入播放列表页（PlayerScreen），上方是当前歌曲封面，下方是播放列表
 * - 特殊操作：在进度条区域 **向上滑动** 打开曲库首页（HomeScreen）
 * - 平板（大屏）布局与手机完全不同（后续适配）
 *
 * 使用方法：
 *   import { openHomeScreen } from '../helpers/gestures';
 *
 *   // 在 aiAction 中直接用文字描述（推荐，AI 可以结合当前截图理解）
 *   await agent.aiAction('从进度条区域向上滑动打开曲库首页');
 *
 *   // 或用精确的 ADB 坐标滑动（更稳定）
 *   await openHomeScreen(agent);
 */

import type { AndroidAgent } from '@midscene/android';

/** 等待指定毫秒 */
function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * 获取设备屏幕尺寸
 */
async function getScreenSize(agent: AndroidAgent): Promise<{ width: number; height: number }> {
  const output = await agent.runAdbShell('wm size');
  const match = output.match(/(\d+)x(\d+)/);
  if (!match) throw new Error(`无法获取屏幕尺寸: ${output}`);
  return { width: parseInt(match[1]), height: parseInt(match[2]) };
}

/**
 * 从播放列表页（PlayerScreen）通过上滑进度条打开曲库首页（HomeScreen）。
 *
 * 使用精确的 ADB 坐标执行滑动，不依赖 AI 视觉识别。
 */
export async function openHomeScreen(agent: AndroidAgent): Promise<void> {
  const { width, height } = await getScreenSize(agent);

  // 进度条位于屏幕底部约 85% 的位置（已验证：1200×2608 分辨率下起始点为 600,2220）
  // 从进度条区域上滑至屏幕中部以上
  const startX = Math.round(width / 2);
  const startY = Math.round(height * 0.85);
  const endY = Math.round(height * 0.25);

  console.log(`[gesture] swipe from (${startX}, ${startY}) to (${startX}, ${endY})`);
  await agent.runAdbShell(`input touchscreen swipe ${startX} ${startY} ${startX} ${endY} 400`);

  // 等待页面切换动画完成
  await sleep(2000);
}

/**
 * 确保当前在曲库首页（HomeScreen）。
 *
 * 策略：
 * 1. 先用 AI 判断当前页面
 * 2. 如果不是首页，先用 AI 方式尝试导航（灵活处理各种场景）
 * 3. 如果 AI 方式失败，用 ADB 精确手势兜底
 */
export async function ensureHomeScreen(agent: AndroidAgent): Promise<void> {
  const checkResult = await agent.aiQuery<string>(
    '当前是否已经在 LMusic 的曲库首页（HomeScreen）？只回答 true 或 false。',
  );
  const onHome = checkResult?.toString().trim().toLowerCase() === 'true';
  if (onHome) {
    console.log('[ensureHomeScreen] ✅ 已经在首页');
    return;
  }

  console.log('[ensureHomeScreen] 尝试导航到首页...');

  // 尝试用 AI 方式导航：先尝试点击"首页"tab 或侧边栏，不行就执行上滑手势
  await agent.aiAction(
    '请打开 LMusic 的曲库首页（HomeScreen）：如果底部有"首页"tab 就点击它，否则从进度条区域向上滑动打开首页',
  );
  await sleep(2000);

  // 验证是否成功到达
  const recheck = await agent.aiQuery<string>(
    '当前是否在曲库首页？只回答 true 或 false。',
  );
  const onHomeNow = recheck?.toString().trim().toLowerCase() === 'true';

  if (!onHomeNow) {
    console.log('[ensureHomeScreen] AI 导航失败，使用 ADB 精确手势...');
    // 按返回键几次确保不在深层页面
    await agent.runAdbShell('input keyevent KEYCODE_BACK');
    await sleep(500);
    await agent.runAdbShell('input keyevent KEYCODE_BACK');
    await sleep(500);
    // 执行上滑手势
    await openHomeScreen(agent);

    // 最终验证
    const finalCheck = await agent.aiQuery<string>(
      '当前是否在曲库首页？只回答 true 或 false。',
    );
    const onHomeFinal = finalCheck?.toString().trim().toLowerCase() === 'true';
    if (!onHomeFinal) {
      console.warn('[ensureHomeScreen] ⚠️ 可能未到达首页，继续执行...');
    } else {
      console.log('[ensureHomeScreen] ✅ 已到达首页');
    }
  } else {
    console.log('[ensureHomeScreen] ✅ 已到达首页');
  }
}

/**
 * 从曲库首页回到播放列表页（按返回键）。
 */
export async function backToPlayerScreen(agent: AndroidAgent): Promise<void> {
  await agent.runAdbShell('input keyevent KEYCODE_BACK');
  await sleep(1500);
}

/**
 * LMusic 页面结构上下文提示（用于 aiActionContext 或 prompt 中，
 * 让 AI 理解 LMusic 的特有交互方式）。
 */
export const LMUSIC_UI_CONTEXT = `
LMusic 音乐播放器应用的页面结构说明：
1. 启动后看到的是"播放列表页"（PlayerScreen）—— 上方是当前歌曲的方形封面，下方是播放列表和播放进度条。
2. 在进度条区域向上滑动会打开"曲库首页"（HomeScreen），这是 App 的真正首页，包含发现、专辑、艺术家等入口。
3. 底部有导航栏，通常包含"首页"、"歌单"、"设置"等 tab。
4. 侧边栏可从左侧滑出，包含 Discover（首页、专辑、艺术家）、Library（历史、媒体源、设置）等区域。
5. 平板（大屏）布局与手机布局完全不同，需额外适配。
注意：以上信息仅用于 LMusic 应用测试。如果当前屏幕上看到的不是 LMusic 界面，请忽略此提示。
`;
