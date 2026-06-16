import * as THREE from 'three';

export const ARMILY_MODEL_CONSTANTS = {
    BEIJING_LATITUDE_DEG: 39.9,
    BEARING_CONFIGS: {
        '赤道轴': { innerDiameter: 120, outerDiameter: 150, width: 80 },
        '赤纬轴': { innerDiameter: 100, outerDiameter: 130, width: 70 },
        '地平经轴': { innerDiameter: 80, outerDiameter: 120, width: 60 },
        '地平纬轴': { innerDiameter: 70, outerDiameter: 110, width: 55 }
    },
    BEARING_POSITIONS: {
        '赤道轴': [0, 1.5, 0],
        '赤纬轴': [0, 1.5, 0],
        '地平经轴': [0, 1.5, 2.5],
        '地平纬轴': [2.5, 1.5, 0]
    },
    ERROR_VECTOR_SCALE: 50
};

function createMaterials() {
    return {
        bronze: new THREE.MeshStandardMaterial({
            color: 0xcd7f32, metalness: 0.8, roughness: 0.3
        }),
        darkBronze: new THREE.MeshStandardMaterial({
            color: 0x8b4513, metalness: 0.7, roughness: 0.4
        }),
        gold: new THREE.MeshStandardMaterial({
            color: 0xffd700, metalness: 0.9, roughness: 0.2
        }),
        glass: new THREE.MeshPhysicalMaterial({
            color: 0x87ceeb, metalness: 0.1, roughness: 0.1,
            transparent: true, opacity: 0.4, transmission: 0.6, clearcoat: 1.0
        }),
        steel: new THREE.MeshStandardMaterial({
            color: 0x708090, metalness: 0.9, roughness: 0.2
        })
    };
}

function createStructure(modelGroup, materials) {
    const structureGroup = new THREE.Group();
    structureGroup.name = 'structure';

    const parts = [
        { geom: new THREE.CylinderGeometry(2.5, 3, 0.5, 32),
          pos: [0, -2.5, 0], mat: materials.darkBronze, name: 'base' },
        { geom: new THREE.CylinderGeometry(0.3, 0.4, 4, 16),
          pos: [0, -0.5, 0], mat: materials.bronze, name: 'pillar' },
        { geom: new THREE.TorusGeometry(2.8, 0.12, 16, 64),
          pos: [0, 1.5, 0], rot: [Math.PI/2, 0, 0],
          mat: materials.bronze, name: 'azimuthRing' },
        { geom: new THREE.TorusGeometry(2.5, 0.1, 16, 64),
          pos: [0, 1.5, 0], mat: materials.gold, name: 'altitudeRing' },
        { geom: new THREE.TorusGeometry(2.2, 0.12, 16, 64),
          pos: [0, 1.5, 0],
          rot: [Math.PI/2 - (ARMILY_MODEL_CONSTANTS.BEIJING_LATITUDE_DEG * Math.PI / 180), 0, 0],
          mat: materials.bronze, name: 'equatorialRing' },
        { geom: new THREE.TorusGeometry(1.8, 0.1, 16, 64),
          pos: [0, 1.5, 0], mat: materials.gold, name: 'declinationRing' },
        { geom: new THREE.CylinderGeometry(0.05, 0.08, 3.5, 16),
          pos: [0, 1.5, 0], rot: [0, 0, Math.PI/2],
          mat: materials.bronze, name: 'sightTube' },
        { geom: new THREE.TorusGeometry(1.5, 0.08, 12, 48),
          pos: [0, 1.5, 0], rot: [Math.PI/2, 0, 0],
          mat: materials.bronze, name: 'rightAscensionRing' }
    ];

    parts.forEach(p => {
        const mesh = new THREE.Mesh(p.geom, p.mat);
        mesh.position.set(...p.pos);
        if (p.rot) mesh.rotation.set(...p.rot);
        mesh.castShadow = true;
        mesh.receiveShadow = true;
        mesh.name = p.name;
        structureGroup.add(mesh);
    });

    modelGroup.add(structureGroup);
    return structureGroup;
}

function createBearing(position, axisName, config, materials) {
    const bearing = new THREE.Group();
    bearing.name = `bearing-${axisName}`;

    const outer = new THREE.Mesh(
        new THREE.TorusGeometry(config.outerDiameter / 100, 0.06, 12, 32),
        materials.steel
    );
    outer.rotation.x = Math.PI / 2;
    bearing.add(outer);

    const inner = new THREE.Mesh(
        new THREE.TorusGeometry(config.innerDiameter / 100, 0.04, 12, 32),
        materials.bronze
    );
    inner.rotation.x = Math.PI / 2;
    bearing.add(inner);

    const ballRadius = (config.outerDiameter / 100 + config.innerDiameter / 100) / 2;
    for (let i = 0; i < 12; i++) {
        const angle = (i / 12) * Math.PI * 2;
        const ball = new THREE.Mesh(
            new THREE.SphereGeometry(0.03, 16, 16),
            materials.steel
        );
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
            color: 0x00ffff, transparent: true, opacity: 0.3,
            side: THREE.DoubleSide
        })
    );
    cutPlane.rotation.y = Math.PI / 2;
    bearing.add(cutPlane);

    const crossSection = new THREE.Mesh(
        new THREE.RingGeometry(
            config.innerDiameter / 100,
            config.outerDiameter / 100,
            64, 1, 0, Math.PI
        ),
        materials.glass
    );
    crossSection.rotation.x = Math.PI / 2;
    bearing.add(crossSection);

    bearing.position.set(...position);
    bearing.userData = { axisName, config };
    return bearing;
}

