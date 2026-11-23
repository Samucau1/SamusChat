/* No fetch handler or page cache: this worker only handles push delivery. */
self.addEventListener('install',event=>event.waitUntil(self.skipWaiting()));
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
function notificationUrl(data) {
 const identity=/^usr_[1-9][0-9]*_[A-Za-z0-9_-]{22}$/;
 if(!identity.test(data?.recipientId??''))return '/';
 const url=new URL('/',self.location.origin);
 url.searchParams.set('recipient',data.recipientId);
 if(data.channelId==='direct'&&identity.test(data.contactId??'')){
  url.searchParams.set('notification','direct');url.searchParams.set('contact',data.contactId);
 }else if(/^[1-9][0-9]*$/.test(data.channelId??'')&&Number.isSafeInteger(Number(data.channelId))){
  url.searchParams.set('notification','channel');url.searchParams.set('channel',data.channelId);
 }else return '/';
 return url.pathname+url.search;
}
self.addEventListener('push',event=>{
 if(!event.data)return;
 let payload;
 try {payload=event.data.json();} catch {return;}
 if(payload?.data?.type!=='NEW_MESSAGE')return;
 event.waitUntil((async()=>{
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  const url=notificationUrl(payload.data);
  const visible=windows.filter(client=>client.visibilityState==='visible');
  if(visible.length){
   for(const client of visible)client.postMessage({type:'SAMUSCHAT_NEW_MESSAGE',url});
   return;
  }
  // Never expose message contents or sender identity on the lock screen.
  await self.registration.showNotification('SamusChat',{
   body:'Você tem uma nova mensagem.',tag:'samuschat-messages',
   data:{url},
  });
 })());
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 event.waitUntil((async()=>{
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  const app=windows.find(client=>new URL(client.url).origin===self.location.origin);
  const requested=new URL(event.notification.data?.url??'/',self.location.origin);
  const url=requested.origin===self.location.origin&&requested.pathname==='/'?requested.pathname+requested.search:'/';
  if(app){
   if(url!=='/')app.postMessage({type:'SAMUSCHAT_OPEN_NOTIFICATION',url});
   await app.focus();
  }else await self.clients.openWindow(url);
 })());
});
