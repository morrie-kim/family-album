(() => {
  "use strict";

  const API = (window.FAMILY_ALBUM_CONFIG?.apiBaseUrl || "").replace(/\/$/, "");
  const app = document.getElementById("app");
  const toastEl = document.getElementById("toast");
  let csrf = "";
  let csrfHeader = "X-XSRF-TOKEN";
  let me = null;
  let folders = [];
  let photos = [];
  let activeFolder = "";
  let selectedWorldcup = null;
  let currentMatch = null;

  const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({
    "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
  }[c]));
  let toastTimer;
  const toast = message => {
    toastEl.textContent = message;
    toastEl.classList.add("show");
    clearTimeout(toastTimer); toastTimer = setTimeout(() => toastEl.classList.remove("show"), 2800);
  };
  const cookie = name => document.cookie.split("; ").find(x => x.startsWith(name + "="))?.split("=").slice(1).join("=") || "";
  const getCsrf = async () => {
    const r = await fetch(API + "/api/auth/csrf", {credentials:"include"});
    if (!r.ok) throw new Error("보안 토큰을 가져오지 못했습니다.");
    const data = await r.json();
    csrf = data.token;
    csrfHeader = data.headerName;
  };
  async function request(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    if (options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
    if (!["GET","HEAD","OPTIONS"].includes(method)) {
      if (!csrf) await getCsrf();
      headers.set(csrfHeader, csrf);
    }
    const response = await fetch(API + path, {
      ...options, method, headers, credentials:"include"
    });
    if (response.status === 204) return null;
    const type = response.headers.get("content-type") || "";
    const data = type.includes("application/json") ? await response.json() : await response.text();
    if (!response.ok) {
      if (response.status === 401) {
        me = null;
        renderAuth();
      }
      throw new Error(data?.detail || data?.message || data?.error || (typeof data === "string" ? data : "") || "요청을 처리하지 못했습니다.");
    }
    return data;
  }
  const json = value => JSON.stringify(value);
  const go = route => { location.hash = route; };
  const route = () => (location.hash.slice(1) || "photos").split("/");
  const shell = (title, content, active = "photos") => {
    app.innerHTML = `
      <header class="topbar"><a class="brand" href="#photos">▧ 가족앨범</a>
        <nav><a class="${active==="photos"?"on":""}" href="#photos">사진</a><a class="${active==="family"?"on":""}" href="#family">가족</a><a class="${active==="worldcups"?"on":""}" href="#worldcups">월드컵</a><a class="${active==="google"?"on":""}" href="#google">구글 포토</a></nav>
        <span class="user">${esc(me?.name)}</span><button class="btn ghost" id="logout">로그아웃</button>
      </header><main class="wrap"><div class="pagehead"><h1>${title}</h1></div>${content}</main>
      <nav class="mobile-nav"><a href="#photos">사진</a><a href="#family">가족</a><a href="#worldcups">월드컵</a><a href="#google">구글</a></nav>`;
    document.getElementById("logout").onclick = logout;
  };
  function renderAuth(message = "") {
    app.innerHTML = `<main class="auth-card"><div class="logo">▧</div><h1>가족의 순간을<br>한곳에 모아요</h1><p class="muted">이메일로 로그인하고 가족 앨범을 열어 보세요.</p>
      ${message ? `<p class="error">${esc(message)}</p>` : ""}
      <form id="login" class="stack"><label>이메일<input name="email" type="email" autocomplete="username" required></label><label>비밀번호<input name="password" type="password" autocomplete="current-password" required></label><button class="btn primary">로그인</button></form>
      <details><summary>처음이세요? 회원가입</summary><form id="signup" class="stack">
        <label>이름<input name="name" required maxlength="50"></label><label>이메일<input name="email" type="email" required></label>
        <label>비밀번호<input name="password" type="password" minlength="8" maxlength="72" required></label>
        <label>가족 가입 방식<select name="mode" id="signupMode"><option value="new">새 가족 만들기</option><option value="join">초대 코드로 합류</option></select></label>
        <label id="familyNameField">가족 이름<input name="familyName" maxlength="100"></label>
        <label id="inviteField" hidden>초대 코드<input name="inviteCode" maxlength="16"></label>
        <button class="btn secondary">가입하기</button></form></details></main>`;
    document.getElementById("login").onsubmit = async e => {
      e.preventDefault(); const f = new FormData(e.currentTarget);
      try { await getCsrf(); await request("/api/auth/login",{method:"POST",body:json({email:f.get("email"),password:f.get("password")})}); await boot(); }
      catch(err){ renderAuth(err.message); }
    };
    document.getElementById("signupMode").onchange = e => {
      document.getElementById("familyNameField").hidden = e.target.value !== "new";
      document.getElementById("inviteField").hidden = e.target.value !== "join";
    };
    document.getElementById("signup").onsubmit = async e => {
      e.preventDefault(); const f = new FormData(e.currentTarget); const join = f.get("mode")==="join";
      try {
        await getCsrf();
        await request("/api/auth/signup",{method:"POST",body:json({
          name:f.get("name"),email:f.get("email"),password:f.get("password"),
          familyName:join?null:f.get("familyName"),inviteCode:join?f.get("inviteCode"):null
        })});
        await request("/api/auth/login",{method:"POST",body:json({email:f.get("email"),password:f.get("password")})});
        await boot();
      } catch(err){ renderAuth(err.message); }
    };
  }
  async function logout() {
    try { await request("/api/auth/logout",{method:"POST"}); me=null; csrf=""; renderAuth(); }
    catch(e){ toast(e.message); }
  }
  async function boot() {
    if (!API || API.includes("example.com")) { app.innerHTML='<main class="auth-card"><h1>API 주소 설정 필요</h1><p>frontend/config.js의 apiBaseUrl을 실제 api 도메인으로 변경하세요.</p></main>'; return; }
    try { await getCsrf(); me = await request("/api/auth/me"); await renderRoute(); }
    catch { me=null; renderAuth(); }
  }
  async function loadFolders() { folders = await request("/api/folders"); return folders; }
  async function renderPhotos(pageNo = 0) {
    await loadFolders();
    const qs = activeFolder ? "?folderId="+encodeURIComponent(activeFolder)+"&page="+pageNo+"&size=60" : "?page="+pageNo+"&size=60";
    const page = await request("/api/photos"+qs); photos = page.content || [];
    shell("우리 가족 사진",`
      <div class="toolbar"><select id="folderFilter"><option value="">전체 폴더</option>${folders.map(f=>`<option value="${f.id}" ${String(f.id)===activeFolder?"selected":""}>${esc(f.name)} (${f.photoCount})</option>`).join("")}</select>
      <button class="btn secondary" id="newFolder">새 폴더</button><label class="btn primary file-btn">사진 올리기<input id="upload" type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/heic" multiple></label></div>
      <p class="muted">${page.totalElements}장의 추억</p>
      <div class="photo-grid">${photos.map(p=>`<button class="photo" data-photo="${p.id}"><img loading="lazy" src="${API}/api/photos/${p.id}/file" alt="${esc(p.originalFilename)}"><span>${esc(p.uploaderName)}</span></button>`).join("")}</div>
      <div class="pager">${page.page>0?'<button id="prev" class="btn ghost">이전</button>':""} <span>${page.totalPages? page.page+1+" / "+page.totalPages:""}</span> ${page.page+1<page.totalPages?'<button id="next" class="btn ghost">다음</button>':""}</div>
      <dialog id="viewer"><button class="close" id="closeViewer">닫기</button><img id="viewerImage"><p id="viewerMeta"></p><button id="deletePhoto" class="btn danger">사진 삭제</button></dialog>`);
    document.getElementById("folderFilter").onchange=e=>{activeFolder=e.target.value;renderPhotos().catch(showError);};
    document.getElementById("newFolder").onclick=()=>go("folder/new");
    document.getElementById("upload").onchange=uploadPhotos;
    document.querySelectorAll("[data-photo]").forEach(b=>b.onclick=()=>openPhoto(b.dataset.photo));
    document.getElementById("closeViewer").onclick=()=>document.getElementById("viewer").close();
    document.getElementById("prev")?.addEventListener("click",()=>renderPhotos(Math.max(0,page.page-1)));
    document.getElementById("next")?.addEventListener("click",()=>renderPhotos(page.page+1));
  }
  async function uploadPhotos(e) {
    const files=[...e.target.files]; if(!files.length)return;
    const folderId=activeFolder || folders.find(f=>f.canEdit)?.id;
    if(!folderId){toast("업로드 권한이 있는 폴더가 없습니다.");return;}
    const description=prompt("사진 설명 (선택)")||"";
    let success=0, failures=[];
    for(let i=0;i<files.length;i+=8){
      const fd=new FormData(); files.slice(i,i+8).forEach(f=>fd.append("files",f,f.name));
      fd.append("folderId",folderId); if(description)fd.append("description",description);
      try {
        const result=await request("/api/photos/batch",{method:"POST",body:fd});
        success+=result.uploaded.length; failures.push(...result.failed);
      } catch(err){ failures.push({filename:files.slice(i,i+8).map(f=>f.name).join(", "),reason:err.message}); }
    }
    toast(success+"장 업로드"+(failures.length?", 실패 "+failures.length+"장":" 완료"));
    if(failures.length) alert(failures.map(f=>f.filename+" : "+f.reason).join("\n"));
    e.target.value=""; await renderPhotos();
  }
  function openPhoto(id) {
    const p=photos.find(x=>String(x.id)===String(id)); if(!p)return;
    const d=document.getElementById("viewer"); document.getElementById("viewerImage").src=API+"/api/photos/"+p.id+"/file";
    document.getElementById("viewerMeta").textContent=[p.originalFilename,p.uploaderName,p.description].filter(Boolean).join(" · ");
    const del=document.getElementById("deletePhoto"); del.hidden=!p.canDelete;
    del.onclick=async()=>{if(!confirm("이 사진을 삭제할까요?"))return;try{await request("/api/photos/"+p.id,{method:"DELETE"});d.close();await renderPhotos();}catch(e){toast(e.message);}};
    d.showModal();
  }
  async function renderFamily() {
    const family=await request("/api/family");
    shell("우리 가족",`<section class="panel"><h2>${esc(family.name)}</h2><p class="muted">초대 코드</p><div class="invite"><strong>${esc(family.inviteCode)}</strong><button id="copy" class="btn secondary">복사</button></div></section>
      <section class="panel"><h2>구성원 ${family.members.length}명</h2><ul class="members">${family.members.map(m=>`<li><b>${esc(m.name)}</b><span>${esc(m.email)}</span><em>${m.role==="ADMIN"?"관리자":"구성원"}</em></li>`).join("")}</ul></section>`,"family");
    document.getElementById("copy").onclick=async()=>{await navigator.clipboard.writeText(family.inviteCode);toast("초대 코드를 복사했습니다.");};
  }
  async function renderFolder(parts) {
    await loadFolders(); const id=parts[1]; const editing=!!id;
    const detail=editing?await request("/api/folders/"+id):{id:null,name:"",members:[]};
    const members=detail.members||[];
    shell(editing?"폴더 설정":"새 폴더",`<form id="folderForm" class="panel stack"><label>폴더 이름<input name="name" maxlength="100" required value="${esc(detail.name)}"></label>
      <h3>접근 권한</h3><p class="muted">선택하지 않은 구성원은 폴더에 접근할 수 없습니다.</p>
      ${members.map(m=>`<label class="access-row"><span>${esc(m.name)} · ${esc(m.email)}</span>${m.implicit?'<b>관리자/생성자 · 전체 권한</b>':`<select name="access_${m.userId}"><option value="NONE" ${m.level==="NONE"?"selected":""}>접근 없음</option><option value="VIEW" ${m.level==="VIEW"?"selected":""}>보기</option><option value="EDIT" ${m.level==="EDIT"?"selected":""}>올리기</option></select>`}</label>`).join("")}
      <div class="actions"><a class="btn ghost" href="#photos">취소</a><button class="btn primary">저장</button></div></form>
      ${editing&&!detail.defaultFolder?'<button id="deleteFolder" class="btn danger">폴더 삭제</button>':""}`);
    document.getElementById("folderForm").onsubmit=async e=>{
      e.preventDefault();const f=new FormData(e.currentTarget);const access=[];
      for(const [k,v] of f.entries())if(k.startsWith("access_")&&v!=="NONE")access.push({userId:Number(k.slice(7)),level:v});
      try{const body={name:f.get("name"),access};if(editing)await request("/api/folders/"+id,{method:"PUT",body:json(body)});else await request("/api/folders",{method:"POST",body:json(body)});toast("폴더를 저장했습니다.");go("photos");}catch(err){toast(err.message);}
    };
    document.getElementById("deleteFolder")?.addEventListener("click",async()=>{if(!confirm("폴더를 삭제할까요?"))return;try{await request("/api/folders/"+id,{method:"DELETE"});go("photos");}catch(e){toast(e.message);}});
  }
  async function renderGoogle() {
    const status=await request("/api/google/status"); await loadFolders();
    const writable=folders.filter(f=>f.canEdit);
    shell("Google Photos",`<section class="panel"><h2>내 구글 포토</h2>
      ${!status.enabled?'<p class="muted">서버에 Google OAuth 설정이 필요합니다.</p><code>'+esc(status.redirectUri)+'</code>':
      !status.connected?'<p class="muted">Google 계정을 연결하면 직접 선택한 사진을 가족 앨범으로 가져올 수 있습니다.</p><button class="btn primary" id="connectGoogle">Google 계정 연결</button>':
      `<p class="muted">가져올 폴더를 선택하고 Google Photos Picker에서 사진을 고르세요.</p><select id="gFolder">${writable.map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join("")}</select><button class="btn primary" id="pickGoogle" ${writable.length?"":"disabled"}>사진 선택 및 가져오기</button><button class="btn ghost" id="disconnectGoogle">연결 해제</button>`}
      <p id="gStatus" class="muted"></p></section>`,"google");
    document.getElementById("connectGoogle")?.addEventListener("click",async()=>{try{const r=await request("/api/google/connect",{method:"POST"});location.href=r.authorizationUrl;}catch(e){toast(e.message);}});
    document.getElementById("disconnectGoogle")?.addEventListener("click",async()=>{if(!confirm("Google 연결을 해제할까요?"))return;await request("/api/google/disconnect",{method:"POST"});renderGoogle();});
    document.getElementById("pickGoogle")?.addEventListener("click",async()=>{
      const folderId=document.getElementById("gFolder").value, statusEl=document.getElementById("gStatus"), btn=document.getElementById("pickGoogle");
      btn.disabled=true;let popup=null;
      try{
        const s=await request("/api/google/picker/sessions?folderId="+folderId,{method:"POST"});
        popup=window.open(s.pickerUri,"google-picker","popup,width=1000,height=750");
        statusEl.textContent="Google Photos에서 사진을 선택하고 완료를 눌러 주세요.";
        const deadline=Date.now()+(s.timeoutMs||600000);let ready=false;
        while(Date.now()<deadline){await new Promise(r=>setTimeout(r,Math.max(2000,s.pollIntervalMs||5000)));const p=await request("/api/google/picker/sessions/"+encodeURIComponent(s.sessionId));if(p.ready){ready=true;break;}}
        if(!ready)throw new Error("사진 선택 시간이 초과됐습니다.");
        const result=await request("/api/google/picker/sessions/"+encodeURIComponent(s.sessionId)+"/import?folderId="+folderId,{method:"POST"});
        statusEl.textContent=result.imported+"장을 가져왔습니다. 건너뜀 "+result.skipped+"장, 실패 "+result.failed.length+"장.";
      }catch(e){statusEl.textContent=e.message;}finally{btn.disabled=false;try{popup?.close();}catch{}}
    });
  }
  async function renderWorldcups() {
    const list=await request("/api/worldcups");
    shell("이상형 월드컵",`<div class="toolbar"><button id="newWc" class="btn primary">월드컵 만들기</button></div><div class="cards">${list.map(w=>`<article class="panel"><h3><a href="#worldcups/${w.id}">${esc(w.title)}</a></h3><p class="muted">${w.entryCount}개 후보 · 플레이 ${w.playCount}회 · ${esc(w.creatorName)}</p>${w.canDelete?'<button class="btn danger small" data-wc-delete="'+w.id+'">삭제</button>':""}</article>`).join("")||'<p class="muted">아직 월드컵이 없습니다.</p>'}</div>`,"worldcups");
    document.getElementById("newWc").onclick=()=>go("worldcups/new");
    document.querySelectorAll("[data-wc-delete]").forEach(b=>b.onclick=async()=>{if(confirm("삭제할까요?")){await request("/api/worldcups/"+b.dataset.wcDelete,{method:"DELETE"});renderWorldcups();}});
  }
  async function renderWorldcupNew() {
    await loadFolders();
    shell("월드컵 만들기",`<form id="wcCreate" class="panel stack"><label>주제<input name="title" maxlength="100" required placeholder="예: 최고의 여행지"></label><label>후보 유형<select name="mode" id="wcMode"><option value="text">텍스트 후보</option><option value="photo">앨범 사진</option><option value="web">인터넷 이미지</option></select></label>
      <label id="labelsField">후보 목록 (줄바꿈으로 구분)<textarea name="labels" rows="8" placeholder="제주도&#10;오사카&#10;파리"></textarea></label>
      <div id="photoField" hidden><label>폴더<select id="wcFolder">${folders.map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join("")}</select></label><div id="photoChoices" class="photo-grid"></div></div>
      <div id="webField" hidden><label>이미지 검색어<input id="imageQuery"></label><button type="button" id="imageSearch" class="btn secondary">이미지 검색</button><div id="imageChoices" class="photo-grid"></div></div>
      <button class="btn primary">월드컵 만들기</button></form>`,"worldcups");
    let pickedPhotos=[], pickedImages=[], imageQuery="";
    const mode=document.getElementById("wcMode");
    const change=async()=>{const m=mode.value;document.getElementById("labelsField").hidden=m!=="text";document.getElementById("photoField").hidden=m!=="photo";document.getElementById("webField").hidden=m!=="web";if(m==="photo")await loadPhotoChoices();};
    mode.onchange=change;
    async function loadPhotoChoices(){const id=document.getElementById("wcFolder").value;if(!id)return;const page=await request("/api/photos?folderId="+id+"&size=60");document.getElementById("photoChoices").innerHTML=page.content.map(p=>`<label class="pick-photo"><input type="checkbox" value="${p.id}"><img src="${API}/api/photos/${p.id}/file"><span>${esc(p.originalFilename)}</span></label>`).join("");document.querySelectorAll("#photoChoices input").forEach(x=>x.onchange=()=>{pickedPhotos=x.checked?[...pickedPhotos,Number(x.value)]:pickedPhotos.filter(v=>v!==Number(x.value));});}
    document.getElementById("wcFolder").onchange=loadPhotoChoices;
    document.getElementById("imageSearch").onclick=async()=>{imageQuery=document.getElementById("imageQuery").value;try{const r=await request("/api/images/search?q="+encodeURIComponent(imageQuery));pickedImages=[];document.getElementById("imageChoices").innerHTML=r.results.map(x=>`<label class="pick-photo"><input type="checkbox" value="${esc(x.id)}"><img src="${esc(x.thumbUrl)}"><span>${esc(x.title||x.creator||"이미지")}</span></label>`).join("");document.querySelectorAll("#imageChoices input").forEach(x=>x.onchange=()=>{pickedImages=x.checked?[...pickedImages,x.value]:pickedImages.filter(v=>v!==x.value);});}catch(e){toast(e.message);}};
    document.getElementById("wcCreate").onsubmit=async e=>{e.preventDefault();const f=new FormData(e.currentTarget),m=f.get("mode");try{const r=await request("/api/worldcups",{method:"POST",body:json({title:f.get("title"),mode:m,labels:f.get("labels"),folderId:m==="photo"?Number(document.getElementById("wcFolder").value):null,photoIds:pickedPhotos,query:imageQuery,imageIds:pickedImages})});go("worldcups/"+r.id);}catch(err){toast(err.message);}};
    await change();
  }
  async function renderWorldcupDetail(id) {
    const w=await request("/api/worldcups/"+id);
    shell(w.title,`<section class="panel"><p class="muted">${w.entries.length}개 후보 · 플레이 ${w.playCount}회</p><div class="photo-grid">${w.entries.map(e=>`<div class="pick-photo">${e.photoId?'<img src="'+API+'/api/photos/'+e.photoId+'/file">':e.imageUrl?'<img src="'+esc(e.imageUrl)+'">':'<span>'+esc(e.label)+'</span>'}</div>`).join("")}</div>
      <label>라운드<select id="rounds">${w.roundOptions.map(n=>`<option value="${n}">${n}강</option>`).join("")}</select></label><button class="btn primary" id="startPlay">시작</button><button class="btn secondary" id="ranking">랭킹</button></section>`,"worldcups");
    document.getElementById("startPlay").onclick=async()=>{try{const r=await request("/api/worldcups/"+id+"/plays",{method:"POST",body:json({rounds:Number(document.getElementById("rounds").value)})});go("worldcups/"+id+"/plays/"+r.playId);}catch(e){toast(e.message);}};
    document.getElementById("ranking").onclick=()=>go("worldcups/"+id+"/results");
  }
  async function renderPlay(id,playId) {
    let match;try{match=await request("/api/worldcups/"+id+"/plays/"+playId);}catch(e){if(e.message.includes("끝났")){go("worldcups/"+id+"/plays/"+playId+"/result");return;}throw e;}
    const card=e=>`<button class="choice" data-winner="${e.id}">${e.photoId?'<img src="'+API+'/api/photos/'+e.photoId+'/file">':e.imageUrl?'<img src="'+esc(e.imageUrl)+'">':'<span>'+esc(e.label)+'</span>'}<b>${esc(e.label||e.photoName||"선택")}</b></button>`;
    shell(match.title,`<section class="panel"><p class="muted">${esc(match.roundLabel)} · ${match.matchNo}/${match.matchTotal} · 진행률 ${match.progressPercent}%</p><div class="choices">${card(match.a)}${card(match.b)}</div></section>`,"worldcups");
    document.querySelectorAll("[data-winner]").forEach(b=>b.onclick=async()=>{try{const r=await request("/api/worldcups/"+id+"/plays/"+playId+"/pick",{method:"POST",body:json({winnerId:Number(b.dataset.winner)})});if(r.done)go("worldcups/"+id+"/plays/"+playId+"/result");else renderPlay(id,playId);}catch(e){toast(e.message);}});
  }
  async function renderWorldcupResult(id,playId) {
    const result=playId?await request("/api/worldcups/"+id+"/plays/"+playId+"/result"):null;
    const ranking=await request("/api/worldcups/"+id+"/results");
    shell(result?"플레이 결과":"가족 랭킹",`<section class="panel">${result?'<h2>우승</h2><h3>'+esc(result.champion?.label||result.champion?.photoName)+'</h3><p>준우승: '+esc(result.runnerUp?.label||result.runnerUp?.photoName)+'</p>':""}<h2>랭킹</h2><ol class="ranking">${ranking.rows.map(r=>`<li><b>${r.rank}위</b> ${esc(r.entry.label||r.entry.photoName)} <span>${r.champions}회 우승 · 승률 ${r.winRatePercent}%</span></li>`).join("")}</ol><a class="btn secondary" href="#worldcups/${id}">월드컵으로</a></section>`,"worldcups");
  }
  function showError(error){toast(error?.message||"오류가 발생했습니다.");}
  async function renderRoute() {
    const parts=route(), page=parts[0];
    if(page==="photos") {activeFolder=new URLSearchParams(location.hash.split("?")[1]||"").get("folder")||"";await renderPhotos();}
    else if(page==="family") await renderFamily();
    else if(page==="folder") await renderFolder(parts);
    else if(page==="google") await renderGoogle();
    else if(page==="worldcups"&&parts[1]==="new") await renderWorldcupNew();
    else if(page==="worldcups"&&parts[1]&&parts[2]==="plays"&&parts[4]==="result") await renderWorldcupResult(parts[1],parts[3]);
    else if(page==="worldcups"&&parts[1]&&parts[2]==="plays") await renderPlay(parts[1],parts[3]);
    else if(page==="worldcups"&&parts[1]&&parts[2]==="results") await renderWorldcupResult(parts[1],null);
    else if(page==="worldcups"&&parts[1]) await renderWorldcupDetail(parts[1]);
    else await renderWorldcups();
  }
  window.addEventListener("hashchange",()=>{if(me)renderRoute().catch(showError);});
  if(location.search.includes("google=connected")) location.hash="google";
  boot().catch(showError);
})();
