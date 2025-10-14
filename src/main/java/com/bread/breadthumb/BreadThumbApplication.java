package com.bread.breadthumb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author huang
 */
@SpringBootApplication
@EnableScheduling
public class BreadThumbApplication {

    public static void main(String[] args) {
        SpringApplication.run(BreadThumbApplication.class, args);
    }

}
