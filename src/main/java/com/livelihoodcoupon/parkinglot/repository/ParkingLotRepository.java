package com.livelihoodcoupon.parkinglot.repository;

import com.livelihoodcoupon.parkinglot.dto.ParkingLotWithDistance;
import com.livelihoodcoupon.parkinglot.entity.ParkingLot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

	    @Query(nativeQuery = true,

	        value = "SELECT id, parking_lot_nm as parkingLotNm, road_address as roadAddress, lot_address as lotAddress, " +
	            "parking_charge_info as parkingChargeInfo, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng, " +
	            "ST_Distance(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) as distance " +
	            "FROM parking_lot " +
	            "WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius) " +
	            "ORDER BY distance ASC",
	        countQuery = "SELECT count(*) FROM parking_lot " +
	            "WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)")
	Page<ParkingLotWithDistance> findNearbyParkingLots(
		@Param("lat") double lat,
		@Param("lng") double lng,
		        @Param("radius") double radius,		Pageable pageable
	);

	
	// 1) 백필 대상: location IS NULL 이고 주소가 하나라도 있는 행 상위 N개
	interface ToGeocode {
		Long getId();
		String getLotAddress();
		String getRoadAddress();
	}

	@Query(value = """
        SELECT id,
               lot_address  AS lotAddress,
               road_address AS roadAddress
          FROM parking_lot
         WHERE location IS NULL
           AND (lot_address IS NOT NULL OR road_address IS NOT NULL)
         ORDER BY id ASC
         LIMIT :limit
        """, nativeQuery = true)
	List<ToGeocode> findTargets(@Param("limit") int limit);

	// 2) 좌표 업데이트 (주의: ST_MakePoint(경도 lng, 위도 lat))
	@Modifying
	@Query(value = """
        UPDATE parking_lot
           SET location = ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
         WHERE id = :id
        """, nativeQuery = true)
	int updateLocation(@Param("id") long id,
		@Param("lat") double lat,
		@Param("lng") double lng);

	// 3) 최종 CSV용 스트림: 좌표 있는 행만
	@Transactional(readOnly = true)
	@Query("SELECT p FROM ParkingLot p WHERE p.location IS NOT NULL ORDER BY p.id ASC")
	Stream<ParkingLot> streamAllWithLocation();

}
