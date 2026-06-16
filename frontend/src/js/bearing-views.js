function createValueNoise(seed) {
    const permutation = new Uint8Array(512);
    for (let i = 0; i < 256; i++) permutation[i] = i;
    for (let i = 255; i > 0; i--) {
        const j = Math.floor(Math.sin(seed++) * 16807) % (i + 1);
        if (j < 0) j += i + 1;
        [permutation[i], permutation[j]] = [permutation[j], permutation[i]];
    }
    for (let i = 0; i < 256; i++) permutation[i + 256] = permutation[i];

    return function(x, y) {
        const xi = Math.floor(x) & 255;
        const yi = Math.floor(y) & 255;
        const xf = x - Math.floor(x);
        const yf = y - Math.floor(y);
        const u = xf * xf * (3 - 2 * xf);
        const v = yf * yf * (3 - 2 * yf);

        const aa = permutation[permutation[xi] + yi];
        const ab = permutation[permutation[xi] + yi + 1];
        const ba = permutation[permutation[xi + 1] + yi];
        const bb = permutation[permutation[xi + 1] + yi + 1];

        const x1 = aa + xf * (ba - aa);
        const x2 = ab + xf * (bb - ab);
        const y1 = x1 + v * (x2 - x1);

        return (y1 / 255) * 2 - 1;
    };
}

function fbmNoise(x, y, octaves = 4) {
    let value = 0;
    let amplitude = 1;
    let frequency = 1;
    let maxValue = 0;
    for (let i = 0; i < octaves; i++) {
        value += noiseFn(x * frequency, y * frequency) * amplitude;
        maxValue += amplitude;
        amplitude *= 0.5;
        frequency *= 2;
    }
    return value / maxValue;
}

let noiseFn = createValueNoise(12345);

function generateBronzeGrainTexture(width, height, scale = 30) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    const imageData = ctx.createImageData(width, height);
    const data = imageData.data;

    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const idx = (y * width + x) * 4;
            const nx = x / scale;
            const ny = y / scale;
            const grainValue = (fbmNoise(nx, ny, 4) + 1) / 2;
            const grainBoundaries = Math.abs(fbmNoise(nx * 3, ny * 3, 2));

            let r, g, b;
            if (grainBoundaries > 0.75) {
                r = 139 + grainValue * 30;
                g = 69 + grainValue * 20;
                b = 19 + grainValue * 10;
            } else {
                r = 205 + (Math.random() - 0.5) * 20;
                g = 127 + grainValue * 30;
                b = 50 + grainValue * 20;
            }

            const patina = fbmNoise(nx * 0.5, ny * 0.5, 3);
            if (patina > 0.6) {
                r *= 0.85;
                g *= 0.95;
                b *= 0.75;
            }

            data[idx] = Math.min(255, r);
            data[idx + 1] = Math.min(255, g);
            data[idx + 2] = Math.min(255, b);
            data[idx + 3] = 255;
        }
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas;
}

function generateCastIronTexture(width, height, scale = 25) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    const imageData = ctx.createImageData(width, height);
    const data = imageData.data;

    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const idx = (y * width + x) * 4;
            const nx = x / scale;
            const ny = y / scale;

            const baseValue = (fbmNoise(nx, ny, 4) + 1) / 2;
            const flakeValue = fbmNoise(nx * 4, ny * 4, 2);
            const flake = flakeValue > 0.6;

            let r, g, b;
            if (flake) {
                r = 60 + baseValue * 30;
                g = 60 + baseValue * 30;
                b = 70 + baseValue * 30;
            } else {
                r = 100 + baseValue * 40;
                g = 100 + baseValue * 40;
                b = 110 + baseValue * 40;
            }

            const scratch = Math.abs(fbmNoise(nx * 10, ny * 10, 1));
            if (scratch > 0.8) {
                r += 30;
                g += 30;
                b += 30;
            }

            data[idx] = Math.min(255, r);
            data[idx + 1] = Math.min(255, g);
            data[idx + 2] = Math.min(255, b);
            data[idx + 3] = 255;
        }
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas;
}

