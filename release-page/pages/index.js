import React from 'react';
import Head from 'next/head';

// ── SSR: 直接从 GitHub API 获取数据（避免 Vercel Protection 导致内部请求失败） ──
export async function getServerSideProps() {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);

    const res = await fetch(
      'https://api.github.com/repos/cy745/LMusic-KMP/releases/tags/snapshot',
      {
        headers: {
          Accept: 'application/vnd.github+json',
          'User-Agent': 'lmusic-snapshot-page',
        },
        signal: controller.signal,
      }
    );
    clearTimeout(timeout);

    if (res.status === 404) {
      return { props: { snapshot: null, error: null, no_snapshot: true } };
    }

    if (!res.ok) throw new Error(`GitHub API: ${res.status}`);
    const data = await res.json();
    return { props: { snapshot: data, error: null, no_snapshot: false } };
  } catch (err) {
    return { props: { snapshot: null, error: err.message, no_snapshot: false } };
  }
}

// ── 工具函数 ──
function fmtSize(bytes) {
  if (!bytes) return '';
  return bytes < 1048576
    ? (bytes / 1024).toFixed(0) + ' KB'
    : (bytes / 1048576).toFixed(1) + ' MB';
}

function platformIcon(name) {
  const n = name.toLowerCase();
  if (n.includes('.apk') || n.includes('.aab')) return 'android';
  if (n.includes('.exe') || n.includes('.msi')) return 'windows';
  if (n.includes('.dmg')) return 'macos';
  if (n.includes('.deb') || n.includes('.appimage')) return 'linux';
  if (n.includes('.ipa')) return 'ios';
  return 'other';
}

const PLATFORM_META = {
  android: { label: 'Android', icon: 'ri-android-fill',   color: '#2e7d32', iconColor: '#3ddc84' },
  windows: { label: 'Windows', icon: 'ri-windows-fill',   color: '#00a4ef', iconColor: '#00a4ef' },
  macos:   { label: 'macOS',   icon: 'ri-mac-line',       color: '#555',    iconColor: '#555'    },
  linux:   { label: 'Linux',   icon: 'ri-ubuntu-line',    color: '#e95420', iconColor: '#e95420' },
  ios:     { label: 'iOS',     icon: 'ri-apple-line',     color: '#000',    iconColor: '#555'    },
  other:   { label: '其他',    icon: 'ri-file-2-fill',    color: '#888',    iconColor: '#888'    },
};

// ── 主组件 ──
export default function SnapshotPage({ snapshot, error }) {
  const [testflightMsg, setTestflightMsg] = React.useState('');

  // 没有 snapshot release 时的空白状态
  const empty = !snapshot || snapshot.no_snapshot;

  const commitSha = snapshot?.tag_name || snapshot?.target_commitish || '';
  const commitShort = commitSha.slice(0, 7);
  const publishedAt = snapshot?.published_at || snapshot?.created_at;
  const assets = snapshot?.assets || [];
  const body = snapshot?.body || '';

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

  const handleTestFlight = () => {
    setTestflightMsg('敬请期待～');
  };

  return (
    <div className="page">
      <Head>
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/remixicon@4.5.0/fonts/remixicon.css" />
      </Head>

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
                  <span className="info-value">{publishedAt ? new Date(publishedAt).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '--'}</span>
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

            {/* ── 构建详情（移到下载上面） ── */}
            <section className="section">
              <details className="details">
                <summary className="details-summary">构建详情</summary>
                <pre className="details-pre">{JSON.stringify({ sha: commitSha, published: publishedAt, assets: assets.length }, null, 2)}</pre>
              </details>
            </section>

            {/* ── 下载列表 ── */}
            <section className="section">
              <h2 className="section-title">下载</h2>
              {hasAssets ? (
                <div className="downloads">
                  {Object.entries(groups).filter(([pf]) => pf !== 'windows').map(([pf, items]) => (
                    <div key={pf} className="platform-group">
                      <div className="platform-label" data-platform={pf}>
                        <i className={`${PLATFORM_META[pf]?.icon || 'ri-file-2-fill'} ri-fw`} style={{ color: PLATFORM_META[pf]?.iconColor }} />
                        <span style={{ color: PLATFORM_META[pf]?.color }}>{PLATFORM_META[pf]?.label || pf}</span>
                        {pf === 'ios' && <span className="badge-ios">需要自行签名</span>}
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
                            <span className="asset-arrow">
                              <i className="ri-download-2-line" />
                            </span>
                          </a>
                        ))}
                      </div>
                    </div>
                  ))}

                  {/* ── TestFlight 入口 ── */}
                  <div className="platform-group">
                    <div className="platform-label">
                      <i className="ri-flight-takeoff-fill ri-fw" />
                      TestFlight
                    </div>
                    <div className="asset-list">
                      <button className="asset-link testflight-btn" onClick={handleTestFlight}>
                        <span className="asset-name">
                          {testflightMsg || '通过邮件邀请加入 TestFlight 内测'}
                        </span>
                        <span className="asset-arrow">
                          <i className="ri-arrow-right-s-line" />
                        </span>
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                <p className="muted">当前版本无构建产物</p>
              )}
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
        .platform-label i {
          font-size: 16px;
        }
        .badge-ios {
          font-size: 10px;
          font-weight: 400;
          color: #fff;
          background: #e74c3c;
          padding: 1px 6px;
          border-radius: 3px;
          letter-spacing: 0;
          text-transform: none;
          margin-left: auto;
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
          font-size: 14px;
          transition: transform 0.15s;
          display: flex;
          align-items: center;
        }
        .asset-link:hover .asset-arrow {
          transform: translateX(2px);
          color: var(--accent);
        }
        .testflight-btn {
          cursor: pointer;
          border: none;
          background: none;
          width: 100%;
          text-align: left;
          font-family: var(--font);
        }
        .testflight-btn:hover {
          background: var(--bg);
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
      `}</style>
    </div>
  );
}
