import React from 'react';

// ── SSR: 每次请求时从 Vercel API 获取最新 snapshot 数据 ──
export async function getServerSideProps() {
  const base = process.env.VERCEL_URL
    ? `https://${process.env.VERCEL_URL}`
    : 'http://localhost:3000';

  try {
    const res = await fetch(`${base}/api/snapshot`, { timeout: 8000 });
    if (!res.ok) throw new Error(`API ${res.status}`);
    const data = await res.json();
    return { props: { snapshot: data, error: null } };
  } catch (err) {
    return { props: { snapshot: null, error: err.message } };
  }
}

// ── 工具函数 ──
function fmtSize(bytes) {
  if (!bytes) return '';
  return bytes < 1048576
    ? (bytes / 1024).toFixed(0) + ' KB'
    : (bytes / 1048576).toFixed(1) + ' MB';
}

function fmtDate(iso) {
  if (!iso) return '--';
  const d = new Date(iso);
  return d.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

function timeAgo(iso) {
  if (!iso) return '';
  const sec = (Date.now() - new Date(iso).getTime()) / 1000;
  if (sec < 60) return '刚刚';
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`;
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`;
  return `${Math.floor(sec / 86400)} 天前`;
}

// ── 平台标识 ──
function platformIcon(name) {
  const n = name.toLowerCase();
  if (n.includes('.apk')) return 'android';
  if (n.includes('.aab')) return 'android';
  if (n.includes('.exe') || n.includes('.msi')) return 'windows';
  if (n.includes('.dmg')) return 'macos';
  if (n.includes('.deb') || n.includes('.appimage')) return 'linux';
  return 'other';
}

const PLATFORM_META = {
  android: { label: 'Android', color: '#a4c639' },
  windows: { label: 'Windows', color: '#00a4ef' },
  macos:   { label: 'macOS',   color: '#a2aaad' },
  linux:   { label: 'Linux',   color: '#f9a825' },
  other:   { label: '其他',    color: '#888' },
};

// ── 主组件 ──
export default function SnapshotPage({ snapshot, error }) {
  // 没有 snapshot release 时的空白状态
  const empty = !snapshot || snapshot.no_snapshot;

  // 从 API 获取的数据或占位
  const commitSha = snapshot?.tag_name || snapshot?.target_commitish || '';
  const commitShort = commitSha.slice(0, 7);
  const publishedAt = snapshot?.published_at || snapshot?.created_at;
  const assets = snapshot?.assets || [];
  const body = snapshot?.body || '';

  // 提取 commit message
  const commitMsg = body
    ? body.split('\n')[0].replace(/^Auto-built from /, '')
    : '';

  const hasAssets = assets.length > 0;

  // 按平台分组
  const groups = {};
  for (const asset of assets) {
    const pf = platformIcon(asset.name);
    if (!groups[pf]) groups[pf] = [];
    groups[pf].push(asset);
  }

  return (
    <div className="page">
      <header className="header">
        <div className="header-inner">
          <div className="brand">
            <span className="logo">LMusic</span>
            <span className="badge">Snapshot</span>
          </div>
          <nav className="nav">
            <a href="https://github.com/cy745/LMusic-KMP" target="_blank" rel="noreferrer">GitHub</a>
          </nav>
        </div>
      </header>

      <main className="main">
        {error ? (
          <div className="empty">
            <p className="empty-icon">--</p>
            <p className="empty-text">无法加载构建信息</p>
            <p className="empty-sub">{error}</p>
          </div>
        ) : empty ? (
          <div className="empty">
            <p className="empty-icon">○</p>
            <p className="empty-text">暂无 Snapshot 构建</p>
            <p className="empty-sub">等待 dev 分支的首次提交</p>
          </div>
        ) : (
          <>
            {/* ── 构建信息 ── */}
            <section className="section">
              <div className="info-grid">
                <div className="info-card">
                  <span className="info-label">Branch</span>
                  <span className="info-value mono">dev</span>
                </div>
                <div className="info-card">
                  <span className="info-label">Commit</span>
                  <span className="info-value mono">{commitShort}</span>
                </div>
                <div className="info-card">
                  <span className="info-label">Built</span>
                  <span className="info-value">{timeAgo(publishedAt) || fmtDate(publishedAt)}</span>
                </div>
                <div className="info-card">
                  <span className="info-label">Artifacts</span>
                  <span className="info-value">{assets.length}</span>
                </div>
              </div>
            </section>

            {/* ── Commit 信息 ── */}
            {commitMsg && (
              <section className="section">
                <div className="commit-card">
                  <span className="commit-icon">◆</span>
                  <span className="commit-text">{commitMsg}</span>
                </div>
              </section>
            )}

            {/* ── 下载列表 ── */}
            <section className="section">
              <h2 className="section-title">下载</h2>
              {hasAssets ? (
                <div className="downloads">
                  {Object.entries(groups).map(([pf, items]) => (
                    <div key={pf} className="platform-group">
                      <div className="platform-label">
                        <span
                          className="platform-dot"
                          style={{ background: PLATFORM_META[pf]?.color || '#888' }}
                        />
                        {PLATFORM_META[pf]?.label || pf}
                      </div>
                      <div className="asset-list">
                        {items.map((asset) => (
                          <a
                            key={asset.id}
                            href={asset.browser_download_url}
                            className="asset-link"
                            target="_blank"
                            rel="noreferrer"
                          >
                            <span className="asset-name">{asset.name}</span>
                            <span className="asset-size">{fmtSize(asset.size)}</span>
                            <span className="asset-arrow">↓</span>
                          </a>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted">当前版本无构建产物</p>
              )}
            </section>

            {/* ── 版本详情 ── */}
            <section className="section">
              <details className="details">
                <summary className="details-summary">构建详情</summary>
                <pre className="details-pre">{JSON.stringify({ sha: commitSha, published: publishedAt, assets: assets.length }, null, 2)}</pre>
              </details>
            </section>
          </>
        )}
      </main>

      <footer className="footer">
        <span>Snapshot 构建 · dev 分支</span>
      </footer>

      <style jsx global>{`
        /* ── 极简主义设计 ── */
        :root {
          --bg: #f8f8fa;
          --surface: #ffffff;
          --border: #e8e8ee;
          --text: #1a1a2e;
          --text-secondary: #8888a0;
          --text-muted: #b0b0c0;
          --accent: #4a4a6a;
          --accent-hover: #3a3a5a;
          --radius: 8px;
          --font: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Inter', 'Segoe UI', Roboto, sans-serif;
          --font-mono: 'SF Mono', 'Fira Code', 'JetBrains Mono', monospace;
        }

        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
          font-family: var(--font);
          background: var(--bg);
          color: var(--text);
          line-height: 1.6;
          -webkit-font-smoothing: antialiased;
        }

        .page {
          max-width: 680px;
          margin: 0 auto;
          padding: 0 24px;
          min-height: 100vh;
          display: flex;
          flex-direction: column;
        }

        /* ── Header ── */
        .header {
          padding: 24px 0;
          border-bottom: 1px solid var(--border);
        }
        .header-inner {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }
        .brand {
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .logo {
          font-size: 18px;
          font-weight: 600;
          letter-spacing: -0.3px;
        }
        .badge {
          font-size: 11px;
          font-weight: 500;
          color: var(--text-secondary);
          border: 1px solid var(--border);
          padding: 1px 8px;
          border-radius: 4px;
          letter-spacing: 0.3px;
          text-transform: uppercase;
        }
        .nav a {
          font-size: 13px;
          color: var(--text-secondary);
          text-decoration: none;
        }
        .nav a:hover { color: var(--accent); }

        /* ── Main ── */
        .main {
          flex: 1;
          padding: 32px 0;
        }

        /* ── Empty State ── */
        .empty {
          text-align: center;
          padding: 80px 0;
        }
        .empty-icon {
          font-size: 36px;
          color: var(--text-muted);
          margin-bottom: 12px;
        }
        .empty-text {
          font-size: 15px;
          color: var(--text);
          margin-bottom: 6px;
        }
        .empty-sub {
          font-size: 13px;
          color: var(--text-muted);
        }

        /* ── Section ── */
        .section {
          margin-bottom: 24px;
        }
        .section-title {
          font-size: 12px;
          font-weight: 600;
          color: var(--text-secondary);
          text-transform: uppercase;
          letter-spacing: 0.8px;
          margin-bottom: 12px;
        }

        /* ── Info Grid ── */
        .info-grid {
          display: grid;
          grid-template-columns: repeat(4, 1fr);
          gap: 1px;
          background: var(--border);
          border: 1px solid var(--border);
          border-radius: var(--radius);
          overflow: hidden;
        }
        .info-card {
          background: var(--surface);
          padding: 16px;
          display: flex;
          flex-direction: column;
          gap: 4px;
        }
        .info-label {
          font-size: 11px;
          color: var(--text-muted);
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }
        .info-value {
          font-size: 16px;
          font-weight: 500;
          color: var(--text);
        }
        .mono {
          font-family: var(--font-mono);
          font-size: 14px;
        }

        /* ── Commit Card ── */
        .commit-card {
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: var(--radius);
          padding: 14px 16px;
          display: flex;
          align-items: center;
          gap: 10px;
          font-size: 14px;
        }
        .commit-icon {
          color: var(--text-muted);
          font-size: 10px;
        }
        .commit-text {
          color: var(--text-secondary);
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        /* ── Downloads ── */
        .downloads {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }
        .platform-group {
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: var(--radius);
          overflow: hidden;
        }
        .platform-label {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 12px 16px;
          font-size: 12px;
          font-weight: 600;
          color: var(--text-secondary);
          text-transform: uppercase;
          letter-spacing: 0.5px;
          border-bottom: 1px solid var(--border);
          background: var(--bg);
        }
        .platform-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          display: inline-block;
        }
        .asset-list {
          display: flex;
          flex-direction: column;
        }
        .asset-link {
          display: flex;
          align-items: center;
          padding: 12px 16px;
          text-decoration: none;
          color: var(--text);
          font-size: 13px;
          transition: background 0.15s;
          gap: 12px;
        }
        .asset-link:hover {
          background: var(--bg);
        }
        .asset-link + .asset-link {
          border-top: 1px solid var(--border);
        }
        .asset-name {
          flex: 1;
          font-family: var(--font-mono);
          font-size: 12px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .asset-size {
          color: var(--text-muted);
          font-size: 12px;
          font-variant-numeric: tabular-nums;
        }
        .asset-arrow {
          color: var(--text-muted);
          font-size: 12px;
          transition: transform 0.15s;
        }
        .asset-link:hover .asset-arrow {
          transform: translateY(1px);
          color: var(--accent);
        }

        /* ── Details ── */
        .details {
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: var(--radius);
        }
        .details-summary {
          padding: 12px 16px;
          font-size: 13px;
          color: var(--text-secondary);
          cursor: pointer;
          user-select: none;
        }
        .details-summary:hover { color: var(--accent); }
        .details-pre {
          padding: 12px 16px;
          font-family: var(--font-mono);
          font-size: 12px;
          color: var(--text-secondary);
          border-top: 1px solid var(--border);
          overflow-x: auto;
        }

        /* ── Muted ── */
        .muted {
          color: var(--text-muted);
          font-size: 13px;
        }

        /* ── Footer ── */
        .footer {
          padding: 24px 0;
          text-align: center;
          font-size: 12px;
          color: var(--text-muted);
          border-top: 1px solid var(--border);
        }

        /* ── 响应式 ── */
        @media (max-width: 480px) {
          .page { padding: 0 16px; }
          .info-grid { grid-template-columns: repeat(2, 1fr); }
          .info-card { padding: 12px; }
          .info-value { font-size: 14px; }
        }
      }`}</style>
    </div>
  );
}
