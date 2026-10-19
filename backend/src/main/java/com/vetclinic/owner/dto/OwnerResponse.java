package com.vetclinic.owner.dto;

import com.vetclinic.owner.Owner;
import java.util.List;

public record OwnerResponse(Long id, String name, String phone, List<PetResponse> pets) {

    public static OwnerResponse from(Owner o, List<PetResponse> pets) {
        return new OwnerResponse(o.getId(), o.getName(), o.getPhone(), pets);
    }
}
