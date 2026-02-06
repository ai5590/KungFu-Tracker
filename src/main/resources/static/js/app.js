let currentExercisePath = null;

function log(msg) {
    const el = document.getElementById('logContent');
    const ts = new Date().toLocaleTimeString();
    el.textContent += `[${ts}] ${msg}\n`;
    el.scrollTop = el.scrollHeight;
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
    const container = document.getElementById('tree');
    container.innerHTML = '';
    renderNodes(data, container);
}

function renderNodes(nodes, container) {
    for (const node of nodes) {
        const div = document.createElement('div');
        div.className = 'tree-node';

        const label = document.createElement('div');
        label.className = 'tree-label';
        if (node.path === currentExercisePath) label.classList.add('selected');

        const icon = document.createElement('span');
        icon.className = 'icon';

        const name = document.createElement('span');
        name.textContent = node.name;

        const ctxBtns = document.createElement('span');
        ctxBtns.className = 'ctx-btns';

        if (node.nodeType === 'SECTION') {
            icon.textContent = '📁';
            const childrenDiv = document.createElement('div');
            childrenDiv.className = 'tree-children';
            childrenDiv.style.display = 'none';

            label.addEventListener('click', (e) => {
                if (e.target.tagName === 'BUTTON') return;
                const open = childrenDiv.style.display !== 'none';
                childrenDiv.style.display = open ? 'none' : 'block';
                icon.textContent = open ? '📁' : '📂';
            });

            const addSecBtn = document.createElement('button');
            addSecBtn.textContent = '+S';
            addSecBtn.title = 'Add sub-section';
            addSecBtn.onclick = (e) => { e.stopPropagation(); promptCreateSection(node.path); };

            const addExBtn = document.createElement('button');
            addExBtn.textContent = '+E';
            addExBtn.title = 'Add exercise';
            addExBtn.onclick = (e) => { e.stopPropagation(); promptCreateExercise(node.path); };

            const renBtn = document.createElement('button');
            renBtn.textContent = '✏';
            renBtn.title = 'Rename';
            renBtn.onclick = (e) => { e.stopPropagation(); promptRenameSection(node.path, node.name); };

            const delBtn = document.createElement('button');
            delBtn.textContent = '✕';
            delBtn.title = 'Delete';
            delBtn.onclick = (e) => { e.stopPropagation(); promptDeleteSection(node.path); };

            ctxBtns.append(addSecBtn, addExBtn, renBtn, delBtn);
            label.append(icon, name, ctxBtns);
            div.append(label, childrenDiv);

            if (node.children && node.children.length > 0) {
                renderNodes(node.children, childrenDiv);
            }
        } else {
            icon.textContent = '🥋';
            label.addEventListener('click', (e) => {
                if (e.target.tagName === 'BUTTON') return;
                loadExercise(node.path);
            });
            label.append(icon, name, ctxBtns);
            div.append(label);
        }

        container.appendChild(div);
    }
}

async function loadExercise(path) {
    const data = await api('/api/exercises?path=' + encodeURIComponent(path));
    if (!data) return;
    currentExercisePath = path;
    document.getElementById('placeholder').style.display = 'none';
    document.getElementById('exercisePanel').style.display = 'block';
    document.getElementById('exerciseTitle').textContent = data.title;
    document.getElementById('exerciseText').value = data.text || '';
    document.getElementById('exerciseNotes').value = data.notes || '';
    renderFiles(data.files);
    document.getElementById('mediaViewer').style.display = 'none';
    document.getElementById('mediaViewer').innerHTML = '';

    document.querySelectorAll('.tree-label.selected').forEach(el => el.classList.remove('selected'));
    document.querySelectorAll('.tree-label').forEach(el => {
        const nameEl = el.querySelector('span:nth-child(2)');
        if (nameEl && el.closest('.tree-node')) {
            // re-select will happen on tree reload
        }
    });
    loadTree();
}

function renderFiles(files) {
    const container = document.getElementById('fileList');
    container.innerHTML = '';
    if (!files || files.length === 0) {
        container.innerHTML = '<div style="color:#666;font-size:13px">No files yet</div>';
        return;
    }
    for (const f of files) {
        const div = document.createElement('div');
        div.className = 'file-item';

        const fname = document.createElement('span');
        fname.className = 'fname';
        fname.textContent = f.fileName;

        const fsize = document.createElement('span');
        fsize.className = 'fsize';
        fsize.textContent = formatSize(f.size);

        const openBtn = document.createElement('button');
        openBtn.textContent = 'Open';
        openBtn.onclick = () => openMedia(f);

        const dlBtn = document.createElement('button');
        dlBtn.textContent = 'Download';
        dlBtn.onclick = () => { window.open(f.url, '_blank'); };

        const delBtn = document.createElement('button');
        delBtn.className = 'btn-del';
        delBtn.textContent = 'Delete';
        delBtn.onclick = () => deleteFile(f.fileName);

        div.append(fname, fsize, openBtn, dlBtn, delBtn);
        container.appendChild(div);
    }
}

