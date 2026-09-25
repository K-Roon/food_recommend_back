package com.kroon.food_recommend_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class FoodRecommendBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(FoodRecommendBackApplication.class, args);
	}

}
