package com.tutorsmart.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.dossier")
public class DossierApplication {
    public static void main(String[] args) {
        SpringApplication.run(DossierApplication.class, args);
    }
}