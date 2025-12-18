<template>
  <div class="query-container">
    <div class="sidebar">
      <div class="sidebar-header">
        <h2>🚌 实时公交查询</h2>
        <el-button link type="primary" size="small" @click="$router.push('/admin')">
          去后台管理 >
        </el-button>
      </div>

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
            查询方案 (直达/换乘1次)
          </el-button>
        </el-form>
      </el-card>

      <div class="result-list">
         <el-alert v-if="routes.length === 0 && searched" title="未找到合适路线 (仅限直达或一次换乘)" type="warning" :closable="false" show-icon style="margin-top: 10px"/>
         
         <div v-for="(route, idx) in routes" :key="idx" 
              class="route-item" 
              :class="{ active: selectedRouteIndex === idx }"
              @click="handleRouteClick(route, idx)">
            <div class="r-head">
               <div style="display: flex; align-items: center;">
                 <span>方案 {{ idx + 1 }}</span>
                 <el-tag v-if="route.transfers === 0" type="success" effect="dark" size="small" style="margin-left: 8px;">
                   直达
                 </el-tag>
                 <el-tag v-else type="info" size="small" style="margin-left: 8px;">
                   换乘 {{ route.transfers }} 次
                 </el-tag>
               </div>
               <span style="font-weight: normal; color: #666; font-size: 13px;">
                 约 {{ route.duration || (route.totalStops * 3) }} 分钟
               </span>
            </div>
            <div class="r-body">
               <div class="sub-info">
                 换乘: {{ route.transfers }} 次 | 总站数: {{ route.totalStops }}
               </div>
               
               <div v-if="route.segments && route.segments.length" class="segments-container">
                 <div v-for="(seg, sIdx) in route.segments" :key="sIdx" class="seg-row">
                    <div class="step-dot" :style="{ background: getLineColor(seg.lineName) }"></div>
                    <div class="step-line" v-if="sIdx < route.segments.length -1"></div>
                    
                    <div class="seg-content">
                      <div class="bus-name">
                        乘坐 <b :style="{ color: getLineColor(seg.lineName) }">{{ seg.lineName || '未知线路' }}</b>
                      </div>
                      <div class="stop-count">
                        经过 {{ seg.stopsCount || 0 }} 站
                        <span v-if="seg.stationDetails && seg.stationDetails.length">
                           ({{ getStationName(seg.stationDetails[0]) }} → {{ getStationName(seg.stationDetails[seg.stationDetails.length-1]) }})
                        </span>
                      </div>
                    </div>
                 </div>
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

const mapAK = import.meta.env.VITE_BAIDU_AK;
const startInput = ref('');
const endInput = ref('');
const startStation = ref(null);
const endStation = ref(null);
const routes = ref([]);
const loading = ref(false); 
const searched = ref(false);
const selectedRouteIndex = ref(-1);
let mapInstance = null;

// --- 辅助工具 ---
const getLineColor = (str) => {
  if (!str) return '#999';
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  const c = (hash & 0x00FFFFFF).toString(16).toUpperCase();
  return '#' + '00000'.substring(0, 6 - c.length) + c;
};

// 【关键修复】：全方位兼容获取站点名称
// 不管后端给 stationName, station_name 还是 name，通通拿下
const getStationName = (s) => {
  if (!s) return '未知站点';
  return s.stationName || s.station_name || s.name || '';
};

// 【关键修复】：全方位兼容获取站点ID
const getStationId = (s) => {
  if (!s) return '';
  return s.stationId || s.station_id || s.id || '';
};

const querySearch = async (queryString, cb) => {
  if (!queryString) { cb([]); return; }
  try {
    const res = await searchStations(queryString);
    // 兼容后端返回结构：List 或 Map
    let list = Array.isArray(res) ? res : (res.data || res.content || []);
    
    // 【映射修复】构建自动补全列表
    const results = list.map(item => ({
      value: getStationName(item), // 调用兼容方法
      id: getStationId(item),      // 调用兼容方法
      lat: parseFloat(item.latitude || item.lat || 0),
      lng: parseFloat(item.longitude || item.lng || 0)
    })).filter(r => r.value);
    
    cb(results);
  } catch (e) { cb([]); }
};

const handleSelect = (item, type) => {
  if (type === 'start') { startInput.value = item.value; startStation.value = item; }
  else { endInput.value = item.value; endStation.value = item; }
};

