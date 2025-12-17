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
import java.util.List;

@Service
public class BusDataService {

    @Autowired private StationRepository stationRepo;
    @Autowired private RoadRepository roadRepo;
    @Autowired private LineStationRepository lineStationRepo;

    // 注入核心服务，用于数据更新后刷新缓存
    @Autowired private PathFindingService pathFindingService;

    // --- 站点管理 ---

    public String addStation(Integer id, String name) {
        if (stationRepo.existsById(id)) {
            return "错误：站点ID " + id + " 已存在";
        }
        // 简单模拟原Python逻辑，实际生产中这里可以用 Repository 的 findByName
        // 这里简化直接保存
        Station s = new Station();
        s.setStationId(id);
        s.setStationName(name);
        // 经纬度暂空，后续可扩展
        stationRepo.save(s);

        pathFindingService.loadData(); // 刷新缓存
        return "成功：站点 " + name + " 已添加";
    }

    @Transactional
    public String deleteStation(Integer id) {
        if (!stationRepo.existsById(id)) {
            return "错误：站点ID " + id + " 不存在";
        }
        // 级联删除：先删关系表，再删站点表
        lineStationRepo.deleteByStationId(id);
        stationRepo.deleteById(id);

        pathFindingService.loadData(); // 刷新缓存
        return "成功：站点及其关联记录已删除";
    }

    // --- 线路管理 ---

    @Transactional
    public String addLine(Integer lineOrder, String lineName, String direction,
                          String st, String ft, Integer interval, List<Integer> stationIds) {
        if (roadRepo.existsById(lineOrder)) {
            return "错误：线路编号 " + lineOrder + " 已存在";
        }

        // 1. 保存线路基础信息
        Road road = new Road();
        road.setLineOrder(lineOrder);
        road.setLineName(lineName);
        road.setDirection(direction);
        try {
            // 简单的时间格式处理，假设输入是 HH:mm:ss
            road.setStartTime(Time.valueOf(st));
            road.setFinishTime(Time.valueOf(ft));
        } catch (Exception e) {
            // 时间格式容错
            road.setStartTime(Time.valueOf("06:00:00"));
            road.setFinishTime(Time.valueOf("22:00:00"));
        }
        road.setIntervalTime(interval);
        roadRepo.save(road);

        // 2. 保存站点序列
        int seq = 1;
        for (Integer sid : stationIds) {
            // 简单校验站点是否存在
            if (stationRepo.existsById(sid)) {
                LineStation ls = new LineStation();
                ls.setLineOrder(lineOrder);
                ls.setStationId(sid);
                ls.setSequenceNo(seq++);
                lineStationRepo.save(ls);
            }
        }

        pathFindingService.loadData(); // 刷新缓存
        return "成功：线路 " + lineName + " 添加完成";
    }

    @Transactional
    public String deleteLine(Integer lineOrder) {
        if (!roadRepo.existsById(lineOrder)) {
            return "错误：线路编号 " + lineOrder + " 不存在";
        }
        lineStationRepo.deleteByLineOrder(lineOrder);
        roadRepo.deleteById(lineOrder);

        pathFindingService.loadData(); // 刷新缓存
        return "成功：线路已删除";
    }
}