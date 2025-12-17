<template>
  <div class="query-container">
    <div class="sidebar">
      <h2>🚌 实时公交查询</h2>
      <el-card class="box-card">
        <el-form label-position="top">
          <el-form-item label="起点">
            <el-autocomplete
              v-model="startInput"
              :fetch-suggestions="querySearch"
              placeholder="输入 ID 或 站名"
              @select="(item) => handleSelect(item, 'start')"
              class="w-100"
              :trigger-on-focus="false"
              clearable
            >
              <template #default="{ item }">
                <div class="suggestion-row">
                  <span class="s-name">{{ item.value }}</span>
                  <span class="s-id">ID:{{ item.id }}</span>
                </div>
              </template>
            </el-autocomplete>
          </el-form-item>

          <el-form-item label="终点">
            <el-autocomplete
              v-model="endInput"
              :fetch-suggestions="querySearch"
              placeholder="输入 ID 或 站名"
              @select="(item) => handleSelect(item, 'end')"
              class="w-100"
              :trigger-on-focus="false"
              clearable
            >
              <template #default="{ item }">
                <div class="suggestion-row">
                  <span class="s-name">{{ item.value }}</span>
                  <span class="s-id">ID:{{ item.id }}</span>
                </div>
              </template>
            </el-autocomplete>
          </el-form-item>

          <el-button type="primary" class="w-100" @click="handlePlan" :loading="loading" size="large">
            查询方案
          </el-button>
        </el-form>
      </el-card>

      <div class="result-list">
         <el-alert v-if="routes.length === 0 && searched" title="未找到合适路线或后端数据为空" type="warning" :closable="false" show-icon style="margin-top: 10px"/>
         
         <div v-for="(route, idx) in routes" :key="idx" class="route-item" @click="drawRoute(route)">
            <div class="r-head">
               <el-tag effect="dark">方案 {{ idx + 1 }}</el-tag>
               <span>约 {{ route.duration || '?' }} 分钟</span>
            </div>
            <div class="r-body">
               <div>换乘: {{ route.transfers || 0 }} 次 | 总站数: {{ route.totalStops || 0 }}</div>
               <div v-if="route.segments && route.segments.length">
                 <div v-for="(seg, sIdx) in route.segments" :key="sIdx" class="seg-info">
                    - 乘坐 <b>{{ seg.lineName || '未知线路' }}</b> ({{ seg.stopsCount || 0 }}站)
                 </div>
               </div>
               <div v-else class="seg-info" style="color: red">
                 (该方案无详细路段数据)
               </div>
            </div>
         </div>
      </div>
    </div>

    <div class="map-box">
       <BaiduMap :ak="mapAK" @map-loaded="onMapLoaded" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { searchStations, planRoute } from '@/api/bus';
import BaiduMap from '@/components/BaiduMap.vue';
import { ElMessage } from 'element-plus';

// ⚠️ 记得填 AK
const mapAK = 'SYQHP6YoaTDqq1EB0FxeIDzQUlWu0IMD'; 
const startInput = ref('');
const endInput = ref('');
const startStation = ref(null);
const endStation = ref(null);
const routes = ref([]);
const loading = ref(false);
const searched = ref(false);
let mapInstance = null;

// ==========================================
// 1. 站点搜索 & 数据清洗
// ==========================================
const querySearch = async (queryString, cb) => {
  // 如果没输入内容，就不搜索，直接返回空
  if (!queryString) {
    cb([]);
    return;
  }
  
  try {
    const res = await searchStations(queryString);
    
    // 🔍 【调试关键点】请按 F12 看控制台打印了什么！
    console.log("【调试】API返回的原始数据:", res);

    // 1. 确保 list 是数组 (防止后端返回 null 或 undefined)
    let list = [];
    if (Array.isArray(res)) {
      list = res;
    } else if (res && Array.isArray(res.data)) {
      list = res.data;
    } else if (res && Array.isArray(res.content)) {
      list = res.content;
    }

    // 2. 映射数据（修复核心）
    const results = list.map(item => {
      let name = "未知站点";
      let id = 0;
      let lat = 0;
      let lng = 0;

      // 情况A：如果 item 本身就是字符串（例如后端返回 ["站点A", "站点B"]）
      if (typeof item === 'string') {
        name = item;
        id = item; // ID 暂时也用名字代替
      } 
      // 情况B：如果 item 是对象
      else if (typeof item === 'object' && item !== null) {
        // 暴力尝试所有可能的字段名
        name = item.stationName || item.name || item.station_name || item.value || item.s_name || "未知站点";
        id = item.stationId || item.id || item.station_id || 0;
        
        // 尝试获取坐标
        lat = parseFloat(item.latitude || item.lat || 0);
        lng = parseFloat(item.longitude || item.lng || 0);
      }

      // Element Plus 的 Autocomplete 组件必须包含 'value' 字段才能显示文字
      return {
        value: name,   // 必须有 value 字段！
        id: id,
        lat: lat,
        lng: lng,
        original: item // 存一份原始数据备用
      };
    });
    
    // 3. 过滤掉名字为空的数据，防止空行
    const validResults = results.filter(r => r.value && r.value !== "未知站点");
    
    console.log("【调试】清洗后给下拉框的数据:", validResults);
    cb(validResults);

  } catch (e) {
    console.error("搜索接口报错:", e);
    cb([]);
  }
};

