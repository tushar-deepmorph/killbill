/*
 * Copyright 2026 The Billing Project, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package org.killbill.billing.util.tag;

import java.util.UUID;

/**
 * Identifier for the PAID_BY_EXTERNAL account-level system control tag.
 *
 * Kept outside {@link ControlTagType} until the corresponding public
 * killbill-api release contains the new enum member.
 */
public final class PaidByExternalTag {

    public static final UUID ID = new UUID(0, 10);
    public static final String NAME = "PAID_BY_EXTERNAL";

    private PaidByExternalTag() {
    }
}
