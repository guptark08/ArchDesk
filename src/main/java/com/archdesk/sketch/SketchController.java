package com.archdesk.sketch;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.archdesk.client.ClientDtos.SketchResponse;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/projects/{projectId}/sketches")
public class SketchController {
    private final SketchService service;

    public SketchController(SketchService service) {
        this.service = service;
    }

    @GetMapping
    List<SketchResponse> list(@PathVariable Long projectId) {
        return service.list(projectId);
    }

    @PostMapping
    SketchResponse upload(
            @PathVariable Long projectId,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String caption) {
        return service.upload(projectId, file, caption);
    }

    @PatchMapping("/{sketchId}")
    SketchResponse updateCaption(
            @PathVariable Long projectId,
            @PathVariable Long sketchId,
            @RequestBody CaptionRequest request) {
        return service.updateCaption(projectId, sketchId, request.caption());
    }

    @DeleteMapping("/{sketchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long projectId, @PathVariable Long sketchId) {
        service.delete(projectId, sketchId);
    }

    record CaptionRequest(String caption) {
    }
}
