import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';
import { Chart, registerables } from 'chart.js';
import { initArmillaryModel } from './armillary-model.js';
import { initBearingViews } from './bearing-views.js';
import { initCharts } from './charts.js';

Chart.register(...registerables);

const CONFIG = {
    API_BASE: '/api',
    WS_URL: '/ws/armillary',
    DEFAULT_INSTRUMENT_ID: 'a1b2c3d4-e5f6-7890-abcd-ef0123456789'
};

const state = {
    currentInstrumentId: CONFIG.DEFAULT_INSTRUMENT_ID,
    selectedAxis: '赤道轴',
    autoRotate: true,
    showErrorVectors: true,
    stompClient: null,
    latestSensorData: {},
    latestFrictionData: {},
    latestPointingData: null,
    activeAlerts: [],
    scene: null,
    armillaryModel: null,
    controls: null,
    charts: {}
};

const elements = {
    connectionStatus: document.getElementById('connection-status'),
    currentTime: document.getElementById('current-time'),
    axisSelect: document.getElementById('axis-select'),
    instrumentName: document.getElementById('instrument-name'),
    instrumentModel: document.getElementById('instrument-model'),
    instrumentLocation: document.getElementById('instrument-location'),
    instrumentYear: document.getElementById('instrument-year'),
    dataSpeed: document.getElementById('data-speed'),
    dataTorque: document.getElementById('data-torque'),
    dataWear: document.getElementById('data-wear'),
    dataTemp: document.getElementById('data-temp'),
    dataLoadR: document.getElementById('data-load-r'),
    dataLoadA: document.getElementById('data-load-a'),
    dataLambda: document.getElementById('data-lambda'),
    dataFilm: document.getElementById('data-film'),
    dataPressure: document.getElementById('data-pressure'),
    dataFrictionCoeff: document.getElementById('data-friction-coeff'),
    dataAsperity: document.getElementById('data-asperity'),
    dataWearRate: document.getElementById('data-wear-rate'),
    dataTargetRa: document.getElementById('data-target-ra'),
    dataTargetDec: document.getElementById('data-target-dec'),
    dataAzError: document.getElementById('data-az-error'),
    dataAltError: document.getElementById('data-alt-error'),
    dataTotalError: document.getElementById('data-total-error'),
    dataUncertainty: document.getElementById('data-uncertainty'),
    alertsDisplay: document.getElementById('alerts-display'),
    analyzeBtn: document.getElementById('analyze-btn'),
    alertToast: document.getElementById('alert-toast'),
    alertToastMessage: document.getElementById('alert-toast-message'),
    alertToastClose: document.getElementById('alert-toast-close')
};

function updateCurrentTime() {
    const now = new Date();
    elements.currentTime.textContent = now.toLocaleTimeString('zh-CN', {
        hour12: false
    });
}

function setConnectionStatus(status) {
    elements.connectionStatus.className = 'status-indicator status-' + status;
    const texts = {
        connected: '已连接',
        disconnected: '未连接',
        connecting: '连接中...'
    };
    elements.connectionStatus.textContent = texts[status] || status;
}

async function loadInstrumentInfo() {
    try {
        const response = await fetch(
            `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}`
        );
        if (response.ok) {
            const instrument = await response.json();
            elements.instrumentName.textContent = instrument.name;
            elements.instrumentModel.textContent = instrument.model;
            elements.instrumentLocation.textContent = instrument.location;
            elements.instrumentYear.textContent = instrument.buildYear;
        }
    } catch (e) {
        console.error('Failed to load instrument info:', e);
    }
}

async function loadInitialData() {
    try {
        const [sensorData, frictionData, alerts] = await Promise.all([
            fetch(`${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/sensor-data/latest`)
                .then(r => r.ok ? r.json() : []),
            fetch(`${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/simulation/friction/latest`)
                .then(r => r.ok ? r.json() : []),
            fetch(`${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/alerts/active`)
                .then(r => r.ok ? r.json() : [])
        ]);

        sensorData.forEach(data => {
            state.latestSensorData[data.axisName] = data;
        });

        frictionData.forEach(data => {
            state.latestFrictionData[data.axisName] = data;
        });

        state.activeAlerts = alerts;

        updateSensorDisplay();
        updateFrictionDisplay();
        updateAlertsDisplay();
    } catch (e) {
        console.error('Failed to load initial data:', e);
    }
}

function updateSensorDisplay() {
    const data = state.latestSensorData[state.selectedAxis];
    if (!data) return;

    elements.dataSpeed.textContent = formatNumber(data.rotationalSpeed, 4) + ' rpm';
    elements.dataTorque.textContent = formatNumber(data.frictionTorque, 4) + ' N·m';
    elements.dataWear.textContent = formatNumber(data.wearDepth, 6) + ' mm';
    elements.dataTemp.textContent = formatNumber(data.temperature, 2) + ' °C';
    elements.dataLoadR.textContent = formatNumber(data.loadRadial, 2) + ' kg';
    elements.dataLoadA.textContent = formatNumber(data.loadAxial, 2) + ' kg';
}

