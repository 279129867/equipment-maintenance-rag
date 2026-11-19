const { createApp, reactive, onMounted } = Vue;

const API = {
    async request(url, options) {
        const res = await fetch(url, options || {});
        const text = await res.text();
        let body;
        try {
            body = text ? JSON.parse(text) : {};
        } catch (e) {
            throw new Error('返回不是合法 JSON：' + text.slice(0, 120));
        }
        if (!res.ok) {
            throw new Error(body.msg || ('HTTP ' + res.status));
        }
        if (body.code !== 0) {
            throw new Error(body.msg || '业务处理失败');
        }
        return body.data;
    },
    get(url) {
        return this.request(url);
    },
    post(url, data) {
        return this.request(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        });
    },
    postForm(url, formData) {
        return this.request(url, {method: 'POST', body: formData});
    },
    del(url) {
        return this.request(url, {method: 'DELETE'});
    }
};

const STATUS_TEXT = {10: '待派单', 20: '待接单', 30: '维修中', 35: '已挂起', 40: '待验收', 50: '已关闭', 60: '已驳回', 70: '已取消'};
const STATUS_CLASS = {10: 'amber', 20: 'amber', 30: 'blue', 35: 'gray', 40: 'teal', 50: 'green', 60: 'gray', 70: 'gray'};
const ACTION_TEXT = {
    CREATE: '创建', DISPATCH: '派单', ACCEPT: '接单', SUSPEND: '挂起', RESUME: '恢复',
    FINISH: '完工', PASS: '验收通过', REWORK: '返修', CANCEL: '取消', REJECT: '驳回'
};
const ACTIONS = {
    10: [['DISPATCH', '派单'], ['CANCEL', '取消'], ['REJECT', '驳回']],
    20: [['ACCEPT', '接单'], ['CANCEL', '取消']],
    30: [['FINISH', '完工'], ['SUSPEND', '挂起']],
    35: [['RESUME', '恢复'], ['FINISH', '完工']],
    40: [['PASS', '验收通过'], ['REWORK', '返修']]
};

