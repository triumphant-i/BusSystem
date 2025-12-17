package com.example.bussystem.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class LineStationId implements Serializable {
    private Integer lineOrder;
    private Integer sequenceNo;
}