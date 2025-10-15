package com.livelihoodcoupon.batch;

import java.io.IOException;

import jakarta.persistence.EntityManagerFactory;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;

import com.livelihoodcoupon.place.entity.Place;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class PlaceCsvBatchConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager platformTransactionManager;
	private final EntityManagerFactory entityManagerFactory;
	private final ResourcePatternResolver resourcePatternResolver;

	@Bean
	public Job placeCsvJob() {
		return new JobBuilder("placeCsvJob", jobRepository)
			.start(placeCsvStep())
			.build();
	}

	@Bean
	public Step placeCsvStep() {
		return new StepBuilder("placeCsvStep", jobRepository)
			.<PlaceCsvDto, Place>chunk(1000, platformTransactionManager)
			.reader(multiResourceItemReader(null))
			.processor(placeCsvProcessor()) // @StepScope 프록시 객체 전달
			.writer(placeCsvWriter())
			// 내결함성 추가: DB에 중복 키 제약조건 위반 등 모든 예외 발생 시 해당 건만 skip
			.faultTolerant()
			.skip(Exception.class)
			.skipLimit(Integer.MAX_VALUE)
			.listener(new com.livelihoodcoupon.batch.listener.CustomSkipListener())
			.build();
	}

	// MultiResourceItemReader를 위한 FlatFileItemReader 델리게이트
	private FlatFileItemReader<PlaceCsvDto> placeCsvReaderDelegate() {
		FlatFileItemReader<PlaceCsvDto> flatFileItemReader = new FlatFileItemReader<>();
		flatFileItemReader.setLinesToSkip(1); // 헤더 스킵
		flatFileItemReader.setLineMapper(placeCsvLineMapper());
		return flatFileItemReader;
	}

	@Bean
	@StepScope
	public MultiResourceItemReader<PlaceCsvDto> multiResourceItemReader(
		@Value("${batch.csv.file.path}") String csvFilePath) {
		MultiResourceItemReader<PlaceCsvDto> reader = new MultiResourceItemReader<>();
		try {
			Resource[] resources = resourcePatternResolver.getResources("file:" + csvFilePath + "/*.csv");
			log.info("Resolved CSV path: {}", csvFilePath);
			log.info("Number of CSV resources found: {}", resources.length);
			reader.setResources(resources);
		} catch (IOException e) {
			log.error("CSV 리소스 경로에서 파일을 로드하는 중 오류 발생: {}", csvFilePath, e);
			throw new RuntimeException("CSV 리소스 로드 실패", e);
		}
		reader.setDelegate(placeCsvReaderDelegate());
		return reader;
	}

	@Bean
	public LineMapper<PlaceCsvDto> placeCsvLineMapper() {
		DefaultLineMapper<PlaceCsvDto> defaultLineMapper = new DefaultLineMapper<>();

		DelimitedLineTokenizer delimitedLineTokenizer = new DelimitedLineTokenizer();
		delimitedLineTokenizer.setNames("placeId", "region", "placeName", "roadAddress", "lotAddress", "lat", "lng",
			"phone", "categoryName", "keyword", "categoryGroupCode", "categoryGroupName", "placeUrl");
		delimitedLineTokenizer.setDelimiter(",");
		delimitedLineTokenizer.setStrict(false); // 컬럼 수 불일치 허용

		BeanWrapperFieldSetMapper<PlaceCsvDto> beanWrapperFieldSetMapper = new BeanWrapperFieldSetMapper<>();
		beanWrapperFieldSetMapper.setTargetType(PlaceCsvDto.class);

		defaultLineMapper.setLineTokenizer(delimitedLineTokenizer);
		defaultLineMapper.setFieldSetMapper(beanWrapperFieldSetMapper);

		return defaultLineMapper;
	}

	@Bean
	@StepScope // Step 실행 범위에서 생성되도록 변경
	public ItemProcessor<PlaceCsvDto, Place> placeCsvProcessor() {
		return item -> {

			// --- SQL 로직 기반 필드 분리 시작 ---
			String[] roadAddressParts =
				item.getRoadAddress() != null ? item.getRoadAddress().split(" ") : new String[0];
			String roadAddressSido = roadAddressParts.length > 0 ? roadAddressParts[0] : null;
			String roadAddressSigungu = roadAddressParts.length > 1 ? roadAddressParts[1] : null;
			String roadAddressRoad = roadAddressParts.length > 2 ? roadAddressParts[2] : null;

			String[] lotAddressParts = item.getLotAddress() != null ? item.getLotAddress().split(" ") : new String[0];
			String roadAddressDong = lotAddressParts.length > 2 ? lotAddressParts[2] : null;

			String[] categoryParts =
				item.getCategoryName() != null ? item.getCategoryName().split(" > ") : new String[0];
			String categoryLevel1 = categoryParts.length > 0 ? categoryParts[0].trim() : null;
			String categoryLevel2 = categoryParts.length > 1 ? categoryParts[1].trim() : null;
			String categoryLevel3 = categoryParts.length > 2 ? categoryParts[2].trim() : null;
			String categoryLevel4 = categoryParts.length > 3 ? categoryParts[3].trim() : null;
			// --- SQL 로직 기반 필드 끝 ---

			GeometryFactory geometryFactory = new GeometryFactory();
			WKTReader wktReader = new WKTReader(geometryFactory);
			Point point = null;
			try {
				// CSV의 위도(lat)와 경도(lng)를 Point 객체로 변환
				// WKT 형식: POINT (경도 위도)
				String wktPoint = String.format("POINT (%s %s)", item.getLng(), item.getLat());
				point = (Point)wktReader.read(wktPoint);
				point.setSRID(4326); // SRID를 WGS84 (4326)으로 설정
			} catch (ParseException e) {
				log.error("placeId: {} 에 대한 WKT 파싱 오류 발생", item.getPlaceId(), e);
				return null; // 파싱 실패 시 해당 아이템 건너뜀
			}

			return Place.builder()
				.placeId(item.getPlaceId())
				.region(item.getRegion())
				.placeName(item.getPlaceName())
				.roadAddress(item.getRoadAddress())
				.lotAddress(item.getLotAddress())
				.phone(item.getPhone())
				.category(item.getCategoryName())
				.keyword(item.getKeyword())
				.categoryGroupCode(item.getCategoryGroupCode())
				.categoryGroupName(item.getCategoryGroupName())
				.placeUrl(item.getPlaceUrl())
				.location(point)
				// 분리된 주소 및 카테고리 필드 추가
				.roadAddressSido(roadAddressSido)
				.roadAddressSigungu(roadAddressSigungu)
				.roadAddressRoad(roadAddressRoad)
				.roadAddressDong(roadAddressDong)
				.categoryLevel1(categoryLevel1)
				.categoryLevel2(categoryLevel2)
				.categoryLevel3(categoryLevel3)
				.categoryLevel4(categoryLevel4)
				.build();
		};
	}

	@Bean
	public JpaItemWriter<Place> placeCsvWriter() {
		JpaItemWriter<Place> jpaItemWriter = new JpaItemWriter<>();
		jpaItemWriter.setEntityManagerFactory(entityManagerFactory);
		return jpaItemWriter;
	}
}
