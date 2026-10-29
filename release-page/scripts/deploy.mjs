import { readFile } from 'node:fs/promises';

// Only the reviewed website sources are sent; never upload .env or build artifacts.
const sources = ['package.json', 'package-lock.json', 'next.config.js', 'vercel.json',
  'pages/index.js', 'pages/api/snapshot.js'];
const { VERCEL_TOKEN: token, VERCEL_ORG_ID: team, VERCEL_PROJECT_ID: project } = process.env;
if (!token || !team || !project) throw new Error('Missing Vercel configuration');

async function request(path, body) {
  const url = new URL(path, 'https://api.vercel.com');
  url.searchParams.set('teamId', team);
  const response = await fetch(url, {
    method: body ? 'POST' : 'GET',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(60000),
  });
  // Don't dump response bodies, headers, or credentials into CI logs.
  if (!response.ok) throw new Error(`Vercel ${path}: HTTP ${response.status}`);
  return response.json();
}

const target = await request(`/v9/projects/${encodeURIComponent(project)}`);
if (target.id !== project || target.accountId !== team || target.name !== 'lmusic-snapshot') {
  throw new Error('Deployment target does not match lmusic-snapshot');
}
const files = await Promise.all(sources.map(async file => ({
  file, encoding: 'base64', data: (await readFile(new URL(`../${file}`, import.meta.url))).toString('base64'),
})));
const deployment = await request('/v13/deployments', {
  name: target.name, project, target: 'production', files,
  projectSettings: { framework: 'nextjs', installCommand: 'npm ci', buildCommand: 'npm run build' },
  meta: { sourceCommit: process.env.GITHUB_SHA || '', source: 'snapshot-ci' },
});
if (!deployment.id || !deployment.url) throw new Error('Missing deployment identity');
console.log(`Deployment: https://${deployment.url}`);
for (let attempt = 0; attempt < 120; attempt++) {
  const state = await request(`/v13/deployments/${encodeURIComponent(deployment.id)}`);
  const status = state.readyState || state.status;
  if (status === 'READY' && state.aliasAssigned) {
    console.log(`READY: https://${deployment.url} (production domains assigned)`);
    process.exit(0);
  }
  if (['ERROR', 'CANCELED'].includes(status) || state.aliasError) {
    throw new Error(`Deployment failed: ${status}; inspect deployment build logs`);
  }
  if (attempt % 6 === 0) console.log(`Deployment status: ${status}`);
  await new Promise(resolve => setTimeout(resolve, 5000));
}
throw new Error('Timed out waiting for deployment and production domains');