const handlePlan = async () => {
  if (!startInput.value || !endInput.value) return ElMessage.warning('请输入起终点');
  if (!startStation.value) startStation.value = { value: startInput.value, lng: 0, lat: 0 };
  if (!endStation.value) endStation.value = { value: endInput.value, lng: 0, lat: 0 };

  loading.value = true;
  searched.value = true;
  routes.value = [];
  selectedRouteIndex.value = -1;
  if(mapInstance) mapInstance.clearOverlays();

  try {
    const rawRes = await planRoute(startStation.value.value, endStation.value.value);
    const rawList = Array.isArray(rawRes) ? rawRes : [];

    // 【映射修复】RouteResultDTO 强力兼容
    routes.value = rawList.map(r => ({
      ...r,
      routeId: r.routeId || r.route_id,
      duration: r.duration,
      // 兼容 totalStops 和 total_stops
      totalStops: r.totalStops !== undefined ? r.totalStops : (r.total_stops || 0),
      transfers: r.transfers || 0,
      
      // 处理分段信息
      segments: (Array.isArray(r.segments) ? r.segments : []).map(s => ({
        ...s,
        // 兼容 lineName 和 line_name (修复“未命名”的关键)
        lineName: s.lineName || s.line_name || '未知线路',
        
        // 兼容 stopsCount 和 stops_count (修复“经过0站”)
        stopsCount: s.stopsCount !== undefined ? s.stopsCount : (s.stops_count || 0),
        
        // 兼容 stationDetails 和 station_details
        stationDetails: s.stationDetails || s.station_details || []
      }))
    }));

    if (routes.value.length > 0) {
      handleRouteClick(routes.value[0], 0);
    }
  } catch (e) {
    console.error(e);
    ElMessage.error('查询服务异常');
  } finally {
    loading.value = false;
  }
};

const onMapLoaded = (map) => { mapInstance = map; };

const handleRouteClick = (route, index) => {
  selectedRouteIndex.value = index;
  setTimeout(() => { drawRoute(route); }, 50);
};

