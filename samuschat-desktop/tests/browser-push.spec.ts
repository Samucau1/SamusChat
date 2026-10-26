import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

test('Edge receives a push in the real service worker after every app tab is closed',async({browser})=>{
 const context=await browser.newContext({permissions:['notifications']});
 try{
  // The controller stays on about:blank; no SamusChat page remains open at delivery.
  const controller=await context.newPage();
  const cdp=await context.newCDPSession(controller);
  let registrationId='';
  cdp.on('ServiceWorker.workerRegistrationUpdated',event=>{
   for(const registration of event.registrations)if(registration.scopeURL==='http://127.0.0.1:5174/')registrationId=registration.registrationId;
  });
  await cdp.send('ServiceWorker.enable');
  const page=await context.newPage();await page.goto('/');
  await page.evaluate(async()=>{
   await navigator.serviceWorker.register('/push-sw.js');await navigator.serviceWorker.ready;
  });
  await expect.poll(()=>registrationId).not.toBe('');
  await expect.poll(()=>context.serviceWorkers().length).toBe(1);
  await page.close();
  expect(context.pages().every(item=>!item.url().startsWith('http://127.0.0.1:5174'))).toBe(true);
  // Browser DevTools injects a push event; this does not claim FCM network delivery.
  await cdp.send('ServiceWorker.deliverPushMessage',{
   origin:'http://127.0.0.1:5174',registrationId,
   data:JSON.stringify({notification:{title:'Private sender',body:'Private content'},data:{type:'NEW_MESSAGE'}}),
  });
  const worker=context.serviceWorkers()[0];
  await expect.poll(()=>worker.evaluate(async()=>{
   const registration=(globalThis as any).registration;
   return (await registration.getNotifications()).map((item:any)=>({title:item.title,body:item.body,tag:item.tag}));
  })).toEqual([{title:'SamusChat',body:'Você tem uma nova mensagem.',tag:'samuschat-messages'}]);
 }finally{await context.close();}
});

test('notification click handler focuses an existing app or opens only the same origin root',async()=>{
 const handlers:Record<string,(event:any)=>void>={};
 let opened='',focused=false,closed=false;
 let windows:any[]=[];
 const scope={addEventListener:(name:string,handler:any)=>handlers[name]=handler,
  location:{origin:'https://samuschat.test'},
  clients:{matchAll:async()=>windows,openWindow:async(url:string)=>{opened=url;}},
 };
 runInNewContext(readFileSync('public/push-sw.js','utf8'),{self:scope,URL});
 let completion=Promise.resolve();
 const click=()=>handlers.notificationclick({
  notification:{data:{url:'https://untrusted.test'},close:()=>{closed=true;}},
  waitUntil:(task:Promise<void>)=>{completion=task;},
 });
 click();await completion;expect(opened).toBe('/');expect(closed).toBe(true);
 windows=[{url:'https://samuschat.test/',focus:async()=>{focused=true;}}];opened='';
 click();await completion;expect(focused).toBe(true);expect(opened).toBe('');
});

test('service worker ignores malformed or unrelated pushes and suppresses a visible app',async({page,context})=>{
 await context.grantPermissions(['notifications']);await page.goto('/');
 await page.evaluate(async()=>{await navigator.serviceWorker.register('/push-sw.js');await navigator.serviceWorker.ready;});
 await expect.poll(()=>context.serviceWorkers().length).toBe(1);
 const worker=context.serviceWorkers()[0];
 const result=await worker.evaluate(async()=>{
  const scope=globalThis as any;
  const tasks:Promise<unknown>[]=[];
  for(const data of ['broken',JSON.stringify({data:{type:'OTHER'}}),JSON.stringify({data:{type:'NEW_MESSAGE'}})]){
   const event=new scope.PushEvent('push',{data});
   Object.defineProperty(event,'waitUntil',{value:(task:Promise<unknown>)=>tasks.push(task)});
   scope.dispatchEvent(event);
  }
  await Promise.all(tasks);
  return (await scope.registration.getNotifications()).length;
 });
 expect(result).toBe(0);
});

test('deactivation preserves registration on server failure and cannot cancel another account',async({page})=>{
 await page.goto('/');
 let fail=true,requests=0;
 await page.route('**/api/devices/unregister?*',route=>{
  requests++;return route.fulfill({status:fail?503:200,json:fail?{success:false,error:'offline'}:{success:true}});
 });
 await page.evaluate(async()=>{
  await navigator.serviceWorker.register('/push-sw.js');await navigator.serviceWorker.ready;
  localStorage.setItem('samuschat.webPush',JSON.stringify({owner:'account-a',token:'test-token'}));
 });
 const other=await page.evaluate(async()=>{
  const {disableBackgroundPush}=await import('/src/models/browserPush.ts');
  const {ApiRepository}=await import('/src/models/apiRepository.ts');
  await disableBackgroundPush(new ApiRepository('jwt'),'account-b');
  return localStorage.getItem('samuschat.webPush');
 });
 expect(other).toContain('account-a');expect(requests).toBe(0);
 const failed=await page.evaluate(async()=>{
  const {disableBackgroundPush}=await import('/src/models/browserPush.ts');
  const {ApiRepository}=await import('/src/models/apiRepository.ts');
  try{await disableBackgroundPush(new ApiRepository('jwt'),'account-a');return false;}catch{return true;}
 });
 expect(failed).toBe(true);
 expect(await page.evaluate(()=>localStorage.getItem('samuschat.webPush'))).toContain('test-token');
 fail=false;
 await page.evaluate(async()=>{
  const {disableBackgroundPush}=await import('/src/models/browserPush.ts');
  const {ApiRepository}=await import('/src/models/apiRepository.ts');
  await disableBackgroundPush(new ApiRepository('jwt'),'account-a');
 });
 expect(await page.evaluate(()=>localStorage.getItem('samuschat.webPush'))).toBeNull();
 expect(requests).toBe(2);
});