function generateSteelBallTexture(diameter) {
    const canvas = document.createElement('canvas');
    const size = diameter;
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    const imageData = ctx.createImageData(size, size);
    const data = imageData.data;
    const center = diameter / 2;
    const radius = diameter / 2;

    for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
            const idx = (y * size + x) * 4;
            const dx = x - center;
            const dy = y - center;
            const dist = Math.sqrt(dx * dx + dy * dy);

            if (dist <= radius) {
                const nx = dx / radius;
                const ny = dy / radius;
                const nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));

                const lightX = 0.5;
                const lightY = -0.5;
                const lightZ = 0.7;

                const halfLen = Math.sqrt((nx + lightX) ** 2 + (ny + lightY) ** 2 + (nz + lightZ) ** 2);
                const hx = (nx + lightX) / halfLen;
                const hy = (ny + lightY) / halfLen;
                const hz = (nz + lightZ) / halfLen;

                const ambient = 0.3;
                const diffuse = Math.max(0, nx * lightX + ny * lightY + nz * lightZ);
                const specular = Math.pow(Math.max(0, hx * nx + hy * ny + hz * nz), 32) * 0.8;

                const grainNoise = fbmNoise(x / 5, y / 5, 3) * 0.3;

                const scratchNoise = fbmNoise(x * 0.3 + Math.sin(x * 0.1) * 10, y * 0.3, 2);
                const hasScratch = Math.abs(scratchNoise) > 0.7;

                const baseR = 180;
                const baseG = 180;
                const baseB = 190;

                let r = baseR * (ambient + diffuse * 0.6) + specular * 255;
                let g = baseG * (ambient + diffuse * 0.6) + specular * 255;
                let b = baseB * (ambient + diffuse * 0.7) + specular * 255;

                r += grainNoise * 30;
                g += grainNoise * 30;
                b += grainNoise * 30;

                if (hasScratch) {
                    r *= 1.3;
                    g *= 1.3;
                    b *= 1.3;
                }

                const edgeFactor = 1 - Math.pow(dist / radius, 3);
                r *= edgeFactor;
                g *= edgeFactor;
                b *= edgeFactor;

                data[idx] = Math.min(255, r);
                data[idx + 1] = Math.min(255, g);
                data[idx + 2] = Math.min(255, b);
                data[idx + 3] = 255;
            } else {
                data[idx + 3] = 0;
            }
        }
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas;
}

function generateOilFilmTexture(width, height, filmThickness) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    const imageData = ctx.createImageData(width, height);
    const data = imageData.data;

    const centerX = width / 2;
    const centerY = height / 2;
    const maxDist = Math.max(centerX, centerY);

    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const idx = (y * width + x) * 4;

            const dx = x - centerX;
            const dy = y - centerY;
            const dist = Math.sqrt(dx * dx + dy * dy);

            const thickness = filmThickness * (1 + 0.3 * Math.sin(dist * 0.05));

            const wavelengthR = 650e-9;
            const wavelengthG = 550e-9;
            const wavelengthB = 450e-9;
            const filmThicknessMeters = thickness * 1e-6;

            const phaseR = 4 * Math.PI * filmThicknessMeters / wavelengthR;
            const phaseG = 4 * Math.PI * filmThicknessMeters / wavelengthG;
            const phaseB = 4 * Math.PI * filmThicknessMeters / wavelengthB;

            const intensityR = Math.sin(phaseR) ** 2 * 0.5 + 0.5;
            const intensityG = Math.sin(phaseG) ** 2 * 0.5 + 0.5;
            const intensityB = Math.sin(phaseB) ** 2 * 0.5 + 0.5;

            const flowNoise = fbmNoise(x / 20, y / 20, 3) * 0.3 + 0.7;

            let r = intensityR * 255 * flowNoise;
            let g = intensityG * 255 * flowNoise;
            let b = intensityB * 255 * flowNoise;

            if (dist > maxDist * 0.9) {
                const edgeFade = 1 - (dist - maxDist * 0.9) / (maxDist * 0.1);
                r *= edgeFade;
                g *= edgeFade;
                b *= edgeFade;
            }

            data[idx] = Math.min(255, r);
            data[idx + 1] = Math.min(255, g);
            data[idx + 2] = Math.min(255, b);
            data[idx + 3] = Math.min(255, 120 + flowNoise * 80);
        }
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas;
}

