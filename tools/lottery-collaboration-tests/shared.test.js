import { readFile } from "node:fs/promises";
import { before, beforeEach, after, test } from "node:test";
import assert from "node:assert/strict";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDoc, writeBatch, serverTimestamp, setLogLevel } from "firebase/firestore";
setLogLevel("silent");
let environment;
const local = Date.now()-6*3600000;
const day = offset => new Date(local+offset*86400000).toISOString().slice(0,10);
const base = { id:"draw",ownerId:"coordinator",campsiteId:"primary",campsiteIds:["primary"],
  campsiteOwners:{},campsiteInvitations:{},numberOfWinners:3,useMaxCapacity:false,waitingListCapacity:0,
  registrationStartDate:day(-1),registrationEndDate:day(1),bookingStartDate:day(2),bookingEndDate:day(3),
  stayStartDate:day(10),stayEndDate:day(12),registered:["entrant"],registeredGroupSizes:{entrant:1},
  pending:[],booked:[],drawCompleted:false,listingRevision:0,cancelledEntrantIds:[] };
const db = uid => environment.authenticatedContext(uid).firestore();
const pending = { ...base,campsiteInvitations:{partner:"partner-owner"} };
const shared = { ...base,campsiteIds:["primary","partner"],campsiteOwners:{partner:"partner-owner"} };
before(async()=> { environment=await initializeTestEnvironment({projectId:"demo-shared-lottery",
  firestore:{host:"127.0.0.1",port:8085,rules:await readFile(new URL(".cache/test.rules",import.meta.url),"utf8")} }); });
after(async()=>{ await environment?.cleanup(); });
async function seed(entries) { await environment.withSecurityRulesDisabled(async ctx=>{
  for(const [path,data] of Object.entries(entries)) await setDoc(doc(ctx.firestore(),path),data);
}); }
beforeEach(async()=>{ await environment.clearFirestore(); await seed({"lotteries/draw":base,
  "campsites/primary":{id:"primary",ownerId:"coordinator",published:true,maxOccupancy:8},
  "campsites/partner":{id:"partner",ownerId:"partner-owner",published:true,maxOccupancy:4},
  "campsites/third":{id:"third",ownerId:"third-owner",published:true,maxOccupancy:4},
  ...Object.fromEntries(["coordinator","partner-owner","third-owner","entrant"].map(uid=>[`users/${uid}`,{isAdmin:false}]))}); });
function commit(uid, fields, notice=false, kind="updated", recipients=["entrant"]) {
  const client=db(uid),batch=writeBatch(client),revision=fields.listingRevision??1;
  if(kind==="deleted") batch.delete(doc(client,"lotteries/draw")); else batch.update(doc(client,"lotteries/draw"),fields);
  if(notice) batch.set(doc(client,`lotteryNotices/draw_${kind==="deleted"?"deleted":revision}`),{
    lotteryId:"draw",ownerId:uid,recipientIds:recipients,kind,title:"Lottery updated",message:"Changed",createdAt:serverTimestamp()});
  return batch.commit();
}
const inviteFields = {campsiteInvitations:{partner:"partner-owner"},collaborationAction:"invite",collaborationCampsiteId:"partner",listingRevision:1};
const acceptFields = {campsiteIds:["primary","partner"],campsiteOwners:{partner:"partner-owner"},campsiteInvitations:{},
  useMaxCapacity:false,collaborationAction:"accept",collaborationCampsiteId:"partner",listingRevision:1};
const withdrawFields = {campsiteIds:["primary"],campsiteOwners:{},numberOfWinners:3,useMaxCapacity:false,
  collaborationAction:"withdraw",collaborationCampsiteId:"partner",listingRevision:1};

test("coordinator can invite, but cannot add another owner's campsite without consent",async()=>{
  await assertSucceeds(commit("coordinator",inviteFields));
  assert.deepEqual((await getDoc(doc(db("entrant"),"lotteries/draw"))).data().campsiteIds,["primary"]);
  await assertFails(commit("coordinator",{...acceptFields,listingRevision:2},true));
});

