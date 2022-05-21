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
import org.microbean.bean.Id;
import org.microbean.bean.Selector;

import org.microbean.qualifier.Qualifiers;

import org.microbean.type.DefaultParameterizedType;
import org.microbean.type.JavaType;

import static org.microbean.bean.Prioritized.DEFAULT_PRIORITY;

import static org.microbean.bean.Selector.ANY_QUALIFIER;

import static org.microbean.scope.Scope.SINGLETON;

public final class SingletonScopelet extends MapBackedScopelet<SingletonScopelet> {

  private static final long serialVersionUID = 1L;
  
  public SingletonScopelet() {
    super(SINGLETON.id(), // the scope we implement
          Id.of(Selector.of(Qualifiers.ofDisparate(ANY_QUALIFIER, SINGLETON.id()), // SINGLETON here is a qualifier
                            JavaType.ofExactly(true,                                               
                                               List.of(SingletonScopelet.class,
                                                       new DefaultParameterizedType(null,
                                                                                    Scopelet.class,
                                                                                    SingletonScopelet.class)))),
                SINGLETON.id())); // the scope we belong to
  }                

  @Override // AbstractScopelet
  public final SingletonScopelet singleton() {
    return this;
  }

  @Override // AbstractScopelet
  public final SingletonScopelet produce(final Creation<SingletonScopelet> bc) {
    return this;
  }

}
