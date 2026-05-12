package com.archdesk.note;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.archdesk.client.ClientDtos.MeetingNoteRequest;
import com.archdesk.client.ClientDtos.MeetingNoteResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clients/{clientId}/notes")
public class MeetingNoteController {
    private final MeetingNoteService service;

    public MeetingNoteController(MeetingNoteService service) {
        this.service = service;
    }

    @PostMapping
    MeetingNoteResponse create(@PathVariable Long clientId, @Valid @RequestBody MeetingNoteRequest request) {
        return service.create(clientId, request);
    }

    @PutMapping("/{noteId}")
    MeetingNoteResponse update(@PathVariable Long clientId, @PathVariable Long noteId, @Valid @RequestBody MeetingNoteRequest request) {
        return service.update(clientId, noteId, request);
    }

    @DeleteMapping("/{noteId}")
    void delete(@PathVariable Long clientId, @PathVariable Long noteId) {
        service.delete(clientId, noteId);
    }
}
