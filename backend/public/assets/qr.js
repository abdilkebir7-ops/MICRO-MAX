/* Minimal QR Code generator (byte mode, ECC level M, versions 1-30). Browser + Node. */
(function(root){
  var EPB=[-1,10,16,26,18,24,16,18,22,22,26,30,22,22,24,24,28,28,26,26,26,26,28,28,28,28,28,28,28,28,28];
  var NB=[-1,1,1,1,2,2,4,4,4,5,5,5,8,9,9,10,10,11,13,14,16,17,17,18,20,21,23,25,26,28,29];
  function rawModules(v){var r=(16*v+128)*v+64;if(v>=2){var n=Math.floor(v/7)+2;r-=(25*n-10)*n-55;if(v>=7)r-=36;}return r;}
  function dataCw(v){return Math.floor(rawModules(v)/8)-EPB[v]*NB[v];}
  function rsMul(x,y){var z=0;for(var i=7;i>=0;i--){z=(z<<1)^((z>>>7)*0x11D);z^=((y>>>i)&1)*x;}return z&255;}
  function rsGen(d){var r=[];for(var i=0;i<d-1;i++)r.push(0);r.push(1);var root=1;for(var i=0;i<d;i++){for(var j=0;j<r.length;j++){r[j]=rsMul(r[j],root);if(j+1<r.length)r[j]^=r[j+1];}root=rsMul(root,2);}return r;}
  function rsRem(data,gen){var r=gen.map(function(){return 0;});data.forEach(function(b){var f=b^r.shift();r.push(0);gen.forEach(function(c,i){r[i]^=rsMul(c,f);});});return r;}
  function utf8(s){return Array.from(new TextEncoder().encode(s));}
  function encode(text){
    var bytes=utf8(String(text)),ver=0;
    for(var v=1;v<=30;v++){var cc=v<10?8:16;if(4+cc+bytes.length*8<=dataCw(v)*8){ver=v;break;}}
    if(!ver)throw new Error("QR_TOO_LONG");
    var bits=[];function put(val,len){for(var i=len-1;i>=0;i--)bits.push((val>>>i)&1);}
    put(4,4);put(bytes.length,ver<10?8:16);bytes.forEach(function(b){put(b,8);});
    var cap=dataCw(ver)*8;put(0,Math.min(4,cap-bits.length));put(0,(8-bits.length%8)%8);
    for(var pad=0xEC;bits.length<cap;pad^=0xEC^0x11)put(pad,8);
    var data=[];for(var i=0;i<bits.length;i+=8){var b=0;for(var k=0;k<8;k++)b=(b<<1)|bits[i+k];data.push(b);}
    var nb=NB[ver],el=EPB[ver],raw=Math.floor(rawModules(ver)/8),ns=nb-raw%nb,sl=Math.floor(raw/nb),gen=rsGen(el),blocks=[],k=0;
    for(var i=0;i<nb;i++){var dat=data.slice(k,k+sl-el+(i<ns?0:1));k+=dat.length;var e=rsRem(dat,gen);if(i<ns)dat.push(0);blocks.push(dat.concat(e));}
    var all=[];for(var i=0;i<blocks[0].length;i++)blocks.forEach(function(bl,j){if(i!==sl-el||j>=ns)all.push(bl[i]);});
    var size=ver*4+17,M=[],F=[];for(var y=0;y<size;y++){M.push(new Array(size).fill(false));F.push(new Array(size).fill(false));}
    function sf(x,y,d){M[y][x]=!!d;F[y][x]=true;}
    for(var i=0;i<size;i++){sf(6,i,i%2===0);sf(i,6,i%2===0);}
    function finder(cx,cy){for(var dy=-4;dy<=4;dy++)for(var dx=-4;dx<=4;dx++){var d=Math.max(Math.abs(dx),Math.abs(dy)),x=cx+dx,y=cy+dy;if(x>=0&&x<size&&y>=0&&y<size)sf(x,y,d!==2&&d!==4);}}
    finder(3,3);finder(size-4,3);finder(3,size-4);
    var pos=[];if(ver>1){var na=Math.floor(ver/7)+2,step=ver===32?26:Math.ceil((ver*4+4)/(na*2-2))*2;pos=[6];for(var p=size-7;pos.length<na;p-=step)pos.splice(1,0,p);}
    pos.forEach(function(a,i){pos.forEach(function(b,j){if((i===0&&j===0)||(i===0&&j===pos.length-1)||(i===pos.length-1&&j===0))return;for(var dy=-2;dy<=2;dy++)for(var dx=-2;dx<=2;dx++)sf(a+dx,b+dy,Math.max(Math.abs(dx),Math.abs(dy))!==1);});});
    function fmt(mask){var d=mask,r=d;for(var i=0;i<10;i++)r=(r<<1)^((r>>>9)*0x537);var bs=((d<<10)|r)^0x5412;function g(i){return((bs>>>i)&1)!==0;}
      for(var i=0;i<=5;i++)sf(8,i,g(i));sf(8,7,g(6));sf(8,8,g(7));sf(7,8,g(8));for(var i=9;i<15;i++)sf(14-i,8,g(i));
      for(var i=0;i<8;i++)sf(size-1-i,8,g(i));for(var i=8;i<15;i++)sf(8,size-15+i,g(i));sf(8,size-8,true);}
    fmt(0);
    if(ver>=7){var r=ver;for(var i=0;i<12;i++)r=(r<<1)^((r>>>11)*0x1F25);var vb=(ver<<12)|r;for(var i=0;i<18;i++){var bt=((vb>>>i)&1)!==0,a=size-11+i%3,b=Math.floor(i/3);sf(a,b,bt);sf(b,a,bt);}}
    var idx=0;for(var right=size-1;right>=1;right-=2){if(right===6)right=5;for(var vert=0;vert<size;vert++)for(var j=0;j<2;j++){var x=right-j,up=((right+1)&2)===0,y=up?size-1-vert:vert;if(!F[y][x]&&idx<all.length*8){M[y][x]=((all[idx>>>3]>>>(7-(idx&7)))&1)!==0;idx++;}}}
    function apply(m){for(var y=0;y<size;y++)for(var x=0;x<size;x++){var inv;switch(m){case 0:inv=(x+y)%2===0;break;case 1:inv=y%2===0;break;case 2:inv=x%3===0;break;case 3:inv=(x+y)%3===0;break;case 4:inv=(Math.floor(x/3)+Math.floor(y/2))%2===0;break;case 5:inv=x*y%2+x*y%3===0;break;case 6:inv=(x*y%2+x*y%3)%2===0;break;default:inv=((x+y)%2+x*y%3)%2===0;}if(!F[y][x]&&inv)M[y][x]=!M[y][x];}}
    function penalty(){var p=0,dark=0;
      for(var y=0;y<size;y++){var run=1;for(var x=1;x<size;x++){if(M[y][x]===M[y][x-1]){run++;if(run===5)p+=3;else if(run>5)p++;}else run=1;}}
      for(var x=0;x<size;x++){var run=1;for(var y=1;y<size;y++){if(M[y][x]===M[y-1][x]){run++;if(run===5)p+=3;else if(run>5)p++;}else run=1;}}
      for(var y=0;y<size-1;y++)for(var x=0;x<size-1;x++){var c=M[y][x];if(c===M[y][x+1]&&c===M[y+1][x]&&c===M[y+1][x+1])p+=3;}
      var pat=[true,false,true,true,true,false,true,false,false,false,false];
      function chk(get){for(var a=0;a<size;a++)for(var b=0;b<=size-11;b++){var m1=true,m2=true;for(var t=0;t<11;t++){var v=get(a,b+t);if(v!==pat[t])m1=false;if(v!==pat[10-t])m2=false;}if(m1||m2)p+=40;}}
      chk(function(a,b){return M[a][b];});chk(function(a,b){return M[b][a];});
      for(var y=0;y<size;y++)for(var x=0;x<size;x++)if(M[y][x])dark++;
      p+=Math.floor(Math.abs(dark*20-size*size*10)/(size*size))*10;return p;}
    var best=0,bp=1e9;for(var m=0;m<8;m++){apply(m);fmt(m);var pn=penalty();if(pn<bp){bp=pn;best=m;}apply(m);}
    apply(best);fmt(best);
    return {size:size,version:ver,get:function(x,y){return M[y][x];}};
  }
  function toSvg(text,o){o=o||{};var q=encode(text),m=o.margin==null?4:o.margin,n=q.size+2*m,d="";
    for(var y=0;y<q.size;y++){var x=0;while(x<q.size){if(q.get(x,y)){var s=x;while(x<q.size&&q.get(x,y))x++;d+="M"+(s+m)+" "+(y+m)+"h"+(x-s)+"v1h-"+(x-s)+"z";}else x++;}}
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 '+n+' '+n+'" shape-rendering="crispEdges"'+(o.size?' width="'+o.size+'" height="'+o.size+'"':'')+'><rect width="100%" height="100%" fill="'+(o.bg||"#fff")+'"/><path d="'+d+'" fill="'+(o.fg||"#000")+'"/></svg>';}
  var api={encode:encode,toSvg:toSvg};
  if(typeof module!=="undefined"&&module.exports)module.exports=api;else root.MMQR=api;
})(typeof window!=="undefined"?window:globalThis);
