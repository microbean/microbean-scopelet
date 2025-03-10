/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2025 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.scopelet;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.microbean.bean.Reducer;
import org.microbean.bean.Selectable;

import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.microbean.assign.Qualifiers.primordialQualifier;
import static org.microbean.assign.Qualifiers.qualifier;

import static org.microbean.scopelet.Scopelet.NONE_ID;
import static org.microbean.scopelet.Scopelet.SCOPE;
import static org.microbean.scopelet.Scopelet.SINGLETON_ID;

final class TestScopedInstances {

  private static Domain d;

  private static ScopedInstances i;

  private TestScopedInstances() {
    super();
  }

  @BeforeAll
  static final void setup() {
    d = new DefaultDomain();
    i = new ScopedInstances(d, Selectable.of(), Reducer.ofFailing());
  }

  @Test
  final void testIsScope() {
    // The Scope Attributes is not, itself, a scope identifier.
    assertFalse(i.isScopeId(SCOPE));

    // SINGLETON_ID is a scope identifier because it is attributed with the Scope Attributes.
    assertTrue(i.isScopeId(SINGLETON_ID));

    // NONE_ID is a scope identifier for the same reason.
    assertTrue(i.isScopeId(NONE_ID));

    // Even though here the Any Attributes is attributed with NONE_ID, that doesn't make it a scope identifier (it is not
    // attributed with the Scope Attributes; the Scope Attributes is not transitive).
    final Attributes a = Attributes.of("Any", NONE_ID);
    assertFalse(i.isScopeId(a));

    // An instance of the None Attributes without any additional attributes is equal to the canonical NONE_ID Attributes
    // which does have additional attributes. This is because by design only names and values are considered in equality
    // comparisons.
    assertEquals(Attributes.of("None", Map.of("normal", BooleanValue.of(false)), Map.of(), Map.of()), i.findScopeId(a.attributes()));
  }

  @Test
  final void testSingletonIsPrimordial() {
    final Attributes singletonId = i.findScopeId(List.of(SINGLETON_ID));
    assertSame(SINGLETON_ID, singletonId);
    assertTrue(SINGLETON_ID.attributes().contains(primordialQualifier()));
  }

}
