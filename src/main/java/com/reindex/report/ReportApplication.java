package com.reindex.report;
// test git
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EnableScheduling
public class ReportApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReportApplication.class, args);
		log.info("Report Application started successfully");
	}
}
