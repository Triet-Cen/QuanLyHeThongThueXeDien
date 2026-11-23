package com.example.QuanLyChoThueXeDien.auth.services;


import com.example.QuanLyChoThueXeDien.auth.dtos.ProfileForm;
import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import com.example.QuanLyChoThueXeDien.auth.repositories.CustomerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository repository;

    public CustomerProfileService(CustomerProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProfileForm getForm(String email){
        CustomerProfile profile= findByEmail(email);

        ProfileForm form= new ProfileForm();
        form.setFullName(profile.getFullName());
        form.setPhone(profile.getPhone());
        form.setBirthDate(profile.getBirthDate());
        form.setAddress(profile.getAddress());
        return form;
    }

    @Transactional
    public void update(String email, ProfileForm form){

        CustomerProfile profile= findByEmail(email);
        profile.setFullName(form.getFullName().trim());
        profile.setPhone(form.getPhone().trim());
        profile.setBirthDate(form.getBirthDate());
        profile.setAddress(form.getAddress()==null
        || form.getAddress().isBlank() ? null : form.getAddress().trim()
        );

    }

    private CustomerProfile findByEmail(String email){
        return repository.findByAccount_Email(email)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ khách hàng"
                ));
    }
}
