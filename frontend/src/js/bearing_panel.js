export const BEARING_PANEL_CONSTANTS = {
    CANVAS_SIZE: 300,
    TEXTURE_SIZE: 256,
    DEFAULT_MATERIAL: 'BRONZE_CASTIRON',
    DEFAULT_FILM_THICKNESS_UM: 1.5,
    COLORS: {
        AZIMUTH_ERROR: '#ff6b6b',
        ALTITUDE_ERROR: '#4ecdc4',
        TOTAL_ERROR: '#ffe66d',
        PANEL_BG: 'rgba(13, 71, 161, 0.85)',
        PANEL_BORDER: '#1e88e5'
    }
};

function createValueNoise(seed) {
    const perm = new Uint8Array(512);
    let s = seed;
    for (let i = 0; i < 256; i++) perm[i] = i;
    for (let i = 255; i > 0; i--) {
        const j = Math.floor(Math.sin(s++) * 16807) % (i + 1);
        [perm[i], perm[j]] = [perm[j], perm[i]];
    }
    for (let i = 0; i < 256; i++) perm[i + 256] = perm[i];

    return (x, y) => {
        const xi = Math.floor(x) & 255;
        const yi = Math.floor(y) & 255;
        const xf = x - Math.floor(x);
        const yf = y - Math.floor(y);
        const u = xf * xf * (3 - 2 * xf);
        const v = yf * yf * (3 - 2 * yf);
        const aa = perm[perm[xi] + yi];
        const ab = perm[perm[xi] + yi + 1];
        const ba = perm[perm[xi + 1] + yi];
        const bb = perm[perm[xi + 1] + yi + 1];
        const lerp = (a, b, t) => a + t * (b - a);
        const x1 = lerp(aa / 255 * 2 - 1, ba / 255 * 2 - 1, u);
        const x2 = lerp(ab / 255 * 2 - 1, bb / 255 * 2 - 1, u);
        return lerp(x1, x2, v);
    };
}

function fbm(noise, x, y, octaves = 4) {
    let value = 0, amp = 1, freq = 1, max = 0;
    for (let i = 0; i < octaves; i++) {
        value += noise(x * freq, y * freq) * amp;
        max += amp;
        amp *= 0.5;
        freq *= 2;
    }
    return value / max;
}

