/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.scopelet;

import java.lang.ref.WeakReference;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.microbean.scopelet.NoneScopelet.DependentInstance;

final class TestDependentInstanceStrategy {

  private TestDependentInstanceStrategy() {
    super();
  }

  @Test
  final void test() {

    final Consumer<Object> c = i -> System.out.println("destroying: " + i);

    Object i = Integer.valueOf(1);
    WeakReference<Object> r = new DependentInstance<>(i, c);
    assertTrue(r.refersTo(i));
    i = null;
    // Simulate garbage collection
    assertTrue(r.enqueue());
    assertNull(r.get());

    i = Integer.valueOf(2);
    r = new DependentInstance<>(i, c);
    assertTrue(r.refersTo(i));
    // 
    
  }
  
}
