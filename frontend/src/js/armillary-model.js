import * as THREE from 'three';

export function initArmillaryModel(scene, state) {
    const modelGroup = new THREE.Group();
    modelGroup.name = 'armillary';

    const bearingGroup = new THREE.Group();
    bearingGroup.name = 'bearings';
    bearingGroup.visible = true;

    const errorVectorGroup = new THREE.Group();
    errorVectorGroup.name = 'errorVectors';

    const bronzeMaterial = new THREE.MeshStandardMaterial({
        color: 0xcd7f32,
        metalness: 0.8,
        roughness: 0.3
    });

    const darkBronzeMaterial = new THREE.MeshStandardMaterial({
        color: 0x8b4513,
        metalness: 0.7,
        roughness: 0.4
    });

    const goldMaterial = new THREE.MeshStandardMaterial({
        color: 0xffd700,
        metalness: 0.9,
        roughness: 0.2
    });

    const glassMaterial = new THREE.MeshPhysicalMaterial({
        color: 0x87ceeb,
        metalness: 0.1,
        roughness: 0.1,
        transparent: true,
        opacity: 0.4,
        transmission: 0.6,
        clearcoat: 1.0
    });

    const steelMaterial = new THREE.MeshStandardMaterial({
        color: 0x708090,
        metalness: 0.9,
        roughness: 0.2
    });

    const baseGeometry = new THREE.CylinderGeometry(2.5, 3, 0.5, 32);
    const base = new THREE.Mesh(baseGeometry, darkBronzeMaterial);
    base.position.y = -2.5;
    base.castShadow = true;
    base.receiveShadow = true;
    modelGroup.add(base);

    const pillarGeometry = new THREE.CylinderGeometry(0.3, 0.4, 4, 16);
    const pillar = new THREE.Mesh(pillarGeometry, bronzeMaterial);
    pillar.position.y = -0.5;
    pillar.castShadow = true;
    modelGroup.add(pillar);

    const azimuthRingGeometry = new THREE.TorusGeometry(2.8, 0.12, 16, 64);
    const azimuthRing = new THREE.Mesh(azimuthRingGeometry, bronzeMaterial);
    azimuthRing.rotation.x = Math.PI / 2;
    azimuthRing.position.y = 1.5;
    azimuthRing.castShadow = true;
    modelGroup.add(azimuthRing);

    const altitudeRingGeometry = new THREE.TorusGeometry(2.5, 0.1, 16, 64);
    const altitudeRing = new THREE.Mesh(altitudeRingGeometry, goldMaterial);
    altitudeRing.position.y = 1.5;
    altitudeRing.castShadow = true;
    modelGroup.add(altitudeRing);

    const equatorialRingGeometry = new THREE.TorusGeometry(2.2, 0.12, 16, 64);
    const equatorialRing = new THREE.Mesh(equatorialRingGeometry, bronzeMaterial);
    equatorialRing.rotation.x = Math.PI / 2 - (40 * Math.PI / 180);
    equatorialRing.position.y = 1.5;
    equatorialRing.castShadow = true;
    modelGroup.add(equatorialRing);

    const declinationRingGeometry = new THREE.TorusGeometry(1.8, 0.1, 16, 64);
    const declinationRing = new THREE.Mesh(declinationRingGeometry, goldMaterial);
    declinationRing.position.y = 1.5;
    declinationRing.castShadow = true;
    modelGroup.add(declinationRing);

    const sightTubeGeometry = new THREE.CylinderGeometry(0.05, 0.08, 3.5, 16);
    const sightTube = new THREE.Mesh(sightTubeGeometry, bronzeMaterial);
    sightTube.rotation.z = Math.PI / 2;
    sightTube.position.y = 1.5;
    sightTube.castShadow = true;
    modelGroup.add(sightTube);

    const rightAscensionRingGeometry = new THREE.TorusGeometry(1.5, 0.08, 12, 48);
    const rightAscensionRing = new THREE.Mesh(rightAscensionRingGeometry, bronzeMaterial);
    rightAscensionRing.rotation.x = Math.PI / 2;
    rightAscensionRing.position.y = 1.5;
    rightAscensionRing.castShadow = true;
    modelGroup.add(rightAscensionRing);

    function createBearing(position, axisName, config) {
        const bearing = new THREE.Group();
        bearing.name = `bearing-${axisName}`;

        const outerRingGeometry = new THREE.TorusGeometry(config.outerDiameter / 100, 0.06, 12, 32);
        const outerRing = new THREE.Mesh(outerRingGeometry, steelMaterial);
        outerRing.rotation.x = Math.PI / 2;
        bearing.add(outerRing);

        const innerRingGeometry = new THREE.TorusGeometry(config.innerDiameter / 100, 0.04, 12, 32);
        const innerRing = new THREE.Mesh(innerRingGeometry, bronzeMaterial);
        innerRing.rotation.x = Math.PI / 2;
        bearing.add(innerRing);

        const ballCount = 12;
        for (let i = 0; i < ballCount; i++) {
            const angle = (i / ballCount) * Math.PI * 2;
            const ballGeometry = new THREE.SphereGeometry(0.03, 16, 16);
            const ball = new THREE.Mesh(ballGeometry, steelMaterial);
            const ballRadius = (config.outerDiameter / 100 + config.innerDiameter / 100) / 2;
            ball.position.set(
                Math.cos(angle) * ballRadius,
                0,
                Math.sin(angle) * ballRadius
            );
            bearing.add(ball);
        }

        const cutPlane = new THREE.Mesh(
            new THREE.PlaneGeometry(config.outerDiameter / 50, config.outerDiameter / 50),
            new THREE.MeshBasicMaterial({
                color: 0x00ffff,
                transparent: true,
                opacity: 0.3,
                side: THREE.DoubleSide
            })
        );
        cutPlane.rotation.y = Math.PI / 2;
        bearing.add(cutPlane);

        const crossSectionGeometry = new THREE.RingGeometry(
            config.innerDiameter / 100,
            config.outerDiameter / 100,
            64,
            1,
            0,
            Math.PI
        );
        const crossSection = new THREE.Mesh(
            crossSectionGeometry,
            glassMaterial
        );
        crossSection.rotation.x = Math.PI / 2;
        bearing.add(crossSection);

        bearing.position.copy(position);
        bearing.userData = { axisName, config };

        return bearing;
    }

    const bearingConfigs = {
        '赤道轴': { innerDiameter: 120, outerDiameter: 150, width: 80 },
        '赤纬轴': { innerDiameter: 100, outerDiameter: 130, width: 70 },
        '地平经轴': { innerDiameter: 80, outerDiameter: 120, width: 60 },
        '地平纬轴': { innerDiameter: 70, outerDiameter: 110, width: 55 }
    };

    const bearingPositions = {
        '赤道轴': new THREE.Vector3(0, 1.5, 0),
        '赤纬轴': new THREE.Vector3(0, 1.5, 0),
        '地平经轴': new THREE.Vector3(0, 1.5, 2.5),
        '地平纬轴': new THREE.Vector3(2.5, 1.5, 0)
    };

    Object.entries(bearingConfigs).forEach(([axisName, config]) => {
        const bearing = createBearing(bearingPositions[axisName], axisName, config);
        bearingGroup.add(bearing);
    });

    modelGroup.add(bearingGroup);

    function createErrorVector(color, direction, name) {
        const arrowGroup = new THREE.Group();
        arrowGroup.name = name;

        const arrowHelper = new THREE.ArrowHelper(
            direction,
            new THREE.Vector3(0, 0, 0),
            2,
            color,
            0.3,
            0.15
        );
        arrowGroup.add(arrowHelper);

        const lineGeometry = new THREE.BufferGeometry().setFromPoints([
            new THREE.Vector3(0, 0, 0),
            direction.clone().multiplyScalar(2)
        ]);
        const lineMaterial = new THREE.LineDashedMaterial({
            color: color,
            dashSize: 0.1,
            gapSize: 0.05,
            transparent: true,
            opacity: 0.7
        });
        const dashedLine = new THREE.Line(lineGeometry, lineMaterial);
        dashedLine.computeLineDistances();
        arrowGroup.add(dashedLine);

        return arrowGroup;
    }

    const azimuthErrorVector = createErrorVector(0xff6b6b, new THREE.Vector3(1, 0, 0), 'azimuthError');
    azimuthErrorVector.position.set(0, 1.5, 0);
    errorVectorGroup.add(azimuthErrorVector);

    const altitudeErrorVector = createErrorVector(0x4ecdc4, new THREE.Vector3(0, 1, 0), 'altitudeError');
    altitudeErrorVector.position.set(0, 1.5, 0);
    errorVectorGroup.add(altitudeErrorVector);

    const totalErrorVector = createErrorVector(0xffe66d, new THREE.Vector3(0.707, 0.707, 0), 'totalError');
    totalErrorVector.position.set(0, 1.5, 0);
    errorVectorGroup.add(totalErrorVector);

    modelGroup.add(errorVectorGroup);

    const labelCanvas = document.createElement('canvas');
    labelCanvas.width = 256;
    labelCanvas.height = 64;
    const labelCtx = labelCanvas.getContext('2d');
    labelCtx.fillStyle = 'rgba(13, 71, 161, 0.9)';
    labelCtx.fillRect(0, 0, 256, 64);
    labelCtx.font = 'bold 20px Microsoft YaHei';
    labelCtx.fillStyle = '#81d4fa';
    labelCtx.textAlign = 'center';
    labelCtx.fillText('郭守敬简仪 (1279年)', 128, 40);

    const labelTexture = new THREE.CanvasTexture(labelCanvas);
    const labelMaterial = new THREE.SpriteMaterial({
        map: labelTexture,
        transparent: true
    });
    const labelSprite = new THREE.Sprite(labelMaterial);
    labelSprite.scale.set(3, 0.75, 1);
    labelSprite.position.set(0, 4, 0);
    modelGroup.add(labelSprite);

    scene.add(modelGroup);

    return {
        group: modelGroup,
        bearingGroup: bearingGroup,
        errorVectorGroup: errorVectorGroup,
        azimuthRing: azimuthRing,
        equatorialRing: equatorialRing,
        declinationRing: declinationRing,
        altitudeRing: altitudeRing,
        rightAscensionRing: rightAscensionRing,
        sightTube: sightTube,
        autoRotate: true,
        rotationSpeed: 0.002,
        currentErrors: { azimuth: 0, altitude: 0, total: 0 },
        bearingData: {},

        setRotation(enabled) {
            this.autoRotate = enabled;
        },

        animate() {
            modelGroup.rotation.y += this.rotationSpeed;
            this.declinationRing.rotation.z += this.rotationSpeed * 1.5;
            this.sightTube.rotation.x += this.rotationSpeed * 0.5;

            bearingGroup.children.forEach((bearing, index) => {
                bearing.rotation.y += this.rotationSpeed * (0.5 + index * 0.1);
            });
        },

        toggleBearings() {
            bearingGroup.visible = !bearingGroup.visible;
        },

        setErrorVectorsVisible(visible) {
            errorVectorGroup.visible = visible;
        },

        updateErrorVectors(azimuthError, altitudeError, totalError) {
            this.currentErrors = {
                azimuth: azimuthError,
                altitude: altitudeError,
                total: totalError
            };

            const scale = 50;

            if (azimuthErrorVector) {
                azimuthErrorVector.scale.set(Math.max(0.5 + Math.abs(azimuthError) * scale, 1), 1, 1);
            }

            if (altitudeErrorVector) {
                altitudeErrorVector.scale.set(1, Math.max(0.5 + Math.abs(altitudeError) * scale, 1), 1);
            }

            if (totalErrorVector) {
                const totalScale = 0.5 + Math.abs(totalError) * scale;
                totalErrorVector.scale.setScalar(totalScale);

                const direction = new THREE.Vector3(
                    Math.sign(azimuthError) || 1,
                    Math.sign(altitudeError) || 1,
                    0
                ).normalize();

                totalErrorVector.children[0].setDirection(direction);
            }
        },

        updateSensorData(data) {
            this.bearingData[data.axisName] = data;
        }
    };
}
