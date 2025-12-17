package com.example.bussystem.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "line_stations")
@IdClass(LineStationId.class) // 需要定义复合主键类
public class LineStation {
    @Id
    @Column(name = "Line_order")
    private Integer lineOrder;

    @Id
    @Column(name = "Sequence_No")
    private Integer sequenceNo;

    @Column(name = "Station_ID")
    private Integer stationId;
}