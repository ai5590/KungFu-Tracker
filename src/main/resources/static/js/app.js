const STORAGE_KEY_EXPANDED = 'kf_tree_expanded_v1';
const STORAGE_KEY_SELECTED = 'kf_selected_path_v1';

const appState = {
    selectedPath: null,
    rightMode: 'NONE',
    expandedPaths: new Set(),
    sidebarOpen: true,
    currentSectionPath: null,
    treeData: [],
};

function log(msg) {
    const el = document.getElementById('logContent');
    if (!el) return;
    const ts = new Date().toLocaleTimeString();
    el.textContent += `[${ts}] ${msg}\n`;
    el.scrollTop = el.scrollHeight;
}

function saveTreeState() {
    try {
        localStorage.setItem(STORAGE_KEY_EXPANDED, JSON.stringify([...appState.expandedPaths]));
        console.log('[state] Saved expandedPaths:', appState.expandedPaths.size, 'nodes');
    } catch(e) {}
}

function restoreTreeState() {
    try {
        const stored = localStorage.getItem(STORAGE_KEY_EXPANDED);
        if (stored) {
            const arr = JSON.parse(stored);
            appState.expandedPaths = new Set(arr);
            console.log('[state] Restored expandedPaths:', appState.expandedPaths.size, 'nodes');
        }
        const selPath = localStorage.getItem(STORAGE_KEY_SELECTED);
        if (selPath) appState.selectedPath = selPath;
    } catch(e) {}
}

function isMobile() { return window.innerWidth <= 900; }

function toggleSidebar() {
    if (isMobile()) {
        const sb = document.getElementById('sidebar');
        const ov = document.getElementById('sidebarOverlay');
        const isOpen = sb.classList.contains('mobile-open');
        sb.classList.toggle('mobile-open', !isOpen);
        ov.classList.toggle('visible', !isOpen);
    } else {
        const sb = document.getElementById('sidebar');
        sb.classList.toggle('collapsed');
        appState.sidebarOpen = !sb.classList.contains('collapsed');
    }
}

function closeSidebar() {
    const sb = document.getElementById('sidebar');
    const ov = document.getElementById('sidebarOverlay');
    sb.classList.remove('mobile-open');
    ov.classList.remove('visible');
}

async function api(url, opts = {}) {
    const method = opts.method || 'GET';
    log(`${method} ${url}`);
    try {
        const resp = await fetch(url, opts);
        if (!resp.ok) {
            let errMsg = `HTTP ${resp.status}`;
            try { const j = await resp.json(); errMsg = j.message || j.error || errMsg; } catch(e) {}
            log(`ERROR: ${errMsg}`);
            alert(errMsg);
            return null;
        }
        const data = await resp.json();
        log(`OK ${resp.status}`);
        return data;
    } catch(e) {
        log(`FETCH ERROR: ${e.message}`);
        alert('Request failed: ' + e.message);
        return null;
    }
}

async function loadTree() {
    const data = await api('/api/tree');
    if (!data) return;
    appState.treeData = data;
    renderTree();
}

function renderTree() {
    const container = document.getElementById('tree');
    container.innerHTML = '';
    renderNodes(appState.treeData, container);
}

