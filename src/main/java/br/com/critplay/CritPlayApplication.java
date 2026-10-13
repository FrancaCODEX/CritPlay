package br.com.critplay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CritPlayApplication {
    public static void main(String[] args) {
        if(java.util.Arrays.asList(args).contains("--migrate-h2-to-postgres")){br.com.critplay.migration.H2ToPostgres.run();return;}
        SpringApplication.run(CritPlayApplication.class, args);
    }
}
