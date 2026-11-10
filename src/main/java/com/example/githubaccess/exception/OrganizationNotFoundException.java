package com.example.githubaccess.exception;

public class OrganizationNotFoundException extends RuntimeException {

    private final String organization;

    public OrganizationNotFoundException(String organization) {
        super("Organization not found: " + organization);
        this.organization = organization;
    }

    public String getOrganization() {
        return organization;
    }
}

