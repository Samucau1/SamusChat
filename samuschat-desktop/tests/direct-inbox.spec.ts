import {test,expect} from '@playwright/test';
test('authoritative snapshot clears reads from another device but preserves newer live arrivals',async({page})=>{
 await page.goto('/');
 const result=await page.evaluate(async()=>{
  const {DirectInbox}=await import('/src/models/directInbox.ts');const inbox=new DirectInbox('me');
  const message={id:1,senderEmail:'friend',recipientEmail:'me',senderUsername:'Friend',content:'hello',createdAt:'2026-10-20T12:00:00'};
  inbox.accept(message);const cursor=inbox.snapshotCursor;inbox.accept({...message,id:2});
  inbox.replace([],cursor);const concurrent=inbox.counts.friend;inbox.replace([]);
  return {concurrent,afterRemoteRead:inbox.counts.friend??0};
 });
 expect(result).toEqual({concurrent:1,afterRemoteRead:0});
});
test('inbox deduplicates events and excludes own or unrelated messages',async({page})=>{
 await page.goto('/');
 const result=await page.evaluate(async()=>{
  const {DirectInbox}=await import('/src/models/directInbox.ts');const inbox=new DirectInbox('me');
  const event={id:10,senderEmail:'friend',recipientEmail:'me',senderUsername:'Friend',content:'hello',createdAt:'2026-10-20T12:00:00'};
  const first=inbox.accept(event),repeat=inbox.accept(event),self=inbox.accept({...event,id:11,senderEmail:'me',recipientEmail:'friend'});
  inbox.accept({...event,id:12,senderEmail:'other'});inbox.accept({...event,id:13,recipientEmail:'outsider'});
  return {first,repeat,self,counts:inbox.counts};
 });
 expect(result.first).toBe(true);expect(result.repeat).toBe(false);expect(result.self).toBe(false);
 expect(result.counts).toEqual({friend:1,other:1});
});
test('read watermark retains messages arriving after displayed history',async({page})=>{
 await page.goto('/');
 const result=await page.evaluate(async()=>{
  const {DirectInbox}=await import('/src/models/directInbox.ts');const inbox=new DirectInbox('me');
  inbox.replace([{senderEmail:'friend',count:2,lastId:20}]);
  const event={id:21,senderEmail:'friend',recipientEmail:'me',senderUsername:'Friend',content:'hello',createdAt:'2026-10-20T12:00:00'};
  inbox.accept(event);inbox.read('friend',20);const pending={...inbox.counts};inbox.read('friend',21);
  return {pending,final:inbox.counts,duplicate:inbox.accept(event)};
 });
 expect(result.pending.friend).toBe(1);expect(result.final.friend??0).toBe(0);expect(result.duplicate).toBe(false);
});