function renderNodes(nodes, container) {
    for (const node of nodes) {
        const div = document.createElement('div');
        div.className = 'tree-node';

        const label = document.createElement('div');
        label.className = 'tree-label';
        if (node.nodeType === 'EXERCISE' && node.path === appState.selectedPath && appState.rightMode === 'VIEW_EXERCISE') {
            label.classList.add('active');
        }
        if (node.nodeType === 'SECTION' && node.path === appState.currentSectionPath && appState.rightMode === 'VIEW_SECTION') {
            label.classList.add('section-active');
        }

        if (node.nodeType === 'SECTION') {
            const isExpanded = appState.expandedPaths.has(node.path);

            const arrow = document.createElement('span');
            arrow.className = 'tree-arrow' + (isExpanded ? ' open' : '');
            arrow.textContent = '\u25B6';

            const icon = document.createElement('span');
            icon.className = 'tree-icon';
            icon.textContent = isExpanded ? '\uD83D\uDCC2' : '\uD83D\uDCC1';

            const name = document.createElement('span');
            name.className = 'tree-name';
            name.textContent = node.name;

            const ctx = document.createElement('span');
            ctx.className = 'tree-ctx';
            ctx.innerHTML = `<button title="Add sub-section">+S</button><button title="Add exercise">+E</button><button title="Rename">\u270F</button><button title="Delete">\u2715</button>`;
            const ctxBtns = ctx.querySelectorAll('button');
            ctxBtns[0].onclick = (e) => { e.stopPropagation(); promptCreateSection(node.path); };
            ctxBtns[1].onclick = (e) => { e.stopPropagation(); promptCreateExercise(node.path); };
            ctxBtns[2].onclick = (e) => { e.stopPropagation(); promptRenameSection(node.path, node.name); };
            ctxBtns[3].onclick = (e) => { e.stopPropagation(); promptDeleteSection(node.path); };

            const childrenDiv = document.createElement('div');
            childrenDiv.className = 'tree-children' + (isExpanded ? ' open' : '');

            label.addEventListener('click', (e) => {
                if (e.target.tagName === 'BUTTON') return;
                const nowExpanded = appState.expandedPaths.has(node.path);
                if (nowExpanded) {
                    appState.expandedPaths.delete(node.path);
                } else {
                    appState.expandedPaths.add(node.path);
                }
                saveTreeState();
                renderTree();
                showSection(node.path);
            });

            label.append(arrow, icon, name, ctx);
            div.append(label, childrenDiv);

            if (node.children && node.children.length > 0) {
                renderNodes(node.children, childrenDiv);
            }
        } else {
            const arrow = document.createElement('span');
            arrow.className = 'tree-arrow';
            arrow.textContent = '';

            const icon = document.createElement('span');
            icon.className = 'tree-icon';
            icon.textContent = '\uD83E\uDD4B';

            const name = document.createElement('span');
            name.className = 'tree-name';
            name.textContent = node.name;

            label.addEventListener('click', (e) => {
                if (e.target.tagName === 'BUTTON') return;
                loadExercise(node.path);
                if (isMobile()) closeSidebar();
            });

            label.append(arrow, icon, name);
            div.append(label);
        }

        container.appendChild(div);
    }
}

function findNodeByPath(nodes, path) {
    for (const n of nodes) {
        if (n.path === path) return n;
        if (n.children) {
            const found = findNodeByPath(n.children, path);
            if (found) return found;
        }
    }
    return null;
}

function getNodeChildren(path) {
    const node = findNodeByPath(appState.treeData, path);
    if (!node || node.nodeType !== 'SECTION') return { sections: [], exercises: [] };
    const sections = [];
    const exercises = [];
    if (node.children) {
        for (const c of node.children) {
            if (c.nodeType === 'SECTION') sections.push(c);
            else exercises.push(c);
        }
    }
    return { sections, exercises };
}

function renderBreadcrumbs(path) {
    const bc = document.getElementById('breadcrumbs');
    bc.innerHTML = '';
    if (!path) return;

    const parts = path.split('/');
    let cumulative = '';
    for (let i = 0; i < parts.length; i++) {
        if (i > 0) {
            const sep = document.createElement('span');
            sep.className = 'sep';
            sep.textContent = '/';
            bc.appendChild(sep);
        }
        cumulative += (i > 0 ? '/' : '') + parts[i];
        const segPath = cumulative;

        if (i === parts.length - 1) {
            const span = document.createElement('span');
            span.className = 'current';
            span.textContent = parts[i];
            bc.appendChild(span);
        } else {
            const a = document.createElement('a');
            a.textContent = parts[i];
            a.onclick = () => {
                const node = findNodeByPath(appState.treeData, segPath);
                if (node && node.nodeType === 'SECTION') showSection(segPath);
                else if (node && node.nodeType === 'EXERCISE') loadExercise(segPath);
            };
            bc.appendChild(a);
        }
    }
}

