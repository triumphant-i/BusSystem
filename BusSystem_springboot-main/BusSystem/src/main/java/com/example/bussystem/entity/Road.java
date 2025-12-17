package com.example.bussystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Time;
import java.util.List;

@Data
@Entity
@Table(name = "roads")
public class Road {
    @Id
    @Column(name = "Line_order")

    private Integer lineOrder;

    @Column(name = "Line_name", length = 4)

    private String lineName;

    @Column(name = "Direction", length = 2)

    private String direction;

    @Column(name = "St")

    private Time startTime; // 对应 St

    @Column(name = "Ft")

    private Time finishTime; // 对应 Ft

    @Column(name = "Interval_time")

    private Integer intervalTime;

    // 辅助字段：线路包含的站点ID列表（API返回需要）
    @Transient

    private List<Integer> stationIds;
}