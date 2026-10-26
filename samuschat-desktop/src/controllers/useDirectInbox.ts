import {useEffect,useRef,useState,type MutableRefObject,type Dispatch,type SetStateAction} from 'react';
import {Client} from '@stomp/stompjs';
import {DirectInbox} from '../models/directInbox';
import {type ApiRepository,type ApiMessage,type User,mergeApiMessages} from '../models/apiRepository';
import type {Conversation} from './useChatController';

export function useDirectInbox(api:ApiRepository,user:User,selected:MutableRefObject<Conversation|null>,setMessages:Dispatch<SetStateAction<ApiMessage[]>>,syncSelected:MutableRefObject<()=>void>,messages:ApiMessage[]){
 const inbox=useRef(new DirectInbox(user.publicId??user.email));
 const live=useRef(false);
 const [connected,setConnected]=useState(false);
 const [unread,setUnread]=useState<Record<string,number>>({});
 const [notice,setNotice]=useState<ApiMessage|null>(null);
 const [visibility,setVisibility]=useState(0);
 const reads=useRef(new Map<string,number>());
 const pendingReads=useRef(new Set<string>());
 const [notificationStatus,setNotificationStatus]=useState('');
 async function enableNotifications(){
  if(!('Notification' in window)){setNotificationStatus('Este navegador não oferece notificações.');return;}
  try{
   const permission=Notification.permission==='default'?await Notification.requestPermission():Notification.permission;
   setNotificationStatus(permission==='granted'?'Avisos ativados enquanto o SamusChat estiver aberto.':permission==='denied'?'Notificações bloqueadas. Libere nas configurações deste site no navegador.':'Permissão não concedida. Você pode tentar novamente.');
  }catch{setNotificationStatus('Não foi possível ativar os avisos neste navegador.');}
 }
 useEffect(()=>{
  let alive=true;let snapshotVersion=0;
  const snapshot=async()=>{const version=++snapshotVersion,cursor=inbox.current.snapshotCursor;try{const items=await api.unreadDirect();if(alive&&version===snapshotVersion){inbox.current.replace(items,cursor);setUnread(inbox.current.counts);}}catch{/* retry on focus/reconnection */}};
  const url=new URL('/ws/websocket',window.location.href);url.protocol=url.protocol==='https:'?'wss:':'ws:';
  const client=new Client({brokerURL:url.href,connectHeaders:{Authorization:`Bearer ${api.token}`},reconnectDelay:3000,connectionTimeout:10000,
   onConnect:()=>{
    if(!alive)return;live.current=true;setConnected(true);
    client.subscribe('/user/queue/direct-messages',event=>{
     if(!alive)return;
     try{
      const item=JSON.parse(event.body) as ApiMessage,active=selected.current?.contact;
      const me=user.publicId??user.email;
      if(active&&((item.senderEmail===active&&item.recipientEmail===me)||(item.senderEmail===me&&item.recipientEmail===active)))setMessages(previous=>mergeApiMessages(previous,[item]));
      if(inbox.current.accept(item)){
       setUnread(inbox.current.counts);
       if(document.hidden||active!==item.senderEmail){
        setNotice(item);
        if(document.hidden&&'Notification' in window&&Notification.permission==='granted'){
         const notification=new Notification('Nova mensagem no SamusChat',{body:`${item.senderUsername} enviou uma mensagem`,tag:`dm:${item.senderEmail}`});
         notification.onclick=()=>{window.focus();notification.close();};
        }
       }
      }
     }catch{void snapshot();syncSelected.current();}
    });void snapshot();syncSelected.current();
   },onWebSocketClose:()=>{live.current=false;if(alive)setConnected(false);},onStompError:()=>{live.current=false;if(alive)setConnected(false);client.forceDisconnect();}
  });
  client.activate();void snapshot();
  const focus=()=>{if(!document.hidden){void snapshot();syncSelected.current();setVisibility(n=>n+1);}};
  const offline=()=>{live.current=false;setConnected(false);client.forceDisconnect();};
  window.addEventListener('focus',focus);document.addEventListener('visibilitychange',focus);window.addEventListener('offline',offline);
  return()=>{alive=false;live.current=false;void client.deactivate();window.removeEventListener('focus',focus);document.removeEventListener('visibilitychange',focus);window.removeEventListener('offline',offline);};
 },[api]);
 const contact=selected.current?.contact;
 useEffect(()=>{
  if(!contact||document.hidden||pendingReads.current.has(contact))return;
  const throughId=Math.max(0,...messages.filter(m=>m.senderEmail===contact&&m.recipientEmail===(user.publicId??user.email)).map(m=>m.id));
  if(!throughId||throughId<=(reads.current.get(contact)??0))return;
  pendingReads.current.add(contact);let succeeded=false;
  void api.readDirect(contact,throughId).then(()=>{reads.current.set(contact,throughId);inbox.current.read(contact,throughId);setUnread(inbox.current.counts);succeeded=true;})
   .catch(()=>{}).finally(()=>{pendingReads.current.delete(contact);if(succeeded)setVisibility(n=>n+1);});
 },[api,contact,messages,visibility]);
 return {unread,notice,dismissNotice:()=>setNotice(null),enableNotifications,notificationStatus,live,connected};
}
