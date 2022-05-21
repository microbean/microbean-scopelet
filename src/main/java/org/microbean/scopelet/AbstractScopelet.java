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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;

import java.util.Objects;

import org.microbean.bean.Creation;
import org.microbean.bean.Id;

import org.microbean.qualifier.Qualifier;

public abstract class AbstractScopelet<S extends AbstractScopelet<S>> implements Scopelet<S> {

  private static final VarHandle ME;

  static {
    final Lookup lookup = MethodHandles.lookup();
    try {
      ME = lookup.findVarHandle(AbstractScopelet.class, "me", AbstractScopelet.class);
    } catch (final NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
      throw (Error)new ExceptionInInitializerError(reflectiveOperationException.getMessage()).initCause(reflectiveOperationException);
    }
  }
  
  private final Qualifier<?> scopeId;

  private final Id id;

  private volatile S me;
  
  protected AbstractScopelet(final Qualifier<?> scopeId, final Id id) {
    super();
    this.scopeId = scopeId;
    this.id = Objects.requireNonNull(id, "id");
  }

  @Override // Scopelet<S>
  public final Qualifier<?> scopeId() {
    return this.scopeId;
  }

  @Override // ScopeMember
  public final boolean governedBy(final Qualifier<?> scopeId) {
    return this.id().governedBy(scopeId);
  }

  @Override // ScopeMember
  public final Qualifier<?> governingScopeId() {
    return this.id().governingScopeId();
  }

  @Override // Scopelet<S>
  public final Id id() {
    return this.id;
  }

  @Override // Singleton<S>
  public S singleton() {
    return this.me; // volatile read
  }

  @Override // Factory<S>
  @SuppressWarnings("unchecked")
  public S produce(final Creation<S> c) {
    if (ME.compareAndSet(this, null, this)) { // volatile write
      if (c != null) {
        // TODO: emit initialized event
      }
    }
    return (S)this;
  }
  
}
