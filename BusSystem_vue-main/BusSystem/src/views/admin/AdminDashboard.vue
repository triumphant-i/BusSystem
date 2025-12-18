<template>
  <div class="admin-container">
    <div class="header">
      <h2>🔧 公交系统后台管理</h2>
      <el-button type="info" size="small" @click="$router.push('/')">返回前台查询</el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      
      <el-tab-pane label="站点管理 (Station)" name="stations">
        <div class="toolbar">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索ID或名称" 
            style="width: 200px" 
            clearable 
            @clear="loadStations"
            @keyup.enter="handleSearchStations"
          />
          <el-button type="primary" icon="Search" @click="handleSearchStations">搜索</el-button>
          <div style="flex-grow: 1"></div>
          <el-button type="success" @click="openStationDialog('add')">+ 新增站点</el-button>
          <el-button type="default" @click="loadStations" icon="Refresh">刷新列表</el-button>
        </div>
        
        <el-table :data="stationList" border stripe style="width: 100%; margin-top: 10px" height="500">
          <el-table-column prop="stationId" label="站点ID" width="100" sortable />
          <el-table-column prop="stationName" label="站点名称" />
          <el-table-column prop="longitude" label="经度" />
          <el-table-column prop="latitude" label="纬度" />
          <el-table-column label="操作" width="180">
            <template #default="scope">
              <el-button type="primary" size="small" @click="openStationDialog('edit', scope.row)">编辑</el-button>
              <el-popconfirm title="确认删除？将同步从经过的线路中移除此站。" @confirm="handleDeleteStation(scope.row.stationId)">
                <template #reference>
                  <el-button type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="线路管理 (Line)" name="lines">
        <div class="toolbar">
          <el-select v-model="lineSearchMode" style="width: 100px; margin-right: 10px">
            <el-option label="全部" value="all" />
            <el-option label="按ID" value="id" />
            <el-option label="按名称" value="name" />
          </el-select>
        
          <el-input 
            v-model="lineSearchKeyword" 
            placeholder="请输入关键字" 
            style="width: 200px" 
            clearable
          />
          <div style="flex-grow: 1"></div>
          <el-button type="success" @click="openLineDialog('add')">+ 新增线路</el-button>
          <el-button type="default" @click="loadLines" icon="Refresh">刷新</el-button>
        </div>

        <el-table :data="filteredLineList" border stripe style="width: 100%; margin-top: 10px" height="500">
          <el-table-column prop="lineOrder" label="ID" width="80" sortable />
          <el-table-column prop="lineName" label="线路名称" width="120" />
          <el-table-column prop="direction" label="方向" width="80" />
          <el-table-column prop="startTime" label="首班" width="100" />
          <el-table-column prop="finishTime" label="末班" width="100" />
          <el-table-column prop="intervalTime" label="间隔(分)" width="100" />
          <el-table-column label="操作" width="180">
            <template #default="scope">
               <el-button type="primary" size="small" @click="openLineDialog('edit', scope.row)">编辑</el-button>
              <el-popconfirm title="确认删除此线路？" @confirm="handleDeleteLine(scope.row.lineOrder)">
                <template #reference>
                  <el-button type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="stationDialogVisible" :title="stationMode === 'add' ? '新增站点' : '修改站点'" width="400px">
      <el-form :model="stationForm" label-width="80px">
        <el-form-item label="ID">
          <el-input v-model="stationForm.id" type="number" :disabled="stationMode === 'edit'" placeholder="请输入数字ID" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="stationForm.name" placeholder="请输入站点名" />
        </el-form-item>
        <div v-if="stationMode==='add'" style="font-size: 12px; color: gray; margin-left: 80px;">
          * 经纬度将由系统自动调用百度地图API生成
        </div>
      </el-form>
      <template #footer>
        <el-button @click="stationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lineDialogVisible" :title="lineMode === 'add' ? '新增线路' : '修改线路'" width="600px">
      <el-form :model="lineForm" label-width="100px">
        <el-form-item label="线路ID">
          <el-input v-model="lineForm.lineOrder" type="number" :disabled="lineMode === 'edit'" placeholder="数字编号" />
        </el-form-item>
        <el-form-item label="线路名称">
          <el-input v-model="lineForm.lineName" placeholder="例如：1路" />
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="lineForm.direction">
            <el-radio label="上行">上行</el-radio>
            <el-radio label="下行">下行</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="运营时间">
          <el-time-picker v-model="lineForm.startTime" value-format="HH:mm:ss" placeholder="首班" style="width: 140px"/>
          -
          <el-time-picker v-model="lineForm.finishTime" value-format="HH:mm:ss" placeholder="末班" style="width: 140px"/>
        </el-form-item>
        <el-form-item label="间隔(分)">
           <el-input-number v-model="lineForm.intervalTime" :min="1" />
        </el-form-item>
        <el-form-item label="站点ID序列">
          <el-select 
            v-model="lineForm.stationIds" 
            multiple 
            filterable 
            allow-create 
            default-first-option
            placeholder="请输入/选择站点ID">
            <el-option 
              v-for="item in stationList" 
              :key="item.stationId" 
              :label="item.stationId + ' - ' + item.stationName" 
              :value="item.stationId" />
          </el-select>
          <div style="font-size: 12px; color: gray">请按顺序选择或输入站点ID</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitLine">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { getAllStations, searchStations as apiSearchStations, addStation, updateStation, deleteStation, getAllRoutes, addLine, updateLine, deleteLine } from '@/api/bus';
