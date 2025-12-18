import axios from 'axios';
import { ElMessage } from 'element-plus';

const request = axios.create({
  baseURL: '', 
  timeout: 5000
});

// 响应拦截器：暴力拆解后端包装，确保拿到真实数据
request.interceptors.response.use(
  res => {
    const data = res.data;
    // 1. 如果后端没包装，直接返回
    if (!data || typeof data !== 'object') return data;
    
    // 2. 只有当 code 存在且不为 0/200 时才报错
    if (typeof data.code !== 'undefined' && data.code !== 200 && data.code !== 0) {
      ElMessage.error(data.msg || '操作失败');
      return Promise.reject(data);
    }

    // 3. 优先取 content (Spring Data 常用)，其次取 data，最后取 data 本身
    // 您的API文档显示 content 包裹了 schema
    return data.content || data.data || data; 
  },
  err => {
    ElMessage.error('无法连接到服务器');
    return Promise.reject(err);
  }
);

// --- 站点接口 ---
export const getAllStations = () => request.get('/api/stations');
export const searchStations = (query) => request.get('/api/stations/search', { params: { query } });
export const addStation = (id, name) => request.post(`/api/admin/station?id=${id}&name=${encodeURIComponent(name)}`);
export const deleteStation = (id) => request.delete(`/api/admin/station/${id}`);

// --- 线路接口 (本次补全) ---
export const getAllRoutes = () => request.get('/api/routes');
// 添加线路：body 是 JSON
export const addLine = (lineData) => request.post('/api/admin/line', lineData);
// 删除线路：根据ID删除
export const deleteLine = (id) => request.delete(`/api/admin/line/${id}`);

// --- 路径规划 ---
export const planRoute = (start, end) => request.get('/api/routes/plan', {
  params: { start, end }
});