package com.orthiva.core.identity;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Internal users (lab, planners, production, accounting, representatives) are created by ADMIN. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final IdentityService identity;

    public AdminUserController(IdentityService identity) {
        this.identity = identity;
    }

    public record CreateStaffRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull PersonType type,
            @NotBlank @Size(min = 8, max = 64) String temporaryPassword) {
    }

    @GetMapping
    public List<PersonDto> list() {
        return identity.listStaff().stream().map(PersonDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonDto create(@Valid @RequestBody CreateStaffRequest body) {
        var person = identity.createStaff(new IdentityService.NewStaffInput(
                body.email(), body.firstName(), body.lastName(), body.type(), body.temporaryPassword()));
        return PersonDto.from(person);
    }
}