import { ElMessage } from 'element-plus';

const activeTab = ref('stations');
const searchKeyword = ref('');

// --- 站点逻辑 ---
const stationList = ref([]);
const stationDialogVisible = ref(false);
const stationMode = ref('add'); // 'add' or 'edit'
const stationForm = ref({ id: '', name: '' });
const lineSearchMode = ref('all'); // 搜索模式：all, id, name

// 【兼容修复】站点数据清洗
// 保证拿到 stationId 和 stationName
const mapStationData = (rawList) => {
  return (rawList || []).map(s => ({
    ...s,
    stationId: s.stationId || s.station_id || s.id,
    stationName: s.stationName || s.station_name || s.name,
    longitude: s.longitude || s.lng,
    latitude: s.latitude || s.lat
  }));
};

const loadStations = async () => {
  try {
    const res = await apiSearchStations(null);
    stationList.value = mapStationData(res.data);
  } catch (e) { console.error(e); }
};

const handleSearchStations = async () => {
  try {
    const res = await apiSearchStations(searchKeyword.value);
    stationList.value = mapStationData(res.data);
  } catch (e) { console.error(e); }
}

const openStationDialog = (mode, row) => {
  stationMode.value = mode;
  if (mode === 'edit' && row) {
    stationForm.value = { id: row.stationId, name: row.stationName };
  } else {
    stationForm.value = { id: '', name: '' };
  }
  stationDialogVisible.value = true;
};

const submitStation = async () => {
  const { id, name } = stationForm.value;
  if(!id || !name) return ElMessage.warning("请填写完整");
  
  let res;
  if (stationMode.value === 'add') {
    res = await addStation(id, name);
  } else {
    res = await updateStation(id, name);
  }

  if (res && res.success) {
    ElMessage.success(res.message);
    stationDialogVisible.value = false;
    loadStations();
  } else {
    ElMessage.error(res.message || '操作失败，可能ID已存在');
  }
};

const handleDeleteStation = async (id) => {
  const res = await deleteStation(id);
  if (res && res.success) {
    ElMessage.success(res.message);
    loadStations();
  } else {
    ElMessage.error(res.message);
  }
};

// --- 线路逻辑 ---
const lineList = ref([]);
const lineDialogVisible = ref(false);
const lineMode = ref('add');
const lineForm = ref({});
const lineSearchKeyword = ref(''); 

const filteredLineList = computed(() => {
  if (!lineSearchKeyword.value) {
    return lineList.value;
  }
  const kw = lineSearchKeyword.value.toLowerCase().trim();
  const mode = lineSearchMode.value;

  return lineList.value.filter(line => {
    const idMatch = String(line.lineOrder).includes(kw);
    const nameMatch = line.lineName && line.lineName.toLowerCase().includes(kw);

    if (mode === 'id') return idMatch; 
    if (mode === 'name') return nameMatch;
    return idMatch || nameMatch; 
  });
});

const loadLines = async () => {
  try {
    const res = await getAllRoutes();
    const rawList = Array.isArray(res) ? res : [];
    
    // 【兼容修复】线路数据清洗
    lineList.value = rawList.map(r => ({
      ...r,
      lineOrder: r.lineOrder || r.line_order,
      lineName: r.lineName || r.line_name,
      direction: r.direction, 
      startTime: r.startTime || r.start_time || r.st,
      finishTime: r.finishTime || r.finish_time || r.ft,
      intervalTime: r.intervalTime !== undefined ? r.intervalTime : (r.interval_time || r.interval),
      stationIds: r.stationIds || r.station_ids || []
    }));
  } catch (e) { console.error(e); }
};

const openLineDialog = (mode, row) => {
  lineMode.value = mode;
  if (mode === 'edit' && row) {
    lineForm.value = { 
      lineOrder: row.lineOrder, 
      lineName: row.lineName, 
      direction: row.direction, 
      startTime: row.startTime, 
      finishTime: row.finishTime, 
      intervalTime: row.intervalTime,
      stationIds: row.stationIds || [] 
    };
  } else {
    lineForm.value = { lineOrder: '', lineName: '', direction: '上行', startTime: '06:30:00', finishTime: '21:30:00', intervalTime: 10, stationIds: [] };
  }
  lineDialogVisible.value = true;
};

const submitLine = async () => {
  const payload = { ...lineForm.value };
  let res;
  if (lineMode.value === 'add') {
    res = await addLine(payload);
  } else {
    res = await updateLine(payload);
  }

  if (res && res.success) {
    ElMessage.success(res.message);
    lineDialogVisible.value = false;
    loadLines();
  } else {
    ElMessage.error(res.message || '操作失败');
  }
};

const handleDeleteLine = async (id) => {
  const res = await deleteLine(id);
  if (res && res.success) {
    ElMessage.success(res.message);
    loadLines();
  } else {
    ElMessage.error(res.message);
  }
};

onMounted(() => {
  loadStations();
  loadLines();
});
</script>

<style scoped>
.admin-container { padding: 20px; background: #f5f7fa; min-height: 100vh; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.toolbar { margin-bottom: 15px; display: flex; gap: 10px; align-items: center; }
</style>