import {test,expect,type Page} from '@playwright/test';
const password='DisposableInboxTest42!';
async function login(page:Page,email:string){
 await page.goto('/');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);
 await page.getByRole('button',{name:'Entrar',exact:true}).click();await expect(page.getByRole('button',{name:'Amigos',exact:true})).toBeVisible();
}
test('global private socket, persisted unread, reconnect recovery and visible read acknowledgement',async({page,browser,request})=>{
 const accounts=[];
 for(const prefix of ['inboxA','inboxB']){
  const username=prefix+crypto.randomUUID().slice(0,8),email=username+'@example.com';
  const r=await request.post('/api/auth/register',{data:{username,email,password}});expect(r.ok()).toBe(true);
  accounts.push({username,email,token:(await r.json()).data.token});
 }
 const [a,b]=accounts;
 await login(page,b.email);
 const contactResponse=await request.get('/api/calls/contacts',{headers:{Authorization:`Bearer ${a.token}`}});
 const recipient=(await contactResponse.json()).data.find((c:{username:string})=>c.username===b.username).email;
 const frames:string[]=[];
 page.on('websocket',socket=>socket.on('framereceived',event=>frames.push(String(event.payload))));
 // Reload after installing instrumentation; leave every conversation closed.
 await page.reload();await expect(page.getByRole('button',{name:a.username,exact:true})).toBeVisible();
 await expect.poll(()=>frames.some(f=>f.startsWith('CONNECTED'))).toBe(true);
 await page.waitForTimeout(300);
 let historyGets=0;
 await page.route('**/api/direct-messages?*',async route=>{if(route.request().method()==='GET')historyGets++;await route.continue();});
 const send=async(content:string)=>{const r=await request.post('/api/direct-messages',{headers:{Authorization:`Bearer ${a.token}`},params:{contact:recipient},data:{content}});expect(r.ok()).toBe(true);};
 await send('Live inbox message');
 await expect(page.getByRole('status').filter({hasText:`Nova mensagem de ${a.username}`})).toBeVisible();
 await expect(page.locator('.unread-badge')).toHaveText('1');expect(historyGets).toBe(0);
 expect(frames.filter(f=>f.startsWith('MESSAGE')).some(f=>f.includes('Live inbox message'))).toBe(true);
 await page.reload();await expect(page.locator('.unread-badge')).toHaveText('1');
 await page.context().setOffline(true);await send('Arrived while offline');await page.context().setOffline(false);
 await expect(page.locator('.unread-badge')).toHaveText('2',{timeout:20000});
 await page.getByRole('button',{name:new RegExp(a.username)}).click();
 await expect(page.locator('.message p').filter({hasText:'Live inbox message'})).toHaveCount(1);
 await expect(page.locator('.message p').filter({hasText:'Arrived while offline'})).toHaveCount(1);
 await expect(page.locator('.unread-badge')).toHaveCount(0);
 const unread=await request.get('/api/direct-messages/unread',{headers:{Authorization:`Bearer ${b.token}`}});
 expect((await unread.json()).data).toEqual([]);
});