// --- 核心绘制逻辑 ---
const drawRoute = (route) => {
  if (!mapInstance) return;
  mapInstance.clearOverlays();
  const BMap = window.BMap;
  const allPoints = [];

  if (route.segments && route.segments.length) {
    route.segments.forEach((seg, idx) => {
      // 获取站点详情列表，注意兼容
      const details = seg.stationDetails || seg.station_details || [];
      const segmentPoints = [];
      
      details.forEach(s => {
         const lat = s.latitude || s.lat;
         const lng = s.longitude || s.lng;
         if (lat && lng) {
           segmentPoints.push(new BMap.Point(lng, lat));
         }
      });

      if (segmentPoints.length > 0) {
        // 使用兼容后的 lineName
        const lineNameStr = seg.lineName || seg.line_name || '线路';
        const color = getLineColor(lineNameStr);
        
        const sy = new BMap.Symbol(window.BMap_Symbol_SHAPE_BACKWARD_OPEN_ARROW, {
          scale: 0.6,
          strokeColor: '#fff',
          strokeWeight: 2,
        });
        const icons = new BMap.IconSequence(sy, '5%', '5%', false);

        const polyline = new BMap.Polyline(segmentPoints, {
          strokeColor: color,
          strokeWeight: 6,
          strokeOpacity: 0.9,
          icons: [icons]
        });
        
        mapInstance.addOverlay(polyline);
        allPoints.push(...segmentPoints);

        if (segmentPoints.length > 1) {
          const midPoint = segmentPoints[Math.floor(segmentPoints.length / 2)];
          const label = new BMap.Label(`${lineNameStr}`, { position: midPoint, offset: new BMap.Size(-10, -20) });
          label.setStyle({
             backgroundColor: color, color: "#fff", border: "none", padding: "2px 5px", borderRadius: "3px", fontSize: "12px",
             boxShadow: "0 2px 4px rgba(0,0,0,0.2)"
          });
          mapInstance.addOverlay(label);
        }

        if (idx < route.segments.length - 1) {
           const transferP = segmentPoints[segmentPoints.length - 1];
           const tMarker = new BMap.Marker(transferP);

           let transferName = "换乘";
           if (details.length > 0) {
              const lastS = details[details.length - 1];
              transferName = getStationName(lastS); // 使用兼容方法
           }

           const tLabel = new BMap.Label(`换乘: ${transferName}`, { offset: new BMap.Size(20, -10) });
           tLabel.setStyle({ 
               color: "#fff", backgroundColor: "#E65100", border: "1px solid #BF360C", 
               padding: "4px 8px", borderRadius: "4px", fontWeight: "bold", zIndex: 999,
               boxShadow: "0 2px 4px rgba(0,0,0,0.3)"
           });
           tMarker.setLabel(tLabel);
           tMarker.setZIndex(1000);
           mapInstance.addOverlay(tMarker);
        }
      }
    });
  }

  if (allPoints.length === 0 && startStation.value && endStation.value) {
     if (startStation.value.lng && endStation.value.lng) {
       const p1 = new BMap.Point(startStation.value.lng, startStation.value.lat);
       const p2 = new BMap.Point(endStation.value.lng, endStation.value.lat);
       allPoints.push(p1, p2);
       const polyline = new BMap.Polyline([p1, p2], { strokeColor: "blue", style: "dashed", strokeWeight: 4 });
       mapInstance.addOverlay(polyline);
     }
  }

  if (allPoints.length > 0) {
     const startP = allPoints[0];
     const endP = allPoints[allPoints.length - 1];
     
     const startMarker = new BMap.Marker(startP);
     const startLabel = new BMap.Label(`起点: ${startStation.value?.value || '起点'}`, { offset: new BMap.Size(20, -10) });
     startLabel.setStyle({ 
       color: "#fff", backgroundColor: "#52c41a", border: "1px solid #28a745", 
       padding: "4px 8px", borderRadius: "4px", fontWeight: "bold", zIndex: 999,
       boxShadow: "0 2px 4px rgba(0,0,0,0.3)"
     });
     startMarker.setLabel(startLabel);
     startMarker.setZIndex(1000);
     mapInstance.addOverlay(startMarker);
     
     const endMarker = new BMap.Marker(endP);
     const endLabel = new BMap.Label(`终点: ${endStation.value?.value || '终点'}`, { offset: new BMap.Size(20, -10) });
     endLabel.setStyle({ 
       color: "#fff", backgroundColor: "#f5222d", border: "1px solid #cf1322", 
       padding: "4px 8px", borderRadius: "4px", fontWeight: "bold", zIndex: 999,
       boxShadow: "0 2px 4px rgba(0,0,0,0.3)"
     });
     endMarker.setLabel(endLabel);
     endMarker.setZIndex(1000);
     mapInstance.addOverlay(endMarker);
     
     mapInstance.setViewport(allPoints);
  }
};
</script>

<style scoped>
/* 保持原有样式不变 */
.query-container { display: flex; height: 100vh; overflow: hidden; }
.sidebar { 
  width: 400px; 
  background: #fff; 
  display: flex; flex-direction: column; 
  z-index: 99; box-shadow: 2px 0 10px rgba(0,0,0,0.1); 
  height: 100%;
}
.sidebar-header { display: flex; justify-content: space-between; align-items: center; padding: 20px; border-bottom: 1px solid #eee; }

.result-list { flex: 1; overflow-y: auto; padding: 20px; }
.map-box { flex: 1; position: relative; }
.w-100 { width: 100%; }
.suggestion-row { display: flex; justify-content: space-between; font-size: 13px; }

.route-item { 
  border: 1px solid #eee; padding: 15px; margin-top: 15px; border-radius: 8px; cursor: pointer; transition: all 0.2s; background: #fff;
}
.route-item:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.route-item.active { border: 2px solid #409EFF; background: #f0f9ff; }

.r-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-weight: bold; }
.sub-info { color: #666; font-size: 12px; margin-bottom: 10px; border-bottom: 1px dashed #eee; padding-bottom: 8px;}

.segments-container { padding-left: 5px; }
.seg-row { display: flex; position: relative; padding-bottom: 15px; }
.step-dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 5px; margin-right: 10px; z-index: 1; border: 2px solid #fff; box-shadow: 0 0 2px rgba(0,0,0,0.3); }
.step-line { position: absolute; left: 4px; top: 14px; bottom: -6px; width: 2px; background: #e4e7ed; }
.bus-name { font-size: 14px; font-weight: 500; }
.stop-count { font-size: 12px; color: #909399; margin-top: 2px; }
</style>