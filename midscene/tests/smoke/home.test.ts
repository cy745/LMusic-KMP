/**
 * Smoke test: Home Page
 *
 * LMusic 特殊操作：启动后进入播放列表页（PlayerScreen），
 * 在进度条上滑打开曲库首页（HomeScreen），这才是真正的"首页"。
 *
 * 每个测试用例独立确保在 HomeScreen 上开始执行。
 */

import { describe, beforeAll, afterAll, beforeEach, it, expect } from 'vitest';
import { agentFromAdbDevice, type AndroidAgent } from '@midscene/android';
import { LMUSIC_UI_CONTEXT, ensureHomeScreen } from '../../helpers/gestures';
import { sleep } from '../../config/device';
import 'dotenv/config';

let agent: AndroidAgent;

beforeAll(async () => {
  agent = await agentFromAdbDevice(undefined, {
    aiActionContext: LMUSIC_UI_CONTEXT,
  });
  // 启动 LMusic 并进入首页
  await agent.aiAction('打开 LMusic 应用');
  await sleep(2000);
  await ensureHomeScreen(agent);
}, 90_000);

beforeEach(async () => {
  // 每个用例执行前确保在首页，避免用例间状态污染
  await ensureHomeScreen(agent);
}, 30_000);

afterAll(() => {
  agent?.destroy();
});

describe('Home Page', () => {
  it('should display the HomeScreen with main modules', async () => {
    // 用 AI 识别首页内容和模块
    const pageInfo = await agent.aiQuery<{
      isHomeScreen: boolean;
      mainModules: string[];
    }>(`
      分析当前页面是否是 LMusic 的曲库首页（HomeScreen）。
      如果是，列出所有可见的主要功能模块名称（如每日推荐、最近添加、最近播放等）。
      以 JSON 格式返回：{ "isHomeScreen": boolean, "mainModules": string[] }
    `);
    console.log('[home] page info:', JSON.stringify(pageInfo));
    expect(pageInfo).toBeTruthy();
    expect(pageInfo.isHomeScreen).toBe(true);
    expect(pageInfo.mainModules).toBeTruthy();
    expect(pageInfo.mainModules.length).toBeGreaterThan(0);
  });

  it('should have visible navigation elements', async () => {
    const navInfo = await agent.aiQuery<{
      bottomTabs: string[];
      sidebarItems: string[];
    }>(`
      列出当前页面中所有可见的导航元素：
      1. 底部导航栏中所有 tab 名称（如果有的话）
      2. 侧边栏或顶部导航中的菜单项名称
      以 JSON 格式返回：{ "bottomTabs": string[], "sidebarItems": string[] }
    `);
    console.log('[home] navigation:', JSON.stringify(navInfo));
    expect(navInfo).toBeTruthy();
    // 至少有一种导航方式存在（底部 tab 或侧边栏）
    const hasNav = (navInfo.bottomTabs?.length ?? 0) > 0 ||
                   (navInfo.sidebarItems?.length ?? 0) > 0;
    expect(hasNav).toBe(true);
  });

  it('should navigate to playlist page and return to home via bottom tab', async () => {
    // 点击歌单 tab
    await agent.aiTap('点击底部导航栏的"歌单"tab');
    await sleep(2000);

    const playlistPage = await agent.aiQuery<{ isPlaylistPage: boolean; title: string }>(`
      当前页面是否是歌单列表页面？
      以 JSON 格式返回：{ "isPlaylistPage": boolean, "title": string }
    `);
    console.log('[playlist] page:', JSON.stringify(playlistPage));
    expect(playlistPage).toBeTruthy();
    expect(playlistPage.isPlaylistPage).toBe(true);

    // 通过底部导航栏回到首页（不需要 ensureHomeScreen 的复杂逻辑，验证 tab 切换即可）
    await agent.aiTap('点击底部导航栏的"首页"tab');
    await sleep(1500);

    const backHome = await agent.aiQuery<{ isHomeScreen: boolean }>(`
      当前是否回到了曲库首页（HomeScreen）？
      以 JSON 格式返回：{ "isHomeScreen": boolean }
    `);
    console.log('[home] back to home:', JSON.stringify(backHome));
    expect(backHome).toBeTruthy();
    expect(backHome.isHomeScreen).toBe(true);
  });
});
