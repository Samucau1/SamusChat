import {test, expect} from '@playwright/test';
import {RecoveryBackoff} from '../src/models/recovery';

test('retry windows grow, cap at 30 seconds, vary and reset', () => {
  const low = new RecoveryBackoff(3000, 30000, () => 0);
  const high = new RecoveryBackoff(3000, 30000, () => 0.999999);
  expect(Array.from({length: 7}, () => low.next())).toEqual([1500, 3000, 6000, 12000, 15000, 15000, 15000]);
  expect(Array.from({length: 7}, () => high.next())).toEqual([2999, 5999, 11999, 23999, 29999, 29999, 29999]);
  low.reset(); expect(low.next()).toBe(1500);
});

test('slow recovery coalesces a burst and stops pending work on disposal', async ({page}) => {
  await page.goto('/');
  const result = await page.evaluate(async () => {
    const {recoveryTask} = await import('/src/models/recovery.ts');
    let calls = 0, active = 0, maxActive = 0;
    let release!: () => void;
    const task = recoveryTask(async () => {
      calls++; active++; maxActive = Math.max(maxActive, active);
      await new Promise<void>(resolve => { release = resolve; }); active--;
    }, () => false, () => true);
    const first = task.request();
    for (let i=0; i<20; i++) void task.request();
    const before = calls; release(); await first; await Promise.resolve();
    const trailing = calls;
    void task.request(); task.stop(); release(); await Promise.resolve(); await Promise.resolve();
    return {before, trailing, calls, maxActive};
  });
  expect(result).toEqual({before:1, trailing:2, calls:2, maxActive:1});
});

test('offline recovery skips HTTP and resumes serial polling with bounded delays', async ({page}) => {
  await page.goto('/'); await page.clock.install();
  await page.evaluate(async () => {
    const {recoveryTask} = await import('/src/models/recovery.ts');
    const w = window as any; w.recoveryCalls = 0; w.recoveryOnline = false;
    w.recovery = recoveryTask(async () => { w.recoveryCalls++; }, () => true, () => w.recoveryOnline);
    void w.recovery.request();
  });
  await page.clock.runFor(35000);
  expect(await page.evaluate(() => (window as any).recoveryCalls)).toBe(0);
  await page.evaluate(() => { const w=window as any; w.recoveryOnline=true;w.recovery.reset();w.recovery.schedule(); });
  await page.clock.runFor(3100);
  expect(await page.evaluate(() => (window as any).recoveryCalls)).toBe(1);
  await page.evaluate(() => (window as any).recovery.stop());
  await page.clock.runFor(60000);
  expect(await page.evaluate(() => (window as any).recoveryCalls)).toBe(1);
});

test('focus bursts do not overlap unread HTTP requests in the real controller', async ({page, request}) => {
  const username='Recovery'+crypto.randomUUID().slice(0,8), email=username+'@example.com', password='DisposableRecovery42!';
  expect((await request.post('/api/auth/register',{data:{username,email,password}})).ok()).toBe(true);
  await page.goto('/');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await page.getByRole('button',{name:'Amigos',exact:true}).waitFor();
  await page.waitForLoadState('networkidle');
  let active=0, maximum=0, requests=0;
  let release!: () => void;
  const gate=new Promise<void>(resolve=>{release=resolve;});
  await page.route('**/api/direct-messages/unread',async route=>{
    requests++;active++;maximum=Math.max(maximum,active);
    try { await gate; await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({success:true,data:[]})}); }
    finally { active--; }
  });
  await page.evaluate(()=>window.dispatchEvent(new Event('focus')));
  await expect.poll(()=>requests).toBe(1);
  await page.evaluate(()=>{for(let i=0;i<20;i++)window.dispatchEvent(new Event('focus'));});
  await page.waitForTimeout(300);expect(requests).toBe(1);
  release();await expect.poll(()=>requests).toBe(2);
  await page.waitForTimeout(300);expect(maximum).toBe(1);expect(requests).toBe(2);
});

test('failed sockets reconnect progressively and disposal cancels the retry timer', async ({page}) => {
  let attempts=0;
  await page.routeWebSocket('**/recovery-test',socket=>{attempts++;socket.close();});
  await page.goto('/');await page.clock.install({time:new Date('2026-11-04T12:00:00Z')});
  await page.clock.pauseAt(new Date('2026-11-04T12:00:01Z'));
  await page.evaluate(async()=>{
    const {reconnectingClient}=await import('/src/models/recovery.ts');
    Math.random=()=>0;
    (window as any).socketRecovery=reconnectingClient({brokerURL:`ws://${location.host}/recovery-test`,connectionTimeout:10000});
    (window as any).socketRecovery.client.activate();
  });
  const closed=async(count:number)=>{await expect.poll(()=>attempts).toBe(count);await expect.poll(()=>page.evaluate(()=>!(window as any).socketRecovery.client.active)).toBe(true);};
  await closed(1);
  await page.clock.runFor(1499);expect(attempts).toBe(1);
  await page.clock.runFor(1);await closed(2);
  await page.clock.runFor(2999);expect(attempts).toBe(2);
  await page.clock.runFor(1);await closed(3);
  await page.evaluate(()=>(window as any).socketRecovery.stop());
  await page.clock.runFor(60000);expect(attempts).toBe(3);
});
