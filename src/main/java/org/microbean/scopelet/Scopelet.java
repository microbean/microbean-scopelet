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

import java.lang.reflect.Type;

import org.microbean.bean.Bean;
import org.microbean.bean.BeanSource;
import org.microbean.bean.Creation;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.SingletonFactory;

import org.microbean.qualifier.Qualifier;

import org.microbean.scope.ScopeMember;

import org.microbean.type.DefaultParameterizedType;
import org.microbean.type.UnboundedWildcardType;

public interface Scopelet<S extends Scopelet<S>> extends AutoCloseable, BeanSource<S>, SingletonFactory<S>, ScopeMember {

  public static final Type SCOPELET_TYPE = new DefaultParameterizedType(null, Scopelet.class, UnboundedWildcardType.INSTANCE);
  
  public Id id();

  @Override // BeanSource<S>
  public default Bean<S> bean() {
    return Bean.of(this, this.id());
  }

  public Qualifier<?> scopeId();

  public default boolean activate() {
    return false;
  }
  
  public default void deactivate() {

  }
  
  public default boolean active() {
    return true;
  }

  public default boolean containsKey(final Object beanId) {
    return this.get(beanId) == null;
  }

  public default <I> I get(final Object beanId) {
    return this.supply(beanId, null, null);
  }

  public <I> I supply(final Object beanId, final Factory<I> factory, final Creation<I> c);

  public void remove(final Object beanId);

  @Override // Factory<S>
  @SuppressWarnings("unchecked")
  public default S singleton() {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return (S)this;
  }

  @Override // AutoCloseable
  public default void close() {

  }

}
