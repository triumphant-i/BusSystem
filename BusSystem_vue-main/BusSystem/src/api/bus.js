import axios from 'axios';
import { ElMessage } from 'element-plus';

const request = axios.create({
  baseURL: '', // 如果有统一前缀（如 localhost:8080）请填在这里，配合跨域代理可留空
  timeout: 5000
});

// ==========================================
//               响应拦截器
// ==========================================
request.interceptors.response.use(
  res => {
    const data = res.data;
    
    // 1. 基础防空判断
    if (!data || typeof data !== 'object') {
      return data;
    }

    // 2. 后端现在返回统一的 Map 结构：
    //    查询类：{ code: 200, data: [...] }
    //    操作类：{ success: true, message: "..." }
    //    这里直接透传给 Vue 组件，由组件判断 data.success 或 data.code
    return data; 
  },
  err => {
    console.error('API Request Error:', err);
    ElMessage.error('无法连接到服务器');
    return Promise.reject(err);
  }
);

// ==========================================
//               站点接口 (Station)
// ==========================================

// 搜索站点 (对应后端 searchStations)
// GET /api/admin/station?keyword=...
export const searchStations = (keyword) => {
  return request.get('/api/admin/station', { params: { keyword } });
};

// 获取所有站点 (兼容旧调用，本质是搜空字符串)
export const getAllStations = () => searchStations(null);

// 添加站点
// POST /api/admin/station?id=...&name=...
export const addStation = (id, name) => {
  return request.post(`/api/admin/station?id=${id}&name=${encodeURIComponent(name)}`);
};

// 修改站点
// PUT /api/admin/station?id=...&name=...
export const updateStation = (id, name) => {
  return request.put(`/api/admin/station?id=${id}&name=${encodeURIComponent(name)}`);
};

// 删除站点
// DELETE /api/admin/station/{id}
export const deleteStation = (id) => {
  return request.delete(`/api/admin/station/${id}`);
};

// ==========================================
//               线路接口 (Line)
// ==========================================

// 获取所有线路 (假设这是前台通用接口，未变动)
// GET /api/routes
export const getAllRoutes = () => request.get('/api/routes');

// 添加线路
// POST /api/admin/line (Body: JSON)
export const addLine = (lineData) => {
  return request.post('/api/admin/line', lineData);
};

// 修改线路
// PUT /api/admin/line (Body: JSON)
export const updateLine = (lineData) => {
  return request.put('/api/admin/line', lineData);
};

// 删除线路
// DELETE /api/admin/line/{id}
export const deleteLine = (id) => {
  return request.delete(`/api/admin/line/${id}`);
};

// ==========================================
//               路径规划
// ==========================================

// 路径规划查询
// GET /api/routes/plan?start=...&end=...
export const planRoute = (start, end) => {
  return request.get('/api/routes/plan', {
    params: { start, end }
  });
};

export default request;