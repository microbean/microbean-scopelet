/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2022 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.scopelet;

import java.util.List;

import org.microbean.bean.Creation;
import org.microbean.bean.Dependents;
import org.microbean.bean.Id;
import org.microbean.bean.Factory;
import org.microbean.bean.Selector;

import org.microbean.qualifier.Qualifiers;

import org.microbean.type.DefaultParameterizedType;
import org.microbean.type.JavaType;

import static org.microbean.bean.Prioritized.DEFAULT_PRIORITY;

import static org.microbean.bean.Selector.ANY_QUALIFIER;

import static org.microbean.scope.Scope.NONE_ID;
import static org.microbean.scope.Scope.SINGLETON_ID;

public final class NoneScopelet extends AbstractScopelet<NoneScopelet> {

  public NoneScopelet() {
    super(NONE_ID, // the scope we implement
          Id.of(Selector.of(Qualifiers.ofDisparate(ANY_QUALIFIER, NONE_ID), // NONE_ID here is a qualifier
                            JavaType.ofExactly(true,
                                               List.of(NoneScopelet.class,
                                                       new DefaultParameterizedType(null,
                                                                                    Scopelet.class,
                                                                                    NoneScopelet.class)))),
                SINGLETON_ID)); // the scope we belong to
  }

  @Override // AbstractScopelet
  public final boolean containsKey(final Object beanId) {
    return false;
  }

  @Override // AbstractScopelet
  public final <I> I get(final Object beanId) {
    return null;
  }

  @Override // AbstractScopelet
  public final <I> I supply(final Object beanId, final Factory<I> factory, final Creation<I> bc) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    final I returnValue = factory.create(bc);
    if (returnValue != null && bc instanceof Dependents d && factory.destroys()) {
      d.add(new Instance<>(returnValue, factory::destroy, bc.destruction()));
    }
    return returnValue;
  }

  @Override // AbstractScopelet
  public final void remove(final Object ignored) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
  }

  @Override // AbstractScopelet
  public final NoneScopelet singleton() {
    return this;
  }

  @Override // AbstractScopelet
  public final NoneScopelet produce(final Creation<NoneScopelet> bc) {
    return this;
  }

}
