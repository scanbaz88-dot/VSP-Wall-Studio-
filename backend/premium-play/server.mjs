/**
 * VSP Wall Studio — trusted Google Play Lifetime Premium receipt verifier.
 * Deploy this as a PRIVATE BACKEND before enabling production checkout.
 *
 * POST /verify  { purchaseToken: string, productId: "vsp_premium_lifetime" }
 * Response      { entitled: boolean, productId, status: "PURCHASED" | "..."}
 *
 * Authentication comes from Google's purchase token, checked with the
 * Android Publisher API using server credentials. No Google key is ever
 * sent to Android. Avoid logging purchase tokens.
 */
import http from "node:http";
import { GoogleAuth } from "google-auth-library";

const packageName = "com.vintagesoulprint.wallmuse";
const productId = "vsp_premium_lifetime";
const google = new GoogleAuth({ scopes:["https://www.googleapis.com/auth/androidpublisher"] });
const port = Number(process.env.PORT || 8080);
const readMax = 8_192;
function send(res,status,obj){
  res.writeHead(status,{"content-type":"application/json","cache-control":"no-store","x-content-type-options":"nosniff"});
  res.end(JSON.stringify(obj));
}
async function googleRequest(path,method="GET"){
  const auth = await google.getClient();
  const url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"+encodeURIComponent(packageName)+path;
  const response = await auth.request({ url, method, timeout:12_000 });
  return response.data;
}
function verifyGoogleProduct(data){
  if(data?.purchaseStateContext?.purchaseState !== "PURCHASED")return false;
  if(!data.purchaseCompletionTime)return false;
  const target=(data.productLineItem||[]).find(line=>line.productId===productId);
  if(!target)return false;
  if(target.productOfferDetails?.consumptionState==="CONSUMPTION_STATE_CONSUMED")return false;
  // A partially refunded quantity must not unlock a full non-consumable item.
  if(target.productOfferDetails?.refundableQuantity===0)return false;
  return true;
}
http.createServer(async(req,res)=>{
  if(req.method!=="POST"||req.url!=="/verify"){
    send(res,404,{entitled:false,status:"NOT_FOUND"});return;
  }
  let raw="";
  try{
    for await(const chunk of req){
      raw+=chunk.toString();
      if(raw.length>readMax)throw new Error("payload too large");
    }
    const body=JSON.parse(raw);
    const token=body.purchaseToken;
    if(typeof token!=="string"||token.length<20||token.length>2048 ||
       body.productId!==productId || body.packageName!==packageName){
      send(res,400,{entitled:false,status:"INVALID_REQUEST"});return;
    }
    // Always recheck Google. No cached user-supplied 'premium' boolean.
    const purchase=await googleRequest("/purchases/productsv2/tokens/"+encodeURIComponent(token));
    if(!verifyGoogleProduct(purchase)){
      send(res,200,{entitled:false,productId,status:"NOT_PURCHASED"});return;
    }
    if(purchase.acknowledgementState!=="ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"){
      // Call official server-side acknowledgement only after Play verifies purchase.
      await googleRequest("/purchases/products/"+encodeURIComponent(productId)+
          "/tokens/"+encodeURIComponent(token)+":acknowledge","POST");
    }
    // For production hardening additionally integrate RTDN and voided purchases
    // reconciliation, rate limiting, account/device binding if identities exist.
    send(res,200,{entitled:true,productId,status:"PURCHASED"});
  }catch(e){
    // Do not expose secrets or token; Play API failures must fail closed.
    console.error("Google Play purchase verification failed:",e?.status||e?.code||"unavailable");
    send(res,503,{entitled:false,status:"VERIFICATION_UNAVAILABLE"});
  }
}).listen(port,()=>console.log("VSP Play Premium verifier listening"));
