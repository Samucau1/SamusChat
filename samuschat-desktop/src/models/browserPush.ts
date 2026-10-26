import {ApiRepository} from './apiRepository';

const storageKey='samuschat.webPush';
interface SubscriptionRecord {owner:string;token:string}
function saved():SubscriptionRecord|null {
 try {return JSON.parse(localStorage.getItem(storageKey)??'null');} catch {return null;}
}
export function backgroundPushActive(owner:string) {return saved()?.owner===owner;}
const config={
 apiKey:import.meta.env.VITE_FIREBASE_API_KEY,
 projectId:import.meta.env.VITE_FIREBASE_PROJECT_ID,
 messagingSenderId:import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
 appId:import.meta.env.VITE_FIREBASE_APP_ID,
};
export function browserPushConfigured() {
 return Object.values(config).every(Boolean)&&Boolean(import.meta.env.VITE_FIREBASE_VAPID_KEY);
}
async function registration() {
 return navigator.serviceWorker.getRegistration('/');
}
async function clearLocalSubscription() {
 const worker=await registration();
 await (await worker?.pushManager.getSubscription())?.unsubscribe();
 for(const notification of await worker?.getNotifications()??[])notification.close();
 localStorage.removeItem(storageKey);
}
// Serialize activation/deactivation across tabs of the same browser profile.
async function exclusive<T>(operation:()=>Promise<T>):Promise<T> {
 return await navigator.locks.request('samuschat.webPush',operation);
}
export async function enableBackgroundPush(api:ApiRepository,owner:string):Promise<boolean> {
 if(!browserPushConfigured())return false;
 const session=new ApiRepository(api.token);
 return exclusive(async()=>{
  if(!window.isSecureContext||!('serviceWorker' in navigator)||!('PushManager' in window))throw new Error('Este navegador não oferece push neste endereço.');
  if(!(await session.webPushStatus()).enabled)throw new Error('O servidor ainda não está configurado para push. Os avisos com o site aberto continuam disponíveis.');
  const [{initializeApp,getApps},{getMessaging,getToken,isSupported}]=await Promise.all([import('@firebase/app'),import('@firebase/messaging')]);
  if(!await isSupported())throw new Error('Push indisponível neste navegador.');
  const previous=saved();
  // A new account must get a new subscription rather than claim another user's token.
  if(previous&&previous.owner!==owner)await clearLocalSubscription();
  const worker=await navigator.serviceWorker.register('/push-sw.js',{scope:'/'});
  await navigator.serviceWorker.ready;
  const app=getApps().find(item=>item.name==='samuschat-push')??initializeApp(config,'samuschat-push');
  const token=await getToken(getMessaging(app),{vapidKey:import.meta.env.VITE_FIREBASE_VAPID_KEY,serviceWorkerRegistration:worker});
  if(!token)throw new Error('Não foi possível registrar os avisos. Tente novamente.');
  await session.registerDevice(token);
  localStorage.setItem(storageKey,JSON.stringify({owner,token}));
  return true;
 });
}
export async function disableBackgroundPush(api:ApiRepository,owner:string) {
 const session=new ApiRepository(api.token);
 return exclusive(async()=>{
  const current=saved();if(current?.owner!==owner)return;
  await session.unregisterDevice(current.token);
  await clearLocalSubscription();
 });
}
export async function clearPushAfterLogout(owner:string) {
 if(!('serviceWorker' in navigator))return;
 await exclusive(async()=>{if(saved()?.owner===owner)await clearLocalSubscription();});
}
