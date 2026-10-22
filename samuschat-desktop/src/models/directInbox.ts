import type {ApiMessage} from './apiRepository';
export interface UnreadSummary {senderEmail:string;count:number;lastId:number}
/** A server snapshot plus bounded live deltas. Never treats an outgoing echo as unread. */
export class DirectInbox {
 private snapshots=new Map<string,{count:number;lastId:number}>();
 private pending=new Map<number,{contact:string;revision:number}>();
 private revision=0;
 private seen=new Set<number>();
 private readThrough=new Map<string,number>();
 constructor(private readonly me:string){}
 get counts():Record<string,number>{
  const counts:Record<string,number>={};
  for(const [contact,s] of this.snapshots)counts[contact]=s.count;
  for(const {contact} of this.pending.values())counts[contact]=(counts[contact]??0)+1;
  return counts;
 }
 get snapshotCursor(){return this.revision;}
 replace(items:UnreadSummary[],throughRevision=this.revision){
  this.snapshots=new Map(items.filter(s=>s.lastId>(this.readThrough.get(s.senderEmail)??0)).map(s=>[s.senderEmail,{count:s.count,lastId:s.lastId}]));
  for(const [id,{contact,revision}] of this.pending)if(revision<=throughRevision||id<=(this.snapshots.get(contact)?.lastId??0))this.pending.delete(id);
 }
 accept(message:ApiMessage){
  const contact=message.senderEmail;
  if(message.recipientEmail!==this.me||contact===this.me||!Number.isSafeInteger(message.id)||this.seen.has(message.id))return false;
  this.seen.add(message.id);if(this.seen.size>2048)this.seen.delete(this.seen.values().next().value!);
  if(message.id<=(this.readThrough.get(contact)??0)||message.id<=(this.snapshots.get(contact)?.lastId??0))return false;
  this.pending.set(message.id,{contact,revision:++this.revision});return true;
 }
 read(contact:string,throughId:number){
  this.readThrough.set(contact,Math.max(throughId,this.readThrough.get(contact)??0));
  const snapshot=this.snapshots.get(contact);if(snapshot&&snapshot.lastId<=throughId)this.snapshots.delete(contact);
  for(const [id,sender] of this.pending)if(sender.contact===contact&&id<=throughId)this.pending.delete(id);
 }
}
