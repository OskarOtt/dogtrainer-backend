package com.oskott.dogtrainerbackend.stats.controller;

import com.oskott.dogtrainerbackend.stats.dto.DogProgressResponse;
import com.oskott.dogtrainerbackend.stats.dto.DogStatisticsResponse;
import com.oskott.dogtrainerbackend.stats.service.DogStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dogs/{dogId}")
public class DogStatisticsController {

    private final DogStatisticsService dogStatisticsService;

    public DogStatisticsController(DogStatisticsService dogStatisticsService) {
        this.dogStatisticsService = dogStatisticsService;
    }

    @GetMapping("/statistics")
    public DogStatisticsResponse getStatistics(@PathVariable UUID dogId) {
        return dogStatisticsService.getStatistics(dogId);
    }

    @GetMapping("/progress")
    public DogProgressResponse getProgress(@PathVariable UUID dogId) {
        return dogStatisticsService.getProgress(dogId);
    }
}
