/*
 * Copyright 2026 The Billing Project, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.killbill.billing.util.tag.dao;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.tag.PaidByExternalTag;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSystemTags extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testPaidByExternalSystemTag() {
        final TagDefinitionModelDao definition = SystemTags.lookup(PaidByExternalTag.ID);
        Assert.assertNotNull(definition);
        Assert.assertEquals(definition.getName(), PaidByExternalTag.NAME);
        Assert.assertEquals(definition.getApplicableObjectTypes(), ObjectType.ACCOUNT.name());
        Assert.assertTrue(SystemTags.isSystemTag(PaidByExternalTag.ID));
        Assert.assertTrue(SystemTags.get(false).stream().anyMatch(tag -> PaidByExternalTag.ID.equals(tag.getId())));
    }
}