const handleSelect = (item, type) => {
  if (type === 'start') {
    startInput.value = item.value;
    startStation.value = item;
  } else {
    endInput.value = item.value;
    endStation.value = item;
  }
};

// ==========================================
// 2. 路径规划 & 结果清洗 (修复"垃圾数据"问题)
// ==========================================
const handlePlan = async () => {
  if (!startInput.value || !endInput.value) return ElMessage.warning('请输入起终点');

  // 容错：如果用户只输入了文字没有下拉选择，尝试构造一个临时对象（虽然没有坐标）
  if (!startStation.value) startStation.value = { value: startInput.value, lng: 0, lat: 0 };
  if (!endStation.value) endStation.value = { value: endInput.value, lng: 0, lat: 0 };

  loading.value = true;
  searched.value = true;
  routes.value = [];
  
  try {
    // 调用后端接口
    const rawRes = await planRoute(startStation.value.value, endStation.value.value);
    console.log("【后端原始方案数据】:", rawRes);

    const rawList = Array.isArray(rawRes) ? rawRes : [];

    // 🔥 关键修复：清洗路线数据，处理空值
    routes.value = rawList.map(r => ({
      ...r, // 保留原始属性
      // 防止 null 导致页面显示空白
      duration: r.duration || 0, 
      transfers: r.transfers || 0,
      totalStops: r.totalStops || 0,
      // 确保 segments 是数组
      segments: Array.isArray(r.segments) ? r.segments : []
    }));

    if (routes.value.length > 0) {
      // 默认绘制第一条
      drawRoute(routes.value[0]);
    } else {
      ElMessage.info('后端返回了 0 条方案，请检查数据库是否有连通线路');
    }
  } catch (e) {
    console.error(e);
    ElMessage.error('查询失败');
  } finally {
    loading.value = false;
  }
};

const onMapLoaded = (map) => { mapInstance = map; };

// ==========================================
// 3. 地图绘制 (修复地图不显示问题)
// ==========================================
const drawRoute = (route) => {
  if (!mapInstance) return;
  
  // 1. 清除旧覆盖物
  mapInstance.clearOverlays();

  // 2. 检查坐标有效性
  // 注意：如果用户是手动输入文字而没点下拉框，坐标可能是 0，这时候无法绘图
  const sLat = startStation.value?.lat;
  const sLng = startStation.value?.lng;
  const eLat = endStation.value?.lat;
  const eLng = endStation.value?.lng;

  if (!sLat || !sLng || !eLat || !eLng || sLat === 0 || eLat === 0) {
     ElMessage.warning('当前选中站点无精确坐标，无法在地图显示');
     return;
  }

  const p1 = new window.BMap.Point(sLng, sLat);
  const p2 = new window.BMap.Point(eLng, eLat);

  // 3. 绘制起点和终点图标 (Marker)
  const startMarker = new window.BMap.Marker(p1);
  const endMarker = new window.BMap.Marker(p2);
  
  // 给 Marker 加个标签看看
  startMarker.setLabel(new window.BMap.Label("起点", { offset: new window.BMap.Size(20, -10) }));
  endMarker.setLabel(new window.BMap.Label("终点", { offset: new window.BMap.Size(20, -10) }));

  mapInstance.addOverlay(startMarker);
  mapInstance.addOverlay(endMarker);

  // 4. 绘制连接线 (Polyline)
  // ⚠️ 注意：因为后端 SegmentDTO 里只有站点ID，没有所有中间路径的经纬度数组
  // 所以我们暂时只能画一条直线连接起终点，表示逻辑上的连通。
  // 如果要画弯弯曲曲的真实路线，后端需要在 SegmentDTO 里返回 points: [{lat,lng}, ...]
  const polyline = new window.BMap.Polyline([p1, p2], {
    strokeColor: "blue",
    strokeWeight: 6,
    strokeOpacity: 0.5,
    strokeStyle: 'dashed' // 虚线，表示这是逻辑路线
  });
  mapInstance.addOverlay(polyline);

  // 5. 自动调整视野，让起点终点都出现在屏幕内
  mapInstance.setViewport([p1, p2]);
  
  // ⚠️ 弃用了 transit.search(p1, p2)，因为那个是查百度的库，不是查你的库
};
</script>

<style scoped>
.query-container { display: flex; height: 100vh; }
.sidebar { width: 380px; padding: 20px; background: #fff; overflow-y: auto; box-shadow: 2px 0 10px rgba(0,0,0,0.1); z-index: 10; }
.map-box { flex: 1; }
.w-100 { width: 100%; }
.suggestion-row { display: flex; justify-content: space-between; color: #333; font-size: 14px; padding: 5px 0; }
.s-id { color: #999; font-size: 12px; }
.route-item { border: 1px solid #eee; padding: 15px; margin-top: 15px; border-radius: 8px; cursor: pointer; transition: all 0.3s; }
.route-item:hover { border-color: #409EFF; background: #f0f9eb; }
.r-head { display: flex; justify-content: space-between; font-weight: bold; margin-bottom: 8px; }
.seg-info { color: #666; font-size: 13px; margin-top: 4px; }
</style>