const STORAGE_KEY_EXPANDED = 'kf_tree_expanded_v1';
const STORAGE_KEY_SELECTED = 'kf_selected_path_v1';
const STORAGE_KEY_SIDEBAR_W = 'kf_sidebar_width_v1';
const STORAGE_KEY_THEME = 'kf_theme_v1';

const appState = {
    selectedPath: null,
    rightMode: 'NONE',
    expandedPaths: new Set(),
    sidebarOpen: true,
    currentSectionPath: null,
    treeData: [],
    me: { login: '', admin: false, canEdit: false },
};

function saveTreeState() {
    try {
        localStorage.setItem(STORAGE_KEY_EXPANDED, JSON.stringify([...appState.expandedPaths]));
    } catch(e) {}
}

function restoreTreeState() {
    try {
        const stored = localStorage.getItem(STORAGE_KEY_EXPANDED);
        if (stored) {
            appState.expandedPaths = new Set(JSON.parse(stored));
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
    try {
        const resp = await fetch(url, opts);
        if (!resp.ok) {
            let errMsg = `HTTP ${resp.status}`;
            try { const j = await resp.json(); errMsg = j.message || j.error || errMsg; } catch(e) {}
            alert(errMsg);
            return null;
        }
        const data = await resp.json();
        return data;
    } catch(e) {
        alert('Ошибка запроса: ' + e.message);
        return null;
    }
}

async function fetchMe() {
    const data = await api('/api/me');
    if (data) {
        appState.me = data;
        if (data.admin) {
            document.getElementById('adminBtn').style.display = '';
        } else {
            document.getElementById('adminBtn').style.display = 'none';
        }
        if (data.canEdit) {
            document.getElementById('fab').style.display = 'flex';
        } else {
            document.getElementById('fab').style.display = 'none';
        }
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

            const childrenDiv = document.createElement('div');
            childrenDiv.className = 'tree-children' + (isExpanded ? ' open' : '');

            label.addEventListener('click', () => {
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

            label.append(arrow, icon, name);
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

            label.addEventListener('click', () => {
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

    const homeLink = document.createElement('a');
    homeLink.textContent = 'Главная';
    homeLink.onclick = () => showRootView();
    bc.appendChild(homeLink);

    const parts = path.split('/');
    let cumulative = '';
    for (let i = 0; i < parts.length; i++) {
        const sep = document.createElement('span');
        sep.className = 'sep';
        sep.textContent = '/';
        bc.appendChild(sep);

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

function hideAllPanels() {
    document.getElementById('placeholder').style.display = 'none';
    document.getElementById('exercisePanel').style.display = 'none';
    document.getElementById('sectionPanel').style.display = 'none';
    document.getElementById('rootPanel').style.display = 'none';
    document.getElementById('adminPanel').style.display = 'none';
}

function showRootView() {
    hideAllPanels();
    appState.rightMode = 'ROOT';
    appState.selectedPath = null;
    appState.currentSectionPath = null;
    document.getElementById('rootPanel').style.display = 'block';
    document.getElementById('breadcrumbs').innerHTML = '';

    const container = document.getElementById('rootContents');
    container.innerHTML = '';

    const rootSections = appState.treeData;
    if (rootSections.length === 0) {
        container.innerHTML = '<div style="color:var(--text-muted);padding:20px;">Пока нет разделов.</div>';
        return;
    }
    for (const s of rootSections) {
        const item = document.createElement('div');
        item.className = 'section-item';
        item.onclick = () => {
            appState.expandedPaths.add(s.path);
            saveTreeState();
            renderTree();
            showSection(s.path);
        };
        item.innerHTML = `<span class="section-item-icon">\uD83D\uDCC1</span><div class="section-item-info"><div class="section-item-name">${esc(s.name)}</div><div class="section-item-type">Раздел</div></div>`;
        container.appendChild(item);
    }

    renderTree();
}

function showSection(path) {
    hideAllPanels();
    appState.rightMode = 'VIEW_SECTION';
    appState.currentSectionPath = path;
    appState.selectedPath = null;

    document.getElementById('sectionPanel').style.display = 'block';

    const node = findNodeByPath(appState.treeData, path);
    document.getElementById('sectionTitle').textContent = node ? node.name : path;

    const actions = document.getElementById('sectionActions');
    actions.style.display = appState.me.canEdit ? 'flex' : 'none';

    renderBreadcrumbs(path);

    const { sections, exercises } = getNodeChildren(path);
    const container = document.getElementById('sectionContents');
    container.innerHTML = '';

    if (sections.length === 0 && exercises.length === 0) {
        container.innerHTML = '<div style="color:var(--text-muted);padding:20px;">Пустой раздел.</div>';
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
        item.innerHTML = `<span class="section-item-icon">\uD83D\uDCC1</span><div class="section-item-info"><div class="section-item-name">${esc(s.name)}</div><div class="section-item-type">Раздел</div></div>`;
        container.appendChild(item);
    }
    for (const ex of exercises) {
        const item = document.createElement('div');
        item.className = 'section-item';
        item.onclick = () => loadExercise(ex.path);
        item.innerHTML = `<span class="section-item-icon">\uD83E\uDD4B</span><div class="section-item-info"><div class="section-item-name">${esc(ex.name)}</div><div class="section-item-type">Упражнение</div></div>`;
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

    hideAllPanels();
    appState.selectedPath = path;
    appState.rightMode = 'VIEW_EXERCISE';
    appState.currentSectionPath = null;
    try { localStorage.setItem(STORAGE_KEY_SELECTED, path); } catch(e) {}

    document.getElementById('exercisePanel').style.display = 'block';
    document.getElementById('exerciseTitle').textContent = data.title;

    const canEdit = appState.me.canEdit;
    document.getElementById('exerciseActions').style.display = canEdit ? 'flex' : 'none';
    document.getElementById('exerciseText').readOnly = !canEdit;
    document.getElementById('exerciseNotes').readOnly = !canEdit;
    document.getElementById('uploadArea').style.display = canEdit ? 'flex' : 'none';

    const saveBtns = document.querySelectorAll('#exercisePanel .edit-btn');
    saveBtns.forEach(b => b.style.display = canEdit ? '' : 'none');

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
        container.innerHTML = '<div style="color:var(--text-muted);font-size:13px;padding:8px 0;">Нет файлов</div>';
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

        const descBlock = document.createElement('div');
        descBlock.className = 'file-description' + (f.description ? '' : ' empty');
        descBlock.textContent = f.description || 'Нет описания';
        descBlock.id = 'desc-' + f.fileName;

        const actions = document.createElement('div');
        actions.className = 'file-actions';

        const openBtn = document.createElement('button');
        openBtn.textContent = 'Открыть';
        openBtn.onclick = () => openMediaModal(f);

        const dlBtn = document.createElement('button');
        dlBtn.textContent = 'Скачать';
        dlBtn.onclick = () => { window.open(f.url, '_blank'); };

        actions.append(openBtn, dlBtn);

        if (appState.me.canEdit) {
            const editDescBtn = document.createElement('button');
            editDescBtn.textContent = 'Описание';
            editDescBtn.onclick = () => toggleDescriptionEdit(f, descBlock);
            actions.appendChild(editDescBtn);

            const delBtn = document.createElement('button');
            delBtn.className = 'btn-del';
            delBtn.textContent = 'Удалить';
            delBtn.onclick = () => deleteFile(f.fileName);
            actions.appendChild(delBtn);
        }

        info.append(fname, fmeta, descBlock, actions);
        card.append(thumb, info);
        container.appendChild(card);
    }
}

function toggleDescriptionEdit(f, descBlock) {
    if (descBlock.querySelector('.file-desc-edit')) return;

    const editDiv = document.createElement('div');
    editDiv.className = 'file-desc-edit';

    const input = document.createElement('input');
    input.type = 'text';
    input.value = f.description || '';
    input.placeholder = 'Введите описание...';

    const saveBtn = document.createElement('button');
    saveBtn.textContent = 'Сохранить';
    saveBtn.onclick = async () => {
        const result = await api('/api/files/description?exercisePath=' + encodeURIComponent(appState.selectedPath) + '&fileName=' + encodeURIComponent(f.fileName), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description: input.value })
        });
        if (result) {
            f.description = input.value;
            descBlock.textContent = input.value || 'Нет описания';
            descBlock.className = 'file-description' + (input.value ? '' : ' empty');
        }
        editDiv.remove();
    };

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'cancel-btn';
    cancelBtn.textContent = 'Отмена';
    cancelBtn.onclick = () => editDiv.remove();

    editDiv.append(input, saveBtn, cancelBtn);
    descBlock.textContent = '';
    descBlock.className = 'file-description';
    descBlock.appendChild(editDiv);
    input.focus();
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
        a.textContent = 'Скачать: ' + f.fileName;
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
    if (bytes < 1024) return bytes + ' Б';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' КБ';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' МБ';
    return (bytes / 1073741824).toFixed(2) + ' ГБ';
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
    if (!input.files.length) { alert('Выберите файлы'); return; }
    const fd = new FormData();
    for (const f of input.files) fd.append('files', f);
    const resp = await fetch('/api/files/upload?exercisePath=' + encodeURIComponent(appState.selectedPath), {
        method: 'POST',
        body: fd
    });
    if (resp.ok) {
        input.value = '';
        loadExercise(appState.selectedPath);
    } else {
        alert('Ошибка загрузки');
    }
}

async function deleteFile(fileName) {
    if (!confirm('Удалить файл «' + fileName + '»?')) return;
    const url = '/api/files?exercisePath=' + encodeURIComponent(appState.selectedPath) + '&fileName=' + encodeURIComponent(fileName);
    await api(url, { method: 'DELETE' });
    loadExercise(appState.selectedPath);
}

async function promptCreateSection(parentPath) {
    const title = prompt('Название нового раздела:');
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
    await loadTree();
    if (appState.rightMode === 'ROOT') showRootView();
    else if (appState.rightMode === 'VIEW_SECTION' && appState.currentSectionPath) showSection(appState.currentSectionPath);
}

function promptAddSubSection() {
    if (!appState.currentSectionPath) return;
    promptCreateSection(appState.currentSectionPath);
}

async function promptAddExerciseInSection() {
    if (!appState.currentSectionPath) return;
    promptCreateExercise(appState.currentSectionPath);
}

async function promptCreateExercise(sectionPath) {
    const title = prompt('Название нового упражнения:');
    if (!title) return;
    await api('/api/exercises', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sectionPath, title })
    });
    appState.expandedPaths.add(sectionPath);
    saveTreeState();
    await loadTree();
    if (appState.rightMode === 'VIEW_SECTION' && appState.currentSectionPath) showSection(appState.currentSectionPath);
}

async function promptRenameSection(path, currentName) {
    const newTitle = prompt('Переименовать раздел:', currentName);
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
    await loadTree();
    if (appState.currentSectionPath) showSection(appState.currentSectionPath);
}

async function promptDeleteSection(path) {
    if (!confirm('Удалить раздел «' + path + '» и всё содержимое?')) return;
    await api('/api/sections?path=' + encodeURIComponent(path), { method: 'DELETE' });
    appState.expandedPaths.delete(path);
    saveTreeState();
    if (appState.selectedPath && appState.selectedPath.startsWith(path)) {
        appState.selectedPath = null;
    }
    if (appState.currentSectionPath && appState.currentSectionPath.startsWith(path)) {
        appState.currentSectionPath = null;
    }
    await loadTree();
    showRootView();
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
    const newTitle = prompt('Переименовать упражнение:', currentName);
    if (!newTitle || newTitle === currentName) return;
    api('/api/exercises/rename', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: appState.selectedPath, newTitle })
    }).then(async data => {
        if (data && data.path) {
            appState.selectedPath = data.path;
            await loadTree();
            loadExercise(appState.selectedPath);
        }
    });
}

