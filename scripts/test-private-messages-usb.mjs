// Uses the already authenticated PC test tab and normal Android installation.
// Never reads or exports the phone's stored token; leaves two labelled smoke messages.
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {mediaEvaluate} from './media-session-cdp.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const serial=process.argv[2];if(!/^[A-Za-z0-9_-]+$/.test(serial??''))throw Error('USB serial required');
const adb=(...args)=>execFileSync(path.join(root,'.android-sdk/platform-tools/adb.exe'),['-s',serial,...args],{encoding:'utf8',timeout:20000,stdio:['ignore','pipe','pipe']});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
function ui(){adb('shell','uiautomator','dump','/sdcard/samus-beta-ui.xml');return adb('shell','cat','/sdcard/samus-beta-ui.xml');}
function nodes(xml){return [...xml.matchAll(/<node\b([^>]+)>/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]])));}
async function tap(text){for(let i=0;i<8;i++){const n=nodes(ui()).find(n=>n.text===text||n['content-desc']===text);if(n){const b=n.bounds.match(/\d+/g).map(Number);adb('shell','input','tap',String(Math.round((b[0]+b[2])/2)),String(Math.round((b[1]+b[3])/2)));return;}if(i===3)adb('shell','input','swipe','350','1100','350','500','300');await delay(500);}throw Error('Control unavailable: '+text);}
const state=await mediaEvaluate(`(async()=>{const token=sessionStorage.getItem('samuschat.desktop.session');if(!token)throw Error('PC session required');const h={Authorization:'Bearer '+token};const me=(await(await fetch('/api/users/me',{headers:h})).json()).data;const contacts=(await(await fetch('/api/calls/contacts',{headers:h})).json()).data;if(contacts.length!==1)throw Error('Expected only the two authorized development accounts');return {pcName:me.username,contact:contacts[0]};})()`);
adb('shell','am','start','-n','com.samuschat/.MainActivity');await tap('Amigos');await tap('Atualizar contatos');await delay(1000);
const stamp=Date.now(),out=`Beta PC para Android ${stamp}`,reply=`Beta Android para PC ${stamp}`;
assert.equal(await mediaEvaluate(`(async()=>{const r=await fetch('/api/direct-messages?contact='+encodeURIComponent(${JSON.stringify(state.contact.email)}),{method:'POST',headers:{Authorization:'Bearer '+sessionStorage.getItem('samuschat.desktop.session'),'Content-Type':'application/json'},body:JSON.stringify({content:${JSON.stringify(out)}})});return r.status;})()`),200);
await delay(1500);let xml=ui();
assert.ok(xml.includes('mensagens nao lidas'),'Android unread badge missing');
assert.ok(xml.includes('Nova mensagem de'),'Android notification missing');
await tap('Abrir');await delay(1000);assert.ok(ui().includes(out),'Android did not show PC message');
let input=nodes(ui()).find(n=>n.class==='android.widget.EditText');assert.ok(input,'Android composer missing');
const b=input.bounds.match(/\d+/g).map(Number);adb('shell','input','tap',String(Math.round((b[0]+b[2])/2)),String(Math.round((b[1]+b[3])/2)));
adb('shell','input','text',reply.replaceAll(' ','%s'));adb('shell','input','keyevent','4');await tap('Enviar mensagem');await delay(1200);
const pc=await mediaEvaluate(`({notice:document.querySelector('.message-notice')?.textContent,unread:[...document.querySelectorAll('.unread-badge')].map(n=>n.textContent)})`);
assert.ok(pc.notice?.includes('Nova mensagem de'),'PC notification missing');assert.ok(pc.unread.length,'PC unread badge missing');
await mediaEvaluate(`(()=>{const button=[...document.querySelectorAll('nav[aria-label=Conversas] button')].find(b=>b.textContent.includes(${JSON.stringify(state.contact.username)}));if(!button)throw Error('PC contact unavailable');button.click();return true;})()`);
await delay(1000);
assert.equal(await mediaEvaluate(`document.querySelectorAll('.message p').length>0&&[...document.querySelectorAll('.message p')].some(p=>p.textContent===${JSON.stringify(reply)})`),true);
await tap('Voltar aos amigos');await delay(600);
const result={passed:true,atUtc:new Date().toISOString(),pcToAndroid:true,androidToPc:true,androidUnreadAndNotice:true,pcUnreadAndNotice:true,pcUnreadCleared:await mediaEvaluate(`document.querySelectorAll('.unread-badge').length===0`),androidUnreadCleared:!ui().includes('mensagens nao lidas')};
assert.ok(result.pcUnreadCleared&&result.androidUnreadCleared,'Read acknowledgement did not clear counters');
fs.writeFileSync(path.join(root,'logs/beta-local-usb-messages.json'),JSON.stringify(result,null,2)+'\n');console.log(result);
