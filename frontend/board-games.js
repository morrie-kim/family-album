(() => {
  "use strict";
  const app=()=>document.getElementById("app");
  const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  const shell=(title,content)=>{app().innerHTML='<header class="topbar"><a class="brand" href="#photos">▧ 가족앨범</a><nav><a href="#photos">사진</a><a href="#family">가족</a><a href="#worldcups">월드컵</a><a href="#google">구글 포토</a><a href="#contents">콘텐츠 찾기</a><a class="on" href="#minigames">미니게임</a><a href="#utilities">유틸</a></nav><span class="user">'+esc(window.__familyUserName||"")+'</span></header><main class="wrap"><div class="pagehead"><h1>'+title+'</h1></div>'+content+'</main><nav class="mobile-nav"><a href="#photos">사진</a><a href="#family">가족</a><a href="#worldcups">월드컵</a><a href="#google">구글</a><a href="#contents">콘텐츠</a><a href="#minigames">게임</a><a href="#utilities">유틸</a></nav>';};
  const menu='<div class="minigame-menu"><a class="minigame-card" href="#tetris"><span class="minigame-icon">🧱</span><h2>테트리스</h2><p>블록을 회전하고 쌓아 줄을 완성하세요.</p><span class="btn secondary">플레이</span></a><a class="minigame-card" href="#janggi"><span class="minigame-icon">♟️</span><h2>장기</h2><p>초와 한이 번갈아 두는 2인 전략 보드게임.</p><span class="btn secondary">플레이</span></a><a class="minigame-card" href="#omok"><span class="minigame-icon">⚫</span><h2>오목</h2><p>흑과 백이 번갈아 두어 먼저 다섯 알을 연결하세요.</p><span class="btn secondary">플레이</span></a></div>';
  window.renderMiniGames=()=>shell("미니게임",menu);

  const N=10,M=9;
  const palace=(r,c,side)=>side==="blue"?(r>=0&&r<=2&&c>=3&&c<=5):(r>=7&&r<=9&&c>=3&&c<=5);
  const inBoard=(r,c)=>r>=0&&r<N&&c>=0&&c<M;
  const dirs=[[1,0],[-1,0],[0,1],[0,-1]];
  const key=(r,c)=>r+","+c;
  const copyBoard=b=>b.map(row=>row.slice());
  const initial=()=>{
    const b=Array.from({length:N},()=>Array(M).fill(null));
    const back=["車","馬","象","士","將","士","象","馬","車"];
    back.forEach((p,c)=>{b[0][c]={side:"blue",type:p};b[9][c]={side:"red",type:p==="將"?"將":p};});
    [1,7].forEach(c=>{b[2][c]={side:"blue",type:"包"};b[7][c]={side:"red",type:"包"};});
    [0,2,4,6,8].forEach(c=>{b[3][c]={side:"blue",type:"卒"};b[6][c]={side:"red",type:"卒"};});
    return b;
  };
  function palaceLine(r,c,nr,nc){
    const blue=[[0,3],[1,4],[2,5],[0,5],[1,4],[2,3]];
    const red=[[7,3],[8,4],[9,5],[7,5],[8,4],[9,3]];
    const lines=[[[0,3],[1,4],[2,5]],[[0,5],[1,4],[2,3]],[[7,3],[8,4],[9,5]],[[7,5],[8,4],[9,3]]];
    return lines.some(line=>line.some(p=>p[0]===r&&p[1]===c)&&line.some(p=>p[0]===nr&&p[1]===nc)&&Math.abs(line.findIndex(p=>p[0]===r&&p[1]===c)-line.findIndex(p=>p[0]===nr&&p[1]===nc))===1);
  }
  function moves(b,r,c){
    const p=b[r][c];if(!p)return [];
    const out=[],add=(nr,nc)=>{if(!inBoard(nr,nc))return;const t=b[nr][nc];if(t?.side===p.side)return;out.push([nr,nc]);};
    if(p.type==="將"||p.type==="士"){
      for(const [dr,dc] of dirs){const nr=r+dr,nc=c+dc;if(palace(nr,nc,p.side))add(nr,nc);}
      for(let nr=0;nr<N;nr++)for(let nc=0;nc<M;nc++)if(palace(nr,nc,p.side)&&palaceLine(r,c,nr,nc))add(nr,nc);
    }else if(p.type==="車"){
      for(const [dr,dc] of dirs){let nr=r+dr,nc=c+dc;while(inBoard(nr,nc)){if(b[nr][nc]){add(nr,nc);break;}add(nr,nc);nr+=dr;nc+=dc;}}
      for(const line of [[[0,3],[1,4],[2,5]],[[0,5],[1,4],[2,3]],[[7,3],[8,4],[9,5]],[[7,5],[8,4],[9,3]]]){
        const i=line.findIndex(q=>q[0]===r&&q[1]===c);if(i<0)continue;
        for(const d of [-1,1]){let j=i+d;while(j>=0&&j<3){const [nr,nc]=line[j];if(b[nr][nc]){add(nr,nc);break;}add(nr,nc);j+=d;}}
      }
    }else if(p.type==="包"){
      for(const [dr,dc] of dirs){let nr=r+dr,nc=c+dc,screen=false;while(inBoard(nr,nc)){const t=b[nr][nc];if(!screen){if(t){if(t.type==="包")break;screen=true;}}else{if(t){if(t.side!==p.side&&t.type!=="包")add(nr,nc);break;}add(nr,nc);}nr+=dr;nc+=dc;}}
      for(const line of [[[0,3],[1,4],[2,5]],[[0,5],[1,4],[2,3]],[[7,3],[8,4],[9,5]],[[7,5],[8,4],[9,3]]]){
        const i=line.findIndex(q=>q[0]===r&&q[1]===c);if(i<0)continue;
        for(const d of [-1,1]){let j=i+d,screen=false;while(j>=0&&j<3){const [nr,nc]=line[j],t=b[nr][nc];if(!screen){if(t){if(t.type==="包")break;screen=true;}}else{if(t){if(t.side!==p.side&&t.type!=="包")add(nr,nc);break;}add(nr,nc);}j+=d;}}
      }
    }else if(p.type==="卒"){
      const forward=p.side==="blue"?1:-1;add(r+forward,c);add(r,c-1);add(r,c+1);
      const palaceRows=p.side==="blue"?[0,1,2]:[7,8,9];
      if(palaceRows.includes(r)&&c>=3&&c<=5)for(const [dr,dc] of [[forward,-1],[forward,1]])if(palace(r+dr,c+dc,p.side)&&palaceLine(r,c,r+dr,c+dc))add(r+dr,c+dc);
    }else if(p.type==="馬"){
      for(const [dr,dc] of [[-2,-1],[-2,1],[2,-1],[2,1],[-1,-2],[-1,2],[1,-2],[1,2]]){
        const br=r+(Math.abs(dr)===2?Math.sign(dr):0),bc=c+(Math.abs(dr)===2?0:Math.sign(dc));
        if(b[br]?.[bc])continue;add(r+dr,c+dc);
      }
    }else if(p.type==="象"){
      for(const [dr,dc] of [[-3,-2],[-3,2],[3,-2],[3,2],[-2,-3],[-2,3],[2,-3],[2,3]]){
        const s1r=r+(Math.abs(dr)===3?Math.sign(dr):0),s1c=c+(Math.abs(dr)===3?0:Math.sign(dc));
        const s2r=r+(Math.abs(dr)===3?2*Math.sign(dr):Math.sign(dr)),s2c=c+(Math.abs(dr)===3?Math.sign(dc):2*Math.sign(dc));
        if(b[s1r]?.[s1c]||b[s2r]?.[s2c])continue;add(r+dr,c+dc);
      }
    }
    return out;
  }
  window.renderJanggi=()=>{
    shell("장기",'<section class="panel board-game"><div class="board-game-toolbar"><div id="janggiStatus" class="board-game-status">초 차례 · 파란색</div><div class="board-game-actions"><button class="btn ghost" id="janggiMenu">게임 목록</button><button class="btn primary" id="janggiReset">새 게임</button></div></div><div class="janggi-wrap"><div id="janggiBoard" class="janggi-board" role="grid" aria-label="장기판"></div><p class="janggi-hint">파란색: 초 · 빨간색: 한 · 기물을 선택한 뒤 이동할 칸을 누르세요.</p></div><p class="board-game-help">기물 이동 규칙을 적용한 2인 로컬 대전입니다. 상대 장군을 잡으면 승리합니다.</p></section>');
    let board=initial(),turn="blue",selected=null,legal=[],ended=false;
    const root=document.getElementById("janggiBoard"),status=document.getElementById("janggiStatus");
    const labels={將:"楚",車:"車",馬:"馬",象:"象",士:"士",包:"包",卒:"卒"};
    function draw(){
      root.innerHTML="";
      for(let r=0;r<N;r++)for(let c=0;c<M;c++){
        const cell=document.createElement("div");cell.className="janggi-cell"+(selected&&selected[0]===r&&selected[1]===c?" selected":"")+(legal.some(q=>q[0]===r&&q[1]===c)?" legal":"");
        const btn=document.createElement("button");btn.type="button";btn.setAttribute("aria-label",r+"행 "+(c+1)+"열"+(board[r][c]?" "+labels[board[r][c].type]:""));
        const p=board[r][c];if(p){const stone=document.createElement("span");stone.className="janggi-piece "+p.side;stone.textContent=p.type==="將"?(p.side==="blue"?"楚":"漢"):labels[p.type];btn.appendChild(stone);}
        btn.onclick=()=>play(r,c);cell.appendChild(btn);root.appendChild(cell);
      }
      status.textContent=ended?(turn==="blue"?"초":"한")+" 승리 · 새 게임으로 다시 시작하세요.":(turn==="blue"?"초 차례 · 파란색":"한 차례 · 빨간색");
    }
    function play(r,c){
      if(ended)return;
      const p=board[r][c];
      if(selected&&legal.some(q=>q[0]===r&&q[1]===c)){
        const [sr,sc]=selected,captured=board[r][c];board[r][c]=board[sr][sc];board[sr][sc]=null;
        selected=null;legal=[];if(captured?.type==="將"){ended=true;}else turn=turn==="blue"?"red":"blue";draw();return;
      }
      if(p?.side===turn){selected=[r,c];legal=moves(board,r,c);draw();return;}
      selected=null;legal=[];draw();
    }
    document.getElementById("janggiReset").onclick=()=>{board=initial();turn="blue";selected=null;legal=[];ended=false;draw();};
    document.getElementById("janggiMenu").onclick=()=>location.hash="#minigames";
    draw();
  };

  window.renderOmok=()=>{
    shell("오목",'<section class="panel board-game"><div class="board-game-toolbar"><div id="omokStatus" class="board-game-status">흑 차례</div><div class="board-game-actions"><button class="btn ghost" id="omokUndo">한 수 무르기</button><button class="btn ghost" id="omokMenu">게임 목록</button><button class="btn primary" id="omokReset">새 게임</button></div></div><div class="omok-board-wrap"><div id="omokBoard" class="omok-board" role="grid" aria-label="15줄 오목판"></div></div><p class="board-game-help">흑돌부터 시작합니다. 가로·세로·대각선 중 한 방향으로 5개 이상을 먼저 연결하면 승리합니다. 무르기는 마지막 한 수를 취소합니다.</p></section>');
    const size=15,board=Array.from({length:size},()=>Array(size).fill(0)),history=[];
    let turn=1,ended=false,last=null;
    const root=document.getElementById("omokBoard"),status=document.getElementById("omokStatus");
    const win=(r,c,color)=>{
      for(const [dr,dc] of [[1,0],[0,1],[1,1],[1,-1]]){
        let count=1;
        for(const sign of [-1,1]){let nr=r+dr*sign,nc=c+dc*sign;while(nr>=0&&nr<size&&nc>=0&&nc<size&&board[nr][nc]===color){count++;nr+=dr*sign;nc+=dc*sign;}}
        if(count>=5)return true;
      }return false;
    };
    function draw(){
      root.innerHTML="";
      for(let r=0;r<size;r++)for(let c=0;c<size;c++){
        const btn=document.createElement("button");btn.type="button";btn.className="omok-cell"+(last&&last[0]===r&&last[1]===c?" last":"");btn.setAttribute("aria-label",(r+1)+"행 "+(c+1)+"열");
        if(board[r][c]){const stone=document.createElement("span");stone.className="omok-stone "+(board[r][c]===1?"black":"white");btn.appendChild(stone);}
        btn.disabled=ended||board[r][c]!==0;btn.onclick=()=>play(r,c);root.appendChild(btn);
      }
      status.textContent=ended?(turn===1?"흑":"백")+" 승리!":(turn===1?"흑 차례":"백 차례");
    }
    function play(r,c){if(ended||board[r][c])return;board[r][c]=turn;history.push([r,c,turn]);last=[r,c];if(win(r,c,turn))ended=true;else turn=3-turn;draw();}
    document.getElementById("omokUndo").onclick=()=>{if(!history.length)return;const [r,c,color]=history.pop();board[r][c]=0;turn=color;ended=false;last=history.length?[history[history.length-1][0],history[history.length-1][1]]:null;draw();};
    document.getElementById("omokReset").onclick=()=>{for(let r=0;r<size;r++)board[r].fill(0);history.length=0;turn=1;ended=false;last=null;draw();};
    document.getElementById("omokMenu").onclick=()=>location.hash="#minigames";
    draw();
  };
})();