function promptDeleteExercise() {
    if (!appState.selectedPath) return;
    if (!confirm('Удалить упражнение «' + appState.selectedPath + '»?')) return;
    api('/api/exercises?path=' + encodeURIComponent(appState.selectedPath), { method: 'DELETE' }).then(async () => {
        appState.selectedPath = null;
        appState.rightMode = 'NONE';
        await loadTree();
        showRootView();
    });
}

function toggleFabMenu() {
    const menu = document.getElementById('fabMenu');
    const fab = document.getElementById('fab');
    const visible = menu.style.display !== 'none';
    menu.style.display = visible ? 'none' : 'block';
    fab.classList.toggle('open', !visible);
}

function closeFabMenu() {
    document.getElementById('fabMenu').style.display = 'none';
    document.getElementById('fab').classList.remove('open');
}

function fabAddSection() {
    closeFabMenu();
    if (appState.rightMode === 'VIEW_SECTION' && appState.currentSectionPath) {
        promptCreateSection(appState.currentSectionPath);
    } else {
        promptCreateSection('');
    }
}

function fabAddExercise() {
    closeFabMenu();
    let sectionPath = null;
    if (appState.rightMode === 'VIEW_SECTION' && appState.currentSectionPath) {
        sectionPath = appState.currentSectionPath;
    } else if (appState.rightMode === 'VIEW_EXERCISE' && appState.selectedPath) {
        const parts = appState.selectedPath.split('/');
        parts.pop();
        sectionPath = parts.join('/');
    }
    if (sectionPath) {
        promptCreateExercise(sectionPath);
    } else {
        alert('Сначала выберите раздел, в который нужно добавить упражнение.');
    }
}

