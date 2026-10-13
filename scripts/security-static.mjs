// Read-only, bounded scan of tracked files. Print locations, never matched secret values.
import { execFileSync } from 'node:child_process';
import { lstat, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const files = execFileSync('git', ['ls-files', '-z'], { cwd: root }).toString().split('\0').filter(Boolean);
const detectors = [
  ['private_key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
  ['github_token', /\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{40,}\b/],
  ['aws_access_key_id', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
  ['google_api_key_candidate', /\bAIza[0-9A-Za-z_-]{35}\b/],
];
const candidates = []; let scanned = 0, skipped = 0;
for (const file of files) {
  const metadata = await lstat(path.join(root, file));
  if (!metadata.isFile() || metadata.size > 2_000_000) { skipped++; continue; }
  const buffer = await readFile(path.join(root, file));
  if (buffer.length > 2_000_000 || buffer.includes(0)) { skipped++; continue; }
  scanned++;
  buffer.toString('utf8').split(/\r?\n/).forEach((line, index) => {
    for (const [kind, regex] of detectors) if (regex.test(line)) candidates.push({ file, line: index + 1, kind });
  });
}
const read = file => readFile(path.join(root, file), 'utf8');
const manifestFile = type => `samuschat-android/app/build/intermediates/merged_manifest/${type}/process${type[0].toUpperCase() + type.slice(1)}MainManifest/AndroidManifest.xml`;
const manifests = {};
for (const type of ['debug', 'validation', 'release']) {
  const text = await read(manifestFile(type));
  const get = name => text.match(new RegExp(`android:${name}="([^"]+)"`))?.[1] ?? 'absent';
  manifests[type] = { targetSdk: get('targetSdkVersion'), cleartext: get('usesCleartextTraffic'), debuggable: get('debuggable'), backup: get('allowBackup'), source: manifestFile(type) };
}
const token = await read('samuschat-android/app/src/main/java/com/samuschat/util/TokenManager.kt');
const retrofit = await read('samuschat-android/app/src/main/java/com/samuschat/data/api/RetrofitClient.kt');
const ws = await read('samuschat-android/app/src/main/java/com/samuschat/ui/chat/WebSocketManager.kt');
const nginx = await read('nginx/nginx.conf');
const normalConfig = await read('chatapp-backend/src/main/resources/application.properties');
const prodConfig = await read('chatapp-backend/src/main/resources/application-prod.properties');
const androidBuild = await read('samuschat-android/app/build.gradle.kts');
const result = {
  checkedAt: new Date().toISOString(), secretScan: { scope: 'Tracked working-tree text files only; history, ignored files and binaries excluded', scanned, skipped, candidates, candidateIsNotVerifiedLiveSecret: true }, manifests,
  observations: {
    androidTokenUsesOrdinaryDataStore: token.includes('stringPreferencesKey("jwt_token")') && !token.includes('Cipher'),
    androidHttpLoggerRedactsAuthorization: retrofit.includes('redactHeader("Authorization")'),
    androidProductionHttpLoggingDisabled: retrofit.includes('else HttpLoggingInterceptor.Level.NONE'),
    androidWebSocketUsesOkHttpNotJavaWebSocket: ws.includes('Stomp.ConnectionProvider.OKHTTP'),
    androidDefaultBuildUrlIsLocalHttp: androidBuild.includes('http://10.0.2.2:8080/'),
    nginxReplacesForwardedFor: nginx.includes('proxy_set_header X-Forwarded-For $remote_addr;'),
    nginxHasTlsListener: /listen\s+443/.test(nginx),
    defaultProfileHasPublicJwtFallback: /jwt\.secret=\$\{JWT_SECRET:(?!\$\{random\.uuid\})[^}]+\}/.test(normalConfig),
    prodProfileRequiresJwtEnvironment: prodConfig.includes('jwt.secret=${JWT_SECRET}'),
  },
};
await writeFile(path.join(root, 'logs/security-static-results.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify({ secretScan: result.secretScan, manifests, observations: result.observations }, null, 2));