createApp({
    setup() {
        const state = reactive({
            view: 'orders',
            routeId: null,
            busy: false,
            toast: '',
            toastError: false,

            assetMap: {},
            assets: {list: [], total: 0, page: 1, size: 10},
            assetQuery: {keyword: '', workshop: '', assetType: ''},
            assetFormOpen: false,
            assetForm: {
                assetCode: '', assetName: '', assetType: 'CNC加工中心', workshop: '',
                lineName: '', location: '', manufacturer: '', model: '', criticalLevel: 2
            },

            plans: [],
            planFormOpen: false,
            planForm: {
                planCode: '', planName: '', assetId: null, cycleType: 1, executorRole: '操作工',
                items: [{itemName: '主轴油温', checkMethod: 1, standardText: '≤45℃', minValue: null, maxValue: 45, unit: '℃'}]
            },
            submitPlan: null,
            submitForm: {inspector: '张工', costMinutes: 15, abnormalDesc: '', items: {}},
            submitResult: null,
            records: {list: [], total: 0, page: 1, size: 10},

            orders: {list: [], total: 0, page: 1, size: 10},
            orderQuery: {status: ''},
            orderFormOpen: false,
            orderForm: {assetId: null, faultType: '机械', faultDesc: '', urgency: null, reporter: '李四'},

            detail: null,
            similar: [],
            detailOperator: '王工',
            detailRemark: '',

            statsQuery: {from: '', to: ''},
            stats: {overview: null, assets: [], workshops: [], faultTypes: [], trend: []},

            rag: {docs: [], question: '', answer: null},
            uploadFile: null,
            uploadAssetType: ''
        });

        function notify(msg, isError) {
            state.toast = msg;
            state.toastError = !!isError;
            setTimeout(() => {
                state.toast = '';
            }, isError ? 5000 : 2600);
        }

        async function run(task) {
            state.busy = true;
            try {
                await task();
            } catch (e) {
                notify(e.message || String(e), true);
            } finally {
                state.busy = false;
            }
        }

        function qs(params) {
            const parts = [];
            Object.keys(params).forEach(k => {
                const v = params[k];
                if (v !== undefined && v !== null && v !== '') {
                    parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
                }
            });
            return parts.length ? '?' + parts.join('&') : '';
        }

        // ---------- 基础数据 ----------
        async function loadAssetMap() {
            const page = await API.get('/api/assets?page=1&size=200');
            const map = {};
            (page.records || []).forEach(a => {
                map[a.id] = a;
            });
            state.assetMap = map;
        }

        async function loadAssets(pageNo) {
            await run(async () => {
                const p = await API.get('/api/assets' + qs({
                    page: pageNo || state.assets.page,
                    size: state.assets.size,
                    keyword: state.assetQuery.keyword,
                    workshop: state.assetQuery.workshop,
                    assetType: state.assetQuery.assetType
                }));
                state.assets.list = p.records || [];
                state.assets.total = p.total || 0;
                state.assets.page = p.current || 1;
                await loadAssetMap();
            });
        }

        function resetAssetQuery() {
            state.assetQuery.keyword = '';
            state.assetQuery.workshop = '';
            state.assetQuery.assetType = '';
            loadAssets(1);
        }

        function resetAssetForm() {
            Object.assign(state.assetForm, {
                assetCode: '', assetName: '', assetType: 'CNC加工中心', workshop: '',
                lineName: '', location: '', manufacturer: '', model: '', criticalLevel: 2
            });
        }

        async function createAsset() {
            const f = state.assetForm;
            if (!f.assetCode.trim() || !f.assetName.trim() || !f.assetType.trim()) {
                return notify('设备编号 / 名称 / 类型 不能为空', true);
            }
            await run(async () => {
                const created = await API.post('/api/assets', {
                    assetCode: f.assetCode.trim(),
                    assetName: f.assetName.trim(),
                    assetType: f.assetType.trim(),
                    workshop: f.workshop,
                    lineName: f.lineName,
                    location: f.location,
                    manufacturer: f.manufacturer,
                    model: f.model,
                    criticalLevel: f.criticalLevel
                });
                notify('已新增设备 ' + created.assetCode);
                state.assetFormOpen = false;
                resetAssetForm();
                await loadAssets(1);
            });
        }

        // ---------- 点检 ----------
        async function loadPlans() {
            await run(async () => {
                state.plans = await API.get('/api/inspection/plans');
                await loadAssetMap();
            });
        }

        async function loadRecords(pageNo) {
            await run(async () => {
                const p = await API.get('/api/inspection/records' + qs({
                    page: pageNo || state.records.page,
                    size: state.records.size
                }));
                state.records.list = p.records || [];
                state.records.total = p.total || 0;
                state.records.page = p.current || 1;
            });
        }

        function addPlanItem() {
            state.planForm.items.push({
                itemName: '', checkMethod: 1, standardText: '', minValue: null, maxValue: null, unit: ''
            });
        }

        function removePlanItem(idx) {
            state.planForm.items.splice(idx, 1);
        }

        async function createPlan() {
            const f = state.planForm;
            if (!f.planCode.trim() || !f.planName.trim() || !f.assetId) {
                return notify('计划编号 / 名称 / 设备 必填', true);
            }
            await run(async () => {
                await API.post('/api/inspection/plans', {
                    planCode: f.planCode.trim(),
                    planName: f.planName.trim(),
                    assetId: f.assetId,
                    cycleType: f.cycleType,
                    executorRole: f.executorRole,
                    items: f.items.filter(i => i.itemName && i.itemName.trim()).map(i => ({
                        itemName: i.itemName.trim(),
                        checkMethod: i.checkMethod,
                        standardText: i.standardText,
                        minValue: i.minValue === '' ? null : i.minValue,
                        maxValue: i.maxValue === '' ? null : i.maxValue,
                        unit: i.unit
                    }))
                });
                notify('点检计划已创建');
                state.planFormOpen = false;
                await loadPlans();
            });
        }

        function openSubmit(plan) {
            state.submitPlan = plan;
            state.submitResult = null;
            const items = {};
            plan.items.forEach(it => {
                items[it.id] = {measureValue: '', result: null};
            });
            state.submitForm.items = items;
        }

        async function submitRecord() {
            if (!state.submitPlan) {
                return;
            }
            const plan = state.submitPlan;
            const payload = {
                planId: plan.plan.id,
                inspector: state.submitForm.inspector,
                costMinutes: state.submitForm.costMinutes,
                abnormalDesc: state.submitForm.abnormalDesc,
                items: plan.items.map(it => {
                    const row = state.submitForm.items[it.id] || {};
                    return {
                        itemId: it.id,
                        result: row.result === null || row.result === undefined ? null : Number(row.result),
                        measureValue: row.measureValue === '' || row.measureValue === null ? null : Number(row.measureValue),
                        photoUrl: '',
                        remark: ''
                    };
                })
            };
            await run(async () => {
                state.submitResult = await API.post('/api/inspection/records', payload);
                notify('点检已提交');
                await loadRecords(1);
                await loadAssets(1);
            });
        }

        // ---------- 工单 ----------
        async function loadOrders(pageNo) {
            await run(async () => {
                const p = await API.get('/api/workorders' + qs({
                    page: pageNo || state.orders.page,
                    size: state.orders.size,
                    status: state.orderQuery.status
                }));
                state.orders.list = p.records || [];
                state.orders.total = p.total || 0;
                state.orders.page = p.current || 1;
                await loadAssetMap();
            });
        }

        async function createOrder() {
            const f = state.orderForm;
            if (!f.assetId || !f.faultDesc.trim()) {
                return notify('设备和故障描述必填', true);
            }
            await run(async () => {
                const created = await API.post('/api/workorders', {
                    assetId: f.assetId,
                    faultType: f.faultType,
                    faultDesc: f.faultDesc.trim(),
                    urgency: f.urgency,
                    reporter: f.reporter
                });
                notify('已创建工单 ' + created.orderNo);
                state.orderFormOpen = false;
                state.orderForm = {assetId: null, faultType: '机械', faultDesc: '', urgency: null, reporter: '李四'};
                await loadOrders(1);
                await loadAssets(1);
            });
        }

        async function loadDetail(id) {
            await run(async () => {
                state.detail = await API.get('/api/workorders/' + id);
                state.similar = [];
                await loadAssetMap();
                try {
                    state.similar = await API.get('/api/rag/similar-orders' + qs({orderId: id, topK: 3}));
                } catch (e) {
                    state.similar = [];
                }
            });
        }

        async function doAction(action) {
            if (!state.detail) {
                return;
            }
            await run(async () => {
                await API.post('/api/workorders/' + state.detail.order.id + '/action', {
                    action: action,
                    operator: state.detailOperator,
                    remark: state.detailRemark
                });
                notify('操作成功：' + action);
                state.detailRemark = '';
                await loadDetail(state.detail.order.id);
                await loadAssets(1);
                if (state.view === 'orders') {
                    await loadOrders(state.orders.page);
                }
            });
        }

        async function feedback(refOrderId, hit) {
            if (!state.detail) {
                return;
            }
            await run(async () => {
                await API.post('/api/rag/similar-orders/feedback' + qs({
                    orderId: state.detail.order.id, refOrderId: refOrderId, hit: hit
                }));
                notify(hit ? '已标记为有用' : '已标记为无关');
            });
        }

        // ---------- 统计 ----------
        async function loadStats() {
            await run(async () => {
                const range = {from: state.statsQuery.from, to: state.statsQuery.to};
                const results = await Promise.all([
                    API.get('/api/stats/overview' + qs(range)),
                    API.get('/api/stats/assets' + qs(range)),
                    API.get('/api/stats/workshops' + qs(range)),
                    API.get('/api/stats/fault-types' + qs(range)),
                    API.get('/api/stats/trend' + qs(range))
                ]);
                state.stats.overview = results[0];
                state.stats.assets = results[1] || [];
                state.stats.workshops = results[2] || [];
                state.stats.faultTypes = results[3] || [];
                state.stats.trend = results[4] || [];
            });
        }

        // ---------- RAG ----------
        async function loadDocs() {
            await run(async () => {
                state.rag.docs = await API.get('/api/rag/documents');
            });
        }

        function onFileChange(e) {
            state.uploadFile = e.target.files && e.target.files[0] ? e.target.files[0] : null;
        }

        async function uploadDoc() {
            if (!state.uploadFile) {
                return notify('请先选择文件', true);
            }
            const form = new FormData();
            form.append('file', state.uploadFile);
            if (state.uploadAssetType.trim()) {
                form.append('assetType', state.uploadAssetType.trim());
            }
            form.append('uploader', 'demo');
            await run(async () => {
                const doc = await API.postForm('/api/rag/documents', form);
                notify('已入库：' + doc.docName + '（' + doc.chunkCount + ' 块，' + doc.parseMsg + '）');
                state.uploadFile = null;
                await loadDocs();
            });
        }

        async function deleteDoc(id) {
            await run(async () => {
                await API.del('/api/rag/documents/' + id);
                notify('文档已删除');
                await loadDocs();
            });
        }

        async function ask() {
            const q = state.rag.question.trim();
            if (!q) {
                return;
            }
            await run(async () => {
                state.rag.answer = await API.post('/api/rag/ask', {question: q, topK: 4});
            });
        }

        async function rebuildOrderIndex() {
            await run(async () => {
                const count = await API.post('/api/rag/index-orders', {});
                notify('已重建历史工单向量：' + count + ' 条');
            });
        }

        // 换向量模型（512 维哈希 ↔ 1024 维 bge-m3）后必须执行一次，否则余弦算不出来
        async function reindexAll() {
            await run(async () => {
                const result = await API.post('/api/rag/reindex', {});
                notify('已重建向量：文档块 ' + result.chunks + ' 块，历史工单 ' + result.orders + ' 条');
            });
        }

        // ---------- 路由 ----------
        function applyRoute() {
            const hash = location.hash.replace(/^#/, '') || '/orders';
            const parts = hash.split('/').filter(Boolean);
            const head = parts[0] || 'orders';
            if (head === 'orders' && parts[1]) {
                state.view = 'orderDetail';
                state.routeId = Number(parts[1]);
                loadDetail(state.routeId);
                return;
            }
            state.detail = null;
            if (head === 'assets') {
                state.view = 'assets';
                loadAssets(state.assets.page);
            } else if (head === 'inspection') {
                state.view = 'inspection';
                loadPlans();
                loadRecords(1);
            } else if (head === 'stats') {
                state.view = 'stats';
                loadStats();
            } else if (head === 'rag') {
                state.view = 'rag';
                loadDocs();
            } else {
                state.view = 'orders';
                loadOrders(state.orders.page);
            }
        }

        function goOrder(id) {
            location.hash = '#/orders/' + id;
        }

        function goOrders() {
            location.hash = '#/orders';
        }

        // ---------- 展示辅助 ----------
        function assetLabel(id) {
            const a = state.assetMap[id];
            if (!a) {
                return '设备#' + id;
            }
            return a.assetCode + ' ' + a.assetName;
        }

        function statusText(code) {
            return STATUS_TEXT[code] || ('状态' + code);
        }

        function statusClass(code) {
            return STATUS_CLASS[code] || 'gray';
        }

        function criticalText(level) {
            return level === 1 ? 'A 关键' : (level === 2 ? 'B 重要' : 'C 一般');
        }

        function runStatusText(v) {
            return ({1: '运行', 2: '停机', 3: '维修中', 4: '报废'})[v] || '-';
        }

        function runStatusClass(v) {
            return v === 1 ? 'green' : (v === 3 ? 'blue' : 'amber');
        }

        function cycleText(v) {
            return ({1: '每班', 2: '每日', 3: '每周'})[v] || '-';
        }

        function actionText(code) {
            return ACTION_TEXT[code] || code || '-';
        }

        function sourceText(v) {            return ({1: '手工报修', 2: '点检异常'})[v] || '-';
        }

        function parseStatusText(v) {
            return ({0: '待解析', 1: '解析中', 2: '成功', 3: '失败'})[v] || '-';
        }

        function fmtTime(v) {
            if (!v) {
                return '-';
            }
            return String(v).replace('T', ' ').slice(0, 16);
        }

        function barWidth(val, rows) {
            const max = rows.reduce((m, r) => Math.max(m, r.downtimeMinutes || 0, r.orderCount || 0), 1);
            const v = val || 0;
            return Math.min(100, Math.round((v / max) * 100)) + '%';
        }

        function newOrderFor(asset) {
            state.orderFormOpen = true;
            state.orderForm.assetId = asset.id;
            state.view = 'orders';
            location.hash = '#/orders';
        }

        function startInspection(asset) {
            state.view = 'inspection';
            location.hash = '#/inspection';
            const plan = state.plans.find(p => p.plan.assetId === asset.id);
            if (plan) {
                openSubmit(plan);
            }
        }

        onMounted(() => {
            window.addEventListener('hashchange', applyRoute);
            loadAssetMap();
            applyRoute();
        });

        // 暴露给模板：全部挂在同一个 reactive 对象上，保持响应式
        Object.assign(state, {
            statusMap: STATUS_TEXT,
            actionsOf: (code) => ACTIONS[code] || [],
            loadAssets, resetAssetQuery, resetAssetForm, createAsset,
            loadPlans, addPlanItem, removePlanItem, createPlan, openSubmit, submitRecord, loadRecords,
            createOrder, doAction, feedback,
            loadStats, loadDocs, onFileChange, uploadDoc, deleteDoc, ask, rebuildOrderIndex, reindexAll,
            goOrder, goOrders, assetLabel, statusText, statusClass, criticalText,
            runStatusText, runStatusClass, cycleText, sourceText, parseStatusText, fmtTime, barWidth, actionText,
            newOrderFor, startInspection
        });
        return state;
    }
}).mount('#app');
