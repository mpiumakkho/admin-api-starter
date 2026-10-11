package com.adminstarter.accesscontrol.audit.spi;

import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.Set;
import java.util.UUID;

/**
 * Whose entries someone may read through roles limited to branches, without activity.view for the whole organization
 * (P-024): the people of the branches where they have it. SecurityConfig answers it from identity, so that audit,
 * which only listens (R5), does not depend on identity.
 */
public interface ActivityAudienceProvider {

    /** Their ids; empty when the actor has activity.view in no branch */
    Set<UUID> peopleFor(Actor actor);
}