export function generateTextureCache() {
    const cache = new Map();
    const noise = createValueNoise(42);

    return {
        getBronze() {
            if (cache.has('bronze')) return cache.get('bronze');
            const cvs = document.createElement('canvas');
            cvs.width = cvs.height = BEARING_PANEL_CONSTANTS.TEXTURE_SIZE;
            const ctx = cvs.getContext('2d');
            const img = ctx.createImageData(cvs.width, cvs.height);
            for (let y = 0; y < cvs.height; y++) {
                for (let x = 0; x < cvs.width; x++) {
                    const nx = x / 30, ny = y / 30;
                    const gv = (fbm(noise, nx, ny, 4) + 1) / 2;
                    const gb = Math.abs(fbm(noise, nx * 3, ny * 3, 2));
                    let r, g, b;
                    if (gb > 0.75) {
                        r = 139 + gv * 30; g = 69 + gv * 20; b = 19 + gv * 10;
                    } else {
                        r = 205 + (Math.random() - 0.5) * 20;
                        g = 127 + gv * 30; b = 50 + gv * 20;
                    }
                    const pa = fbm(noise, nx * 0.5, ny * 0.5, 3);
                    if (pa > 0.6) { r *= 0.85; g *= 0.95; b *= 0.75; }
                    const i = (y * cvs.width + x) * 4;
                    img.data[i] = r; img.data[i+1] = g;
                    img.data[i+2] = b; img.data[i+3] = 255;
                }
            }
            ctx.putImageData(img, 0, 0);
            cache.set('bronze', cvs);
            return cvs;
        },

        getCastIron() {
            if (cache.has('castiron')) return cache.get('castiron');
            const cvs = document.createElement('canvas');
            cvs.width = cvs.height = BEARING_PANEL_CONSTANTS.TEXTURE_SIZE;
            const ctx = cvs.getContext('2d');
            const img = ctx.createImageData(cvs.width, cvs.height);
            for (let y = 0; y < cvs.height; y++) {
                for (let x = 0; x < cvs.width; x++) {
                    const nx = x / 25, ny = y / 25;
                    const bv = (fbm(noise, nx, ny, 4) + 1) / 2;
                    const fv = fbm(noise, nx * 4, ny * 4, 2);
                    const flake = fv > 0.6;
                    let r, g, b;
                    if (flake) { r = 60 + bv * 30; g = 60 + bv * 30; b = 70 + bv * 30; }
                    else { r = 100 + bv * 40; g = 100 + bv * 40; b = 110 + bv * 40; }
                    const sc = Math.abs(fbm(noise, nx * 10, ny * 10, 1));
                    if (sc > 0.8) { r += 30; g += 30; b += 30; }
                    const i = (y * cvs.width + x) * 4;
                    img.data[i] = r; img.data[i+1] = g;
                    img.data[i+2] = b; img.data[i+3] = 255;
                }
            }
            ctx.putImageData(img, 0, 0);
            cache.set('castiron', cvs);
            return cvs;
        },

        getOilFilm(thicknessUm) {
            const key = 'oil_' + thicknessUm.toFixed(1);
            if (cache.has(key)) return cache.get(key);
            const cvs = document.createElement('canvas');
            cvs.width = cvs.height = BEARING_PANEL_CONSTANTS.TEXTURE_SIZE;
            const ctx = cvs.getContext('2d');
            const img = ctx.createImageData(cvs.width, cvs.height);
            const [wR, wG, wB] = [650e-9, 550e-9, 450e-9];
            const meters = thicknessUm * 1e-6;
            const pR = 4 * Math.PI * meters / wR;
            const pG = 4 * Math.PI * meters / wG;
            const pB = 4 * Math.PI * meters / wB;
            const iR = Math.sin(pR) ** 2 * 0.5 + 0.5;
            const iG = Math.sin(pG) ** 2 * 0.5 + 0.5;
            const iB = Math.sin(pB) ** 2 * 0.5 + 0.5;
            for (let y = 0; y < cvs.height; y++) {
                for (let x = 0; x < cvs.width; x++) {
                    const flow = fbm(noise, x / 20, y / 20, 3) * 0.3 + 0.7;
                    const i = (y * cvs.width + x) * 4;
                    img.data[i] = Math.min(255, iR * 255 * flow);
                    img.data[i+1] = Math.min(255, iG * 255 * flow);
                    img.data[i+2] = Math.min(255, iB * 255 * flow);
                    img.data[i+3] = 180;
                }
            }
            ctx.putImageData(img, 0, 0);
            cache.set(key, cvs);
            return cvs;
        },

        getWear(wearPercent) {
            const key = 'wear_' + wearPercent.toFixed(2);
            if (cache.has(key)) return cache.get(key);
            const cvs = document.createElement('canvas');
            cvs.width = cvs.height = BEARING_PANEL_CONSTANTS.TEXTURE_SIZE;
            const ctx = cvs.getContext('2d');
            const img = ctx.createImageData(cvs.width, cvs.height);
            const wa = Math.min(1, wearPercent * 1.5);
            for (let y = 0; y < cvs.height; y++) {
                for (let x = 0; x < cvs.width; x++) {
                    const wn = fbm(noise, x / 15, y / 15, 4);
                    const sn = Math.abs(fbm(noise, x / 8, y / 8, 2));
                    let r, g, b;
                    if (sn > 0.7 + wa * 0.3) { r = 80; g = 60; b = 50; }
                    else {
                        const bb = 150 - wa * 80 + wn * 40;
                        r = bb; g = bb * 0.9; b = bb * 0.8;
                    }
                    if (wa > 0.5) {
                        const f = 0.7 + wn * 0.6;
                        r *= f; g *= f; b *= f;
                    }
                    const i = (y * cvs.width + x) * 4;
                    img.data[i] = r; img.data[i+1] = g;
                    img.data[i+2] = b; img.data[i+3] = 200;
                }
            }
            ctx.putImageData(img, 0, 0);
            cache.set(key, cvs);
            return cvs;
        }
    };
}

