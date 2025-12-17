package com.example.bussystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "stations")
public class Station {
    @Id
    @Column(name = "Station_ID")

    private Integer stationId;

    @Column(name = "Station_NAME", length = 20)

    private String stationName;

    @Column(name = "Longitude")

    private Double longitude;

    @Column(name = "Latitude")

    private Double latitude;
}