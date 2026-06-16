import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

const chartColors = {
    wear: {
        border: '#4caf50',
        background: 'rgba(76, 175, 80, 0.2)',
        threshold: '#f44336'
    },
    torque: {
        border: '#ff9800',
        background: 'rgba(255, 152, 0, 0.2)',
        threshold: '#f44336'
    },
    lambda: {
        border: '#2196f3',
        background: 'rgba(33, 150, 243, 0.2)',
        threshold: '#ff9800'
    },
    pointing: {
        border: '#e91e63',
        background: 'rgba(233, 30, 99, 0.2)',
        threshold: '#f44336'
    }
};

const axisColors = {
    '赤道轴': '#ff6b6b',
    '赤纬轴': '#4ecdc4',
    '地平经轴': '#ffe66d',
    '地平纬轴': '#95e1d3'
};

export function initCharts(state, CONFIG) {
    Object.values(state.charts).forEach(chart => chart?.destroy());
    state.charts = {};

    const timeRange = parseInt(document.getElementById('time-range-select').value) || 86400;
    const endTime = new Date();
    const startTime = new Date(endTime.getTime() - timeRange * 1000);

    Promise.all([
        fetchSensorData(state, CONFIG, startTime, endTime),
        fetchFrictionData(state, CONFIG, startTime, endTime),
        fetchPointingData(state, CONFIG, startTime, endTime)
    ]).then(([sensorData, frictionData, pointingData]) => {
        createWearChart(sensorData);
        createTorqueChart(sensorData);
        createLambdaChart(frictionData);
        createPointingChart(pointingData);
    });
}

async function fetchSensorData(state, CONFIG, startTime, endTime) {
    try {
        const format = (d) => d.toISOString().slice(0, 19).replace('T', ' ');
        const axes = ['赤道轴', '赤纬轴', '地平经轴', '地平纬轴'];
        const data = {};

        for (const axis of axes) {
            const response = await fetch(
                `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/sensor-data/axis/${encodeURIComponent(axis)}` +
                `?startTime=${format(startTime)}&endTime=${format(endTime)}`
            );
            if (response.ok) {
                data[axis] = await response.json();
            }
        }
        return data;
    } catch (e) {
        console.error('Failed to fetch sensor data:', e);
        return {};
    }
}

async function fetchFrictionData(state, CONFIG, startTime, endTime) {
    try {
        const format = (d) => d.toISOString().slice(0, 19).replace('T', ' ');
        const response = await fetch(
            `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/simulation/friction` +
            `?startTime=${format(startTime)}&endTime=${format(endTime)}`
        );
        if (response.ok) {
            return await response.json();
        }
        return [];
    } catch (e) {
        console.error('Failed to fetch friction data:', e);
        return [];
    }
}

async function fetchPointingData(state, CONFIG, startTime, endTime) {
    try {
        const format = (d) => d.toISOString().slice(0, 19).replace('T', ' ');
        const response = await fetch(
            `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/pointing` +
            `?startTime=${format(startTime)}&endTime=${format(endTime)}`
        );
        if (response.ok) {
            return await response.json();
        }
        return [];
    } catch (e) {
        console.error('Failed to fetch pointing data:', e);
        return [];
    }
}

function createWearChart(sensorData) {
    const ctx = document.getElementById('wear-chart').getContext('2d');
    const axes = Object.keys(sensorData);

    const datasets = axes.map(axis => {
        const data = sensorData[axis] || [];
        return {
            label: axis,
            data: data.map(d => ({
                x: new Date(d.timestamp),
                y: d.wearDepth
            })),
            borderColor: axisColors[axis],
            backgroundColor: axisColors[axis] + '33',
            borderWidth: 2,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4
        };
    });

    datasets.push({
        label: '磨损阈值 (0.1 mm)',
        data: [],
        borderColor: chartColors.wear.threshold,
        borderWidth: 2,
        borderDash: [5, 5],
        pointRadius: 0
    });

    state.charts.wear = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    labels: { color: '#bbdefb', font: { size: 11 } }
                },
                tooltip: {
                    backgroundColor: 'rgba(13, 71, 161, 0.9)',
                    titleColor: '#81d4fa',
                    bodyColor: '#fff',
                    callbacks: {
                        label: (context) => {
                            const value = context.parsed.y;
                            return `${context.dataset.label}: ${value?.toFixed(6)} mm`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'MM-dd'
                        }
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                },
                y: {
                    title: {
                        display: true,
                        text: '磨损深度 (mm)',
                        color: '#81d4fa'
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                }
            }
        }
    });

    const chart = state.charts.wear;
    if (chart.scales.x) {
        const xRange = chart.scales.x;
        chart.data.datasets[chart.data.datasets.length - 1].data = [
            { x: xRange.min, y: 0.1 },
            { x: xRange.max, y: 0.1 }
        ];
        chart.update('none');
    }
}

