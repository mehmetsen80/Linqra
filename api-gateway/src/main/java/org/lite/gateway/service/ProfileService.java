package org.lite.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final Environment env;

    /**
     * Checks if the current environment is a cloud-like environment (cloud, prod, or ec2)
     */
    public boolean isCloud() {
        return env.acceptsProfiles(Profiles.of("cloud", "prod", "ec2"));
    }

    /**
     * Checks if the current environment is a development environment
     */
    public boolean isDev() {
        return env.acceptsProfiles(Profiles.of("dev", "default", "remote-dev"));
    }

    /**
     * Checks if the current environment is remote-dev
     */
    public boolean isRemoteDev() {
        return env.acceptsProfiles(Profiles.of("remote-dev"));
    }
}
