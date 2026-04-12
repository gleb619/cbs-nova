package cbs.nova.controller;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration for @WebMvcTest in the starter module. The starter is a library JAR and has no
 * @SpringBootApplication, so tests need their own bootstrap class.
 */
@SpringBootApplication(scanBasePackages = "cbs.nova")
class TestApplication {

}
