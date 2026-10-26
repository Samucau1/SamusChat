import {test,expect} from '@playwright/test';
import {execFileSync} from 'node:child_process';
import path from 'node:path';

test('real USB Android and PC exchange messages, notices and read acknowledgements',async({page,request})=>{
 test.skip(!process.env.SAMUSCHAT_ANDROID_SERIAL,'Requires explicitly selected USB phone and validation APK');
 test.setTimeout(180000);
 const serial=process.env.SAMUSCHAT_ANDROID_SERIAL!;
 expect(serial).toMatch(/^[A-Za-z0-9_-]+$/);
 const adb=(...args:string[])=>execFileSync(path.resolve('../.android-sdk/platform-tools/adb.exe'),['-s',serial,...args],{encoding:'utf8',timeout:20000});
 const ui=()=>{adb('shell','uiautomator','dump','/sdcard/samus-notifications-test.xml');return adb('shell','cat','/sdcard/samus-notifications-test.xml');};
 const nodes=(xml:string)=>[...xml.matchAll(/<node\b([^>]+)>/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]])));
 const tapNode=(node:Record<string,string>)=>{const b=node.bounds.match(/\d+/g)!.map(Number);adb('shell','input','tap',String(Math.round((b[0]+b[2])/2)),String(Math.round((b[1]+b[3])/2)));};
 const tap=(text:string)=>{const node=nodes(ui()).find(n=>n.text===text||n['content-desc']===text);expect(node,`Android control: ${text}`).toBeTruthy();tapNode(node!);};
 const password='NotificationTest42!';
 const users=[];
 for(const prefix of ['UsbPC','UsbPhone']){
  const username=prefix+crypto.randomUUID().slice(0,8),email=username+'@example.com';
  const response=await request.post('/api/auth/register',{data:{username,email,password}});
  expect(response.ok()).toBe(true);users.push({username,email,token:(await response.json()).data.token});
 }
 const [pc,phone]=users;
 await page.goto('/');await page.getByLabel('Email',{exact:true}).fill(pc.email);await page.getByLabel('Senha',{exact:true}).fill(password);
 await page.getByRole('button',{name:'Entrar',exact:true}).click();
 await expect(page.getByRole('button',{name:phone.username,exact:true})).toBeVisible();
 adb('shell','am','start','-n','com.samuschat.validation/com.samuschat.MainActivity');
 let edits=nodes(ui()).filter(n=>n.class==='android.widget.EditText');expect(edits).toHaveLength(2);
 tapNode(edits[0]);
 await expect.poll(()=>nodes(ui()).filter(n=>n.class==='android.widget.EditText')[0]?.focused).toBe('true');
 adb('shell','input','text',phone.email);
 adb('shell','input','keyevent','4');
 edits=nodes(ui()).filter(n=>n.class==='android.widget.EditText');tapNode(edits[1]);
 await expect.poll(()=>nodes(ui()).filter(n=>n.class==='android.widget.EditText')[1]?.focused).toBe('true');
 adb('shell','input','text',password);
 adb('shell','input','keyevent','4');tap('Entrar');
 await expect.poll(()=>ui(),{timeout:30000,intervals:[1000]}).toContain('Atualizar contatos');
 await page.getByRole('button',{name:phone.username,exact:true}).click();
 const content=`Teste PC para celular ${Date.now()}`;
 // REST uses the same authenticated send endpoint as the PC composer.
 const contacts=await request.get('/api/calls/contacts',{headers:{Authorization:`Bearer ${pc.token}`}});
 const contact=(await contacts.json()).data.find((c:any)=>c.username===phone.username).email;
 const sent=await request.post('/api/direct-messages',{headers:{Authorization:`Bearer ${pc.token}`},params:{contact},data:{content}});
 expect(sent.ok()).toBe(true);
 await expect.poll(()=>ui(),{timeout:20000,intervals:[1000]}).toContain('Nova mensagem de');
 const unread=await request.get('/api/direct-messages/unread',{headers:{Authorization:`Bearer ${phone.token}`}});
 expect((await unread.json()).data.some((item:any)=>item.count>0)).toBe(true);
 tap('Abrir');
 await expect.poll(()=>ui(),{timeout:15000,intervals:[1000]}).toContain(content);
 const reply=`Teste celular para PC ${Date.now()}`;
 const edit=nodes(ui()).find(n=>n.class==='android.widget.EditText');expect(edit).toBeTruthy();tapNode(edit!);
 adb('shell','input','text',reply.replaceAll(' ','%s'));adb('shell','input','keyevent','4');
 // Leave the PC conversation to check its global notice and unread badge.
 await page.getByRole('button',{name:'Amigos',exact:true}).click();
 tap('Enviar mensagem');
 await expect(page.getByRole('status').filter({hasText:`Nova mensagem de ${phone.username}`})).toBeVisible();
 await expect(page.locator('.unread-badge')).toHaveText('1');
 await page.getByRole('button',{name:new RegExp(phone.username)}).click();
 await expect(page.locator('.message p').filter({hasText:reply})).toHaveCount(1);
 await expect(page.locator('.unread-badge')).toHaveCount(0);
 tap('Voltar aos amigos');
 await expect.poll(async()=>{
  const response=await request.get('/api/direct-messages/unread',{headers:{Authorization:`Bearer ${phone.token}`}});
  return (await response.json()).data;
 },{timeout:15000}).toEqual([]);
 await page.screenshot({path:'test-results/notifications-pc-usb.png'});
});