function createBearings(modelGroup, materials) {
    const bearingGroup = new THREE.Group();
    bearingGroup.name = 'bearings';
    bearingGroup.visible = true;

    Object.entries(ARMILY_MODEL_CONSTANTS.BEARING_CONFIGS).forEach(([axis, config]) => {
        const pos = ARMILLY_MODEL_CONSTANTS.BEARING_POSITIONS[axis];
        const bearing = createBearing(pos, axis, config, materials);
        bearingGroup.add(bearing);
    });

    modelGroup.add(bearingGroup);
    return bearingGroup;
}

function createErrorVector(color, direction) {
    const arrowGroup = new THREE.Group();
    const arrow = new THREE.ArrowHelper(
        direction, new THREE.Vector3(0, 0, 0),
        2, color, 0.3, 0.15
    );
    arrowGroup.add(arrow);

    const lineGeom = new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(0, 0, 0),
        direction.clone().multiplyScalar(2)
    ]);
    const line = new THREE.Line(
        lineGeom,
        new THREE.LineDashedMaterial({
            color: color, dashSize: 0.1, gapSize: 0.05,
            transparent: true, opacity: 0.7
        })
    );
    line.computeLineDistances();
    arrowGroup.add(line);

    return arrowGroup;
}

function createErrorVectors(modelGroup) {
    const group = new THREE.Group();
    group.name = 'errorVectors';

    const az = createErrorVector(0xff6b6b, new THREE.Vector3(1, 0, 0));
    az.name = 'azimuthError';
    az.position.set(0, 1.5, 0);
    group.add(az);

    const alt = createErrorVector(0x4ecdc4, new THREE.Vector3(0, 1, 0));
    alt.name = 'altitudeError';
    alt.position.set(0, 1.5, 0);
    group.add(alt);

    const tot = createErrorVector(0xffe66d, new THREE.Vector3(0.707, 0.707, 0));
    tot.name = 'totalError';
    tot.position.set(0, 1.5, 0);
    group.add(tot);

    modelGroup.add(group);
    return group;
}

function createLabel(modelGroup) {
    const canvas = document.createElement('canvas');
    canvas.width = 256; canvas.height = 64;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = 'rgba(13, 71, 161, 0.9)';
    ctx.fillRect(0, 0, 256, 64);
    ctx.font = 'bold 20px Microsoft YaHei';
    ctx.fillStyle = '#81d4fa';
    ctx.textAlign = 'center';
    ctx.fillText('郭守敬简仪 (1279年)', 128, 40);

    const sprite = new THREE.Sprite(
        new THREE.SpriteMaterial({
            map: new THREE.CanvasTexture(canvas),
            transparent: true
        })
    );
    sprite.scale.set(3, 0.75, 1);
    sprite.position.set(0, 4, 0);
    modelGroup.add(sprite);
}

export function initSimplifiedArmillary3D(scene) {
    const modelGroup = new THREE.Group();
    modelGroup.name = 'armillary';

    const materials = createMaterials();

    const structureGroup = createStructure(modelGroup, materials);
    const bearingGroup = createBearings(modelGroup, materials);
    const errorVectorGroup = createErrorVectors(modelGroup);
    createLabel(modelGroup);

    scene.add(modelGroup);

    const api = {
        group: modelGroup,
        bearingGroup,
        errorVectorGroup,
        structureGroup,
        autoRotate: true,
        rotationSpeed: 0.002,
        currentErrors: { azimuth: 0, altitude: 0, total: 0 },
        bearingData: {},

        setRotation(enabled) {
            this.autoRotate = enabled;
        },

        animate() {
            if (this.autoRotate) {
                modelGroup.rotation.y += this.rotationSpeed;
            }
            structureGroup.children.find(c => c.name === 'declinationRing')
                .rotation.z += this.rotationSpeed * 1.5;
            structureGroup.children.find(c => c.name === 'sightTube')
                .rotation.x += this.rotationSpeed * 0.5;
            bearingGroup.children.forEach((b, i) => {
                b.rotation.y += this.rotationSpeed * (0.5 + i * 0.1);
            });
        },

        toggleBearings() {
            bearingGroup.visible = !bearingGroup.visible;
        },

        setErrorVectorsVisible(visible) {
            errorVectorGroup.visible = visible;
        },

        updateErrorVectors(azimuthError, altitudeError, totalError) {
            this.currentErrors = { azimuth: azimuthError, altitude: altitudeError, total: totalError };
            const scale = ARMILY_MODEL_CONSTANTS.ERROR_VECTOR_SCALE;

            const az = errorVectorGroup.getObjectByName('azimuthError');
            if (az) az.scale.set(
                Math.max(0.5 + Math.abs(azimuthError) * scale, 1), 1, 1
            );

            const al = errorVectorGroup.getObjectByName('altitudeError');
            if (al) al.scale.set(
                1, Math.max(0.5 + Math.abs(altitudeError) * scale, 1), 1
            );

            const tot = errorVectorGroup.getObjectByName('totalError');
            if (tot) {
                const totalScale = 0.5 + Math.abs(totalError) * scale;
                tot.scale.setScalar(totalScale);
                const dir = new THREE.Vector3(
                    Math.sign(azimuthError) || 1,
                    Math.sign(altitudeError) || 1,
                    0
                ).normalize();
                tot.children[0].setDirection(dir);
            }
        },

        updateSensorData(data) {
            this.bearingData[data.axisName] = data;
        },

        getBearingAxisNames() {
            return Object.keys(ARMILY_MODEL_CONSTANTS.BEARING_CONFIGS);
        }
    };

    return api;
}
