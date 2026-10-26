/* No fetch handler or page cache: this worker only handles push delivery. */
self.addEventListener('install',event=>event.waitUntil(self.skipWaiting()));
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('push',event=>{
 if(!event.data)return;
 let payload;
 try {payload=event.data.json();} catch {return;}
 if(payload?.data?.type!=='NEW_MESSAGE')return;
 event.waitUntil((async()=>{
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  if(windows.some(client=>client.visibilityState==='visible'))return;
  // Never expose message contents or sender identity on the lock screen.
  await self.registration.showNotification('SamusChat',{
   body:'Você tem uma nova mensagem.',tag:'samuschat-messages',
   data:{url:'/'},
  });
 })());
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 event.waitUntil((async()=>{
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  const app=windows.find(client=>new URL(client.url).origin===self.location.origin);
  if(app)await app.focus();
  else await self.clients.openWindow('/');
 })());
});
