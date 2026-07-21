package com.jcm.recommendations.soccer.core.config;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@AutoConfigurationPackage(basePackages = "com.jcm.recommendations.soccer.domain")
@EnableJpaRepositories(basePackages = "com.jcm.recommendations.soccer.core.repository")
public class JpaConfig {
}