function generateWearTexture(width, height, wearPercent) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    const imageData = ctx.createImageData(width, height);
    const data = imageData.data;

    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const idx = (y * width + x) * 4;

            const wearNoise = fbmNoise(x / 15, y / 15, 4);
            const scratchNoise = Math.abs(fbmNoise(x / 8, y / 8, 2));

            const wearAmount = Math.min(1, wearPercent * 1.5);

            let r, g, b;
            if (scratchNoise > 0.7 + wearAmount * 0.3) {
                r = 80;
                g = 60;
                b = 50;
            } else {
                const baseBrightness = 150 - wearAmount * 80 + wearNoise * 40;
                r = baseBrightness;
                g = baseBrightness * 0.9;
                b = baseBrightness * 0.8;
            }

            if (wearAmount > 0.5) {
                r *= 0.7 + wearNoise * 0.6;
                g *= 0.7 + wearNoise * 0.6;
                b *= 0.7 + wearNoise * 0.6;
            }

            data[idx] = Math.min(255, r);
            data[idx + 1] = Math.min(255, g);
            data[idx + 2] = Math.min(255, b);
            data[idx + 3] = 255;
        }
    }
    ctx.putImageData(imageData, 0, 0);
    return canvas;
}

export function initBearingViews(state) {
    const axes = ['赤道轴', '赤纬轴', '地平经轴', '地平纬轴'];
    const axisTypes = ['equatorial', 'declination', 'azimuth', 'altitude'];

    axes.forEach((axisName, index) => {
        const axisType = axisTypes[index];
        const canvas = document.getElementById(`bearing-canvas-${axisType}`);
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        const rect = canvas.getBoundingClientRect();
        canvas.width = rect.width * window.devicePixelRatio;
        canvas.height = rect.height * window.devicePixelRatio;
        ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

        const displayWidth = rect.width;
        const displayHeight = rect.height;
        const centerX = displayWidth / 2;
        const centerY = displayHeight / 2;
        const maxRadius = Math.min(displayWidth, displayHeight) * 0.4;

        const configs = {
            '赤道轴': { innerDiameter: 120, outerDiameter: 150, width: 80 },
            '赤纬轴': { innerDiameter: 100, outerDiameter: 130, width: 70 },
            '地平经轴': { innerDiameter: 80, outerDiameter: 120, width: 60 },
            '地平纬轴': { innerDiameter: 70, outerDiameter: 110, width: 55 }
        };

        const config = configs[axisName];
        const outerRadius = (config.outerDiameter / 150) * maxRadius;
        const innerRadius = (config.innerDiameter / 150) * maxRadius;

        const textureSize = 256;
        noiseFn = createValueNoise(12345 + index * 1000);
        const bronzeTexture = generateBronzeGrainTexture(textureSize, textureSize);
        const castIronTexture = generateCastIronTexture(textureSize, textureSize);
        const ballTextureSize = Math.max(16, Math.floor((outerRadius - innerRadius) * 0.5));
        const ballTexture = generateSteelBallTexture(ballTextureSize * 2);

        function drawBearingRing(ctx, centerX, centerY, radius, texture, isInner) {
            ctx.save();
            ctx.beginPath();
            if (isInner) {
                ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
                ctx.clip();
                const pattern = ctx.createPattern(texture, 'repeat');
                ctx.fillStyle = pattern;
                ctx.fillRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            } else {
                ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
                ctx.arc(centerX, centerY, innerRadius, 0, Math.PI * 2, true);
                ctx.clip();
                const pattern = ctx.createPattern(texture, 'repeat');
                ctx.fillStyle = pattern;
                ctx.fillRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            }
            ctx.restore();

            ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
            ctx.stroke();
        }

        function drawOilFilm(ctx, centerX, centerY, innerR, outerR, filmThickness) {
            const textureDim = Math.floor((outerR - innerR) * 2.2);
            const filmTexture = generateOilFilmTexture(textureDim, textureDim, filmThickness);

            ctx.save();
            ctx.beginPath();
            ctx.arc(centerX, centerY, outerR, 0, Math.PI * 2);
            ctx.arc(centerX, centerY, innerR, 0, Math.PI * 2, true);
            ctx.clip();

            const pattern = ctx.createPattern(filmTexture, 'repeat');
            ctx.fillStyle = pattern;
            ctx.globalAlpha = 0.6;
            ctx.fillRect(centerX - outerR, centerY - outerR, outerR * 2, outerR * 2);
            ctx.restore();
        }

        function drawBall(ctx, x, y, radius) {
            const scale = radius * 2 / ballTexture.width;
            ctx.save();
            ctx.translate(x, y);
            ctx.scale(scale, scale);
            ctx.drawImage(ballTexture, -ballTexture.width / 2, -ballTexture.height / 2);
            ctx.restore();

            ctx.save();
            ctx.beginPath();
            ctx.arc(x, y, radius, 0, Math.PI * 2);
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
            ctx.lineWidth = 1;
            ctx.stroke();
            ctx.restore();
        }

        function drawWearLayer(ctx, centerX, centerY, radius, wearPercent) {
            if (wearPercent < 0.1) return;

            const wearTexture = generateWearTexture(
                Math.floor(radius * 2), Math.floor(radius * 2),
                wearPercent
            );

            ctx.save();
            ctx.globalAlpha = Math.min(0.8, wearPercent);
            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
            ctx.arc(centerX, centerY, radius * 0.95, 0, Math.PI * 2, true);
            ctx.clip();

            const pattern = ctx.createPattern(wearTexture, 'repeat');
            ctx.fillStyle = pattern;
            ctx.fillRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            ctx.restore();
        }

        function draw() {
            ctx.clearRect(0, 0, displayWidth, displayHeight);

            ctx.fillStyle = 'rgba(0, 0, 0, 0.3)';
            ctx.fillRect(0, 0, displayWidth, displayHeight);

            const gradient = ctx.createRadialGradient(
                centerX, centerY, innerRadius * 0.8,
                centerX, centerY, outerRadius * 1.2
            );
            gradient.addColorStop(0, 'rgba(33, 150, 243, 0.1)');
            gradient.addColorStop(1, 'rgba(33, 150, 243, 0.0)');
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 0, displayWidth, displayHeight);

            ctx.strokeStyle = 'rgba(129, 212, 250, 0.3)';
            ctx.lineWidth = 1;
            for (let i = 0; i < 8; i++) {
                const angle = (i / 8) * Math.PI * 2;
                ctx.beginPath();
                ctx.moveTo(centerX, centerY);
                ctx.lineTo(
                    centerX + Math.cos(angle) * outerRadius * 1.3,
                    centerY + Math.sin(angle) * outerRadius * 1.3
                );
                ctx.stroke();
            }

            for (let r = outerRadius * 0.5; r <= outerRadius * 1.3; r += outerRadius * 0.25) {
                ctx.beginPath();
                ctx.arc(centerX, centerY, r, 0, Math.PI * 2);
                ctx.stroke();
            }

            drawBearingRing(ctx, centerX, centerY, outerRadius, castIronTexture, false);
            drawBearingRing(ctx, centerX, centerY, innerRadius, bronzeTexture, true);

            const sensorData = state.latestSensorData[axisName];
            const frictionData = state.latestFrictionData[axisName];

            let wearDepth = 0;
            let wearPercent = 0;
            let filmThickness = 0.5;
            if (sensorData && sensorData.wearDepth) {
                wearDepth = sensorData.wearDepth;
                const maxWear = 0.1;
                wearPercent = Math.min(wearDepth / maxWear, 1);
            }
            if (frictionData && frictionData.filmThickness) {
                filmThickness = frictionData.filmThickness;
            }

            drawWearLayer(ctx, centerX, centerY, outerRadius, wearPercent);
            drawOilFilm(ctx, centerX, centerY, innerRadius, outerRadius, filmThickness);

            const ballCount = 12;
            const ballRadius = (outerRadius - innerRadius) * 0.25;
            const ballCircleRadius = (outerRadius + innerRadius) / 2;

            for (let i = 0; i < ballCount; i++) {
                const angle = (i / ballCount) * Math.PI * 2 + Date.now() * 0.0005;
                const bx = centerX + Math.cos(angle) * ballCircleRadius;
                const by = centerY + Math.sin(angle) * ballCircleRadius;
                drawBall(ctx, bx, by, ballRadius);
            }

            ctx.fillStyle = 'rgba(0, 255, 255, 0.2)';
            ctx.strokeStyle = 'rgba(0, 255, 255, 0.6)';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(centerX, centerY - outerRadius);
            ctx.lineTo(centerX, centerY + outerRadius);
            ctx.lineTo(centerX + outerRadius * 0.3, centerY);
            ctx.closePath();
            ctx.fill();
            ctx.stroke();

            const wearBarWidth = 8;
            const wearBarHeight = outerRadius * 0.8;
            const wearBarX = centerX + outerRadius * 0.6;
            const wearBarY = centerY - wearBarHeight / 2;

            ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
            ctx.fillRect(wearBarX, wearBarY, wearBarWidth, wearBarHeight);

            const wearColor = wearPercent > 0.8 ? '#f44336' :
                             wearPercent > 0.5 ? '#ff9800' : '#4caf50';
            ctx.fillStyle = wearColor;
            ctx.fillRect(
                wearBarX,
                wearBarY + wearBarHeight * (1 - wearPercent),
                wearBarWidth,
                wearBarHeight * wearPercent
            );

            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 1;
            ctx.strokeRect(wearBarX, wearBarY, wearBarWidth, wearBarHeight);

            ctx.fillStyle = '#fff';
            ctx.font = '10px Microsoft YaHei';
            ctx.textAlign = 'left';
            ctx.fillText('磨损', wearBarX - 5, wearBarY - 5);

            const infoDiv = document.getElementById(`bearing-info-${axisType}`);
            if (infoDiv) {
                const lambda = frictionData ? (frictionData.lambdaRatio || frictionData.lambdaRatio === 0 ? frictionData.lambdaRatio.toFixed(3) : '--') : '--';
                const film = frictionData ? (frictionData.filmThickness || frictionData.filmThickness === 0 ? frictionData.filmThickness.toFixed(3) : '--') : '--';
                const pressure = frictionData ? (frictionData.contactPressure || frictionData.contactPressure === 0 ? frictionData.contactPressure.toFixed(2) : '--') : '--';
                const coeff = frictionData ? (frictionData.frictionCoefficient || frictionData.frictionCoefficient === 0 ? frictionData.frictionCoefficient.toFixed(5) : '--') : '--';

                infoDiv.innerHTML = `
                    <div><span>内径:</span><span>${config.innerDiameter} mm</span></div>
                    <div><span>外径:</span><span>${config.outerDiameter} mm</span></div>
                    <div><span>宽度:</span><span>${config.width} mm</span></div>
                    <div><span>λ比:</span><span>${lambda}</span></div>
                    <div><span>油膜:</span><span>${film} μm</span></div>
                    <div><span>压力:</span><span>${pressure} MPa</span></div>
                    <div><span>摩擦系数:</span><span>${coeff}</span></div>
                    <div><span>磨损:</span><span>${wearDepth.toFixed(6)} mm</span></div>
                `;
            }

            if (wearPercent > 0.8) {
                ctx.fillStyle = 'rgba(244, 67, 54, 0.3)';
                ctx.fillRect(0, 0, displayWidth, displayHeight);
                ctx.fillStyle = '#f44336';
                ctx.font = 'bold 12px Microsoft YaHei';
                ctx.textAlign = 'center';
                ctx.fillText('⚠️ 磨损超限', centerX, 20);
            }

            requestAnimationFrame(draw);
        }

        draw();
    });
}
