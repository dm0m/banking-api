package io.github.dm0m.bankingapi.customer;

import org.springframework.stereotype.Component;

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
