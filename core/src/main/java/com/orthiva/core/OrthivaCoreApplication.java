package com.orthiva.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrthivaCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrthivaCoreApplication.class, args);
	}

}
uvicorn app.main:app --reload --port 8000