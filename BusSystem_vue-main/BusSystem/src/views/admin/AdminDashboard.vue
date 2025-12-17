<template>
  <div class="admin-container">
    <div class="header">
      <h2>🔧 公交系统后台管理</h2>
      <el-button type="info" size="small" @click="$router.push('/')">返回前台查询</el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      
      <el-tab-pane label="站点管理 (Station)" name="stations">
        <div class="toolbar">
          <el-button type="primary" @click="openStationDialog">+ 新增站点</el-button>
          <el-button type="default" @click="loadStations" icon="Refresh">刷新</el-button>
        </div>
        
        <el-table :data="stationList" border stripe style="width: 100%; margin-top: 10px" height="500">
          <el-table-column prop="stationId" label="站点ID" width="100" sortable />
          <el-table-column prop="stationName" label="站点名称" />
          <el-table-column prop="longitude" label="经度" />
          <el-table-column prop="latitude" label="纬度" />
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-popconfirm title="确认删除此站点？" @confirm="handleDeleteStation(scope.row.stationId)">
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
          <el-button type="success" @click="openLineDialog">+ 新增线路</el-button>
          <el-button type="default" @click="loadLines" icon="Refresh">刷新</el-button>
        </div>

        <el-table :data="lineList" border stripe style="width: 100%; margin-top: 10px" height="500">
          <el-table-column prop="lineOrder" label="线路编号/ID" width="120" sortable />
          <el-table-column prop="lineName" label="线路名称" width="150">
             <template #default="{ row }">
               <el-tag>{{ row.lineName }}</el-tag>
             </template>
          </el-table-column>
          <el-table-column prop="direction" label="方向" width="100" />
          <el-table-column prop="startTime" label="首班车" />
          <el-table-column prop="finishTime" label="末班车" />
          <el-table-column prop="intervalTime" label="发车间隔(分)" width="120" />
          <el-table-column label="操作" width="120">
            <template #default="scope">
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

    <el-dialog v-model="stationDialogVisible" title="新增站点" width="400px">
      <el-form :model="stationForm" label-width="80px">
        <el-form-item label="ID">
          <el-input v-model="stationForm.id" type="number" placeholder="请输入数字ID" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="stationForm.name" placeholder="请输入站点名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lineDialogVisible" title="新增线路" width="500px">
      <el-form :model="lineForm" label-width="100px">
        <el-form-item label="线路ID">
          <el-input v-model="lineForm.lineOrder" type="number" placeholder="数字编号" />
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
        <el-form-item label="首班时间">
          <el-time-picker v-model="lineForm.startTime" value-format="HH:mm:ss" placeholder="06:00:00" />
        </el-form-item>
        <el-form-item label="末班时间">
          <el-time-picker v-model="lineForm.finishTime" value-format="HH:mm:ss" placeholder="22:00:00" />
        </el-form-item>
        <el-form-item label="间隔(分)">
           <el-input-number v-model="lineForm.intervalTime" :min="1" />
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
import { ref, onMounted } from 'vue';
import { getAllStations, addStation, deleteStation, getAllRoutes, addLine, deleteLine } from '@/api/bus';
import { ElMessage } from 'element-plus';

const activeTab = ref('stations');

// --- 站点逻辑 ---
const stationList = ref([]);
const stationDialogVisible = ref(false);
const stationForm = ref({ id: '', name: '' });

const loadStations = async () => {
  try {
    const res = await getAllStations();
    // 强制修正：如果后端返回的是对象里包数组，尝试取一下，防止 array 报错
    stationList.value = Array.isArray(res) ? res : [];
  } catch (e) { console.error(e); }
};

const openStationDialog = () => { stationForm.value = {id: '', name: ''}; stationDialogVisible.value = true; };
const submitStation = async () => {
  await addStation(stationForm.value.id, stationForm.value.name);
  ElMessage.success('站点添加成功');
  stationDialogVisible.value = false;
  loadStations();
};
const handleDeleteStation = async (id) => {
  await deleteStation(id);
  ElMessage.success('已删除');
  loadStations();
};

// --- 线路逻辑 ---
const lineList = ref([]);
const lineDialogVisible = ref(false);
const lineForm = ref({});

const loadLines = async () => {
  try {
    const res = await getAllRoutes();
    lineList.value = Array.isArray(res) ? res : [];
  } catch (e) { console.error(e); }
};

const openLineDialog = () => {
  lineForm.value = { lineOrder: '', lineName: '', direction: '上行', startTime: '06:30:00', finishTime: '21:30:00', intervalTime: 10 };
  lineDialogVisible.value = true;
};

const submitLine = async () => {
  // 构造 API 需要的 JSON 结构
  const payload = { ...lineForm.value };
  try {
    await addLine(payload);
    ElMessage.success('线路添加成功');
    lineDialogVisible.value = false;
    loadLines();
  } catch(e) { console.error(e); }
};

const handleDeleteLine = async (id) => {
  await deleteLine(id);
  ElMessage.success('线路已删除');
  loadLines();
};

onMounted(() => {
  loadStations();
  loadLines();
});
</script>

<style scoped>
.admin-container { padding: 20px; background: #f5f7fa; min-height: 100vh; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.toolbar { margin-bottom: 15px; display: flex; gap: 10px; }
</style>