function showSection(path) {
    appState.rightMode = 'VIEW_SECTION';
    appState.currentSectionPath = path;
    appState.selectedPath = null;

    document.getElementById('placeholder').style.display = 'none';
    document.getElementById('exercisePanel').style.display = 'none';
    document.getElementById('sectionPanel').style.display = 'block';

    const node = findNodeByPath(appState.treeData, path);
    document.getElementById('sectionTitle').textContent = node ? node.name : path;

    renderBreadcrumbs(path);

    const { sections, exercises } = getNodeChildren(path);
    const container = document.getElementById('sectionContents');
    container.innerHTML = '';

    if (sections.length === 0 && exercises.length === 0) {
        container.innerHTML = '<div style="color:var(--text-muted);padding:20px;">Empty section. Use the tree to add content.</div>';
        return;
    }

    for (const s of sections) {
        const item = document.createElement('div');
        item.className = 'section-item';
        item.onclick = () => {
            appState.expandedPaths.add(s.path);
            saveTreeState();
            renderTree();
            showSection(s.path);
        };
        item.innerHTML = `<span class="section-item-icon">\uD83D\uDCC1</span><div class="section-item-info"><div class="section-item-name">${esc(s.name)}</div><div class="section-item-type">Section</div></div>`;
        container.appendChild(item);
    }
    for (const ex of exercises) {
        const item = document.createElement('div');
        item.className = 'section-item';
        item.onclick = () => loadExercise(ex.path);
        item.innerHTML = `<span class="section-item-icon">\uD83E\uDD4B</span><div class="section-item-info"><div class="section-item-name">${esc(ex.name)}</div><div class="section-item-type">Exercise</div></div>`;
        container.appendChild(item);
    }

    renderTree();
}

function esc(str) {
    const d = document.createElement('div');
    d.textContent = str;
    return d.innerHTML;
}

async function loadExercise(path) {
    const data = await api('/api/exercises?path=' + encodeURIComponent(path));
    if (!data) return;

    appState.selectedPath = path;
    appState.rightMode = 'VIEW_EXERCISE';
    appState.currentSectionPath = null;
    try { localStorage.setItem(STORAGE_KEY_SELECTED, path); } catch(e) {}

    document.getElementById('placeholder').style.display = 'none';
    document.getElementById('sectionPanel').style.display = 'none';
    document.getElementById('exercisePanel').style.display = 'block';
    document.getElementById('exerciseTitle').textContent = data.title;
    document.getElementById('exerciseText').value = data.text || '';
    document.getElementById('exerciseNotes').value = data.notes || '';
    renderFiles(data.files);

    renderBreadcrumbs(path);

    const parts = path.split('/');
    for (let i = 1; i < parts.length; i++) {
        const parentPath = parts.slice(0, i).join('/');
        appState.expandedPaths.add(parentPath);
    }
    saveTreeState();
    renderTree();

    if (isMobile()) closeSidebar();
}