async function showAdminPanel() {
    hideAllPanels();
    appState.rightMode = 'ADMIN';
    document.getElementById('adminPanel').style.display = 'block';
    document.getElementById('breadcrumbs').innerHTML = '';
    await loadAdminUsers();
}

function closeAdminPanel() {
    showRootView();
}

async function loadAdminUsers() {
    const users = await api('/api/admin/users');
    if (!users) return;
    const container = document.getElementById('adminUserList');
    container.innerHTML = '';
    for (const u of users) {
        const row = document.createElement('div');
        row.className = 'admin-user-row';

        const loginDiv = document.createElement('div');
        loginDiv.className = 'admin-user-login';
        loginDiv.textContent = u.login;

        const flags = document.createElement('div');
        flags.className = 'admin-user-flags';
        if (u.admin) {
            const badge = document.createElement('span');
            badge.className = 'role-admin';
            badge.textContent = 'Администратор';
            flags.appendChild(badge);
        }
        if (u.canEdit) {
            const badge = document.createElement('span');
            badge.className = 'role-editor';
            badge.textContent = 'Редактирование';
            flags.appendChild(badge);
        }
        if (!u.canEdit) {
            const badge = document.createElement('span');
            badge.textContent = 'Только просмотр';
            flags.appendChild(badge);
        }

        const actions = document.createElement('div');
        actions.className = 'admin-user-actions';

        const showPwBtn = document.createElement('button');
        showPwBtn.className = 'btn-sm';
        showPwBtn.textContent = 'Показать пароль';
        showPwBtn.onclick = async () => {
            const data = await api('/api/admin/users/password?login=' + encodeURIComponent(u.login));
            if (data) {
                showPwBtn.textContent = data.password;
                setTimeout(() => { showPwBtn.textContent = 'Показать пароль'; }, 5000);
            }
        };

        const toggleAdminBtn = document.createElement('button');
        toggleAdminBtn.className = 'btn-sm';
        toggleAdminBtn.textContent = u.admin ? 'Снять админа' : 'Сделать админом';
        toggleAdminBtn.onclick = async () => {
            await api('/api/admin/users', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ login: u.login, admin: !u.admin })
            });
            loadAdminUsers();
        };

        const toggleEditBtn = document.createElement('button');
        toggleEditBtn.className = 'btn-sm';
        toggleEditBtn.textContent = u.canEdit ? 'Запретить редактирование' : 'Разрешить редактирование';
        toggleEditBtn.onclick = async () => {
            await api('/api/admin/users', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ login: u.login, canEdit: !u.canEdit })
            });
            loadAdminUsers();
        };

        const changePwBtn = document.createElement('button');
        changePwBtn.className = 'btn-sm';
        changePwBtn.textContent = 'Сменить пароль';
        changePwBtn.onclick = async () => {
            const newPw = prompt('Новый пароль для ' + u.login + ':');
            if (!newPw) return;
            await api('/api/admin/users', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ login: u.login, password: newPw })
            });
            loadAdminUsers();
        };

        const delBtn = document.createElement('button');
        delBtn.className = 'btn-sm btn-danger';
        delBtn.textContent = 'Удалить';
        delBtn.onclick = async () => {
            if (u.login === appState.me.login) {
                if (!confirm('Вы собираетесь удалить свой аккаунт! Продолжить?')) return;
            } else {
                if (!confirm('Удалить пользователя «' + u.login + '»?')) return;
            }
            await api('/api/admin/users?login=' + encodeURIComponent(u.login), { method: 'DELETE' });
            loadAdminUsers();
        };

        actions.append(showPwBtn, toggleAdminBtn, toggleEditBtn, changePwBtn, delBtn);
        row.append(loginDiv, flags, actions);
        container.appendChild(row);
    }
}

