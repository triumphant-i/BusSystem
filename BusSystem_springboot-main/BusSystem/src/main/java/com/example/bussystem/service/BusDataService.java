package com.example.bussystem.service;

import com.example.bussystem.entity.LineStation;
import com.example.bussystem.entity.Road;
import com.example.bussystem.entity.Station;
import com.example.bussystem.repository.LineStationRepository;
import com.example.bussystem.repository.RoadRepository;
import com.example.bussystem.repository.StationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

@Service
public class BusDataService {

    @Autowired private StationRepository stationRepo;
    @Autowired private RoadRepository roadRepo;
    @Autowired private LineStationRepository lineStationRepo;

    // 注入路径计算服务，用于在数据变更后刷新图结构缓存
    @Autowired private PathFindingService pathFindingService;

    // ==========================================
    //               站点管理业务
    // ==========================================

    /**
     * 搜索站点
     * 逻辑：优先尝试按 ID 精确查询；若不是数字或 ID 不存在，则按名称模糊查询
     */
    public List<Station> searchStations(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return stationRepo.findAll();
        }

        List<Station> results = new ArrayList<>();

        // 1. 尝试解析为数字 ID 进行查询
        try {
            Integer id = Integer.valueOf(keyword);
            stationRepo.findById(id).ifPresent(results::add);
        } catch (NumberFormatException ignored) {}

        // 2. 如果按 ID 没查到，则扫描所有站点进行名称模糊匹配
        // (注：生产环境数据量大时，建议使用 Repository 的 findByNameContaining)
        if (results.isEmpty()) {
            List<Station> all = stationRepo.findAll();
            for (Station s : all) {
                if (s.getStationName().contains(keyword)) {
                    results.add(s);
                }
            }
        }
        return results;
    }

    // 添加站点
    public String addStation(Integer id, String name) {
        if (stationRepo.existsById(id)) {
            return "错误：站点ID " + id + " 已存在，请使用修改功能";
        }
        Station s = new Station();
        s.setStationId(id);
        s.setStationName(name);

        // 自动补充经纬度信息
        autoFillCoordinates(s);

        stationRepo.save(s);
        pathFindingService.loadData(); // 刷新缓存
        return "成功：站点 " + name + " 已添加";
    }

    // 更新站点
    public String updateStation(Integer id, String name) {
        if (!stationRepo.existsById(id)) {
            return "错误：站点ID " + id + " 不存在";
        }
        Station s = stationRepo.findById(id).get();
        s.setStationName(name);

        // 修改名称时重新计算经纬度（假设位置随名称变化）
        autoFillCoordinates(s);

        stationRepo.save(s);
        pathFindingService.loadData(); // 刷新缓存
        return "成功：站点 " + id + " 信息已更新";
    }

    // 删除站点（事务控制）
    @Transactional
    public String deleteStation(Integer id) {
        if (!stationRepo.existsById(id)) {
            return "错误：站点ID " + id + " 不存在";
        }
        // 级联删除：先删除“线路-站点”关联表中的记录，防止数据不一致
        lineStationRepo.deleteByStationId(id);
        // 再删除站点本身
        stationRepo.deleteById(id);

        pathFindingService.loadData(); // 刷新缓存
        return "成功：站点及其关联线路记录已删除";
    }

    // 辅助方法：模拟自动获取经纬度
    // 实际项目中此处应调用 百度/高德 地图 API
    private void autoFillCoordinates(Station s) {
        double baseLng = 120.1550; // 基础经度
        double baseLat = 30.2740;  // 基础纬度
        // 添加随机偏移量以模拟不同位置
        double lng = baseLng + (Math.random() - 0.5) * 0.1;
        double lat = baseLat + (Math.random() - 0.5) * 0.1;

        // 保留6位小数
        s.setLongitude(Math.round(lng * 1000000.0) / 1000000.0);
        s.setLatitude(Math.round(lat * 1000000.0) / 1000000.0);
    }

    // ==========================================
    //               线路管理业务
    // ==========================================

    // 添加线路（事务控制）
    @Transactional
    public String addLine(Integer lineOrder, String lineName, String direction,
                          String st, String ft, Integer interval, List<Integer> stationIds) {
        if (roadRepo.existsById(lineOrder)) {
            return "错误：线路编号 " + lineOrder + " 已存在";
        }
        saveLineData(lineOrder, lineName, direction, st, ft, interval, stationIds);
        return "成功：线路 " + lineName + " 添加完成";
    }

    // 更新线路（事务控制）
    @Transactional
    public String updateLine(Integer lineOrder, String lineName, String direction,
                             String st, String ft, Integer interval, List<Integer> stationIds) {
        if (!roadRepo.existsById(lineOrder)) {
            return "错误：线路编号 " + lineOrder + " 不存在";
        }
        // 更新逻辑：先清空旧的站点关联，再重新保存新的序列
        lineStationRepo.deleteByLineOrder(lineOrder);
        saveLineData(lineOrder, lineName, direction, st, ft, interval, stationIds);
        return "成功：线路 " + lineName + " 修改完成";
    }

    // 内部通用方法：保存线路基础信息及站点序列
    private void saveLineData(Integer lineOrder, String lineName, String direction,
                              String st, String ft, Integer interval, List<Integer> stationIds) {
        // 1. 保存线路主体信息 (Road表)
        Road road = new Road();
        road.setLineOrder(lineOrder);
        road.setLineName(lineName);
        road.setDirection(direction);
        try {
            road.setStartTime(Time.valueOf(st));
            road.setFinishTime(Time.valueOf(ft));
        } catch (Exception e) {
            // 时间格式容错处理
            road.setStartTime(Time.valueOf("06:00:00"));
            road.setFinishTime(Time.valueOf("22:00:00"));
        }
        road.setIntervalTime(interval);
        roadRepo.save(road);

        // 2. 保存线路与站点的关联序列 (LineStation表)
        int seq = 1;
        for (Integer sid : stationIds) {
            // 仅当站点存在时才建立关联
            if (stationRepo.existsById(sid)) {
                LineStation ls = new LineStation();
                ls.setLineOrder(lineOrder);
                ls.setStationId(sid);
                ls.setSequenceNo(seq++);
                lineStationRepo.save(ls);
            }
        }

        // 3. 刷新图算法缓存
        pathFindingService.loadData();
    }

    // 删除线路（事务控制）
    @Transactional
    public String deleteLine(Integer lineOrder) {
        if (!roadRepo.existsById(lineOrder)) {
            return "错误：线路编号 " + lineOrder + " 不存在";
        }
        // 级联删除：先删关联表，再删主表
        lineStationRepo.deleteByLineOrder(lineOrder);
        roadRepo.deleteById(lineOrder);

        pathFindingService.loadData(); // 刷新缓存
        return "成功：线路已删除";
    }
}