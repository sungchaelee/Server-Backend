package com.livelihoodcoupon.parkinglot.dto;

import com.livelihoodcoupon.parkinglot.dto.ParkingLotWithDistance;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ParkingLotNearbyResponse {

    private final Long id;
    private final String parkingLotName;
    private final String roadAddress;
    private final String lotAddress;
    private final String feeInfo;
    private final Double lat;
    private final Double lng;
    private final Double distance; // 미터 단위 거리

    @Builder
    public ParkingLotNearbyResponse(Long id, String parkingLotName, String roadAddress, String lotAddress, String feeInfo, Double lat, Double lng, Double distance) {
        this.id = id;
        this.parkingLotName = parkingLotName;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.feeInfo = feeInfo;
        this.lat = lat;
        this.lng = lng;
        this.distance = distance;
    }

    public static ParkingLotNearbyResponse from(ParkingLotWithDistance projection) {
        return ParkingLotNearbyResponse.builder()
                .id(projection.getId())
                .parkingLotName(projection.getParkingLotNm())
                .roadAddress(projection.getRoadAddress())
                .lotAddress(projection.getLotAddress())
                .feeInfo(projection.getParkingChargeInfo())
                .lat(projection.getLat())
                .lng(projection.getLng())
                .distance(projection.getDistance())
                .build();
    }
}