function openMedia(f) {
    const viewer = document.getElementById('mediaViewer');
    viewer.innerHTML = '';
    viewer.style.display = 'block';

    if (f.contentType && f.contentType.startsWith('video/')) {
        const video = document.createElement('video');
        video.controls = true;
        video.preload = 'metadata';
        video.src = f.url;
        video.style.maxWidth = '100%';
        viewer.appendChild(video);
    } else if (f.contentType && f.contentType.startsWith('image/')) {
        const img = document.createElement('img');
        img.src = f.url;
        viewer.appendChild(img);
    } else {
        const a = document.createElement('a');
        a.href = f.url;
        a.target = '_blank';
        a.textContent = 'Download: ' + f.fileName;
        a.style.color = '#e94560';
        viewer.appendChild(a);
    }
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(2) + ' GB';
}

async function saveText() {
    if (!currentExercisePath) return;
    const text = document.getElementById('exerciseText').value;
    await api('/api/exercises/text?path=' + encodeURIComponent(currentExercisePath), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
    });
}

async function saveNotes() {
    if (!currentExercisePath) return;
    const notes = document.getElementById('exerciseNotes').value;
    await api('/api/exercises/notes?path=' + encodeURIComponent(currentExercisePath), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes })
    });
}

async function uploadFiles() {
    if (!currentExercisePath) return;
    const input = document.getElementById('fileInput');
    if (!input.files.length) { alert('Select files first'); return; }
    const fd = new FormData();
    for (const f of input.files) fd.append('files', f);
    log(`Uploading ${input.files.length} file(s)...`);
    const resp = await fetch('/api/files/upload?exercisePath=' + encodeURIComponent(currentExercisePath), {
        method: 'POST',
        body: fd
    });
    if (resp.ok) {
        log('Upload OK');
        input.value = '';
        loadExercise(currentExercisePath);
    } else {
        log('Upload failed: ' + resp.status);
        alert('Upload failed');
    }
}

async function deleteFile(fileName) {
    if (!confirm('Delete file "' + fileName + '"?')) return;
    const url = '/api/files?exercisePath=' + encodeURIComponent(currentExercisePath) + '&fileName=' + encodeURIComponent(fileName);
    await api(url, { method: 'DELETE' });
    loadExercise(currentExercisePath);
}

async function promptCreateSection(parentPath) {
    const title = prompt('New section name:');
    if (!title) return;
    await api('/api/sections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ parentPath: parentPath || '', title })
    });
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
    loadTree();
}

async function promptRenameSection(path, currentName) {
    const newTitle = prompt('Rename section:', currentName);
    if (!newTitle || newTitle === currentName) return;
    await api('/api/sections/rename', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path, newTitle })
    });
    loadTree();
}

async function promptDeleteSection(path) {
    if (!confirm('Delete section "' + path + '" and all its contents?')) return;
    await api('/api/sections?path=' + encodeURIComponent(path), { method: 'DELETE' });
    if (currentExercisePath && currentExercisePath.startsWith(path)) {
        currentExercisePath = null;
        document.getElementById('exercisePanel').style.display = 'none';
        document.getElementById('placeholder').style.display = 'flex';
    }
    loadTree();
}

function promptRenameExercise() {
    if (!currentExercisePath) return;
    const currentName = document.getElementById('exerciseTitle').textContent;
    const newTitle = prompt('Rename exercise:', currentName);
    if (!newTitle || newTitle === currentName) return;
    api('/api/exercises/rename', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: currentExercisePath, newTitle })
    }).then(data => {
        if (data && data.path) {
            currentExercisePath = data.path;
            loadExercise(currentExercisePath);
        }
    });
}

function promptDeleteExercise() {
    if (!currentExercisePath) return;
    if (!confirm('Delete exercise "' + currentExercisePath + '"?')) return;
    api('/api/exercises?path=' + encodeURIComponent(currentExercisePath), { method: 'DELETE' }).then(() => {
        currentExercisePath = null;
        document.getElementById('exercisePanel').style.display = 'none';
        document.getElementById('placeholder').style.display = 'flex';
        loadTree();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    loadTree();
});
