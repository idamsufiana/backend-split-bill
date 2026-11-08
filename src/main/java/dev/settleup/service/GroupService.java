package dev.settleup.service;

import dev.settleup.calc.Money;
import dev.settleup.domain.BillGroup;
import dev.settleup.domain.Participant;
import dev.settleup.exception.BusinessRuleException;
import dev.settleup.exception.NotFoundException;
import dev.settleup.repository.BillGroupRepository;
import dev.settleup.domain.dto.ApiMapper;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Transactional
public class GroupService {

    static final String DEFAULT_CURRENCY = "USD";

    private final BillGroupRepository groups;
    private final Clock clock;

    public GroupService(BillGroupRepository groups, Clock clock) {
        this.groups = groups;
        this.clock = clock;
    }

    public Responses.Group create(Requests.CreateGroup request) {
        String code = request.currency() == null ? DEFAULT_CURRENCY : request.currency();
        BillGroup group = new BillGroup(request.name().trim(), Money.currency(code), clock.instant());
        for (String name : request.participants()) {
            addUniqueParticipant(group, name);
        }
        return ApiMapper.group(groups.save(group));
    }

    @Transactional(readOnly = true)
    public Responses.Group get(Long groupId) {
        return ApiMapper.group(load(groupId));
    }

    @Transactional(readOnly = true)
    public List<Responses.Group> list() {
        return groups.findAllByOrderByIdAsc().stream().map(ApiMapper::group).toList();
    }

    @EvictGroupCaches
    public Responses.ParticipantRef addParticipant(Long groupId, Requests.AddParticipant request) {
        BillGroup group = loadForUpdate(groupId);
        Participant participant = addUniqueParticipant(group, request.name());
        groups.saveAndFlush(group);
        return ApiMapper.ref(participant);
    }

    /** Loads a group with its participants or throws 404. Shared by the other services. */
    BillGroup load(Long groupId) {
        return groups.findWithParticipantsById(groupId)
                .orElseThrow(() -> new NotFoundException("Group", groupId));
    }

    /**
     * Loads a group and takes a pessimistic write lock on its row for the rest of the current
     * transaction. Every write to a group's ledger goes through here, which closes check-then-act
     * races such as two concurrent requests both passing the "name is unique" check, or the same
     * expense being voided twice.
     */
    BillGroup loadForUpdate(Long groupId) {
        return groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("Group", groupId));
    }

    static Participant requireMember(BillGroup group, Long participantId, String role) {
        return group.getParticipants().stream()
                .filter(p -> p.getId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        role + " (participant " + participantId + ") is not a member of this group"));
    }

    private static Participant addUniqueParticipant(BillGroup group, String name) {
        if (group.hasParticipantNamed(name)) {
            throw new BusinessRuleException("Participant names must be unique within a group: " + name.trim());
        }
        return group.addParticipant(name);
    }
}
