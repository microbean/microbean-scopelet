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

import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;

import org.microbean.bean.Qualifiers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestScopes {

  private static Qualifiers qualifiers;

  private static Scopes scopes;

  private TestScopes() {
    super();
  }

  @BeforeAll
  static final void setup() {
    qualifiers = new Qualifiers();
    scopes = new Scopes(qualifiers);
  }

  @Test
  final void testIsScope() {
    // The Scope Attributes is not, itself, a scope identifier.
    assertFalse(scopes.scope(scopes.scope()));

    // scopes.singleton() is a scope identifier because it is attributed with the Scope Attributes.
    assertTrue(scopes.scope(scopes.singleton()));

    // scopes.none() is a scope identifier for the same reason.
    assertTrue(scopes.scope(scopes.none()));

    // Even though here the Any Attributes is attributed with scopes.none(), that doesn't make it a scope identifier (it
    // is not attributed with scopes.scope(); scopes.scope() is not transitive).
    final Attributes a = Attributes.of("Any", scopes.none());
    assertFalse(scopes.scope(a));

    // An instance of scopes.none() without any additional attributes is equal to the canonical scopes.none() which does
    // have additional attributes. This is because by design only names and values are considered in equality
    // comparisons.
    assertEquals(Attributes.of("None", Map.of("normal", BooleanValue.of(false)), Map.of(), Map.of()), scopes.findScope(a.attributes()));
  }

  @Test
  final void testNoneIsNotPrimordial() {
    assertSame(scopes.none(), scopes.findScope(List.of(scopes.none())));
    assertFalse(scopes.none().attributes().contains(qualifiers.primordialQualifier()));
    assertSame(scopes.singleton(), scopes.findScope(scopes.none().attributes()));
  }
  
  @Test
  final void testSingletonIsPrimordial() {
    assertSame(scopes.singleton(), scopes.findScope(List.of(scopes.singleton())));
    assertTrue(scopes.singleton().attributes().contains(qualifiers.primordialQualifier()));
    assertNull(scopes.findScope((scopes.singleton().attributes())));
    
  }

}
