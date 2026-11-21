import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const fixture=(name:string)=>readFileSync(fileURLToPath(new URL(`./fixtures/${name}`,import.meta.url)));
const password='SocialValidation42!';
async function account(request:APIRequestContext,prefix:string){const name=`${prefix}_${crypto.randomUUID().slice(0,6)}`,email=`${name}@social.test`;const r=await request.post('/api/auth/register',{data:{username:name,email,password}});expect(r.ok(),await r.text()).toBe(true);const token=(await r.json()).data.token;const profile=(await(await request.get('/api/users/me',{headers:{Authorization:`Bearer ${token}`}})).json()).data;return{name,email,token,id:profile.publicId};}
async function login(page:Page,email:string){await page.goto('/');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);await page.getByRole('button',{name:'Entrar',exact:true}).click();await expect(page.getByRole('heading',{name:'Seus amigos',exact:true})).toBeVisible();}
const shots=process.env.SOCIAL_SCREENSHOTS_DIR;
async function shot(page:Page,name:string){if(shots)await page.screenshot({path:`${shots}/${name}.png`,fullPage:true});}

test('descoberta, convite, aceite e perfil de um amigo funcionam sem adicionar todos os usuários',async({page,browser,request})=>{
  const a=await account(request,'Teste_Lara'),b=await account(request,'Teste_Gabriel'),stranger=await account(request,'Teste_Outro');
  for(const slot of ['avatar','banner','background']){const r=await request.post(`/api/users/me/media/${slot}`,{headers:{Authorization:`Bearer ${b.token}`},multipart:{file:{name:slot+'.png',mimeType:'image/png',buffer:fixture(slot==='avatar'?'social-avatar.png':'social-banner.png')}}});expect(r.ok(),await r.text()).toBe(true);}
  expect((await request.put('/api/users/me/appearance',{headers:{Authorization:`Bearer ${b.token}`},data:{bio:'Desenvolvimento, música e boas conversas. Criando coisas com amigos no SamusChat.',accentColor:'#805ad5'}})).ok()).toBe(true);
  await login(page,a.email);await expect(page.getByText('Sua lista de amigos começa aqui',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:stranger.name,exact:true})).toHaveCount(0);await shot(page,'01-amigos-separados');
  await page.locator('.find-friends').click();await page.getByLabel('Procurar por nome ou ID',{exact:true}).fill(b.name);
  await expect(page.getByRole('button',{name:`Ver perfil de ${b.name}`,exact:true})).toBeVisible();await shot(page,'02-busca-por-nome');
  await page.getByLabel('Procurar por nome ou ID',{exact:true}).fill(b.id);await expect(page.getByRole('button',{name:`Ver perfil de ${b.name}`,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Adicionar amigo',exact:true}).click();await expect(page.getByText('Solicitação enviada. Aguarde a pessoa aceitar.',{exact:true})).toBeVisible();
  const context=await browser.newContext({viewport:{width:1440,height:900}}),other=await context.newPage();
  try {await login(other,b.email);await other.getByRole('tab',{name:/Solicitações/}).click();await expect(other.getByText('Quer ser seu amigo',{exact:true})).toBeVisible();await shot(other,'03-convite-recebido');await other.getByRole('button',{name:'Aceitar',exact:true}).click();await expect(other.getByText('Amizade aceita. Vocês já podem conversar.',{exact:true})).toBeVisible();
    await page.reload();await page.getByRole('tab',{name:/Amigos ·/}).click();await page.locator('.social-list').getByRole('button',{name:'Conversar',exact:true}).click();
    await page.getByLabel(`Mensagem para ${b.name}`,{exact:true}).fill('Agora somos amigos no SamusChat!');await page.getByRole('button',{name:'Enviar ↗',exact:true}).click();await expect(page.locator('.message').getByText('Agora somos amigos no SamusChat!',{exact:true})).toBeVisible();
    await page.getByRole('button',{name:`Ver perfil de ${b.name}`,exact:true}).first().click();const profile=page.getByRole('dialog',{name:'Perfil do usuário'});await expect(profile.getByText('Desenvolvimento, música e boas conversas. Criando coisas com amigos no SamusChat.',{exact:true})).toBeVisible();await expect(profile.locator('.public-profile-banner img')).toBeVisible();await expect.poll(()=>profile.locator('img.avatar').evaluate((img:HTMLImageElement)=>img.naturalWidth)).toBeGreaterThan(0);await expect(profile).not.toContainText(b.email);await shot(page,'04-perfil-do-amigo');
    await page.getByRole('button',{name:'Fechar perfil',exact:true}).click();await page.getByRole('button',{name:'Amigos',exact:true}).click();await page.getByRole('button',{name:`Ver perfil de ${b.name}`,exact:true}).click();await page.getByRole('dialog',{name:'Perfil do usuário'}).getByRole('button',{name:'Remover amigo',exact:true}).click();await page.getByRole('button',{name:'Fechar perfil',exact:true}).click();await page.reload();await expect(page.getByText('Sua lista de amigos começa aqui',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:b.name,exact:true})).toBeVisible();
  }finally{await context.close();}
});
test('perfil informa imagem salva ou recusada e mantém foto após sair e entrar',async({page,request})=>{
  const a=await account(request,'Teste_Imagem');await login(page,a.email);await page.locator('.profile-button').click();
  await page.getByLabel('Foto ou GIF do perfil',{exact:true}).setInputFiles({name:'foto.png',mimeType:'image/png',buffer:fixture('social-avatar.png')});
  await expect(page.getByRole('dialog',{name:'Enquadrar foto de perfil'})).toBeVisible();await page.getByRole('button',{name:'Salvar enquadramento',exact:true}).click();await expect(page.getByText('✓ Imagem aceita e salva na sua conta.',{exact:true})).toBeVisible();await shot(page,'05-feedback-imagem-salva');
  const before=await page.locator('.profile-button img').getAttribute('src');
  await page.getByLabel('Foto ou GIF do perfil',{exact:true}).setInputFiles({name:'arquivo.txt',mimeType:'text/plain',buffer:Buffer.from('Não é uma imagem')});await expect(page.getByText(/Arquivo recusado: escolha uma imagem/)).toBeVisible();await expect(page.locator('.profile-button img')).toHaveAttribute('src',before!);await shot(page,'06-feedback-imagem-recusada');
  await page.getByRole('button',{name:'Sair da conta',exact:true}).click();await expect(page.getByRole('heading',{name:'Bom ter você por aqui',exact:true})).toBeVisible();await login(page,a.email);await expect(page.locator('.profile-button img')).toHaveAttribute('src',before!);await expect.poll(()=>page.locator('.profile-button img').evaluate((img:HTMLImageElement)=>img.naturalWidth)).toBeGreaterThan(0);
  await page.setViewportSize({width:390,height:844});await page.locator('.find-friends').click();await expect(page.getByLabel('Procurar por nome ou ID',{exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await shot(page,'07-amigos-celular');
});