async function addUser() {
    const login = document.getElementById('newUserLogin').value.trim();
    const password = document.getElementById('newUserPassword').value.trim();
    const admin = document.getElementById('newUserAdmin').checked;
    const canEdit = document.getElementById('newUserCanEdit').checked;
    if (!login || !password) { alert('Введите логин и пароль'); return; }
    const result = await api('/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login, password, admin, canEdit })
    });
    if (result) {
        document.getElementById('newUserLogin').value = '';
        document.getElementById('newUserPassword').value = '';
        document.getElementById('newUserAdmin').checked = false;
        document.getElementById('newUserCanEdit').checked = true;
        loadAdminUsers();
    }
}

function showSettingsModal() {
    document.getElementById('settingsModal').style.display = 'flex';
    document.getElementById('oldPassword').value = '';
    document.getElementById('newPassword').value = '';
    document.getElementById('confirmPassword').value = '';
    switchSettingsTab('password', document.querySelector('.settings-tab'));
    updateThemeCards();
}

function closeSettingsModal() {
    document.getElementById('settingsModal').style.display = 'none';
}

function switchSettingsTab(tab, btnEl) {
    document.querySelectorAll('.settings-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.settings-section').forEach(s => s.classList.remove('active'));
    btnEl.classList.add('active');
    if (tab === 'password') {
        document.getElementById('settingsPassword').classList.add('active');
        document.getElementById('oldPassword').focus();
    } else {
        document.getElementById('settingsTheme').classList.add('active');
        updateThemeCards();
    }
}