function drawRing(ctx, cx, cy, innerR, outerR, textureCanvas, alpha = 1) {
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.beginPath();
    ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
    ctx.arc(cx, cy, innerR, 0, Math.PI * 2, true);
    ctx.clip();
    const pattern = ctx.createPattern(textureCanvas, 'repeat');
    ctx.fillStyle = pattern;
    ctx.fillRect(cx - outerR, cy - outerR, outerR * 2, outerR * 2);
    ctx.restore();
}

function drawSectionCut(ctx, cx, cy, innerR, outerR) {
    ctx.save();
    ctx.strokeStyle = 'rgba(0, 255, 255, 0.8)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(cx - outerR, cy);
    ctx.lineTo(cx + outerR, cy);
    ctx.stroke();

    ctx.fillStyle = 'rgba(0, 255, 255, 0.1)';
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, outerR, 0, Math.PI);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
}

function drawBalls(ctx, cx, cy, innerR, outerR, count = 12) {
    const radius = (innerR + outerR) / 2;
    const ballR = (outerR - innerR) * 0.35;
    for (let i = 0; i < count; i++) {
        const angle = (i / count) * Math.PI * 2;
        const bx = cx + Math.cos(angle) * radius;
        const by = cy + Math.sin(angle) * radius;
        const grad = ctx.createRadialGradient(
            bx - ballR * 0.3, by - ballR * 0.3, 0,
            bx, by, ballR
        );
        grad.addColorStop(0, '#e0e8f0');
        grad.addColorStop(0.5, '#a0a8b0');
        grad.addColorStop(1, '#505860');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(bx, by, ballR, 0, Math.PI * 2);
        ctx.fill();
    }
}

