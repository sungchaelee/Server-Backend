
package com.livelihoodcoupon.search.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.livelihoodcoupon.common.dto.Coordinate;
import com.livelihoodcoupon.common.exception.BusinessException;
import com.livelihoodcoupon.common.exception.ErrorCode;
import com.livelihoodcoupon.common.service.KakaoApiService;
import com.livelihoodcoupon.parkinglot.dto.NearbySearchRequest;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotNearbyResponse;
import com.livelihoodcoupon.parkinglot.service.ParkingLotService;
import com.livelihoodcoupon.search.dto.AnalyzedAddress;
import com.livelihoodcoupon.search.dto.AutocompleteDto;
import com.livelihoodcoupon.search.dto.AutocompleteResponseDto;
import com.livelihoodcoupon.search.dto.PageResponse;
import com.livelihoodcoupon.search.dto.PlaceSearchResponseDto;
import com.livelihoodcoupon.search.dto.SearchRequestDto;
import com.livelihoodcoupon.search.dto.SearchServiceResult;
import com.livelihoodcoupon.search.dto.SearchToken;
import com.livelihoodcoupon.search.entity.PlaceDocument;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.Token;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ElasticService {

	private final ElasticPlaceService elasticPlaceService;
	private final SearchService searchService;
	private final KakaoApiService kakaoApiService;
	private final AnalyzerTest analyzerTest;
	private final RedisService redisService;
	private final ParkingLotService parkingLotService;
	private final Komoran komoran = new Komoran(DEFAULT_MODEL.FULL);

	public ElasticService(ElasticPlaceService elasticPlaceService, SearchService searchService,
		KakaoApiService kakaoApiService, AnalyzerTest analyzerTest, RedisService redisService,
		ParkingLotService parkingLotService) {
		this.elasticPlaceService = elasticPlaceService;
		this.searchService = searchService;
		this.kakaoApiService = kakaoApiService;
		this.analyzerTest = analyzerTest;
		this.redisService = redisService;
		this.parkingLotService = parkingLotService;
	}

	/**
	 * 단어 자동완성 서비스
	 * @param dto
	 * @param maxRecordSize
	 * @return
	 * @throws IOException
	 */
	public List<AutocompleteResponseDto> elasticSearchAutocomplete(AutocompleteDto dto, int maxRecordSize) throws
		IOException {
		List<AutocompleteResponseDto> list = elasticPlaceService.autocompletePlaceNames(dto, maxRecordSize);
		if (list == null || list.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "검색 결과가 없습니다.");
		}
		return list;
	}

	/**
	 * 엘라스틱 서치 상세내용
	 */
	public PlaceSearchResponseDto elasticSearchDetail(String id, SearchRequestDto dto) throws
		IOException {
		PlaceDocument doc = elasticPlaceService.getPlace(String.valueOf(id));
		if (doc == null || doc.getLocation() == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "id=" + id + " 문서를 찾을 수 없습니다.");
		}
		//거리계산하기
		double distance = searchService.calculateDistance(dto.getLat(), dto.getLng(),
			doc.getLocation().getLat(), doc.getLocation().getLng());
		System.out.println("444");
		return PlaceSearchResponseDto.fromEntity(doc, distance);
	}

	/**
	 * 엘라스틱 서치
	 * @param dto
	 * @param pageSize
	 * @param maxRecordSize
	 * @return
	 * @throws IOException
	 */
	public SearchServiceResult elasticSearch(SearchRequestDto dto, int pageSize, int maxRecordSize) throws
		IOException {
		String query = dto.getQuery();
		//코모란 자연어 형태소 분리
		AnalyzedAddress analyzedAddress = analysisChat(query);
		List<SearchToken> resultList = analyzedAddress.getResultList();

		Pageable pageable = PageRequest.of(dto.getPage() - 1, pageSize);

		// 요청된 페이지가 최대 결과 수를 초과하는지 확인
		if (pageable.getOffset() >= maxRecordSize) {
			Page<PlaceSearchResponseDto> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, maxRecordSize);
			return new SearchServiceResult(emptyPage, dto.getLat(), dto.getLng());
		}

		// 거리 계산을 위한 기준점 설정 (userLat/userLng가 있으면 사용, 없으면 lat/lng 사용)
		double userLat = (dto.getUserLat() != null) ? dto.getUserLat() : dto.getLat();
		double userLng = (dto.getUserLng() != null) ? dto.getUserLng() : dto.getLng();

		//검색어에 주소가 있을 경우 새로운 위치 가져오기
		double searchLat = dto.getLat();
		double searchLng = dto.getLng();
		log.info("엘라스틱 서치 현재 위치 latitude:{}, longitude:{}", searchLat, searchLng);

		// forceLocationSearch가 true이거나 disableGeoFilter가 true인 경우, 검색어 내 지역 정보는 무시하고 dto.lat/lng를 검색 중심으로 사용
		if (dto.isForceLocationSearch() || dto.isDisableGeoFilter()) {
			log.info("forceLocationSearch 또는 disableGeoFilter가 true이므로, 검색어 내 지역 정보는 무시하고 dto.lat/lng를 검색 중심으로 사용합니다.");
			// searchLat, searchLng는 이미 dto.getLat(), dto.getLng()로 초기화되어 있으므로 추가 작업 불필요
		} else {
			String fullAddressFromAnalysis = analyzedAddress.getFullAddress();
			if (fullAddressFromAnalysis != null && !fullAddressFromAnalysis.trim().isEmpty()) {
				SearchRequestDto result = handleAddressPosition(fullAddressFromAnalysis, dto).block().getBody();
				searchLat = result.getLat();
				searchLng = result.getLng();
				log.info("엘라스틱 서치 재수정된 검색위치 latitude:{}, longitude:{}", searchLat, searchLng);

				// 추가: 좌표로 행정구역 정보 가져오기
				com.livelihoodcoupon.common.dto.Coord2RegionCodeResponse regionInfo = kakaoApiService.getRegionInfo(
					searchLng, searchLat);
				if (regionInfo != null && regionInfo.getDocuments() != null && !regionInfo.getDocuments().isEmpty()) {
					com.livelihoodcoupon.common.dto.Coord2RegionCodeResponse.RegionDocument document = regionInfo.getDocuments()
						.get(0);
					analyzedAddress = new AnalyzedAddress(analyzedAddress.getFullAddress(),
						document.getRegion1DepthName(),
						document.getRegion2DepthName(), analyzedAddress.getResultList());
				}
			}
		}
		//검색하기
		SearchResponse<PlaceDocument> response = elasticPlaceService.searchPlace(analyzedAddress, dto, pageable,
			searchLat, searchLng, userLat, userLng);

		//dto 변환, 거리계산
		List<PlaceSearchResponseDto> dtoPage = response.hits().hits()
			.stream()
			.map(hit -> hit.source())
			.map(place -> {
				// 각 Place에 대해 거리 계산
				return toSearchPosition(place, userLat, userLng);
			}).collect(Collectors.toList());

		long totalHits = response.hits().total() != null ? response.hits().total().value() : 0; //null체크
		long resultTotalHits = Math.min(totalHits, maxRecordSize); //레코드 200개로 자르기
		log.info("Total Hits from ES: {}, Result Total Hits after capping: {}", totalHits, resultTotalHits);
		log.info("엘라스틱 서치 결과 return 총 갯수 : {}", totalHits);

		Page<PlaceSearchResponseDto> page = new PageImpl<>(dtoPage, pageable, resultTotalHits);
		return new SearchServiceResult(page, searchLat, searchLng);
	}

	/**
	 * 장소 검색 후, 해당 위치 기반으로 주변 주차장을 검색하는 2단계 로직을 수행합니다.
	 * @param request
	 * @return PageResponse<ParkingLotNearbyResponse>
	 * @throws IOException
	 */
	public PageResponse<ParkingLotNearbyResponse> searchParkingLotsNearPlace(SearchRequestDto request) throws IOException {
		// 1. 엘라스틱서치를 호출하여 사용자의 쿼리에 대한 중심 좌표를 찾습니다.
		// 이 호출에서는 실제 장소 목록은 필요 없으므로, 페이지 크기를 1로 지정하여 최소한의 데이터만 가져옵니다.
		SearchServiceResult placeSearchResult = this.elasticSearch(request, 1, 1);
		double centerLat = placeSearchResult.getSearchCenterLat();
		double centerLng = placeSearchResult.getSearchCenterLng();

		// 2. 주차장 검색 서비스에 사용할 요청 객체를 준비합니다.
		NearbySearchRequest parkingRequest = new NearbySearchRequest();
		parkingRequest.setLat(centerLat);
		parkingRequest.setLng(centerLng);
		parkingRequest.setRadius(1.0); // 1km 반경
		parkingRequest.setPage(request.getPage()); // 원본 요청의 페이지 번호 사용
		parkingRequest.setSize(10); // 기본 페이지 크기

		// 3. parkingLotService를 호출하여 좌표 기반으로 주변 주차장을 검색합니다.
		return parkingLotService.findNearby(parkingRequest);
	}

	/**
	 * 검색한 위치해서 상가 위치까지 거리계산
	 * @param doc
	 * @param refLat
	 * @param refLng
	 * @return
	 */
	public PlaceSearchResponseDto toSearchPosition(PlaceDocument doc, double refLat, double refLng) {
		double distance = searchService.calculateDistance(refLat, refLng,
			doc.getLocation().getLat(), doc.getLocation().getLng());
		return PlaceSearchResponseDto.fromEntity(doc, distance);
	}

	/**
	 * 주소로 위도, 경도 재탐색
	 * @param searchNewAddress
	 * @param request
	 * @return
	 */
	public Mono<ResponseEntity<SearchRequestDto>> handleAddressPosition(String searchNewAddress,
		SearchRequestDto request) {
		return kakaoApiService.getCoordinatesFromAddress(searchNewAddress)
			.defaultIfEmpty(Coordinate.builder().lng(0).lat(0).build())
			.flatMap(coordinate -> {
				// request에 좌표 세팅
				request.setLat(coordinate.getLat());
				request.setLng(coordinate.getLng());
				log.info("엘라스틱 서치 Mono 검색어 : {},  기준 좌표 위도: {}, 경도: {}", searchNewAddress, coordinate.getLat(),
					coordinate.getLng());
				return Mono.just(request);
			})
			.map(r -> ResponseEntity.ok(r))
			.onErrorResume(e -> {
				log.error("엘라스틱 서치 Mono 좌표 검색 중 오류 발생", e);
				return Mono.just(ResponseEntity.ok(request));
			});
	}

	/**
	 * 단어 형태로 분리
	 * 예) 서울시 강남구 카페 맛집
	 * BeginIndex : 시작위치
	 * EndIndex : 끝위치 :
	 * mar ph : 검색단어
	 * Pos : 단어구분
	 * - 서울시 - NNP	고유 명사 (지명)
	 * - 강남구 - NNP	고유 명사 (지명)
	 * - 카페 - NNG	일반 명사 (장소/시설)
	 * - 맛집 - NNG	일반 명사 (장소/음식점)
	 **/
	public AnalyzedAddress analysisChat(String keyword) throws IOException {
		log.info("analysisChat 호출 시작222");

		//단어 형태 자동 분리
		KomoranResult analyzeResultList = komoran.analyze(keyword);

		//analyzeResultList try-can처리
		List<Token> tokenList;
		try {
			// 분리된 문자열 token list 생성
			tokenList = analyzeResultList.getTokenList();
		} catch (NullPointerException e) {
			log.error("형태소 분석 결과 추출 중 오류 발생 - keyword: {}", keyword);
			// 문제를 알리고 빈 리스트로 대체
			return new AnalyzedAddress("", "", "", Collections.emptyList());
		}

		List<SearchToken> list = new ArrayList<>();
		StringBuilder builder = new StringBuilder();
		//분리된 문자열 token list 생성
		for (Token token : tokenList) {
			//token 공백여부 체크
			if (token == null) {
				log.warn("token 형태소 정보가 비어있습니다: {}", token);
				continue;
			}

			// 형태소 문자열 체크
			if (token.getMorph() == null || token.getMorph().isBlank()) {
				log.warn("getMorph 형태소 정보가 비어있습니다");
				continue;
			}

			//txt dict 파일에서 필드값 가져오기
			String getFieldName = isAddress(token.getMorph());

			//필드가 address 이면 검색할 주소 build
			if (getFieldName != null && getFieldName.equals("address")) {
				builder.append(" ").append(token.getMorph());
			}

			//Token을 SearchToken으로 통합하기
			SearchToken searchToken = new SearchToken(getFieldName, token);

			//SearchToken 리스트에 추가
			list.add(searchToken);

			log.info("====> 형태소 분리 {}, {} {}, {}, {}", token.getBeginIndex(), token.getEndIndex(), token.getMorph(),
				token.getPos(), getFieldName);
		}
		return new AnalyzedAddress(builder.toString().trim(), null, null, list);
	}

	/**
	 * 단어가 주소여부 체크
	 * @param morph
	 * @return
	 */
	public String isAddress(String morph) throws IOException {
		//txt 파일 메모리 에서 address, category 구분
		//return analyzerTest.isCategoryAddress(morph);
		return redisService.getWordInfo(morph);
	}

}

