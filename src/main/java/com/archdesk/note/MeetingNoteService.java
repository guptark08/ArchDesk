package com.archdesk.note;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archdesk.client.ClientDtos;
import com.archdesk.client.ClientDtos.MeetingNoteRequest;
import com.archdesk.client.ClientDtos.MeetingNoteResponse;
import com.archdesk.client.ClientRepository;
import com.archdesk.common.NotFoundException;

@Service
public class MeetingNoteService {
    private final ClientRepository clients;
    private final MeetingNoteRepository notes;

    public MeetingNoteService(ClientRepository clients, MeetingNoteRepository notes) {
        this.clients = clients;
        this.notes = notes;
    }

    @Transactional
    public MeetingNoteResponse create(Long clientId, MeetingNoteRequest request) {
        var client = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Client not found"));
        MeetingNote note = new MeetingNote();
        note.setClient(client);
        note.setNoteDate(request.noteDate());
        note.setContent(request.content().trim());
        return ClientDtos.note(notes.save(note));
    }

    @Transactional
    public MeetingNoteResponse update(Long clientId, Long noteId, MeetingNoteRequest request) {
        MeetingNote note = findOwned(clientId, noteId);
        note.setNoteDate(request.noteDate());
        note.setContent(request.content().trim());
        return ClientDtos.note(note);
    }

    @Transactional
    public void delete(Long clientId, Long noteId) {
        notes.delete(findOwned(clientId, noteId));
    }

    private MeetingNote findOwned(Long clientId, Long noteId) {
        MeetingNote note = notes.findById(noteId).orElseThrow(() -> new NotFoundException("Meeting note not found"));
        if (!note.getClient().getId().equals(clientId)) {
            throw new NotFoundException("Meeting note not found for client");
        }
        return note;
    }
}
