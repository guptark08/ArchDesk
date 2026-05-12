package com.archdesk.sketch;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSketchRepository extends JpaRepository<ProjectSketch, Long> {
    List<ProjectSketch> findByProjectIdOrderByUploadedAtAsc(Long projectId);
}
