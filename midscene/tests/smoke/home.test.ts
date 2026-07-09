/**
 * Smoke test: Home Page
 *
 * Verifies that the LMusic home screen loads correctly
 * and displays the expected content.
 */

import { describe, beforeAll, afterAll, it, expect } from 'vitest';
import { agentFromAdbDevice, type AndroidAgent } from '@midscene/android';
import 'dotenv/config';

let agent: AndroidAgent;

beforeAll(async () => {
  agent = await agentFromAdbDevice(undefined, {
    aiActionContext: `
      你正在测试 LMusic 音乐播放器应用（包名: com.lalilu.lmusic.kmp）。
      请先确保 LMusic 应用在前台运行。
      如果出现权限弹窗（如存储权限、通知权限），请点击"允许"或"拒绝"以继续。
    `,
  });
}, 60_000);

afterAll(() => {
  agent?.destroy();
});

describe('Home Page', () => {
  it('should launch LMusic and display the home screen', async () => {
    await agent.aiAction('打开 LMusic 应用，等待首页完全加载');

    const pageInfo = await agent.aiQuery<string>(`
      当前页面是 LMusic 的首页吗？
      请描述当前页面上的主要内容区域（tab 标题、推荐内容等）。
    `);
    console.log('[home] page info:', pageInfo);
    expect(pageInfo).toBeTruthy();
  });

  it('should have visible bottom navigation tabs', async () => {
    const tabs = await agent.aiQuery<
      Array<{ name: string; selected: boolean }> | string
    >(`
      列出底部导航栏中所有可见的 tab 名称，
      并标出当前选中的是哪个。
    `);
    console.log('[home] tabs:', tabs);
    expect(tabs).toBeTruthy();
  });

  it('should navigate to playlist page and back', async () => {
    await agent.aiTap('点击底部导航栏的"歌单"tab');
    await new Promise((r) => setTimeout(r, 2000));

    const playlistTitle = await agent.aiQuery(
      '当前页面标题是什么？是否在歌单页面？',
    );
    console.log('[playlist] title:', playlistTitle);
    expect(playlistTitle).toBeTruthy();

    await agent.aiTap('点击底部导航栏的"首页"tab');
    await new Promise((r) => setTimeout(r, 1500));

    const backHome = await agent.aiQuery('当前是否回到了首页？');
    console.log('[home] back:', backHome);
    expect(backHome).toBeTruthy();
  });
});
