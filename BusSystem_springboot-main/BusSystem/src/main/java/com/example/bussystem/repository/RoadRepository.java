package com.example.bussystem.repository;

import com.example.bussystem.entity.Road;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadRepository extends JpaRepository<Road, Integer> {
    Road findByLineName(String lineName);
}
