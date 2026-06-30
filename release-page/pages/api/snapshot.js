// Vercel Serverless Function
// 代理 GitHub API，获取最新的 snapshot release 数据
export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 's-maxage=120, stale-while-revalidate');

  const { owner = 'cy745', repo = 'LMusic-KMP' } = req.query;
  const tag = 'snapshot';

  try {
    const url = `https://api.github.com/repos/${owner}/${repo}/releases/tags/${tag}`;
    const githubRes = await fetch(url, {
      headers: {
        Accept: 'application/vnd.github+json',
        'User-Agent': 'lmusic-snapshot-page',
      },
    });

    if (githubRes.status === 404) {
      return res.status(200).json({ no_snapshot: true });
    }

    if (!githubRes.ok) {
      return res.status(githubRes.status).json({ error: `GitHub API: ${githubRes.status}` });
    }

    const data = await githubRes.json();
    res.status(200).json(data);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}