test("existing single-campsite documents can invite and accept without migrating default fields",async()=>{
  const {campsiteIds,campsiteOwners,campsiteInvitations,...legacy}=base;
  await seed({"lotteries/draw":legacy});
  await assertSucceeds(commit("coordinator",inviteFields));
  await assertSucceeds(commit("partner-owner",{...acceptFields,listingRevision:2},true));
  assert.deepEqual((await getDoc(doc(db("entrant"),"lotteries/draw"))).data().campsiteIds,["primary","partner"]);
});
test("unrelated owners cannot invite or accept",async()=>{
  await assertFails(commit("third-owner",inviteFields));
  await seed({"lotteries/draw":pending});
  await assertFails(commit("third-owner",acceptFields,true));
});
test("only the invited current owner can accept with an atomic entrant notice",async()=>{
  await seed({"lotteries/draw":pending});
  await assertFails(commit("partner-owner",acceptFields));
  await assertSucceeds(commit("partner-owner",acceptFields,true));
  assert.deepEqual((await getDoc(doc(db("entrant"),"lotteries/draw"))).data().campsiteIds,["primary","partner"]);
  await assertSucceeds(getDoc(doc(db("entrant"),"lotteryNotices/draw_1")));
});
test("uninvited, transferred and already registered owners cannot accept",async()=>{
  await assertFails(commit("partner-owner",acceptFields,true));
  await seed({"lotteries/draw":pending,"campsites/partner":{ownerId:"third-owner",published:true,maxOccupancy:4}});
  await assertFails(commit("partner-owner",acceptFields,true));
  await seed({"campsites/partner":{ownerId:"partner-owner",published:true,maxOccupancy:4},
    "lotteries/draw":{...pending,registered:["entrant","partner-owner"]}});
  await assertFails(commit("partner-owner",acceptFields,true,"updated",["entrant","partner-owner"]));
});
test("owners can decline only their invitation; coordinator may cancel a pending invitation",async()=>{
  await seed({"lotteries/draw":pending});
  const fields={campsiteInvitations:{},collaborationAction:"decline",collaborationCampsiteId:"partner",listingRevision:1};
  await assertFails(commit("third-owner",fields));
  await assertSucceeds(commit("partner-owner",fields));
  await seed({"lotteries/draw":pending});
  await assertSucceeds(commit("coordinator",fields));
});
test("a contributor can withdraw their own campsite, preserving other campsites",async()=>{
  await seed({"lotteries/draw":shared});
  await assertFails(commit("coordinator",withdrawFields,true));
  await assertFails(commit("third-owner",withdrawFields,true));
  await assertSucceeds(commit("partner-owner",withdrawFields,true));
});
test("withdrawal clamps winner count to the remaining capacity",async()=>{
  await seed({"lotteries/draw":{...shared,numberOfWinners:10}});
  await assertFails(commit("partner-owner",withdrawFields,true));
  await assertSucceeds(commit("partner-owner",{...withdrawFields,numberOfWinners:8,useMaxCapacity:true},true));
});
test("collaborators cannot alter common dates or another campsite through acceptance",async()=>{
  await seed({"lotteries/draw":pending});
  await assertFails(commit("partner-owner",{...acceptFields,stayEndDate:day(14)},true));
  await assertFails(commit("partner-owner",{...acceptFields,campsiteIds:["primary","partner","third"],
    campsiteOwners:{partner:"partner-owner",third:"third-owner"}},true));
});
test("coordinator edits retain contributions or require consent again for changed terms",async()=>{
  await seed({"lotteries/draw":shared});
  await assertFails(commit("coordinator",{stayEndDate:day(14),listingRevision:1},true));
  await assertSucceeds(commit("coordinator",{stayEndDate:day(14),campsiteIds:["primary"],campsiteOwners:{primary:"coordinator"},
    campsiteInvitations:{partner:"partner-owner"},listingRevision:1},true));
});
test("ordinary edits cannot silently remove a contributor or change ownership grants",async()=>{
  await seed({"lotteries/draw":shared});
  await assertFails(commit("coordinator",{campsiteIds:["primary"],campsiteOwners:{},listingRevision:1},true));
  await assertFails(commit("coordinator",{campsiteOwners:{partner:"coordinator"},listingRevision:1},true));
  await assertSucceeds(commit("coordinator",{waitingListCapacity:20,listingRevision:1},true));
});
test("shared lotteries cannot be deleted by a contributor or while other owners remain",async()=>{
  await seed({"lotteries/draw":shared});
  await assertFails(commit("partner-owner",{},true,"deleted"));
  await assertFails(commit("coordinator",{},true,"deleted"));
  await seed({"lotteries/draw":base});
  await assertSucceeds(commit("coordinator",{},true,"deleted"));
});
test("drawn lotteries reject late membership changes and stale acceptance",async()=>{
  await seed({"lotteries/draw":{...pending,drawCompleted:true}});
  await assertFails(commit("partner-owner",acceptFields,true));
  await seed({"lotteries/draw":{...pending,listingRevision:3}});
  await assertFails(commit("partner-owner",acceptFields,true));
});
test("accepted owners cannot join, but entrants can enter for a contributor's larger campsite",async()=>{
  await seed({"lotteries/draw":shared,"campsites/primary":{ownerId:"coordinator",published:true,maxOccupancy:1}});
  const fields=uid=>({registered:["entrant",uid],registeredGroupSizes:{entrant:1,[uid]:2}});
  await assertFails(commit("partner-owner",fields("partner-owner")));
  await assertSucceeds(commit("third-owner",fields("third-owner")));
});
test("contributors can reject only verified reservations for their own campsite",async()=>{
  const booking={id:"booking",entrantId:"entrant",campsiteId:"partner",lotteryId:"draw",campsiteName:"Partner",checkInDate:day(10),checkOutDate:day(12),status:"Confirmed"};
  await seed({"lotteries/draw":{...shared,drawCompleted:true,booked:[booking]},"reservations/booking":booking});
  for(const uid of ["coordinator","partner-owner"]) {
    const client=db(uid),batch=writeBatch(client);
    batch.update(doc(client,"reservations/booking"),{status:"Rejected"});
    batch.update(doc(client,"lotteries/draw"),{cancelledEntrantIds:["entrant"],rejectedReservationId:"booking"});
    if(uid==="coordinator") await assertFails(batch.commit()); else await assertSucceeds(batch.commit());
  }
});
test("acceptance survives the eight-campsite access-budget boundary",async()=>{
  const ids=["primary","two","three","four","five","six","seven"];
  await seed({...Object.fromEntries(ids.slice(1).map(id=>[`campsites/${id}`,{ownerId:"coordinator",published:true,maxOccupancy:8}])),
    "lotteries/draw":{...pending,campsiteIds:ids}});
  await assertSucceeds(commit("partner-owner",{...acceptFields,campsiteIds:[...ids,"partner"]},true));
});

