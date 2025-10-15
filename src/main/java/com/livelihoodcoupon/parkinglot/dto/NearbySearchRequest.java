package com.livelihoodcoupon.parkinglot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Range;

@Getter
@Setter
public class NearbySearchRequest {

    @NotNull(message = "위도는 필수값입니다.")
    @Range(min = -90, max = 90)
    private Double lat;

    @NotNull(message = "경도는 필수값입니다.")
    @Range(min = -180, max = 180)
    private Double lng;

    private Double radius = 1.0;

    private Integer page = 1;
    private Integer size =  10;
}