function updateFrictionDisplay() {
    const data = state.latestFrictionData[state.selectedAxis];
    if (!data) return;

    elements.dataLambda.textContent = formatNumber(data.lambdaRatio, 4);
    elements.dataFilm.textContent = formatNumber(data.filmThickness, 4) + ' μm';
    elements.dataPressure.textContent = formatNumber(data.contactPressure, 2) + ' MPa';
    elements.dataFrictionCoeff.textContent = formatNumber(data.frictionCoefficient, 6);
    elements.dataAsperity.textContent = formatNumber(data.asperityContactRatio * 100, 2) + ' %';
    elements.dataWearRate.textContent = formatExponential(data.wearRate);
}

function updatePointingDisplay(data) {
    if (!data) return;

    const arcminToDeg = 60;
    state.latestPointingData = data;

    elements.dataTargetRa.textContent = formatNumber(data.targetRa, 4) + ' °';
    elements.dataTargetDec.textContent = formatNumber(data.targetDec, 4) + ' °';
    elements.dataAzError.textContent = formatNumber(data.azimuthError * arcminToDeg, 4) + " '";
    elements.dataAltError.textContent = formatNumber(data.altitudeError * arcminToDeg, 4) + " '";
    elements.dataTotalError.textContent = formatNumber(data.totalPointingError * arcminToDeg, 4) + " '";
    elements.dataUncertainty.textContent = formatNumber(data.errorUncertainty * arcminToDeg, 4) + " '";

    if (state.armillaryModel && state.showErrorVectors) {
        state.armillaryModel.updateErrorVectors(
            data.azimuthError,
            data.altitudeError,
            data.totalPointingError
        );
    }
}

function updateAlertsDisplay() {
    if (state.activeAlerts.length === 0) {
        elements.alertsDisplay.innerHTML = '<p class="no-alerts">当前无告警</p>';
        return;
    }

    const levelClasses = {
        WARNING: 'warning',
        INFO: 'info',
        ERROR: ''
    };

    elements.alertsDisplay.innerHTML = state.activeAlerts
        .slice(0, 5)
        .map(alert => `
            <div class="alert-item ${levelClasses[alert.alertLevel] || ''}">
                <strong>[${alert.alertType}]</strong> ${alert.message}
                <br><small>${new Date(alert.createdAt).toLocaleString('zh-CN')}</small>
            </div>
        `).join('');
}

function showAlertToast(message) {
    elements.alertToastMessage.textContent = message;
    elements.alertToast.classList.remove('hidden');

    setTimeout(() => {
        elements.alertToast.classList.add('hidden');
    }, 5000);
}

function formatNumber(value, decimals = 2) {
    if (value === null || value === undefined || isNaN(value)) return '--';
    return Number(value).toFixed(decimals);
}

function formatExponential(value) {
    if (value === null || value === undefined || isNaN(value)) return '--';
    return Number(value).toExponential(2) + ' m/s';
}

function connectWebSocket() {
    setConnectionStatus('connecting');

    const socket = new SockJS(CONFIG.WS_URL);
    state.stompClient = Stomp.over(socket);
    state.stompClient.debug = () => {};

    state.stompClient.connect({},
        () => {
            setConnectionStatus('connected');
            subscribeToTopics();
        },
        (error) => {
            console.error('WebSocket connection error:', error);
            setConnectionStatus('disconnected');
            setTimeout(connectWebSocket, 5000);
        }
    );
}

function subscribeToTopics() {
    const instrumentId = state.currentInstrumentId;

    state.stompClient.subscribe(`/topic/sensor/${instrumentId}`, (message) => {
        try {
            const data = JSON.parse(message.body);
            state.latestSensorData[data.axisName] = data;
            if (data.axisName === state.selectedAxis) {
                updateSensorDisplay();
            }
            if (state.armillaryModel) {
                state.armillaryModel.updateSensorData(data);
            }
        } catch (e) {
            console.error('Failed to parse sensor message:', e);
        }
    });

    state.stompClient.subscribe(`/topic/friction/${instrumentId}`, (message) => {
        try {
            const data = JSON.parse(message.body);
            state.latestFrictionData[data.axisName] = data;
            if (data.axisName === state.selectedAxis) {
                updateFrictionDisplay();
            }
        } catch (e) {
            console.error('Failed to parse friction message:', e);
        }
    });

    state.stompClient.subscribe(`/topic/pointing/${instrumentId}`, (message) => {
        try {
            const data = JSON.parse(message.body);
            updatePointingDisplay(data);
        } catch (e) {
            console.error('Failed to parse pointing message:', e);
        }
    });

    state.stompClient.subscribe(`/topic/alerts/${instrumentId}`, (message) => {
        try {
            const alert = JSON.parse(message.body);
            state.activeAlerts.unshift(alert);
            state.activeAlerts = state.activeAlerts.filter(a => !a.isAcknowledged).slice(0, 10);
            updateAlertsDisplay();
            showAlertToast(alert.message);
        } catch (e) {
            console.error('Failed to parse alert message:', e);
        }
    });
}