export function initBearingPanel(containerSelector) {
    const container = document.querySelector(containerSelector);
    if (!container) {
        console.error('[BearingPanel] 容器不存在:', containerSelector);
        return null;
    }

    const panel = document.createElement('div');
    panel.className = 'bearing-panel';
    Object.assign(panel.style, {
        background: BEARING_PANEL_CONSTANTS.COLORS.PANEL_BG,
        border: `2px solid ${BEARING_PANEL_CONSTANTS.COLORS.PANEL_BORDER}`,
        borderRadius: '12px',
        padding: '16px',
        color: '#e3f2fd',
        fontFamily: 'Microsoft YaHei, sans-serif'
    });

    const title = document.createElement('h3');
    title.textContent = '轴承剖面细节';
    Object.assign(title.style, { margin: '0 0 12px 0', color: '#81d4fa' });
    panel.appendChild(title);

    const axisSelect = document.createElement('select');
    ['赤道轴', '赤纬轴', '地平经轴', '地平纬轴'].forEach(name => {
        const opt = document.createElement('option');
        opt.value = name; opt.textContent = name;
        axisSelect.appendChild(opt);
    });
    axisSelect.style.cssText = 'width:100%;padding:6px;margin-bottom:12px;border-radius:4px;';
    panel.appendChild(axisSelect);

    const canvas = document.createElement('canvas');
    const S = BEARING_PANEL_CONSTANTS.CANVAS_SIZE;
    canvas.width = S; canvas.height = S;
    canvas.style.cssText = `width:${S}px;height:${S}px;background:#0a1929;border-radius:8px;display:block;margin:0 auto;`;
    panel.appendChild(canvas);

    const infoDiv = document.createElement('div');
    Object.assign(infoDiv.style, {
        marginTop: '12px', padding: '10px',
        background: 'rgba(0,0,0,0.3)', borderRadius: '6px',
        fontSize: '13px', lineHeight: '1.8'
    });
    panel.appendChild(infoDiv);

    container.appendChild(panel);

    const ctx = canvas.getContext('2d');
    const textures = generateTextureCache();
    let currentState = {
        axisName: '赤道轴',
        config: { innerDiameter: 120, outerDiameter: 150 },
        wearPercent: 0,
        filmThicknessUm: BEARING_PANEL_CONSTANTS.DEFAULT_FILM_THICKNESS_UM,
        lambdaRatio: 1.5,
        frictionCoefficient: 0.02
    };

    function render() {
        ctx.clearRect(0, 0, S, S);
        const cx = S / 2, cy = S / 2;
        const scale = S / (currentState.config.outerDiameter * 1.5);
        const outerR = currentState.config.outerDiameter * scale / 2;
        const innerR = currentState.config.innerDiameter * scale / 2;

        drawRing(ctx, cx, cy, innerR, outerR, textures.getCastIron(), 1);
        drawBalls(ctx, cx, cy, innerR, outerR);
        drawRing(ctx, cx, cy, innerR * 0.85, innerR, textures.getBronze(), 1);

        if (currentState.filmThicknessUm > 0) {
            drawRing(ctx, cx, cy, innerR, outerR,
                textures.getOilFilm(currentState.filmThicknessUm), 0.4);
        }

        if (currentState.wearPercent > 0) {
            drawRing(ctx, cx, cy, innerR, outerR,
                textures.getWear(currentState.wearPercent),
                Math.min(0.8, currentState.wearPercent * 2));
        }

        drawSectionCut(ctx, cx, cy, innerR, outerR);

        infoDiv.innerHTML = `
            <div><strong>轴名称：</strong>${currentState.axisName}</div>
            <div><strong>尺寸：</strong>φ${currentState.config.innerDiameter}
                 ~ φ${currentState.config.outerDiameter} mm</div>
            <div><strong>λ比：</strong>
                <span style="color:${currentState.lambdaRatio < 1 ? '#ff6b6b' : '#69f0ae'}">
                    ${currentState.lambdaRatio.toFixed(3)}
                </span>
            </div>
            <div><strong>油膜厚度：</strong>${currentState.filmThicknessUm.toFixed(3)} μm</div>
            <div><strong>摩擦系数：</strong>${currentState.frictionCoefficient.toFixed(5)}</div>
            <div><strong>磨损率：</strong>
                <span style="color:${currentState.wearPercent > 0.5 ? '#ff6b6b' : '#81d4fa'}">
                    ${(currentState.wearPercent * 100).toFixed(2)}%
                </span>
            </div>
            <div style="margin-top:8px;padding-top:8px;border-top:1px solid rgba(255,255,255,0.2)">
                <span style="display:inline-block;width:16px;height:12px;background:#a0a8b0;margin-right:6px;"></span>铸铁外圈
                <span style="display:inline-block;width:16px;height:12px;background:#cd7f32;margin:0 6px 0 12px;"></span>青铜内圈
            </div>
        `;
    }

    axisSelect.addEventListener('change', e => {
        const configs = {
            '赤道轴': { innerDiameter: 120, outerDiameter: 150 },
            '赤纬轴': { innerDiameter: 100, outerDiameter: 130 },
            '地平经轴': { innerDiameter: 80, outerDiameter: 120 },
            '地平纬轴': { innerDiameter: 70, outerDiameter: 110 }
        };
        currentState.axisName = e.target.value;
        currentState.config = configs[e.target.value];
        render();
    });

    render();

    return {
        element: panel,
        canvas,
        updateData(data) {
            if (data.axisName) {
                const configs = {
                    '赤道轴': { innerDiameter: 120, outerDiameter: 150 },
                    '赤纬轴': { innerDiameter: 100, outerDiameter: 130 },
                    '地平经轴': { innerDiameter: 80, outerDiameter: 120 },
                    '地平纬轴': { innerDiameter: 70, outerDiameter: 110 }
                };
                if (configs[data.axisName]) {
                    currentState.axisName = data.axisName;
                    currentState.config = configs[data.axisName];
                    axisSelect.value = data.axisName;
                }
            }
            if (data.lambdaRatio !== undefined) currentState.lambdaRatio = data.lambdaRatio;
            if (data.filmThickness !== undefined) currentState.filmThicknessUm = data.filmThickness;
            if (data.frictionCoefficient !== undefined)
                currentState.frictionCoefficient = data.frictionCoefficient;
            if (data.wearPercent !== undefined) currentState.wearPercent = data.wearPercent;
            if (data.totalWearDepth !== undefined && data.maxAllowableWear !== undefined) {
                currentState.wearPercent = Math.min(1,
                    data.totalWearDepth / Math.max(data.maxAllowableWear, 0.001));
            }
            render();
        },
        setVisible(visible) {
            panel.style.display = visible ? 'block' : 'none';
        }
    };
}
