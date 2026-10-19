package com.vetclinic.auth;

import com.vetclinic.appointment.Actor;

/** Caller identity passed to use cases so role and ownership checks live in services. */
public record CurrentUser(Long userId, Role role, Long ownerId, Long veterinarianId) {

    public Actor actor() {
        return Actor.from(role);
    }
}
