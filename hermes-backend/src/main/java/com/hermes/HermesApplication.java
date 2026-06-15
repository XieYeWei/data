package com.hermes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Enterprise Hadoop Cluster Management Platform - Backend
 * Based on official Hadoop 3.3.x client libraries for HDFS/YARN/MapReduce
 */
@SpringBootApplication
public class HermesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesApplication.class, args);
    }

}