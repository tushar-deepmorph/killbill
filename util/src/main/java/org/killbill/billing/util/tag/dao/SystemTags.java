/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.tag.ControlTagType;

public class SystemTags {

    // Invoice
    public static final UUID PARK_TAG_DEFINITION_ID = new UUID(1, 1);
    public static final String PARK_TAG_DEFINITION_NAME = "__PARK__";

    // Account: customer-initiated external payment (https://github.com/killbill/killbill/issues/2040).
    // When present on an account, invoice generation does not create an automatic (synthetic) payment;
    // invoices stay unpaid so overdue and subscription blocking rules apply. The customer's payment is
    // recorded later, out of band, through the record-external-payment API.
    public static final UUID PAID_BY_EXTERNAL_TAG_DEFINITION_ID = new UUID(1, 2);
    public static final String PAID_BY_EXTERNAL_TAG_DEFINITION_NAME = "PAID_BY_EXTERNAL";

    // Internal system tags that users are not allowed to add or remove.
    // Note! TagSqlDao.sql.stg needs to be kept in sync (see userAndSystemTagDefinitions)
    private static final List<TagDefinitionModelDao> SYSTEM_DEFINED_TAG_DEFINITIONS = List.of(new TagDefinitionModelDao(PARK_TAG_DEFINITION_ID, null, null, PARK_TAG_DEFINITION_NAME, "Accounts with invalid invoicing state", ObjectType.ACCOUNT.name()));

    // System tags that are resolvable without a per-tenant tag_definitions row (like control tags) but,
    // unlike SYSTEM_DEFINED_TAG_DEFINITIONS, may be freely added/removed by users on the applicable objects.
    // Note! TagSqlDao.sql.stg needs to be kept in sync (see userAndSystemTagDefinitions)
    private static final List<TagDefinitionModelDao> USER_ASSIGNABLE_SYSTEM_TAG_DEFINITIONS = List.of(new TagDefinitionModelDao(PAID_BY_EXTERNAL_TAG_DEFINITION_ID, null, null, PAID_BY_EXTERNAL_TAG_DEFINITION_NAME, "Invoices are paid by the customer through an external channel and reconciled later", ObjectType.ACCOUNT.name()));

    public static Collection<TagDefinitionModelDao> get(final boolean includeSystemTags) {
        final Collection<TagDefinitionModelDao> all = includeSystemTags ?
                                                      new LinkedList<TagDefinitionModelDao>(SYSTEM_DEFINED_TAG_DEFINITIONS) :
                                                      new LinkedList<TagDefinitionModelDao>();
        // User-assignable system tags always show up as regular tag definitions, like control tags
        all.addAll(USER_ASSIGNABLE_SYSTEM_TAG_DEFINITIONS);
        for (final ControlTagType controlTag : ControlTagType.values()) {
            all.add(new TagDefinitionModelDao(controlTag));
        }
        return all;
    }

    public static TagDefinitionModelDao lookup(final String tagDefinitionName) {
        for (final ControlTagType t : ControlTagType.values()) {
            if (t.name().equals(tagDefinitionName)) {
                return new TagDefinitionModelDao(t);
            }
        }

        for (final TagDefinitionModelDao t : SYSTEM_DEFINED_TAG_DEFINITIONS) {
            if (t.getName().equals(tagDefinitionName)) {
                return t;
            }
        }

        for (final TagDefinitionModelDao t : USER_ASSIGNABLE_SYSTEM_TAG_DEFINITIONS) {
            if (t.getName().equals(tagDefinitionName)) {
                return t;
            }
        }

        return null;
    }

    public static boolean isSystemTag(final UUID tagDefinitionId) {
        return SYSTEM_DEFINED_TAG_DEFINITIONS.stream().anyMatch(input -> input.getId().equals(tagDefinitionId));
    }

    public static TagDefinitionModelDao lookup(final UUID tagDefinitionId) {
        for (final ControlTagType t : ControlTagType.values()) {
            if (t.getId().equals(tagDefinitionId)) {
                return new TagDefinitionModelDao(t);
            }
        }

        for (final TagDefinitionModelDao t : SYSTEM_DEFINED_TAG_DEFINITIONS) {
            if (t.getId().equals(tagDefinitionId)) {
                return t;
            }
        }

        for (final TagDefinitionModelDao t : USER_ASSIGNABLE_SYSTEM_TAG_DEFINITIONS) {
            if (t.getId().equals(tagDefinitionId)) {
                return t;
            }
        }

        return null;
    }
}