test("eight owners can retain consent on a waitlist edit",async()=>{
  const extras=["two","three","four","five","six","seven","eight"];
  const owners=Object.fromEntries(extras.map(id=>[id,`owner-${id}`]));
  const state={...base,campsiteIds:["primary",...extras],campsiteOwners:owners};
  await seed({...Object.fromEntries(extras.map(id=>[`campsites/${id}`,{ownerId:owners[id],published:true,maxOccupancy:4}])),"lotteries/draw":state});
  await assertSucceeds(commit("coordinator",{waitingListCapacity:20,listingRevision:1},true));
});

test("seven contributing owners must reapprove changed dates within the expression budget",async()=>{
  const extras=["two","three","four","five","six","seven","eight"];
  const owners=Object.fromEntries(extras.map(id=>[id,`owner-${id}`]));
  await seed({...Object.fromEntries(extras.map(id=>[`campsites/${id}`,{ownerId:owners[id],published:true,maxOccupancy:4}])),
    "lotteries/draw":{...base,campsiteIds:["primary",...extras],campsiteOwners:owners}});
  await assertSucceeds(commit("coordinator",{stayEndDate:day(14),campsiteIds:["primary"],campsiteOwners:{primary:"coordinator"},
    campsiteInvitations:owners,listingRevision:1},true));
});

test("eight-site membership preserves all other owners on withdrawal",async()=>{
  const extras=["two","three","four","five","six","seven"];
  const ids=["primary",...extras,"partner"];
  await seed({...Object.fromEntries(extras.map(id=>[`campsites/${id}`,{ownerId:`owner-${id}`,published:true,maxOccupancy:4}])),
    "lotteries/draw":{...base,campsiteIds:ids,campsiteOwners:{...Object.fromEntries(extras.map(id=>[id,`owner-${id}`])),partner:"partner-owner"}}});
  await assertSucceeds(commit("partner-owner",{...withdrawFields,campsiteIds:ids.filter(id=>id!=="partner"),
    campsiteOwners:Object.fromEntries(extras.map(id=>[id,`owner-${id}`]))},true));
});

test("creator cannot forge consent for a transferred or newly added foreign campsite",async()=>{
  await seed({"campsites/primary":{ownerId:"partner-owner",published:true,maxOccupancy:8}});
  await assertFails(commit("coordinator",{campsiteOwners:{primary:"partner-owner"},listingRevision:1},true));
  await seed({"campsites/primary":{ownerId:"coordinator",published:true,maxOccupancy:8}});
  await assertFails(commit("coordinator",{campsiteIds:["primary","partner"],campsiteOwners:{partner:"coordinator"},listingRevision:1},true));
});

test("single-owner creation still supports eight campsites without invitation grants",async()=>{
  const ids=["primary","two","three","four","five","six","seven","eight"];
  await seed(Object.fromEntries(ids.slice(1).map(id=>[`campsites/${id}`,{ownerId:"coordinator",published:true,maxOccupancy:8}])));
  await assertSucceeds(setDoc(doc(db("coordinator"),"lotteries/new"),{...base,id:"new",campsiteIds:ids,registered:[],registeredGroupSizes:{}}));
});
