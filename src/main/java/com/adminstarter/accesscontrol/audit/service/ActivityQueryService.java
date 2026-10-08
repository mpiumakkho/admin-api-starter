package com.adminstarter.accesscontrol.audit.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.audit.dto.ActivityResponse;
import com.adminstarter.accesscontrol.audit.entity.AuditLog;
import com.adminstarter.accesscontrol.audit.repository.AuditLogRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class ActivityQueryService {

    /** CorePermissions.ACTIVITY_VIEW; spelled out because audit depends on no other module but their events */
    private static final String ACTIVITY_VIEW = "activity.view";
    private static final TypeReference<List<FieldChange>> CHANGES = new TypeReference<>() {};

    private final AuditLogRepository logs;
    private final JsonMapper json;
    private final int listLimit;

    public ActivityQueryService(AuditLogRepository logs, JsonMapper json, AccessControlProperties properties) {
        this.logs = logs;
        this.json = json;
        this.listLimit = properties.audit().listLimit();
    }

    /** The log, newest first, or nothing without activity.view; the app filters and pages it itself */
    public List<ActivityResponse> list(Actor actor) {
        if (!actor.has(ACTIVITY_VIEW)) {
            return List.of();
        }
        return logs.findAllByOrderByOccurredAtDescIdDesc(Limit.of(listLimit)).stream().map(this::toResponse).toList();
    }

    private ActivityResponse toResponse(AuditLog entry) {
        var changes = entry.getChanges() == null ? null : json.readValue(entry.getChanges(), CHANGES);
        return new ActivityResponse(entry.getId(), entry.getOccurredAt(), entry.getActorId(), entry.getAction(),
                entry.getTargetLabel(), entry.getTargetId(), entry.getIp(), entry.getBrowser(), entry.getOs(), changes);
    }
}
