// USB-only sampler. Does not record audio/video, credentials or private messages.
import { execFileSync, spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
export async function measureAndroid(serial, label, interact = false) {
  if (!/^[\w-]+$/.test(serial || '') || !/^[\w-]+$/.test(label || '')) throw Error('Invalid USB serial or sample label');
  const adb = (...args) => execFileSync(path.join(root, '.android-sdk/platform-tools/adb.exe'), ['-s', serial, ...args], {encoding:'utf8',timeout:30000});
  const directory = path.join(root, 'logs/android-performance', label);
  mkdirSync(directory, {recursive:true});
  const pid = adb('shell','pidof','com.samuschat').trim();
  const initial = adb('shell','dumpsys','gfxinfo','com.samuschat','reset');
  let interaction;
  if (interact) {
    adb('shell','uiautomator','dump','/sdcard/performance-input.xml');
    const xml = adb('shell','cat','/sdcard/performance-input.xml');
    const node = [...xml.matchAll(/<node\b([^>]+)>/g)].find(m=>/content-desc="(?:Silenciar microfone|Ativar microfone)"/.test(m[1]));
    if (!node) throw Error('Microphone button unavailable for rendering probe');
    const b = node[1].match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).slice(1).map(Number);
    const x=(b[0]+b[2])>>1, y=(b[1]+b[3])>>1;
    // Six toggles return the microphone to its initial state. Never sends a message.
    const child=spawn(path.join(root,'.android-sdk/platform-tools/adb.exe'),['-s',serial,'shell',`for i in 1 2 3 4 5 6; do input tap ${x} ${y}; sleep 1; done`],{stdio:'ignore'});
    interaction=new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(Error('Input exercise failed')));});
  }
  const top = adb('shell','top','-b','-n','4','-d','3','-p',pid);
  if (interaction) await interaction;
  const memory = adb('shell','dumpsys','meminfo','com.samuschat');
  const system = adb('shell','cat','/proc/meminfo');
  const graphics = adb('shell','dumpsys','gfxinfo','com.samuschat');
  const thermal = adb('shell','dumpsys','thermalservice');
  const samples = top.split('\n').filter(line=>line.includes('com.samuschat')).map(line=>Number(line.trim().split(/\s+/)[8])).filter(Number.isFinite).slice(1);
  const summary = {
    label, recordedAt: new Date().toISOString(), pid, interaction: interact ? 'six microphone toggles' : 'none',
    cpuPercentOneCore: samples, cpuMeanOneCore: samples.length ? samples.reduce((a,b)=>a+b,0)/samples.length : null,
    pssKb: Number(memory.match(/TOTAL PSS:\s*(\d+)/)?.[1] || memory.match(/TOTAL:\s*(\d+)/)?.[1]) || null,
    availableKb: Number(system.match(/MemAvailable:\s*(\d+)/)?.[1]),
    swapUsedKb: Number(system.match(/SwapTotal:\s*(\d+)/)?.[1])-Number(system.match(/SwapFree:\s*(\d+)/)?.[1]),
    uiFrames: Number(graphics.match(/Total frames rendered:\s*(\d+)/)?.[1]),
    uiJankPercent: Number(graphics.match(/Janky frames:.*\(([\d.]+)%\)/)?.[1]) || 0,
    uiP95Ms: /Total frames rendered:\s*0\b/.test(graphics) ? null : Number(graphics.match(/95th percentile:\s*(\d+)/)?.[1]) || null,
    thermalStatus: Number(thermal.match(/Thermal Status:\s*(\d+)/)?.[1]),
    note: 'CPU uses one-core units. gfxinfo measures rendering, not touch latency. No rendered frames means UI latency is unmeasured.'
  };
  for (const [name, data] of Object.entries({top,memory,system,graphics,thermal,initial})) writeFileSync(path.join(directory,`${name}.txt`),data);
  writeFileSync(path.join(directory,'summary.json'),JSON.stringify(summary,null,2));
  console.log(JSON.stringify(summary)); return summary;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await measureAndroid(process.argv[2],process.argv[3],process.argv.includes('--interact'));
