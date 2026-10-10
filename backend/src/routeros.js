import net from "net";
import tls from "tls";

function encodeLength(n) {
  if (n < 0x80) return Buffer.from([n]);
  if (n < 0x4000) return Buffer.from([(n >> 8) | 0x80, n & 0xff]);
  if (n < 0x200000) return Buffer.from([(n >> 16) | 0xC0, (n >> 8) & 0xff, n & 0xff]);
  if (n < 0x10000000) return Buffer.from([(n >> 24) | 0xE0, (n >> 16)&255, (n >> 8)&255, n&255]);
  return Buffer.from([0xF0, ...Buffer.from(new Uint32Array([n]).buffer).reverse()]);
}

function sentence(words) {
  const chunks = [];
  for (const w of words) {
    const b = Buffer.from(w, "utf8");
    chunks.push(encodeLength(b.length), b);
  }
  chunks.push(Buffer.from([0]));
  return Buffer.concat(chunks);
}

export class RouterOS {
  constructor({host, port=8728, username, password, tls=false, ca, allowInsecureTls=false, timeout=10000, lookup, pinSha256}) {
    this.host=host; this.port=port; this.username=username; this.password=password;
    this.tls=tls; this.ca=ca; this.allowInsecureTls=allowInsecureTls; this.timeout=timeout; this.socket=null; this.buffer=Buffer.alloc(0);
    this.lookup=lookup||undefined;
    this.pinSha256=pinSha256?String(pinSha256).replace(/[^0-9a-fA-F]/g,"").toLowerCase():"";
  }
  async connect() {
    this.socket = await new Promise((resolve,reject)=>{
      const pin=this.pinSha256;
      // With a pinned fingerprint (router self-signed certificate) the chain check is replaced by the pin.
      const s = (this.tls ? tls.connect({host:this.host,port:this.port,ca:this.ca || undefined,lookup:this.lookup,rejectUnauthorized:pin?false:!this.allowInsecureTls,servername:net.isIP(this.host)?undefined:this.host})
                          : net.createConnection({host:this.host,port:this.port,lookup:this.lookup}));
      const timer=setTimeout(()=>{s.destroy();reject(new Error("TIMEOUT"))},this.timeout);
      s.once(this.tls?"secureConnect":"connect",()=>{
        clearTimeout(timer);
        if(this.tls&&pin){
          const fp=String(s.getPeerCertificate()?.fingerprint256||"").replace(/:/g,"").toLowerCase();
          if(fp!==pin){s.destroy();return reject(Object.assign(new Error("CERT_PIN_MISMATCH"),{code:"CERT_PIN_MISMATCH"}));}
        }
        resolve(s);
      });
      s.once("error",e=>{clearTimeout(timer);reject(e)});
    });
    await this.write(sentence(["/login"]));
    let reply=await this.readSentenceSet();
    const challengeWord=reply.find(x=>x.startsWith("=ret="));
    if (challengeWord) {
      const challenge=challengeWord.slice(5);
      const crypto=await import("crypto");
      const md5=crypto.createHash("md5").update(Buffer.concat([Buffer.from([0]),Buffer.from(this.password),Buffer.from(challenge,"hex")])).digest("hex");
      await this.write(sentence(["/login",`=name=${this.username}`,`=response=00${md5}`]));
      reply=await this.readSentenceSet();
    } else {
      await this.write(sentence(["/login",`=name=${this.username}`,`=password=${this.password}`]));
      reply=await this.readSentenceSet();
    }
    if (reply.some(x=>x.startsWith("!trap"))) {
      const err=new Error("AUTH_FAILED"); err.code="AUTH_FAILED";
      err.routerMessage=String((reply.find(x=>x.startsWith("=message="))||"").slice(9)).slice(0,160);
      throw err;
    }
  }
  async write(buf) {
    return new Promise((resolve,reject)=>{
      this.socket.write(buf,e=>e?reject(e):resolve());
    });
  }
  async readSentenceSet() {
    const sentences=[];
    let words=[];
    while(true) {
      const s=await this.readSentence();
      if (s.length===0) continue;
      words.push(...s);
      if (s[0]==="!done" || s[0]==="!trap" || s[0]==="!fatal") {
        sentences.push(words); break;
      }
    }
    return sentences.flat();
  }
  async readSentence() {
    const words=[];
    while(true) {
      const len=await this.readLen();
      if (len===0) return words;
      const data=await this.readBytes(len);
      words.push(data.toString("utf8"));
    }
  }
  async readLen() {
    const b=(await this.readBytes(1))[0];
    if ((b&0x80)===0) return b;
    if ((b&0xC0)===0x80) return ((b&0x3F)<<8)|(await this.readBytes(1))[0];
    if ((b&0xE0)===0xC0) return ((b&0x1F)<<16)|((await this.readBytes(1))[0]<<8)|(await this.readBytes(1))[0];
    if ((b&0xF0)===0xE0) return ((b&0x0F)<<24)|((await this.readBytes(1))[0]<<16)|((await this.readBytes(1))[0]<<8)|(await this.readBytes(1))[0];
    throw new Error("INVALID_LENGTH");
  }
  async readBytes(n) {
    while(this.buffer.length<n) {
      const chunk=await new Promise((resolve,reject)=>{
        const sock=this.socket;
        if(!sock||sock.destroyed)return reject(Object.assign(new Error("CONNECTION_CLOSED"),{code:"CONNECTION_CLOSED"}));
        // A silent or closed router must fail fast instead of hanging the HTTP request forever.
        const timer=setTimeout(()=>{cleanup();try{sock.destroy();}catch{}reject(Object.assign(new Error("TIMEOUT"),{code:"TIMEOUT"}));},this.timeout);
        const onData=d=>{cleanup();resolve(d)};
        const onErr=e=>{cleanup();reject(e)};
        const onEnd=()=>{cleanup();reject(Object.assign(new Error("CONNECTION_CLOSED"),{code:"CONNECTION_CLOSED"}))};
        const cleanup=()=>{clearTimeout(timer);sock.off("data",onData);sock.off("error",onErr);sock.off("end",onEnd);sock.off("close",onEnd)};
        sock.once("data",onData); sock.once("error",onErr); sock.once("end",onEnd); sock.once("close",onEnd);
      });
      this.buffer=Buffer.concat([this.buffer,chunk]);
    }
    const out=this.buffer.subarray(0,n); this.buffer=this.buffer.subarray(n); return out;
  }
  async command(path, args=[]) {
    if(!this.socket) await this.connect();
    await this.write(sentence([path,...args]));
    const out=[];
    while(true) {
      const words=await this.readSentence();
      const obj={};
      for(const w of words) {
        if(w.startsWith("=")) {
          const i=w.indexOf("=",1); if(i>1) obj[w.slice(1,i)]=w.slice(i+1);
        }
      }
      if(words[0]==="!re") out.push(obj);
      if(words[0]==="!trap" || words[0]==="!fatal") throw new Error(obj.message || words[0]);
      if(words[0]==="!done") return out;
    }
  }
  async close() { try { this.socket?.end(); } catch {} this.socket=null; }
}