function renderFiles(files) {
    const container = document.getElementById('fileList');
    container.innerHTML = '';
    if (!files || files.length === 0) {
        container.innerHTML = '<div style="color:var(--text-muted);font-size:13px;padding:8px 0;">No files yet</div>';
        return;
    }
    for (const f of files) {
        const card = document.createElement('div');
        card.className = 'file-card';

        const thumb = document.createElement('div');
        thumb.className = 'file-thumb';

        if (f.contentType && f.contentType.startsWith('video/')) {
            const vid = document.createElement('video');
            vid.muted = true;
            vid.preload = 'metadata';
            vid.src = f.url;
            thumb.appendChild(vid);
        } else if (f.contentType && f.contentType.startsWith('image/')) {
            const img = document.createElement('img');
            img.src = f.url;
            img.loading = 'lazy';
            thumb.appendChild(img);
        } else {
            const ico = document.createElement('span');
            ico.className = 'file-type-icon';
            ico.textContent = getFileIcon(f.contentType, f.fileName);
            thumb.appendChild(ico);
        }

        const info = document.createElement('div');
        info.className = 'file-info';

        const fname = document.createElement('div');
        fname.className = 'file-name';
        fname.textContent = f.fileName;

        const fmeta = document.createElement('div');
        fmeta.className = 'file-meta';
        fmeta.textContent = formatSize(f.size) + ' \u00B7 ' + (f.contentType || 'unknown');

        const actions = document.createElement('div');
        actions.className = 'file-actions';

        const openBtn = document.createElement('button');
        openBtn.textContent = 'Open';
        openBtn.onclick = () => openMediaModal(f);

        const dlBtn = document.createElement('button');
        dlBtn.textContent = 'Download';
        dlBtn.onclick = () => { window.open(f.url, '_blank'); };

        const delBtn = document.createElement('button');
        delBtn.className = 'btn-del';
        delBtn.textContent = 'Delete';
        delBtn.onclick = () => deleteFile(f.fileName);

        actions.append(openBtn, dlBtn, delBtn);
        info.append(fname, fmeta, actions);
        card.append(thumb, info);
        container.appendChild(card);
    }
}

function getFileIcon(contentType, fileName) {
    if (!contentType) return '\uD83D\uDCC4';
    if (contentType.includes('pdf')) return '\uD83D\uDCC4';
    if (contentType.includes('text')) return '\uD83D\uDCDD';
    if (contentType.includes('zip') || contentType.includes('archive')) return '\uD83D\uDDDC';
    return '\uD83D\uDCC4';
}

function openMediaModal(f) {
    const modal = document.getElementById('modal');
    const content = document.getElementById('modalContent');
    content.innerHTML = '';

    if (f.contentType && f.contentType.startsWith('video/')) {
        const video = document.createElement('video');
        video.controls = true;
        video.autoplay = true;
        video.preload = 'metadata';
        video.src = f.url;
        content.appendChild(video);
    } else if (f.contentType && f.contentType.startsWith('image/')) {
        const img = document.createElement('img');
        img.src = f.url;
        content.appendChild(img);
    } else {
        const a = document.createElement('a');
        a.href = f.url;
        a.target = '_blank';
        a.textContent = 'Download: ' + f.fileName;
        a.style.color = '#fff';
        a.style.fontSize = '18px';
        content.appendChild(a);
    }

    modal.style.display = 'flex';
}

function closeModal(event) {
    if (event.target === document.getElementById('modal')) {
        closeModalForce();
    }
}
function closeModalForce() {
    const modal = document.getElementById('modal');
    const content = document.getElementById('modalContent');
    const vid = content.querySelector('video');
    if (vid) vid.pause();
    modal.style.display = 'none';
    content.innerHTML = '';
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(2) + ' GB';
}

async function saveText() {
    if (!appState.selectedPath) return;
    const text = document.getElementById('exerciseText').value;
    await api('/api/exercises/text?path=' + encodeURIComponent(appState.selectedPath), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
    });
}

async function saveNotes() {
    if (!appState.selectedPath) return;
    const notes = document.getElementById('exerciseNotes').value;
    await api('/api/exercises/notes?path=' + encodeURIComponent(appState.selectedPath), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes })
    });
}

async function uploadFiles() {
    if (!appState.selectedPath) return;
    const input = document.getElementById('fileInput');
    if (!input.files.length) { alert('Select files first'); return; }
    const fd = new FormData();
    for (const f of input.files) fd.append('files', f);
    log(`Uploading ${input.files.length} file(s)...`);
    const resp = await fetch('/api/files/upload?exercisePath=' + encodeURIComponent(appState.selectedPath), {
        method: 'POST',
        body: fd
    });
    if (resp.ok) {
        log('Upload OK');
        input.value = '';
        loadExercise(appState.selectedPath);
    } else {
        log('Upload failed: ' + resp.status);
        alert('Upload failed');
    }
}

