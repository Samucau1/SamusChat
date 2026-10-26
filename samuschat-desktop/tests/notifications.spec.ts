import {test,expect} from '@playwright/test';

test.use({launchOptions:{ignoreDefaultArgs:['--disable-backgrounding-occluded-windows','--disable-renderer-backgrounding','--disable-background-timer-throttling']}});

const password='NotificationTest42!';
async function account(request:any,prefix:string){
 const username=prefix+crypto.randomUUID().slice(0,8),email=username+'@example.com';
 const response=await request.post('/api/auth/register',{data:{username,email,password}});
 expect(response.ok()).toBe(true);
 return {username,email,token:(await response.json()).data.token};
}
async function login(page:any,email:string){
 await page.goto('/');
 await page.getByLabel('Email',{exact:true}).fill(email);
 await page.getByLabel('Senha',{exact:true}).fill(password);
 await page.getByRole('button',{name:'Entrar',exact:true}).click();
 await expect(page.getByRole('button',{name:'Amigos',exact:true})).toBeVisible();
}

test('notification permission feedback and real browser notification in background',async({page,context,request})=>{
 const sender=await account(request,'NotifySender'),recipient=await account(request,'NotifyRecipient');
 await context.grantPermissions(['notifications']);
 // Observe calls while retaining the native browser implementation.
 await page.addInitScript(()=>{
  const Native=window.Notification;
  (window as any).receivedNotifications=[];
  window.Notification=new Proxy(Native,{construct(target,args){
   const notification=Reflect.construct(target,args);
   (window as any).receivedNotifications.push({title:args[0],body:args[1]?.body,tag:args[1]?.tag});
   return notification;
  }});
 });
 await login(page,recipient.email);
 await expect(page.getByRole('button',{name:sender.username,exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Ativar avisos de mensagens'}).click();
 await expect(page.getByRole('status').filter({hasText:'Avisos ativados'})).toBeVisible();
 const contacts=await request.get('/api/calls/contacts',{headers:{Authorization:`Bearer ${sender.token}`}});
 const contact=(await contacts.json()).data.find((item:any)=>item.username===recipient.username).email;
 const cdp=await context.newCDPSession(page);
 await cdp.send('Emulation.setFocusEmulationEnabled',{enabled:false});
 const {windowId}=await cdp.send('Browser.getWindowForTarget');
 await cdp.send('Browser.setWindowBounds',{windowId,bounds:{windowState:'minimized'}});
 const hidden=await expect.poll(()=>page.evaluate(()=>document.hidden)).toBe(true).then(()=>true,()=>false);
 if(!hidden){
  await cdp.send('Browser.setWindowBounds',{windowId,bounds:{windowState:'normal'}});
  test.skip(true,'Native notification requires real background visibility; this automation session keeps document.hidden=false even when minimized.');
 }
 // Wait for the authenticated socket before sending the test message.
 await page.waitForTimeout(1000);
 const sent=await request.post('/api/direct-messages',{headers:{Authorization:`Bearer ${sender.token}`},params:{contact},data:{content:'Teste de notificação com navegador em segundo plano'}});
 expect(sent.ok()).toBe(true);
 await expect.poll(()=>page.evaluate(()=>(window as any).receivedNotifications.length)).toBe(1);
 const notifications=await page.evaluate(()=>(window as any).receivedNotifications);
 expect(notifications[0].title).toBe('Nova mensagem no SamusChat');
 expect(notifications[0].body).toContain(sender.username);
 await cdp.send('Browser.setWindowBounds',{windowId,bounds:{windowState:'normal'}});
 await page.bringToFront();
 await expect(page.locator('.unread-badge')).toHaveText('1');
 await page.getByRole('button',{name:new RegExp(sender.username)}).click();
 await expect(page.locator('.unread-badge')).toHaveCount(0);
});

test('granted notification permission explains that the app must remain open',async({page,context,request})=>{
 const user=await account(request,'NotifyGranted');
 await context.grantPermissions(['notifications']);
 await login(page,user.email);
 await page.getByRole('button',{name:'Ativar avisos de mensagens'}).click();
 await expect(page.getByRole('status').filter({hasText:'Avisos ativados enquanto o SamusChat estiver aberto.'})).toBeVisible();
});

test('blocked notifications explain how to enable them',async({page,context,request})=>{
 const user=await account(request,'NotifyBlocked');
 await context.clearPermissions();
 const cdp=await context.newCDPSession(page);
 await cdp.send('Browser.setPermission',{permission:{name:'notifications'},setting:'denied',origin:'http://127.0.0.1:5174'});
 await login(page,user.email);
 await page.getByRole('button',{name:'Ativar avisos de mensagens'}).click();
 await expect(page.getByRole('status').filter({hasText:'Notificações bloqueadas'})).toBeVisible();
});
