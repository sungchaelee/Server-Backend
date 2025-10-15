package com.livelihoodcoupon.batch.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.livelihoodcoupon.common.exception.ErrorCode;
import com.livelihoodcoupon.common.response.CustomApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/admin/batch/es")
@RequiredArgsConstructor
@Profile("!test")
public class ElasticsearchBatchController {

	private final JobLauncher jobLauncher;
	private final ResourcePatternResolver resourcePatternResolver;

	@Qualifier("placeCsvToEsJob")
	private final Job placeCsvToEsJob;

	@Qualifier("placeCsvToEsIncrementalAddJob")
	private final Job placeCsvToEsIncrementalAddJob;

	@Value("${batch.csv.file.path}")
	private String csvFilePath;

	@PostMapping("/new-csv")
	public ResponseEntity<CustomApiResponse<?>> runCsvToEsBatchIncremental() {
		try {
			log.info("CSV to ES 증분 추가 배치 작업 시작 요청됨");
			startBatchJobAsync(placeCsvToEsIncrementalAddJob, "placeCsvToEsIncrementalAddJob", null);
			return ResponseEntity.ok(
				CustomApiResponse.success("CSV to ES 증분 추가 배치 작업이 백그라운드에서 시작되었습니다.")
			);
		} catch (Exception e) {
			log.error("CSV to ES 증분 추가 배치 작업 시작 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR,
					"배치 작업 시작 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	@PostMapping("/all-csv")
	public ResponseEntity<CustomApiResponse<?>> runStagedCsvToEsBatchFullReload() {
		try {
			log.info("CSV to ES 단계적 전체 재구성 배치 작업 시작 요청됨");
			Resource[] resources = resourcePatternResolver.getResources("file:" + csvFilePath + "/*.csv");
			log.info("Resolved csvFilePath in controller: {}", csvFilePath);
			log.info("Number of resources found by controller: {}", resources.length);
			startStagedBatchJobAsync(placeCsvToEsJob, "placeCsvToEsJob", Arrays.asList(resources));
			return ResponseEntity.ok(
				CustomApiResponse.success("CSV to ES 단계적 전체 재구성 배치 작업이 백그라운드에서 시작되었습니다.")
			);
		} catch (IOException e) {
			log.error("CSV 파일 리소스를 찾는 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR,
					"CSV 파일 리소스를 찾는 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	private void startStagedBatchJobAsync(Job jobToRun, String jobName, List<Resource> resources) {
		new Thread(() -> {
			int groupSize = 5;
			List<List<Resource>> resourceGroups = new ArrayList<>();
			for (int i = 0; i < resources.size(); i += groupSize) {
				resourceGroups.add(resources.subList(i, Math.min(i + groupSize, resources.size())));
			}

			for (int i = 0; i < resourceGroups.size(); i++) {
				List<Resource> group = resourceGroups.get(i);
				try {
					String fileResources = group.stream()
						.map(r -> {
							try {
								return r.getURL().toString();
							} catch (IOException e) {
								log.error("리소스 URL을 가져오는 데 실패했습니다.", e);
								return null;
							}
						})
						.filter(s -> s != null)
						.collect(Collectors.joining(","));

					log.info("단계적 배치 그룹 {}/{} 시작. 파일: {}", i + 1, resourceGroups.size(), fileResources);

					JobParameters jobParameters = new JobParametersBuilder()
						.addString("JobID", String.valueOf(System.currentTimeMillis()))
						.addString("fileResources", fileResources)
						.toJobParameters();

					jobLauncher.run(jobToRun, jobParameters);
					log.info("단계적 배치 그룹 {}/{} 완료.", i + 1, resourceGroups.size());

					if (i < resourceGroups.size() - 1) {
						log.info("다음 그룹 실행 전 10초 대기...");
						Thread.sleep(10000);
					}

				} catch (Exception e) {
					log.error("단계적 배치 그룹 {}/{} 실행 중 오류 발생", i + 1, resourceGroups.size(), e);
					// 한 그룹이 실패해도 다음 그룹을 계속 시도
				}
			}
			log.info("모든 단계적 배치 그룹 실행 완료.");
		}).start();
	}

	private void startBatchJobAsync(Job jobToRun, String jobName, String fileResources) {
		new Thread(() -> {
			try {
				JobParametersBuilder builder = new JobParametersBuilder()
					.addString("JobID", String.valueOf(System.currentTimeMillis()));

				if (fileResources != null && !fileResources.isEmpty()) {
					builder.addString("fileResources", fileResources);
				}

				jobLauncher.run(jobToRun, builder.toJobParameters());
				log.info("{} 배치 작업 완료됨", jobName);
			} catch (Exception e) {
				log.error("{} 배치 작업 실행 중 오류 발생", jobName, e);
			}
		}).start();
	}
}
