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

            ctx.fillStyle = '#708090';
            ctx.strokeStyle = '#a0a0a0';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(centerX, centerY, outerRadius, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();

            const innerGradient = ctx.createRadialGradient(
                centerX, centerY, 0,
                centerX, centerY, innerRadius
            );
            innerGradient.addColorStop(0, '#cd7f32');
            innerGradient.addColorStop(1, '#8b4513');
            ctx.fillStyle = innerGradient;
            ctx.beginPath();
            ctx.arc(centerX, centerY, innerRadius, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();

            const ballCount = 12;
            const ballRadius = (outerRadius - innerRadius) * 0.25;
            const ballCircleRadius = (outerRadius + innerRadius) / 2;

            for (let i = 0; i < ballCount; i++) {
                const angle = (i / ballCount) * Math.PI * 2 + Date.now() * 0.0005;
                const bx = centerX + Math.cos(angle) * ballCircleRadius;
                const by = centerY + Math.sin(angle) * ballCircleRadius;

                const ballGradient = ctx.createRadialGradient(
                    bx - ballRadius * 0.3, by - ballRadius * 0.3, 0,
                    bx, by, ballRadius
                );
                ballGradient.addColorStop(0, '#c0c0c0');
                ballGradient.addColorStop(1, '#505050');
                ctx.fillStyle = ballGradient;
                ctx.beginPath();
                ctx.arc(bx, by, ballRadius, 0, Math.PI * 2);
                ctx.fill();
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

            const sensorData = state.latestSensorData[axisName];
            const frictionData = state.latestFrictionData[axisName];

            let wearDepth = 0;
            let wearPercent = 0;
            if (sensorData && sensorData.wearDepth) {
                wearDepth = sensorData.wearDepth;
                const maxWear = 0.1;
                wearPercent = Math.min(wearDepth / maxWear, 1);
            }

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
                const lambda = frictionData ? frictionData.lambdaRatio?.toFixed(3) || '--' : '--';
                const film = frictionData ? frictionData.filmThickness?.toFixed(3) || '--' : '--';
                const pressure = frictionData ? frictionData.contactPressure?.toFixed(2) || '--' : '--';
                const coeff = frictionData ? frictionData.frictionCoefficient?.toFixed(5) || '--' : '--';

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
