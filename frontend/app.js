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
  let activeMiniGameCleanup = null;

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
        <nav><a class="${active==="photos"?"on":""}" href="#photos">사진</a><a class="${active==="family"?"on":""}" href="#family">가족</a><a class="${active==="worldcups"?"on":""}" href="#worldcups">월드컵</a><a class="${active==="google"?"on":""}" href="#google">구글 포토</a><a class="${active==="contents"?"on":""}" href="#contents">콘텐츠 찾기</a><a class="${active==="tetris"?"on":""}" href="#tetris">미니게임</a><a class="${active==="utilities"?"on":""}" href="#utilities">유틸</a></nav>
        <span class="user">${esc(me?.name)}</span><button class="btn ghost" id="logout">로그아웃</button>
      </header><main class="wrap"><div class="pagehead"><h1>${title}</h1></div>${content}</main>
      <nav class="mobile-nav"><a href="#photos">사진</a><a href="#family">가족</a><a href="#worldcups">월드컵</a><a href="#google">구글</a><a href="#contents">콘텐츠</a><a href="#tetris">게임</a><a href="#utilities">유틸</a></nav>`;
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
    // Cloudflare Worker가 프론트엔드와 API를 같은 Origin에서 제공하므로
    // API Base URL이 빈 문자열인 상대 경로 설정을 정상값으로 취급합니다.
    try {
      await getCsrf(); me = await request("/api/auth/me"); await renderRoute();
      const googleState = new URLSearchParams(location.search).get("google");
      if (googleState === "connected") toast("Google 포토 연결이 완료되었습니다.");
      if (googleState === "error") toast("Google 포토 연결에 실패했습니다. 다시 시도해 주세요.");
    } catch { me=null; renderAuth(); }
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
    const folderId=activeFolder || folders.find(f=>["EDIT","MANAGE"].includes(f.permission))?.id;
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
    const writable=folders.filter(f=>["EDIT","MANAGE"].includes(f.permission));
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
        popup=window.open("about:blank","google-picker","popup,width=1000,height=750");
        if(!popup) throw new Error("팝업이 차단되었습니다. 이 사이트의 팝업을 허용한 뒤 다시 시도해 주세요.");
        const s=await request("/api/google/picker/sessions?folderId="+folderId,{method:"POST"});
        popup.location.href=s.pickerUri;
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
    shell("월드컵 만들기",`<form id="wcCreate" class="panel stack">
      <label>월드컵 제목<input name="title" maxlength="100" required placeholder="예: 내가 좋아하는 최고의 것들"></label>
      <label>후보 유형<select name="mode" id="wcMode"><option value="text">텍스트 후보</option><option value="photo">앨범 사진</option><option value="web">키워드 이미지 후보</option></select></label>
      <label id="labelsField">후보 목록 (줄바꿈으로 구분)<textarea name="labels" rows="8" placeholder="제주도&#10;오사카&#10;파리"></textarea></label>
      <div id="photoField" hidden><label>폴더<select id="wcFolder">${folders.map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join("")}</select></label><div id="photoChoices" class="photo-grid"></div></div>
      <section id="webField" class="keyword-worldcup" hidden>
        <div class="keyword-toolbar">
          <label>이미지 검색 키워드<input id="imageKeyword" maxlength="40" placeholder="예: 바다, 강아지, 봄, 자동차"></label>
          <div class="keyword-actions">
            <button type="button" id="imageSearchAll" class="btn secondary">4개 분류 이미지 수집</button>
            <button type="button" id="imageKeywordReset" class="btn ghost">키워드 초기화</button>
          </div>
        </div>
        <p class="muted">키워드를 기준으로 사람·동물·식물·무생물 이미지를 각각 검색합니다. 원하는 분류에서 이미지를 골라 월드컵 후보로 추가하세요.</p>
        <p id="imageSelectionSummary" class="muted">선택한 이미지 0장 (월드컵은 4~64개 후보)</p>
        <div id="imageCategoryChoices" class="image-category-list"></div>
      </section>
      <button class="btn primary">월드컵 만들기</button>
    </form>`,"worldcups");

    let pickedPhotos=[];
    const categories=[
      {key:"people",label:"사람",icon:"👤",terms:"사람 인물 portrait"},
      {key:"animals",label:"동물",icon:"🐾",terms:"동물 야생동물 반려동물 animal"},
      {key:"plants",label:"식물",icon:"🌿",terms:"식물 꽃 나무 plant"},
      {key:"objects",label:"무생물",icon:"📦",terms:"사물 물건 제품 풍경 object"}
    ];
    const imageGroups=Object.fromEntries(categories.map(category=>[category.key,{
      query:"",results:[],selected:new Set(),error:"",loading:false
    }]));
    const mode=document.getElementById("wcMode");
    const change=async()=>{
      const m=mode.value;
      document.getElementById("labelsField").hidden=m!=="text";
      document.getElementById("photoField").hidden=m!=="photo";
      document.getElementById("webField").hidden=m!=="web";
      if(m==="photo")await loadPhotoChoices();
      if(m==="web")renderImageCategories();
    };
    mode.onchange=change;

    async function loadPhotoChoices(){
      const id=document.getElementById("wcFolder").value;
      if(!id)return;
      const page=await request("/api/photos?folderId="+id+"&size=60");
      document.getElementById("photoChoices").innerHTML=page.content.map(p=>`<label class="pick-photo"><input type="checkbox" value="${p.id}"><img src="${API}/api/photos/${p.id}/file"><span>${esc(p.originalFilename)}</span></label>`).join("");
      document.querySelectorAll("#photoChoices input").forEach(x=>x.onchange=()=>{
        pickedPhotos=x.checked?[...pickedPhotos,Number(x.value)]:pickedPhotos.filter(v=>v!==Number(x.value));
      });
    }
    document.getElementById("wcFolder").onchange=loadPhotoChoices;

    function selectedImageCount(){
      return categories.reduce((sum,category)=>sum+imageGroups[category.key].selected.size,0);
    }
    function renderImageCategories(){
      const root=document.getElementById("imageCategoryChoices");
      root.innerHTML=categories.map(category=>{
        const group=imageGroups[category.key];
        const cards=group.results.map(item=>{
          const checked=group.selected.has(item.id);
          return `<label class="pick-photo image-candidate">
            <input type="checkbox" data-image-category="${category.key}" value="${esc(item.id)}" ${checked?"checked":""}>
            <img loading="lazy" src="${esc(item.thumbUrl)}" alt="${esc(item.title||category.label)}" referrerpolicy="no-referrer">
            <span>${esc(item.title||item.creator||category.label)}</span>
            <small>${esc(item.provider||"이미지 제공처")} · ${esc(item.license||"라이선스 확인")}</small>
          </label>`;
        }).join("");
        return `<section class="image-category-panel">
          <div class="image-category-heading"><h3>${category.icon} ${category.label}</h3><span>${group.selected.size}개 선택 / ${group.results.length}개 검색</span></div>
          ${group.loading?'<p class="muted">이미지를 검색하고 있습니다…</p>':""}
          ${group.error?`<p class="error">${esc(group.error)}</p>`:""}
          ${cards?'<div class="photo-grid image-candidate-grid">'+cards+'</div>':'<p class="muted">아직 검색 결과가 없습니다. 키워드를 입력하고 분류별 이미지 수집을 눌러 주세요.</p>'}
        </section>`;
      }).join("");
      document.getElementById("imageSelectionSummary").textContent=
        "선택한 이미지 "+selectedImageCount()+"장 (월드컵은 4~64개 후보)";
    }

    async function searchImageCategory(category){
      const keyword=document.getElementById("imageKeyword").value.trim();
      if(keyword.length<2){toast("키워드를 2자 이상 입력해 주세요.");return;}
      const group=imageGroups[category.key];
      group.query=keyword+" "+category.terms;
      group.loading=true;group.error="";group.results=[];group.selected.clear();
      renderImageCategories();
      try{
        const result=await request("/api/images/search?q="+encodeURIComponent(group.query));
        group.results=result.results||[];
        if(!group.results.length)group.error="검색 결과가 없습니다. 다른 키워드로 시도해 주세요.";
      }catch(error){group.error=error.message;}
      finally{group.loading=false;renderImageCategories();}
    }

    document.getElementById("imageSearchAll").onclick=async()=>{
      const keyword=document.getElementById("imageKeyword").value.trim();
      if(keyword.length<2){toast("키워드를 2자 이상 입력해 주세요.");return;}
      // Openverse 익명 호출 한도를 고려해 분류별 요청을 순차 실행한다.
      for(const category of categories)await searchImageCategory(category);
      toast("4개 분류 이미지 검색이 끝났습니다. 사용할 이미지를 선택해 주세요.");
    };
    document.getElementById("imageCategoryChoices").onchange=event=>{
      const input=event.target.closest("input[data-image-category]");
      if(!input)return;
      const group=imageGroups[input.dataset.imageCategory];
      if(input.checked)group.selected.add(input.value);
      else group.selected.delete(input.value);
      document.getElementById("imageSelectionSummary").textContent=
        "선택한 이미지 "+selectedImageCount()+"장 (월드컵은 4~64개 후보)";
    };
    document.getElementById("imageKeywordReset").onclick=()=>{
      document.getElementById("imageKeyword").value="";
      categories.forEach(category=>{
        const group=imageGroups[category.key];
        group.query="";group.results=[];group.selected.clear();group.error="";group.loading=false;
      });
      renderImageCategories();
      toast("키워드와 이미지 선택을 초기화했습니다.");
    };

    document.getElementById("wcCreate").onsubmit=async e=>{
      e.preventDefault();
      const f=new FormData(e.currentTarget),m=f.get("mode");
      try{
        const imageGroupsPayload=categories.map(category=>{
          const group=imageGroups[category.key];
          return {category:category.label,query:group.query,imageIds:[...group.selected]};
        }).filter(group=>group.imageIds.length);
        const payload={
          title:f.get("title"),mode:m==="web"?"web-groups":m,labels:f.get("labels"),
          folderId:m==="photo"?Number(document.getElementById("wcFolder").value):null,
          photoIds:pickedPhotos,
          imageGroups:imageGroupsPayload
        };
        if(m==="web"&&selectedImageCount()<4){
          toast("이미지를 최소 4개 이상 선택해 주세요.");return;
        }
        const result=await request("/api/worldcups",{method:"POST",body:json(payload)});
        go("worldcups/"+result.id);
      }catch(error){toast(error.message);}
    };
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
    const eliminated=result?.eliminated||[];
    shell(result?"플레이 결과":"가족 랭킹",`<section class="panel">
      ${result?'<h2>우승</h2><h3>'+esc(result.champion?.label||result.champion?.photoName)+'</h3><p>준우승: '+esc(result.runnerUp?.label||result.runnerUp?.photoName)+'</p>'+
        eliminated.map(g=>'<h3>'+esc(g.label)+'</h3><p>'+g.entries.map(e=>esc(e.label||e.photoName)).join(' · ')+'</p>').join(''):""}
      <h2>가족 랭킹</h2><ol class="ranking">${ranking.rows.map(r=>`<li><b>${r.rank}위</b> ${esc(r.entry.label||r.entry.photoName)} <span>${r.champions}회 우승 · ${r.wins}승 · 승률 ${r.winRatePercent}%</span></li>`).join("")}</ol><a class="btn secondary" href="#worldcups/${id}">월드컵으로</a></section>`,"worldcups");
  }

  async function renderTetris() {
    shell("테트리스", '<section class="panel tetris-panel"><div class="tetris-toolbar"><div><span class="muted">점수</span><strong id="tetrisScore">0</strong></div><div><span class="muted">최고 점수</span><strong id="tetrisBest">0</strong></div><div><span class="muted">레벨</span><strong id="tetrisLevel">1</strong></div></div><div class="tetris-layout"><div class="tetris-board-wrap"><canvas id="tetrisBoard" width="300" height="600" aria-label="테트리스 게임 보드"></canvas></div><aside class="tetris-side"><h3>다음 블록</h3><canvas id="tetrisNext" width="120" height="120"></canvas><p class="muted" id="tetrisStatus">게임을 시작해 보세요.</p><button class="btn primary" id="tetrisStart">게임 시작</button><button class="btn secondary" id="tetrisPause" disabled>일시정지</button><button class="btn ghost" id="tetrisRestart">다시 시작</button><div class="tetris-help"><b>조작 방법</b><span>← → 이동</span><span>↓ 빠르게 내리기</span><span>↑ 회전</span><span>Space 즉시 떨어뜨리기</span><span>P 일시정지</span></div></aside></div><div class="tetris-touch" aria-label="터치 조작"><button data-tetris="left" aria-label="왼쪽">←</button><button data-tetris="rotate" aria-label="회전">↻</button><button data-tetris="right" aria-label="오른쪽">→</button><button data-tetris="down" aria-label="아래">↓</button><button data-tetris="drop" aria-label="즉시 떨어뜨리기">⤓</button></div></section>',"tetris");
    const canvas=document.getElementById("tetrisBoard"),ctx=canvas.getContext("2d");
    const nextCanvas=document.getElementById("tetrisNext"),nextCtx=nextCanvas.getContext("2d");
    const COLS=10,ROWS=20,SIZE=30,bestKey="familyAlbumTetrisBest";
    const colors=[null,"#35a7a0","#3978c5","#e49b36","#d5bd39","#805bc4","#d45d79","#55a65b"];
    const shapes=[[[1,1,1,1]],[[1,0,0],[1,1,1]],[[0,0,1],[1,1,1]],[[1,1],[1,1]],[[0,1,1],[1,1,0]],[[0,1,0],[1,1,1]],[[1,1,0],[0,1,1]]];
    let board,player,next,score=0,lines=0,level=1,running=false,paused=false,timer=null;
    const bestEl=document.getElementById("tetrisBest");
    try{bestEl.textContent=localStorage.getItem(bestKey)||"0";}catch{}
    const matrix=()=>Array.from({length:ROWS},()=>Array(COLS).fill(0));
    function piece(){const type=Math.floor(Math.random()*shapes.length),m=shapes[type].map(r=>r.slice());return {matrix:m,type,x:Math.floor(COLS/2)-Math.ceil(m[0].length/2),y:0};}
    function cell(c,x,y,color,size){c.fillStyle=color;c.fillRect(x*size+1,y*size+1,size-2,size-2);c.fillStyle="rgba(255,255,255,.2)";c.fillRect(x*size+3,y*size+3,size-6,3);}
    function draw(){
      ctx.fillStyle="#171c20";ctx.fillRect(0,0,canvas.width,canvas.height);ctx.strokeStyle="#ffffff12";
      for(let x=0;x<=COLS;x++){ctx.beginPath();ctx.moveTo(x*SIZE,0);ctx.lineTo(x*SIZE,canvas.height);ctx.stroke();}
      for(let y=0;y<=ROWS;y++){ctx.beginPath();ctx.moveTo(0,y*SIZE);ctx.lineTo(canvas.width,y*SIZE);ctx.stroke();}
      board.forEach((r,y)=>r.forEach((v,x)=>{if(v)cell(ctx,x,y,colors[v],SIZE);}));
      if(player)player.matrix.forEach((r,y)=>r.forEach((v,x)=>{if(v)cell(ctx,player.x+x,player.y+y,colors[player.type+1],SIZE);}));
      nextCtx.fillStyle="#171c20";nextCtx.fillRect(0,0,120,120);
      if(next){const m=next.matrix,ox=Math.floor((4-m[0].length)/2),oy=Math.floor((4-m.length)/2);m.forEach((r,y)=>r.forEach((v,x)=>{if(v)cell(nextCtx,ox+x,oy+y,colors[next.type+1],28);}));}
    }
    function collide(p){return p.matrix.some((r,y)=>r.some((v,x)=>v&&(p.y+y>=ROWS||p.x+x<0||p.x+x>=COLS||board[p.y+y]?.[p.x+x]!==0)));}
    function merge(){player.matrix.forEach((r,y)=>r.forEach((v,x)=>{if(v&&board[player.y+y])board[player.y+y][player.x+x]=player.type+1;}));}
    function spawn(){player=next||piece();next=piece();player.x=Math.floor(COLS/2)-Math.ceil(player.matrix[0].length/2);player.y=0;if(collide(player))endGame();draw();}
    function updateScore(){document.getElementById("tetrisScore").textContent=score;document.getElementById("tetrisLevel").textContent=level;}
    function setSpeed(){if(timer)clearInterval(timer);if(running&&!paused)timer=setInterval(step,Math.max(90,650-(level-1)*55));}
    function sweep(){let cleared=0;outer:for(let y=ROWS-1;y>=0;y--){for(let x=0;x<COLS;x++)if(!board[y][x])continue outer;board.splice(y,1);board.unshift(Array(COLS).fill(0));cleared++;y++;}if(cleared){lines+=cleared;score+=[0,100,300,500,800][cleared]*level;level=1+Math.floor(lines/10);updateScore();setSpeed();}}
    function step(){if(!running||paused)return;player.y++;if(collide(player)){player.y--;merge();sweep();spawn();}draw();}
    function endGame(){running=false;if(timer)clearInterval(timer);timer=null;document.getElementById("tetrisStatus").textContent="게임 오버! 점수 "+score+"점";document.getElementById("tetrisStart").disabled=false;document.getElementById("tetrisPause").disabled=true;try{const best=Number(localStorage.getItem(bestKey)||0);if(score>best){localStorage.setItem(bestKey,String(score));bestEl.textContent=score;}}catch{}}
    function start(){if(timer)clearInterval(timer);board=matrix();score=0;lines=0;level=1;running=true;paused=false;next=piece();updateScore();spawn();document.getElementById("tetrisStatus").textContent="플레이 중";document.getElementById("tetrisStart").disabled=true;document.getElementById("tetrisPause").disabled=false;document.getElementById("tetrisPause").textContent="일시정지";setSpeed();}
    function pause(){if(!running)return;paused=!paused;document.getElementById("tetrisPause").textContent=paused?"계속하기":"일시정지";document.getElementById("tetrisStatus").textContent=paused?"일시정지 중":"플레이 중";setSpeed();}
    function move(dx){if(!running||paused)return;player.x+=dx;if(collide(player))player.x-=dx;draw();}
    function rotate(){if(!running||paused)return;const old=player.matrix;player.matrix=old[0].map((_,i)=>old.map(r=>r[i]).reverse());let offset=0;while(collide(player)){player.x+=offset===0?1:offset>0?-(offset*2+1):-(offset*2);offset++;if(offset>5){player.matrix=old;player.x-=offset;break;}}draw();}
    function drop(){if(!running||paused)return;let distance=0;do{player.y++;distance++;}while(!collide(player));player.y--;score+=Math.max(0,distance-1)*2;merge();sweep();spawn();updateScore();draw();}
    function keydown(e){if(["ArrowLeft","ArrowRight","ArrowDown","ArrowUp"," "].includes(e.key))e.preventDefault();if(e.key==="ArrowLeft")move(-1);else if(e.key==="ArrowRight")move(1);else if(e.key==="ArrowDown")step();else if(e.key==="ArrowUp")rotate();else if(e.key===" ")drop();else if(e.key.toLowerCase()==="p")pause();}
    document.addEventListener("keydown",keydown);
    document.getElementById("tetrisStart").onclick=start;document.getElementById("tetrisRestart").onclick=start;document.getElementById("tetrisPause").onclick=pause;
    document.querySelectorAll("[data-tetris]").forEach(b=>b.onclick=()=>{const a=b.dataset.tetris;if(a==="left")move(-1);else if(a==="right")move(1);else if(a==="rotate")rotate();else if(a==="down")step();else if(a==="drop")drop();});
    board=matrix();player=null;next=piece();updateScore();draw();
    activeMiniGameCleanup=()=>{if(timer)clearInterval(timer);timer=null;running=false;document.removeEventListener("keydown",keydown);activeMiniGameCleanup=null;};
  }

  async function renderContents() {
    shell("콘텐츠 찾기",`
      <section class="panel content-search">
        <p class="muted">영화·드라마·예능·배우를 검색하고, OTT별 다시보기 및 실시간 방송 검색 링크를 확인할 수 있습니다.</p>
        <form id="contentSearchForm" class="toolbar">
          <input id="contentQuery" type="search" minlength="2" placeholder="프로그램명 또는 배우명 (예: 무빙, 이정재)" required>
          <select id="contentKind" aria-label="검색 유형">
            <option value="all">전체</option><option value="tv">드라마·예능</option><option value="movie">영화</option><option value="person">배우·출연진</option>
          </select>
          <button class="btn primary">검색</button>
        </form>
        <p id="contentMessage" class="muted">검색어를 입력하면 OTT별 외부 검색 링크를 제공합니다.</p>
      </section>
      <section class="content-ott-section">
        <h2>다시보기 OTT 검색</h2>
        <div id="vodResults" class="content-ott-grid"></div>
      </section>
      <section class="content-ott-section">
        <h2>실시간 방송 검색</h2>
        <div id="liveResults" class="content-ott-grid"></div>
      </section>
      <section class="content-ott-section">
        <h2>작품 정보 검색</h2>
        <div id="contentResults" class="content-grid"></div>
      </section>
      <p class="content-attribution">OTT별 링크는 외부 검색 결과로 연결됩니다. 가족앨범은 OTT의 실제 제공 여부나 실시간 편성을 보증하지 않으며, 각 서비스에서 직접 확인해야 합니다.</p>`,"contents");
    const resultEl=document.getElementById("contentResults");
    const vodEl=document.getElementById("vodResults");
    const liveEl=document.getElementById("liveResults");
    const message=document.getElementById("contentMessage");
    const OTT_BRANDS = [
      { match: /넷플릭스|netflix/i, key: "netflix", mark: "N", label: "Netflix" },
      { match: /티빙|tving/i, key: "tving", mark: "T", label: "TVING" },
      { match: /웨이브|wavve/i, key: "wavve", mark: "W", label: "Wavve" },
      { match: /디즈니|disney/i, key: "disney", mark: "D+", label: "Disney+" },
      { match: /쿠팡|coupang/i, key: "coupang", mark: "cp", label: "Coupang Play" },
      { match: /왓챠|watcha/i, key: "watcha", mark: "W", label: "WATCHA" },
      { match: /apple/i, key: "apple", mark: "tv+", label: "Apple TV+" },
      { match: /라프텔|laftel/i, key: "laftel", mark: "L", label: "Laftel" }
    ];
    const ottBrand = name => OTT_BRANDS.find(brand => brand.match.test(name || "")) || {
      key: "default", mark: "▶", label: name || "OTT"
    };
    const renderLinks=links=>(links||[]).map(link=>{
      const brand=ottBrand(link.name);
      return `<a class="content-ott-link" href="${esc(link.url)}" target="_blank" rel="noopener noreferrer">
        <span class="content-ott-brand content-ott-brand-${brand.key}" aria-hidden="true">${brand.mark}</span>
        <span class="content-ott-copy"><b>${esc(link.name)}</b><span>${esc(link.description)}</span><i>검색 결과 열기 ↗</i></span>
      </a>`;
    }).join("") || '<p class="muted">표시할 검색 링크가 없습니다.</p>';
    // 이전 버전 백엔드가 배포된 상태에서도 OTT 링크 영역이 비지 않도록 브라우저에서 보완한다.
    // 최신 백엔드가 정상 배열을 반환하면 서버 결과를 우선 사용한다.
    const fallbackOttLinks=(query, providers, viewingType)=>providers.map(provider=>({
      name:provider.name,
      url:"https://www.google.com/search?q="+encodeURIComponent(query+" "+viewingType+" site:"+provider.domain),
      description:provider.name+" 도메인에서 '"+query+"' "+viewingType+" 검색 결과를 확인합니다. 실제 제공 여부는 플랫폼에서 확인하세요."
    }));
    const VOD_SEARCH_PROVIDERS=[
      {name:"넷플릭스",domain:"netflix.com"},
      {name:"티빙",domain:"tving.com"},
      {name:"웨이브",domain:"wavve.com"},
      {name:"디즈니+",domain:"disneyplus.com"},
      {name:"쿠팡플레이",domain:"coupangplay.com"},
      {name:"왓챠",domain:"watcha.com"},
      {name:"Apple TV+",domain:"tv.apple.com"},
      {name:"라프텔",domain:"laftel.net"}
    ];
    const LIVE_SEARCH_PROVIDERS=[
      {name:"티빙",domain:"tving.com"},
      {name:"웨이브",domain:"wavve.com"}
    ];
    document.getElementById("contentSearchForm").onsubmit=async e=>{
      e.preventDefault();
      const q=document.getElementById("contentQuery").value.trim();
      const kind=document.getElementById("contentKind").value;
      if(q.length<2){toast("검색어를 2자 이상 입력해 주세요.");return;}
      message.textContent="OTT별 검색 링크를 준비하고 있습니다…";
      resultEl.innerHTML="";vodEl.innerHTML="";liveEl.innerHTML="";
      try{
        const data=await request("/api/contents/search?q="+encodeURIComponent(q)+"&kind="+encodeURIComponent(kind));
        message.textContent="검색어: "+q+" · 각 OTT의 검색 결과에서 시청 가능 여부를 확인하세요.";
        const serverVodLinks=Array.isArray(data.vodLinks)?data.vodLinks:[];
        const serverLiveLinks=Array.isArray(data.liveLinks)?data.liveLinks:[];
        const vodLinks=serverVodLinks.length?serverVodLinks:fallbackOttLinks(q,VOD_SEARCH_PROVIDERS,"다시보기");
        const liveLinks=serverLiveLinks.length?serverLiveLinks:fallbackOttLinks(q,LIVE_SEARCH_PROVIDERS,"실시간 방송");
        vodEl.innerHTML=renderLinks(vodLinks);
        liveEl.innerHTML=renderLinks(liveLinks);
        resultEl.innerHTML=renderLinks(Array.isArray(data.searchLinks)?data.searchLinks:[]);
        if(!serverVodLinks.length||!serverLiveLinks.length){
          message.textContent+=" · 서버 응답이 구버전이어서 OTT 검색 링크를 브라우저에서 보완했습니다.";
        }
      }catch(err){
        // API 오류/구버전 백엔드에서도 사용자가 OTT 검색을 진행할 수 있도록 외부 검색 링크를 제공한다.
        message.textContent="콘텐츠 API 응답을 사용할 수 없어 외부 검색 링크로 표시합니다. ("+err.message+")";
        vodEl.innerHTML=renderLinks(fallbackOttLinks(q,VOD_SEARCH_PROVIDERS,"다시보기"));
        liveEl.innerHTML=renderLinks(fallbackOttLinks(q,LIVE_SEARCH_PROVIDERS,"실시간 방송"));
        const encoded=encodeURIComponent(q);
        resultEl.innerHTML=renderLinks([
          {name:"나무위키",url:"https://namu.wiki/Search?q="+encoded,description:"나무위키에서 '"+q+"' 검색"},
          {name:"웹 전체 검색",url:"https://www.google.com/search?q="+encodeURIComponent(q+" 영화 드라마 예능 방송"),description:"웹 전체에서 '"+q+"' 관련 정보 검색"}
        ]);
      }
    };
  }

  async function renderUtilities() {
    shell("유틸",'<section class="panel utility-card"><div class="utility-icon">📍</div><div><h2>중간에서 만나</h2><p class="muted">두 장소를 검색하면 지리적 중간 지점과 주변 버스정류장·지하철역·기차역을 지도에서 확인할 수 있습니다.</p><a class="btn primary" href="#utilities/meet">중간에서 만나 시작하기</a></div></section>',"utilities");
  }
  let meetMap = null;
  async function loadLeaflet() {
    if (window.L) return;
    if (!document.getElementById("leaflet-css")) {
      const link=document.createElement("link");link.id="leaflet-css";link.rel="stylesheet";
      link.href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";document.head.appendChild(link);
    }
    await new Promise((resolve,reject)=>{
      const script=document.createElement("script");script.src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
      script.onload=resolve;script.onerror=()=>reject(new Error("지도 라이브러리를 불러오지 못했습니다."));document.head.appendChild(script);
    });
  }
  async function searchPlaces(query) {
    const url="https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&countrycodes=kr&limit=6&q="+encodeURIComponent(query);
    const response=await fetch(url,{headers:{"Accept-Language":"ko"}});
    if(!response.ok)throw new Error("장소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    return response.json();
  }
  function geoMidpoint(a,b) {
    const rad=Math.PI/180, lon1=a.lon*rad, lat1=a.lat*rad, lon2=b.lon*rad, lat2=b.lat*rad;
    const bx=Math.cos(lat2)*Math.cos(lon2-lon1), by=Math.cos(lat2)*Math.sin(lon2-lon1);
    const lat=Math.atan2(Math.sin(lat1)+Math.sin(lat2),Math.sqrt((Math.cos(lat1)+bx)**2+by**2));
    const lon=lon1+Math.atan2(by,Math.cos(lat1)+bx);
    return {lat:lat/rad,lon:((lon/rad+540)%360)-180};
  }
  function normalizeTransitElements(elements,mid,expectedType){
    const seen=new Set();return(elements||[]).map(el=>{const t=el.tags||{},lat=el.lat??el.center?.lat,lon=el.lon??el.center?.lon;if(!Number.isFinite(lat)||!Number.isFinite(lon))return null;const sub=t.railway==="subway_entrance"||t.station==="subway",rail=t.railway==="station"||t.railway==="halt"||t.railway==="platform"||t.station==="train",bus=t.highway==="bus_stop"||t.public_transport==="platform"&&!sub&&!rail,type=sub?"지하철역":rail?"기차역":bus?"버스정류장":"";if(type!==expectedType)return null;const name=t["name:ko"]||t.name||type,key=type+"|"+name+"|"+lat.toFixed(5)+"|"+lon.toFixed(5);if(seen.has(key))return null;seen.add(key);const dy=(lat-mid.lat)*111.32,dx=(lon-mid.lon)*111.32*Math.cos(mid.lat*Math.PI/180);return{id:el.id,type,name,lat,lon,distance:Math.round(Math.hypot(dx,dy)*1000)};}).filter(Boolean).sort((x,y)=>x.distance-y.distance);
  }
  async function streamTransit(mid,onEvent,onStatus){
    const res=await fetch("/meet/transit/stream?lat="+encodeURIComponent(mid.lat)+"&lon="+encodeURIComponent(mid.lon),{headers:{Accept:"text/event-stream"}});if(!res.ok)throw Error("교통편 스트리밍 서버 오류 ("+res.status+")");if(!res.body)throw Error("실시간 스트리밍 미지원 브라우저입니다.");
    const reader=res.body.getReader(),decoder=new TextDecoder();let buffer="",finished=false;while(!finished){const chunk=await reader.read();if(chunk.done)break;buffer+=decoder.decode(chunk.value,{stream:true});let pos;while((pos=buffer.indexOf("\\n\\n"))>=0){const block=buffer.slice(0,pos);buffer=buffer.slice(pos+2);const event=(block.match(/^event:\\s*(.+)$/m)||[])[1]||"message",line=block.split("\\n").find(x=>x.startsWith("data:"));if(!line)continue;let data;try{data=JSON.parse(line.slice(5).trim());}catch{continue;}if(event==="progress")onStatus(data.message,data.percent);else if(event==="category")onEvent(data.category,data.elements||[],null);else if(event==="error")onEvent(data.category,[],data.message||"조회 실패");else if(event==="done"){finished=true;onEvent("done",[],data);}}}try{reader.releaseLock();}catch{}
  }
  async function renderMeet() {
    shell("중간에서 만나",'<section class="panel meet-panel"><p class="muted">출발지와 도착지를 각각 검색하고, 정확히 일치하는 주소나 장소를 선택해 주세요. 두 위치의 대권 경로상 중간점을 계산합니다.</p><div class="meet-inputs"><div class="meet-location"><label for="meetFrom">첫 번째 위치</label><div class="meet-search-row"><input id="meetFrom" placeholder="예: 서울역, 경기도 성남시 분당구..." autocomplete="off"><button class="btn secondary" id="searchFrom">검색</button></div><div id="fromChoices" class="meet-choices"></div><p id="fromSelected" class="meet-selected">선택된 위치 없음</p></div><div class="meet-location"><label for="meetTo">두 번째 위치</label><div class="meet-search-row"><input id="meetTo" placeholder="예: 강남역, 인천광역시 연수구..." autocomplete="off"><button class="btn secondary" id="searchTo">검색</button></div><div id="toChoices" class="meet-choices"></div><p id="toSelected" class="meet-selected">선택된 위치 없음</p></div></div><button id="findMeet" class="btn primary meet-submit" disabled>중간 지점 및 교통편 찾기</button><p id="meetStatus" class="muted" aria-live="polite">주소 검색은 OpenStreetMap, 대중교통 위치는 OpenStreetMap 데이터로 조회합니다.</p><div id="meetProgressWrap" class="meet-progress-wrap" hidden><div class="meet-progress-track"><div id="meetProgressBar" class="meet-progress-bar"></div></div><div class="meet-progress-meta"><span id="meetProgressLabel">준비 중…</span><span id="meetProgressPercent">0%</span></div></div></section><section id="meetResults" hidden><div class="panel meet-summary" id="meetSummary"></div><div id="meetMap" class="meet-map"></div><div class="panel"><h2>주변 대중교통</h2><div id="meetTransit" class="meet-transit"></div><p class="meet-credit">지도 © OpenStreetMap contributors · 주소 검색 Nominatim · 주변 시설 Overpass API</p></div></section>',"utilities");
    let from=null,to=null;
    const status=document.getElementById("meetStatus");
    async function search(which) {
      const input=document.getElementById(which==="from"?"meetFrom":"meetTo");
      const box=document.getElementById(which==="from"?"fromChoices":"toChoices");
      const selectedEl=document.getElementById(which==="from"?"fromSelected":"toSelected");
      const query=input.value.trim();if(query.length<2){toast("장소를 두 글자 이상 입력해 주세요.");return;}
      box.textContent="검색 중…";
      try {
        const results=await searchPlaces(query);box.innerHTML="";
        if(!results.length){box.textContent="검색 결과가 없습니다. 도로명 주소나 건물명을 더 구체적으로 입력해 주세요.";return;}
        results.forEach(place=>{
          const button=document.createElement("button");button.type="button";button.className="meet-choice";
          button.textContent=place.display_name;button.onclick=()=>{
            const value={lat:Number(place.lat),lon:Number(place.lon),name:place.name||place.display_name,display:place.display_name};
            if(which==="from")from=value;else to=value;
            selectedEl.textContent="선택됨: "+value.display;box.innerHTML="";updateButton();
          };box.appendChild(button);
        });
      } catch(error){box.textContent=error.message;}
    }
    function updateButton(){document.getElementById("findMeet").disabled=!(from&&to);}
    document.getElementById("searchFrom").onclick=()=>search("from");
    document.getElementById("searchTo").onclick=()=>search("to");
    document.getElementById("meetFrom").onkeydown=e=>{if(e.key==="Enter"){e.preventDefault();search("from");}};
    document.getElementById("meetTo").onkeydown=e=>{if(e.key==="Enter"){e.preventDefault();search("to");}};
    const progressWrap=document.getElementById("meetProgressWrap");
    const progressBar=document.getElementById("meetProgressBar");
    const progressLabel=document.getElementById("meetProgressLabel");
    const progressPercent=document.getElementById("meetProgressPercent");
    function setProgress(label,percent){
      progressWrap.hidden=false;progressLabel.textContent=label;
      progressBar.style.width=percent+"%";progressPercent.textContent=percent+"%";
      status.textContent=label;
    }
    let isFinding=false;
    document.getElementById("findMeet").onclick=async()=>{
      if(!from||!to||isFinding)return;isFinding=true;const button=document.getElementById("findMeet");button.disabled=true;document.getElementById("meetResults").hidden=false;const mid=geoMidpoint(from,to),items=[],failures=[];
      try{setProgress("중간 지점을 계산하고 있습니다…",8);await loadLeaflet();document.getElementById("meetSummary").innerHTML='<h2>두 위치의 지리적 중간 지점</h2><p><b>'+esc(from.name)+'</b> ↔ <b>'+esc(to.name)+'</b></p><p>위도 '+mid.lat.toFixed(5)+' · 경도 '+mid.lon.toFixed(5)+'</p><p class="muted">직선거리 기준 지리적 중간점입니다.</p>';if(meetMap){meetMap.remove();meetMap=null;}meetMap=L.map("meetMap").setView([mid.lat,mid.lon],13);L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'}).addTo(meetMap);[{p:from,label:"첫 번째 위치"},{p:to,label:"두 번째 위치"}].forEach(({p,label})=>L.marker([p.lat,p.lon]).addTo(meetMap).bindPopup("<b>"+label+"</b><br>"+esc(p.name)));L.circleMarker([mid.lat,mid.lon],{radius:10,color:"#d45d45",fillColor:"#f28a72",fillOpacity:.9}).addTo(meetMap).bindPopup("<b>지리적 중간 지점</b>").openPopup();const layer=L.layerGroup().addTo(meetMap);
      const render=()=>{const root=document.getElementById("meetTransit");root.innerHTML="";[["버스정류장",items.filter(x=>x.type==="버스정류장")],["지하철역",items.filter(x=>x.type==="지하철역")],["기차역",items.filter(x=>x.type==="기차역")]].forEach(([name,list])=>{const sec=document.createElement("section");sec.className="meet-transit-group";const title=document.createElement("h3");title.textContent=name+" ("+list.length+")";sec.appendChild(title);if(!list.length){const p=document.createElement("p");p.className="muted";p.textContent=failures.includes(name)?"조회 실패":"결과 수신 대기 중…";sec.appendChild(p);}list.slice(0,20).forEach(item=>{const p=document.createElement("p");p.className="meet-transit-item";p.textContent=item.name+" · 중간점에서 약 "+item.distance+"m";p.onclick=()=>meetMap?.setView([item.lat,item.lon],16);sec.appendChild(p);});root.appendChild(sec);});if(failures.length){const p=document.createElement("p");p.className="muted";p.textContent="일부 조회 실패: "+failures.join(", ")+" · 성공 결과 유지";root.appendChild(p);}};
      render();setProgress("교통편 정보를 실시간 수신 중…",15);await streamTransit(mid,(category,elements,error)=>{if(category==="done")return;const types={bus:"버스정류장",subway:"지하철역",train:"기차역"},type=types[category];if(error){if(type&&!failures.includes(type))failures.push(type);render();return;}const known=new Set(items.map(x=>x.type+"|"+x.id));normalizeTransitElements(elements,mid,type).forEach(item=>{const key=item.type+"|"+item.id;if(known.has(key))return;known.add(key);items.push(item);const color=item.type==="버스정류장"?"#3978c5":item.type==="지하철역"?"#269b72":"#9b68bd";L.circleMarker([item.lat,item.lon],{radius:6,color,fillColor:color,fillOpacity:.8}).addTo(layer).bindPopup("<b>"+esc(item.name)+"</b><br>"+item.type+" · 약 "+item.distance+"m");});render();setProgress(type+" 결과 수신 · "+items.length+"곳 반영",Math.min(95,25+items.length));},setProgress);meetMap.fitBounds(L.latLngBounds([[from.lat,from.lon],[to.lat,to.lon],[mid.lat,mid.lon]]).pad(.2));setTimeout(()=>meetMap?.invalidateSize(),100);setProgress("조회 완료 · "+items.length+"곳 표시"+(failures.length?" · 일부 조회 실패":""),100);
      }catch(error){status.textContent=error.message;setProgress(items.length?"일부 결과 표시":"조회 실패 · 다시 시도할 수 있습니다.",items.length?100:0);}finally{isFinding=false;button.disabled=false;}
    };
  }

  function showError(error){toast(error?.message||"오류가 발생했습니다.");}
  async function renderRoute() {
    if(activeMiniGameCleanup)activeMiniGameCleanup();
    const parts=route(), page=parts[0];
    if(page==="photos") {activeFolder=new URLSearchParams(location.hash.split("?")[1]||"").get("folder")||"";await renderPhotos();}
    else if(page==="family") await renderFamily();
    else if(page==="folder") await renderFolder(parts);
    else if(page==="google") await renderGoogle();
    else if(page==="contents") await renderContents();
    else if(page==="tetris") await renderTetris();
    else if(page==="utilities"&&parts[1]==="meet") await renderMeet();
    else if(page==="utilities") await renderUtilities();
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
