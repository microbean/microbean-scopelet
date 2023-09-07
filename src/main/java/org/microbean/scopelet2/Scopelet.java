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
package org.microbean.scopelet2;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;

import java.util.Objects;

import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Bean;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.Factory;
import org.microbean.bean.References;

import org.microbean.qualifier.NamedAttributeMap;

import org.microbean.scope.ScopeMember;

public abstract class Scopelet<S extends Scopelet<S>> implements AutoCloseable, Factory<S>, ScopeMember {

  private static final VarHandle ME;

  static {
    final Lookup lookup = MethodHandles.lookup();
    try {
      ME = lookup.findVarHandle(Scopelet.class, "me", Scopelet.class);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw (Error)new ExceptionInInitializerError(e.getMessage()).initCause(e);
    }
  }

  private volatile S me;

  private volatile boolean closed;

  private final NamedAttributeMap<?> scopeId;

  protected Scopelet(final NamedAttributeMap<?> scopeId) {
    super();
    this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
  }

  public abstract Id id();

  public final Bean<S> bean() {
    return new Bean<>(this.id(), this);
  }

  @Override // Factory<S>
  @SuppressWarnings("unchecked")
  public final S create(final Creation<S> c, final References<?> references) {
    if (ME.compareAndSet(this, null, this)) { // volatile write
      if (references != null) {
        // TODO: emit initialized event
      }
    }
    return (S)this;
  }

  @Override // Factory<S>
  public final S singleton() {
    return this.me; // volatile read
  }

  @Override // Factory<S>
  public boolean destroys() {
    return true;
  }

  @Override // Object
  public int hashCode() {
    int hashCode = 17;
    hashCode = 31 * hashCode + this.id().hashCode();
    hashCode = 31 * hashCode + this.scopeId().hashCode();
    return hashCode;
  }

  @Override // Object
  public boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other != null && other.getClass().equals(this.getClass())) {
      final Scopelet<?> her = (Scopelet<?>)other;
      return
        Objects.equals(this.id(), her.id()) &&
        Objects.equals(this.scopeId(), her.scopeId());
    } else {
      return false;
    }
  }

  @Override // ScopeMember
  public final NamedAttributeMap<?> governingScopeId() {
    return this.id().governingScopeId();
  }

  @Override // ScopeMember
  public final boolean governedBy(final NamedAttributeMap<?> scopeId) {
    return this.id().governedBy(scopeId);
  }


  /*
   * Repository-like concerns.
   */


  public final NamedAttributeMap<?> scopeId() {
    return this.scopeId;
  }

  public boolean active() {
    return !this.closed(); // volatile read
  }

  public boolean containsId(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return this.get(id) != null;
  }

  // id is nullable.
  public <I> I get(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return this.instance(id, null, null, null);
  }

  // All parameters are nullable.
  public abstract <I> I instance(final Object id,
                                 final Factory<I> factory,
                                 final Creation<I> c,
                                 final References<?> r);

  // id is nullable.
  public abstract boolean remove(final Object id);

  @Override // AutoCloseable
  public void close() {
    if (!this.closed()) {
      this.closed = true;
    }
  }

  protected final boolean closed() {
    return this.closed;
  }

}
