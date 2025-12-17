package com.example.bussystem.repository;

import com.example.bussystem.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface StationRepository extends JpaRepository<Station, Integer> {
    // 模糊查询
    List<Station> findByStationNameContainingIgnoreCase(String name);
}