/* Trusted control UI. Online pages live in a separate native WebView. */
(function(){
  if(!window.Stride || typeof Stride.showMedia!=='function')return;
  var open=false,expanded=false,mode='welcome',last=null;
  var launch=document.createElement('button');launch.id='nativeMediaLaunch';launch.textContent='YOUTUBE';
  launch.onclick=function(){Stride.showMedia();};document.body.appendChild(launch);
  var placeholder=document.createElement('div');placeholder.id='nativeMediaSpace';document.body.appendChild(placeholder);
  var values=document.createElement('div');values.id='nativeMediaValues';
  ['SPEED','INCLINE','ELAPSED','DISTANCE'].forEach(function(label){
    var item=document.createElement('div'),key=document.createElement('span'),value=document.createElement('b');
    key.textContent=label;item.appendChild(key);item.appendChild(value);values.appendChild(item);
  });
  var restore=document.createElement('button');restore.textContent='Show search';restore.id='nativeMediaRestore';
  restore.onclick=function(){Stride.restoreMedia();};values.appendChild(restore);document.body.appendChild(values);
  var idle=document.createElement('button');idle.id='nativeMediaIdleClose';idle.textContent='BACK TO WORKOUT SETUP';
  idle.onclick=function(){Stride.closeMedia();};document.body.appendChild(idle);
  function layout(){
    document.body.classList.toggle('native-media-open',open);
    document.body.classList.toggle('native-media-expanded',open&&expanded);
    document.body.classList.toggle('native-media-idle',mode==='welcome');
    launch.style.display=open||mode==='summary'?'none':'block';
    if(!open){document.body.style.transform='';return;}
    var scale=Math.min(innerWidth/1280,innerHeight/800);
    document.body.style.transform='translate('+((innerWidth-1280*scale)/2)+'px,0px) scale('+scale+')';
    var rect=placeholder.getBoundingClientRect();
    Stride.mediaBounds(rect.left/innerWidth,rect.top/innerHeight,rect.width/innerWidth,rect.height/innerHeight);
  }
  window.strideMediaState=function(on,wide){open=on;expanded=wide;layout();};
  window.addEventListener('resize',layout);
  var render=window.render;
  window.render=function(state){
    render(state);var changed=mode!==state.mode;mode=state.mode;
    if(last!==open+':'+expanded+':'+mode||changed){last=open+':'+expanded+':'+mode;layout();}
    var cells=values.querySelectorAll('b');
    cells[0].textContent=STRIDE.speed(state.speed||0)+' '+STRIDE.speedUnit(false);
    cells[1].textContent=(state.incline||0).toFixed(1)+'%';
    cells[2].textContent=STRIDE.mmss(state.elapsed||0);
    cells[3].textContent=STRIDE.distanceLabel(state.distance||0);
  };
})();
