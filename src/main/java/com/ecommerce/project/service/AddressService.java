package com.ecommerce.project.service;

import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;

import java.util.List;

public interface AddressService {

    AddressDTO createAddress(AddressDTO addressDTO, User user);

    List<AddressDTO> getAddresses();

    // Added User parameter
    AddressDTO getAddressesById(Long addressId, User user);

    List<AddressDTO> getUserAddresses(User user);

    // Added User parameter
    AddressDTO updateAddress(Long addressId, AddressDTO addressDTO, User user);

    // Added User parameter
    String deleteAddress(Long addressId, User user);
}