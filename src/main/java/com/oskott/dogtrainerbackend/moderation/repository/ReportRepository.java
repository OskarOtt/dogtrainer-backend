package com.oskott.dogtrainerbackend.moderation.repository;

import com.oskott.dogtrainerbackend.moderation.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
}