function getCurrentTheme() {
    return localStorage.getItem(STORAGE_KEY_THEME) || 'midnight';
}

function setTheme(theme) {
    if (theme === 'midnight') {
        document.documentElement.removeAttribute('data-theme');
    } else {
        document.documentElement.setAttribute('data-theme', theme);
    }
    localStorage.setItem(STORAGE_KEY_THEME, theme);
    updateThemeCards();
}

function updateThemeCards() {
    const current = getCurrentTheme();
    document.querySelectorAll('.theme-card').forEach(card => {
        const onclick = card.getAttribute('onclick');
        const match = onclick && onclick.match(/setTheme\('(\w+)'\)/);
        if (match) {
            card.classList.toggle('active', match[1] === current);
        }
    });
}

function applyStoredTheme() {
    const theme = getCurrentTheme();
    if (theme && theme !== 'midnight') {
        document.documentElement.setAttribute('data-theme', theme);
    }
}

async function changeMyPassword() {
    const oldPw = document.getElementById('oldPassword').value;
    const newPw = document.getElementById('newPassword').value;
    const confirmPw = document.getElementById('confirmPassword').value;
    if (!oldPw || !newPw) { alert('Заполните все поля'); return; }
    if (newPw !== confirmPw) { alert('Новый пароль и повтор не совпадают'); return; }
    const result = await api('/api/me/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ oldPassword: oldPw, newPassword: newPw })
    });
    if (result) {
        alert('Пароль изменён');
        closeSettingsModal();
    }
}

function initResizeHandle() {
    if (isMobile()) return;
    const handle = document.getElementById('resizeHandle');
    const sidebar = document.getElementById('sidebar');
    let isResizing = false;

    const savedWidth = localStorage.getItem(STORAGE_KEY_SIDEBAR_W);
    if (savedWidth) {
        const w = parseInt(savedWidth, 10);
        if (w >= 220 && w <= 600) {
            sidebar.style.width = w + 'px';
        }
    }

    handle.addEventListener('mousedown', (e) => {
        isResizing = true;
        handle.classList.add('active');
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
        e.preventDefault();
    });

    document.addEventListener('mousemove', (e) => {
        if (!isResizing) return;
        let newWidth = e.clientX;
        if (newWidth < 220) newWidth = 220;
        if (newWidth > 600) newWidth = 600;
        sidebar.style.width = newWidth + 'px';
    });

    document.addEventListener('mouseup', () => {
        if (!isResizing) return;
        isResizing = false;
        handle.classList.remove('active');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        try {
            localStorage.setItem(STORAGE_KEY_SIDEBAR_W, sidebar.offsetWidth);
        } catch(e) {}
    });
}

document.addEventListener('click', (e) => {
    const fabMenu = document.getElementById('fabMenu');
    const fab = document.getElementById('fab');
    if (fabMenu.style.display !== 'none' && !fab.contains(e.target) && !fabMenu.contains(e.target)) {
        closeFabMenu();
    }
});

document.addEventListener('DOMContentLoaded', async () => {
    applyStoredTheme();
    restoreTreeState();
    initResizeHandle();
    await fetchMe();
    await loadTree();
    showRootView();
});

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeModalForce();
        closeSettingsModal();
        closeFabMenu();
    }
});
