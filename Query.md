<template>
  <div class="query-page">
    <el-card class="search-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon :size="24"><Search /></el-icon>
          <span>乘车方案查询</span>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="出发站">
              <el-select
                v-model="form.from"
                placeholder="请选择出发站"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="station in stations"
                  :key="station.Station_ID"
                  :label="station.Station_NAME"
                  :value="station.Station_ID"
                />
              </el-select>
            </el-form-item>
          </el-col>
          
          <el-col :span="8">
            <el-form-item label="目的站">
              <el-select
                v-model="form.to"
                placeholder="请选择目的站"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="station in stations"
                  :key="station.Station_ID"
                  :label="station.Station_NAME"
                  :value="station.Station_ID"
                />
              </el-select>
            </el-form-item>
          </el-col>
          
          <el-col :span="8">
            <el-form-item label=" ">
              <el-button
                type="primary"
                :icon="Search"
                @click="handleSearch"
                :loading="loading"
                style="width: 100%"
              >
                查询路线
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
    
    <!-- 地图展示 - 新增 -->
    <el-card v-if="showMap" shadow="hover" class="map-card">
      <template #header>
        <div class="card-header">
          <el-icon :size="20"><MapLocation /></el-icon>
          <span>路线地图</span>
        </div>
      </template>
      
      <BaiduMap
        ref="mapRef"
        :routes="mapRoutes"
        :stations="mapStations"
        :center="mapCenter"
      />
    </el-card>
    
    <!-- 查询结果 -->
    <div v-if="results" class="results">
      <!-- 直达方案 -->
      <el-card v-if="results.direct && results.direct.length > 0" shadow="hover" class="result-card">
        <template #header>
          <div class="card-header success">
            <el-icon :size="20"><CircleCheck /></el-icon>
            <span>直达方案</span>
          </div>
        </template>
        
        <div v-for="(route, index) in results.direct" :key="index" class="route-item direct">
          <div class="route-header">
            <el-tag type="success" size="large">{{ route.line_name }}</el-tag>
            <span class="time-info">
              <el-icon><Clock /></el-icon>
              首班 {{ route.first_bus }} | 末班 {{ route.last_bus }}
            </span>
            <!-- 新增：在地图上显示按钮 -->
            <el-button size="small" @click="showOnMap(route, 'direct')">
              <el-icon><MapLocation /></el-icon>
              地图显示
            </el-button>
          </div>
          
          <div class="stations-flow">
            <div
              v-for="(station, idx) in route.stations"
              :key="idx"
              class="station-node"
            >
              <div class="node-circle" :class="{ start: idx === 0, end: idx === route.stations.length - 1 }"></div>
              <div class="station-name">{{ station.name }}</div>
            </div>
          </div>
        </div>
      </el-card>
    
      <!-- 换乘方案 -->
      <el-card v-if="results.transfer && results.transfer.length > 0" shadow="hover" class="result-card">
        <template #header>
          <div class="card-header warning">
            <el-icon :size="20"><Switch /></el-icon>
            <span>一次换乘方案</span>
          </div>
        </template>
        
        <div v-for="(route, index) in results.transfer" :key="index" class="route-item transfer">
          <div class="transfer-step">
            <el-tag type="primary">{{ route.first_line }}</el-tag>
            <el-icon class="arrow"><Right /></el-icon>
            <span class="station">{{ route.from_station }}</span>
            <el-icon class="arrow"><Right /></el-icon>
            <el-tag type="warning">{{ route.transfer_station }}</el-tag>
            <span class="transfer-label">换乘</span>
          </div>
          
          <div class="transfer-step">
            <el-tag type="success">{{ route.second_line }}</el-tag>
            <el-icon class="arrow"><Right /></el-icon>
            <el-tag type="warning">{{ route.transfer_station }}</el-tag>
            <el-icon class="arrow"><Right /></el-icon>
            <span class="station">{{ route.to_station }}</span>
          </div>
          
          <!-- 新增：在地图上显示按钮 -->
          <div style="margin-top: 12px;">
            <el-button size="small" @click="showOnMap(route, 'transfer')">
              <el-icon><MapLocation /></el-icon>
              地图显示
            </el-button>
          </div>
        </div>
      </el-card>
    
      <!-- 无结果 -->
      <el-empty
        v-if="(!results.direct || results.direct.length === 0) && (!results.transfer || results.transfer.length === 0)"
        description="暂无合适的乘车方案"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, CircleCheck, Switch, Clock, Right, MapLocation } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getStations, searchRoute } from '@/api/bus'