async function analyzePointing() {
    try {
        elements.analyzeBtn.disabled = true;
        elements.analyzeBtn.textContent = '分析中...';

        const response = await fetch(
            `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/pointing/analyze-current`,
            { method: 'POST' }
        );

        if (response.ok) {
            const data = await response.json();
            updatePointingDisplay(data);
        } else {
            console.error('Failed to analyze pointing:', response.status);
        }
    } catch (e) {
        console.error('Failed to analyze pointing:', e);
    } finally {
        elements.analyzeBtn.disabled = false;
        elements.analyzeBtn.textContent = '分析当前指向';
    }
}

function initTabs() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabPanes = document.querySelectorAll('.tab-pane');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const tabName = btn.dataset.tab;

            tabBtns.forEach(b => b.classList.remove('active'));
            tabPanes.forEach(p => p.classList.remove('active'));

            btn.classList.add('active');
            document.getElementById(`tab-${tabName}`).classList.add('active');

            if (tabName === 'bearing') {
                setTimeout(() => initBearingViews(state), 100);
            } else if (tabName === 'charts') {
                setTimeout(() => initCharts(state, CONFIG), 100);
            }
        });
    });
}

function initControls() {
    elements.axisSelect.addEventListener('change', (e) => {
        state.selectedAxis = e.target.value;
        updateSensorDisplay();
        updateFrictionDisplay();
    });

    elements.analyzeBtn.addEventListener('click', analyzePointing);

    elements.alertToastClose.addEventListener('click', () => {
        elements.alertToast.classList.add('hidden');
    });

    document.getElementById('reset-view').addEventListener('click', () => {
        if (state.controls) {
            state.controls.reset();
        }
    });

    document.getElementById('toggle-rotation').addEventListener('click', (e) => {
        state.autoRotate = !state.autoRotate;
        if (state.armillaryModel) {
            state.armillaryModel.setRotation(state.autoRotate);
        }
        e.target.textContent = state.autoRotate ? '暂停旋转' : '开始旋转';
    });

    document.getElementById('show-bearings').addEventListener('click', () => {
        if (state.armillaryModel) {
            state.armillaryModel.toggleBearings();
        }
    });

    document.getElementById('show-error-vectors').addEventListener('change', (e) => {
        state.showErrorVectors = e.target.checked;
        if (state.armillaryModel) {
            state.armillaryModel.setErrorVectorsVisible(state.showErrorVectors);
        }
    });

    document.getElementById('refresh-charts').addEventListener('click', () => {
        initCharts(state, CONFIG);
    });
}

function initThreeJS() {
    const container = document.getElementById('three-container');
    const width = container.clientWidth;
    const height = container.clientHeight;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0c1445);
    scene.fog = new THREE.Fog(0x0c1445, 10, 50);

    const camera = new THREE.PerspectiveCamera(60, width / height, 0.1, 1000);
    camera.position.set(8, 6, 10);

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    container.appendChild(renderer.domElement);

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    controls.minDistance = 3;
    controls.maxDistance = 30;

    const ambientLight = new THREE.AmbientLight(0xffffff, 0.4);
    scene.add(ambientLight);

    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
    directionalLight.position.set(10, 15, 10);
    directionalLight.castShadow = true;
    directionalLight.shadow.mapSize.width = 2048;
    directionalLight.shadow.mapSize.height = 2048;
    scene.add(directionalLight);

    const fillLight = new THREE.DirectionalLight(0x4a90d9, 0.3);
    fillLight.position.set(-10, 5, -10);
    scene.add(fillLight);

    state.scene = scene;
    state.controls = controls;

    state.armillaryModel = initArmillaryModel(scene, state);

    const starsGeometry = new THREE.BufferGeometry();
    const starPositions = [];
    for (let i = 0; i < 2000; i++) {
        const r = 50 + Math.random() * 50;
        const theta = Math.random() * Math.PI * 2;
        const phi = Math.acos(2 * Math.random() - 1);
        starPositions.push(
            r * Math.sin(phi) * Math.cos(theta),
            r * Math.cos(phi),
            r * Math.sin(phi) * Math.sin(theta)
        );
    }
    starsGeometry.setAttribute('position', new THREE.Float32BufferAttribute(starPositions, 3));
    const starsMaterial = new THREE.PointsMaterial({
        color: 0xffffff,
        size: 0.1,
        sizeAttenuation: true
    });
    const stars = new THREE.Points(starsGeometry, starsMaterial);
    scene.add(stars);

    function animate() {
        requestAnimationFrame(animate);
        controls.update();

        if (state.armillaryModel && state.autoRotate) {
            state.armillaryModel.animate();
        }

        renderer.render(scene, camera);
    }
    animate();

    window.addEventListener('resize', () => {
        const w = container.clientWidth;
        const h = container.clientHeight;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
    });
}

async function init() {
    updateCurrentTime();
    setInterval(updateCurrentTime, 1000);

    initTabs();
    initControls();
    initThreeJS();

    await loadInstrumentInfo();
    await loadInitialData();

    connectWebSocket();
}

init();
