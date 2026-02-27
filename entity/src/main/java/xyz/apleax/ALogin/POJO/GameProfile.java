/*
 * Copyright (C) 2018-2023 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package xyz.apleax.ALogin.POJO;

import java.util.List;
import java.util.UUID;

public record GameProfile(UUID id, String name, List<Property> properties) {
    public record Property(String name, String value, String signature) {
    }
}
