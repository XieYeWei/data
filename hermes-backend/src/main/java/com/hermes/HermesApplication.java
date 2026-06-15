package com.hermes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enterprise Hadoop Cluster Management Platform - Backend
 * Based on official Hadoop 3.3.x client libraries
 */
@SpringBootApplication
@EnableScheduling
public class HermesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesApplication.class, args);
    }

}