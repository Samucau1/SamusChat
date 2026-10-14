// Send only public dependency coordinates/versions to OSV; never app source or secrets.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const inventories = new Map();
function add(component, name, version) {
  if (!/^[\d][\w.+-]*$/.test(version)) return;
  const key = `${component}:${name}:${version}`;
  inventories.set(key, { component, package: { ecosystem: 'Maven', name }, version });
}
const backend = (await readFile(path.join(root, 'chatapp-backend/target/security-runtime-dependencies.txt'), 'utf8')).replace(/\u001b\[[0-9;]*m/g, '');
for (const line of backend.split(/\r?\n/)) {
  const match = line.match(/^\s+([\w.-]+):([\w.-]+):jar:(?:[^:\s]+:)?([^:\s]+):(compile|runtime)/);
  if (match) add('backend', `${match[1]}:${match[2]}`, match[3]);
}
const androidBytes = await readFile(path.join(root, 'logs/security-android-dependencies.log'));
const android = androidBytes.toString(androidBytes[0] === 0xff && androidBytes[1] === 0xfe ? 'utf16le' : 'utf8');
for (const line of android.split(/\r?\n/)) {
  if (line.includes('(c)')) continue; // Constraint/BOM entries are not runtime code.
  const match = line.match(/--- ([\w.-]+):([\w.-]+):([^\s]+)/);
  if (match) add('Android', `${match[1]}:${match[2]}`, line.match(/ -> ([^\s]+)/)?.[1] ?? match[3]);
}
const packages = [...inventories.values()];
if (!packages.some(item => item.component === 'Android') || !packages.some(item => item.component === 'backend')) throw new Error('Missing inventories.');
const found = new Map();
let pending = packages.map((item, index) => ({ index, query: { package: item.package, version: item.version } }));
for (let page = 0; pending.length; page++) {
  if (page > 8) throw new Error('Unexpected pagination depth.');
  const response = await fetch('https://api.osv.dev/v1/querybatch', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ queries: pending.map(item => item.query) }), signal: AbortSignal.timeout(45000) });
  if (!response.ok) throw new Error(`OSV HTTP ${response.status}`);
  const data = await response.json();
  if (data.results.length !== pending.length) throw new Error('Invalid OSV response.');
  const next = [];
  data.results.forEach((result, position) => {
    const { index, query } = pending[position], entry = found.get(index) ?? { ...packages[index], vulnerabilities: [] };
    for (const advisory of result.vulns ?? []) if (!entry.vulnerabilities.includes(advisory.id)) entry.vulnerabilities.push(advisory.id);
    found.set(index, entry);
    if (result.next_page_token) next.push({ index, query: { ...query, page_token: result.next_page_token } });
  });
  pending = next;
}
const findings = [...found.values()].filter(item => item.vulnerabilities.length);
const ids = [...new Set(findings.flatMap(item => item.vulnerabilities))];
const advisories = [];
for (let start = 0; start < ids.length; start += 4) {
  const results = await Promise.all(ids.slice(start, start + 4).map(async id => {
    const response = await fetch(`https://api.osv.dev/v1/vulns/${encodeURIComponent(id)}`, { signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new Error(`OSV advisory ${id}: HTTP ${response.status}`);
    const data = await response.json();
    return { id, aliases: data.aliases, summary: data.summary, severity: data.severity, database_specific: data.database_specific, affected: data.affected, references: data.references };
  }));
  advisories.push(...results);
}
const result = { checkedAt: new Date().toISOString(), source: 'https://api.osv.dev/v1/querybatch', scope: 'Resolved backend runtime and Android releaseRuntimeClasspath; public Maven coordinates only', counts: { backend: packages.filter(item => item.component === 'backend').length, Android: packages.filter(item => item.component === 'Android').length, affectedCoordinates: findings.length, distinctAdvisories: ids.length }, inventory: packages, findings, advisories };
await writeFile(path.join(root, 'logs/security-osv-results.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result.counts));
for (const finding of findings) console.log(`${finding.component}: ${finding.package.name}@${finding.version}: ${finding.vulnerabilities.join(', ')}`);
