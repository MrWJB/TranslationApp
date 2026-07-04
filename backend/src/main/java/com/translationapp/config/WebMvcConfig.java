package com.translationapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path frontendDistPath = Paths.get("..", "frontend", "dist").toAbsolutePath().normalize();
        
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + frontendDistPath + "/")
                .setCachePeriod(0);
    }
}