package dev.settleup.web;

import dev.settleup.service.GroupService;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groups;

    public GroupController(GroupService groups) {
        this.groups = groups;
    }

    @PostMapping
    public ResponseEntity<Responses.Group> create(@Valid @RequestBody Requests.CreateGroup request) {
        Responses.Group created = groups.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<Responses.Group> list() {
        return groups.list();
    }

    @GetMapping("/{groupId}")
    public Responses.Group get(@PathVariable Long groupId) {
        return groups.get(groupId);
    }

    @PostMapping("/{groupId}/participants")
    public ResponseEntity<Responses.ParticipantRef> addParticipant(
            @PathVariable Long groupId, @Valid @RequestBody Requests.AddParticipant request) {
        Responses.ParticipantRef added = groups.addParticipant(groupId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/groups/{groupId}").buildAndExpand(groupId).toUri();
        return ResponseEntity.created(location).body(added);
    }
}