import BaiduMap from '@/components/BaiduMap.vue'

const form = ref({
  from: '',
  to: ''
})

const stations = ref([])
const results = ref(null)
const loading = ref(false)
const mapRef = ref(null)
const showMap = ref(false)

// 地图数据
const mapRoutes = ref([])
const mapStations = ref([])
const mapCenter = ref({ lat: 39.915, lng: 116.404 })

// 获取站点列表
const fetchStations = async () => {
  try {
    const data = await getStations()
    stations.value = data
  } catch (error) {
    ElMessage.error('获取站点列表失败')
  }
}

// 查询路线
const handleSearch = async () => {
  if (!form.value.from || !form.value.to) {
    ElMessage.warning('请选择出发站和目的站')
    return
  }

  if (form.value.from === form.value.to) {
    ElMessage.warning('出发站和目的站不能相同')
    return
  }

  loading.value = true
  try {
    const data = await searchRoute({
      from: form.value.from,
      to: form.value.to
    })
    results.value = data
    
    if ((!data.direct || data.direct.length === 0) && 
        (!data.transfer || data.transfer.length === 0)) {
      ElMessage.info('未找到合适的乘车方案')
      showMap.value = false
    }
  } catch (error) {
    ElMessage.error('查询失败，请重试')
  } finally {
    loading.value = false
  }
}

// 在地图上显示路线
const showOnMap = (route, type) => {
  showMap.value = true

  if (type === 'direct') {
    // 直达路线
    mapRoutes.value = [{
      stations: route.stations.map(s => ({
        name: s.name || s,
        lat: s.lat || 39.915 + Math.random() * 0.1, // 模拟坐标，实际应从后端获取
        lng: s.lng || 116.404 + Math.random() * 0.1,
        id: s.id
      }))
    }]
    
    mapStations.value = mapRoutes.value[0].stations
  } else {
    // 换乘路线 - 需要后端返回完整站点信息
    // 这里简化处理，实际需要调用API获取每条线路的详细站点
    ElMessage.warning('换乘路线地图展示需要后端提供详细站点坐标')
  }

  // 计算地图中心点
  if (mapStations.value.length > 0) {
    const avgLat = mapStations.value.reduce((sum, s) => sum + s.lat, 0) / mapStations.value.length
    const avgLng = mapStations.value.reduce((sum, s) => sum + s.lng, 0) / mapStations.value.length
    mapCenter.value = { lat: avgLat, lng: avgLng }
  }

  // 滚动到地图位置
  setTimeout(() => {
    document.querySelector('.map-card')?.scrollIntoView({ behavior: 'smooth' })
  }, 100)
}

onMounted(() => {
  fetchStations()
})
</script>

<style scoped>
.query-page {
  max-width: 1200px;
  margin: 0 auto;
}

.search-card,
.map-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: bold;
}

.card-header.success {
  color: #67C23A;
}

.card-header.warning {
  color: #E6A23C;
}

.results {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.result-card {
  animation: fadeIn 0.3s;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.route-item {
  padding: 20px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 16px;
}

.route-item:last-child {
  margin-bottom: 0;
}

.route-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.time-info {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 14px;
}

.stations-flow {
  display: flex;
  align-items: center;
  gap: 20px;
  overflow-x: auto;
  padding: 10px 0;
}

.station-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
}

.station-node:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 12px;
  left: calc(100% + 10px);
  width: 40px;
  height: 2px;
  background: #67C23A;
}

.node-circle {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #67C23A;
  border: 3px solid #f0f9ff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.node-circle.start {
  background: #409EFF;
}

.node-circle.end {
  background: #F56C6C;
}

.station-name {
  font-size: 14px;
  color: #303133;
  white-space: nowrap;
  font-weight: 500;
}

.transfer-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  flex-wrap: wrap;
}

.transfer-step .arrow {
  color: #909399;
}

.transfer-step .station {
  padding: 4px 12px;
  background: white;
  border-radius: 4px;
  border: 1px solid #DCDFE6;
  font-size: 14px;
}

.transfer-label {
  color: #E6A23C;
  font-weight: bold;
  font-size: 12px;
}
</style>