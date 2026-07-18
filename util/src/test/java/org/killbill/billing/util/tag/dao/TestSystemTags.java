/*
 * Copyright 2020-2024 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.tag.dao;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSystemTags extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testPaidByExternalIsResolvableByIdAndName() {
        final TagDefinitionModelDao byId = SystemTags.lookup(SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID);
        Assert.assertNotNull(byId);
        Assert.assertEquals(byId.getName(), SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_NAME);
        Assert.assertEquals(byId.getApplicableObjectTypes(), ObjectType.ACCOUNT.name());

        final TagDefinitionModelDao byName = SystemTags.lookup(SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_NAME);
        Assert.assertNotNull(byName);
        Assert.assertEquals(byName.getId(), SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID);
    }

    @Test(groups = "fast")
    public void testPaidByExternalIsUserAssignable() {
        // Unlike the internal __PARK__ tag, PAID_BY_EXTERNAL must not be flagged as a (non-assignable) system tag,
        // otherwise DefaultTagUserApi#addTag would reject it with TAG_IS_SYSTEM.
        Assert.assertFalse(SystemTags.isSystemTag(SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID));
        Assert.assertTrue(SystemTags.isSystemTag(SystemTags.PARK_TAG_DEFINITION_ID));
    }

    @Test(groups = "fast")
    public void testPaidByExternalAlwaysListed() {
        // Always exposed as a tag definition, like control tags, regardless of includeSystemTags.
        Assert.assertTrue(SystemTags.get(false).stream()
                                    .anyMatch(t -> SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID.equals(t.getId())));
        Assert.assertTrue(SystemTags.get(true).stream()
                                    .anyMatch(t -> SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID.equals(t.getId())));
    }
}
