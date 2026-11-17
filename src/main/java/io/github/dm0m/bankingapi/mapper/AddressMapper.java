package io.github.dm0m.bankingapi.mapper;

import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.AddressResponse;
import io.github.dm0m.bankingapi.model.Address;

@Component
public class AddressMapper {

    public Address toEntity(AddressRequest request) {
        Address address = new Address();
        updateEntity(address, request);
        return address;
    }

    public void updateEntity(Address address, AddressRequest request) {
        address.setStreetNo(request.streetNo());
        address.setStreet(request.street());
        address.setCity(request.city());
        address.setState(request.state());
        address.setZipCode(request.zipCode());
        address.setCountry(request.country());
    }

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
            address.getStreetNo(),
            address.getStreet(),
            address.getCity(),
            address.getState(),
            address.getZipCode(),
            address.getCountry());
    }
}
