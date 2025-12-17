package com.example.bussystem.repository;

import com.example.bussystem.entity.LineStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineStationRepository extends JpaRepository<LineStation, Integer> {
    // 获取某线路的所有站点，按序号排序
    List<LineStation> findByLineOrderOrderBySequenceNoAsc(Integer lineOrder);

    // 获取某站点经过的所有线路
    List<LineStation> findByStationId(Integer stationId);

    // 删除相关记录
    void deleteByStationId(Integer stationId);
    void deleteByLineOrder(Integer lineOrder);
}