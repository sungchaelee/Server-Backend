package com.livelihoodcoupon.parkinglot.dto;

import org.locationtech.jts.geom.Point;

/**
 * DB에서 공간 쿼리 실행 시, 거리(distance) 값을 함께 받기 위한 Projection Interface
 */
public interface ParkingLotWithDistance {
    Long getId();
    String getParkingLotNm();
    String getRoadAddress();
    String getLotAddress();
    String getParkingChargeInfo();
    Double getLat();
    Double getLng();
    Double getDistance();
}