async function deleteFile(fileName) {
    if (!confirm('Delete file "' + fileName + '"?')) return;
    const url = '/api/files?exercisePath=' + encodeURIComponent(appState.selectedPath) + '&fileName=' + encodeURIComponent(fileName);
    await api(url, { method: 'DELETE' });
    loadExercise(appState.selectedPath);
}

async function promptCreateSection(parentPath) {
    const title = prompt('New section name:');
    if (!title) return;
    await api('/api/sections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ parentPath: parentPath || '', title })
    });
    if (parentPath) {
        appState.expandedPaths.add(parentPath);
        saveTreeState();
    }
    loadTree();
}

async function promptCreateExercise(sectionPath) {
    const title = prompt('New exercise name:');
    if (!title) return;
    await api('/api/exercises', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sectionPath, title })
    });
    appState.expandedPaths.add(sectionPath);
    saveTreeState();
    loadTree();
}

async function promptRenameSection(path, currentName) {
    const newTitle = prompt('Rename section:', currentName);
    if (!newTitle || newTitle === currentName) return;
    const result = await api('/api/sections/rename', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path, newTitle })
    });
    if (result && result.path) {
        appState.expandedPaths.delete(path);
        appState.expandedPaths.add(result.path);
        saveTreeState();
        if (appState.currentSectionPath === path) {
            appState.currentSectionPath = result.path;
        }
    }
    loadTree();
}

async function promptDeleteSection(path) {
    if (!confirm('Delete section "' + path + '" and all its contents?')) return;
    await api('/api/sections?path=' + encodeURIComponent(path), { method: 'DELETE' });
    appState.expandedPaths.delete(path);
    saveTreeState();
    if (appState.selectedPath && appState.selectedPath.startsWith(path)) {
        appState.selectedPath = null;
        appState.rightMode = 'NONE';
        showPlaceholder();
    }
    if (appState.currentSectionPath && appState.currentSectionPath.startsWith(path)) {
        appState.currentSectionPath = null;
        appState.rightMode = 'NONE';
        showPlaceholder();
    }
    loadTree();
}

function promptRenameSectionFromPanel() {
    if (!appState.currentSectionPath) return;
    const node = findNodeByPath(appState.treeData, appState.currentSectionPath);
    const currentName = node ? node.name : '';
    promptRenameSection(appState.currentSectionPath, currentName);
}

function promptDeleteSectionFromPanel() {
    if (!appState.currentSectionPath) return;
    promptDeleteSection(appState.currentSectionPath);
}

function promptRenameExercise() {
    if (!appState.selectedPath) return;
    const currentName = document.getElementById('exerciseTitle').textContent;
    const newTitle = prompt('Rename exercise:', currentName);
    if (!newTitle || newTitle === currentName) return;
    api('/api/exercises/rename', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: appState.selectedPath, newTitle })
    }).then(data => {
        if (data && data.path) {
            appState.selectedPath = data.path;
            loadExercise(appState.selectedPath);
        }
    });
}

function promptDeleteExercise() {
    if (!appState.selectedPath) return;
    if (!confirm('Delete exercise "' + appState.selectedPath + '"?')) return;
    api('/api/exercises?path=' + encodeURIComponent(appState.selectedPath), { method: 'DELETE' }).then(() => {
        appState.selectedPath = null;
        appState.rightMode = 'NONE';
        showPlaceholder();
        loadTree();
    });
}

function showPlaceholder() {
    document.getElementById('exercisePanel').style.display = 'none';
    document.getElementById('sectionPanel').style.display = 'none';
    document.getElementById('placeholder').style.display = 'flex';
    document.getElementById('breadcrumbs').innerHTML = '';
}

document.addEventListener('DOMContentLoaded', () => {
    restoreTreeState();
    loadTree();
    console.log('[init] App state initialized, expandedPaths:', appState.expandedPaths.size);
});

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeModalForce();
});