function createTorqueChart(sensorData) {
    const ctx = document.getElementById('torque-chart').getContext('2d');
    const axes = Object.keys(sensorData);

    const datasets = axes.map(axis => {
        const data = sensorData[axis] || [];
        return {
            label: axis,
            data: data.map(d => ({
                x: new Date(d.timestamp),
                y: d.frictionTorque
            })),
            borderColor: axisColors[axis],
            backgroundColor: axisColors[axis] + '33',
            borderWidth: 2,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4
        };
    });

    state.charts.torque = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    labels: { color: '#bbdefb', font: { size: 11 } }
                },
                tooltip: {
                    backgroundColor: 'rgba(13, 71, 161, 0.9)',
                    titleColor: '#81d4fa',
                    bodyColor: '#fff',
                    callbacks: {
                        label: (context) => {
                            const value = context.parsed.y;
                            return `${context.dataset.label}: ${value?.toFixed(4)} N·m`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'MM-dd'
                        }
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                },
                y: {
                    title: {
                        display: true,
                        text: '摩擦力矩 (N·m)',
                        color: '#81d4fa'
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                }
            }
        }
    });
}

function createLambdaChart(frictionData) {
    const ctx = document.getElementById('lambda-chart').getContext('2d');
    const axes = ['赤道轴', '赤纬轴', '地平经轴', '地平纬轴'];

    const groupedData = {};
    axes.forEach(axis => groupedData[axis] = []);
    frictionData.forEach(d => {
        if (groupedData[d.axisName]) {
            groupedData[d.axisName].push(d);
        }
    });

    const datasets = axes.map(axis => {
        const data = groupedData[axis] || [];
        return {
            label: axis,
            data: data.map(d => ({
                x: new Date(d.simulationTime),
                y: d.lambdaRatio
            })),
            borderColor: axisColors[axis],
            backgroundColor: axisColors[axis] + '33',
            borderWidth: 2,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4
        };
    });

    state.charts.lambda = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    labels: { color: '#bbdefb', font: { size: 11 } }
                },
                tooltip: {
                    backgroundColor: 'rgba(13, 71, 161, 0.9)',
                    titleColor: '#81d4fa',
                    bodyColor: '#fff',
                    callbacks: {
                        label: (context) => {
                            const value = context.parsed.y;
                            return `${context.dataset.label}: ${value?.toFixed(4)}`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'MM-dd'
                        }
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                },
                y: {
                    title: {
                        display: true,
                        text: 'λ比 (油膜厚度/粗糙度)',
                        color: '#81d4fa'
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                }
            }
        }
    });
}

function createPointingChart(pointingData) {
    const ctx = document.getElementById('pointing-chart').getContext('2d');
    const arcminToDeg = 60;

    const datasets = [
        {
            label: '方位误差',
            data: pointingData.map(d => ({
                x: new Date(d.analysisTime),
                y: d.azimuthError * arcminToDeg
            })),
            borderColor: '#ff6b6b',
            backgroundColor: 'rgba(255, 107, 107, 0.2)',
            borderWidth: 2,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4
        },
        {
            label: '高度误差',
            data: pointingData.map(d => ({
                x: new Date(d.analysisTime),
                y: d.altitudeError * arcminToDeg
            })),
            borderColor: '#4ecdc4',
            backgroundColor: 'rgba(78, 205, 196, 0.2)',
            borderWidth: 2,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4
        },
        {
            label: '总指向误差',
            data: pointingData.map(d => ({
                x: new Date(d.analysisTime),
                y: d.totalPointingError * arcminToDeg
            })),
            borderColor: '#ffe66d',
            backgroundColor: 'rgba(255, 230, 109, 0.2)',
            borderWidth: 3,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4
        },
        {
            label: '阈值 (1 角分)',
            data: [],
            borderColor: chartColors.pointing.threshold,
            borderWidth: 2,
            borderDash: [5, 5],
            pointRadius: 0
        }
    ];

    state.charts.pointing = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    labels: { color: '#bbdefb', font: { size: 11 } }
                },
                tooltip: {
                    backgroundColor: 'rgba(13, 71, 161, 0.9)',
                    titleColor: '#81d4fa',
                    bodyColor: '#fff',
                    callbacks: {
                        label: (context) => {
                            const value = context.parsed.y;
                            return `${context.dataset.label}: ${value?.toFixed(4)} 角分`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'MM-dd'
                        }
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                },
                y: {
                    title: {
                        display: true,
                        text: '指向误差 (角分)',
                        color: '#81d4fa'
                    },
                    grid: { color: 'rgba(129, 212, 250, 0.1)' },
                    ticks: { color: '#bbdefb', font: { size: 10 } }
                }
            }
        }
    });

    const chart = state.charts.pointing;
    if (chart.scales.x) {
        const xRange = chart.scales.x;
        chart.data.datasets[chart.data.datasets.length - 1].data = [
            { x: xRange.min, y: 1.0 },
            { x: xRange.max, y: 1.0 }
        ];
        chart.update('none');
    }
}
