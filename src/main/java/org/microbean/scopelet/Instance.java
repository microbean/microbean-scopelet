/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2025 microBean™.
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

import java.lang.invoke.VarHandle;

import java.util.Objects;

import java.util.function.Supplier;

import org.microbean.bean.Destruction;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * An {@link AutoCloseable} pairing of a contextual instance that can be destroyed with a {@link Destructor} that can
 * destroy it and a {@link Destruction} view of the {@link org.microbean.bean.Creation} that caused it to be created.
 *
 * @param <I> the contextual instance type
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see Instance.Destructor
 *
 * @see Destruction
 */
public final class Instance<I> implements AutoCloseable, Supplier<I> {


  /*
   * Static fields.
   */


  private static final VarHandle CLOSED;

  static {
    try {
      CLOSED = lookup().findVarHandle(Instance.class, "closed", boolean.class);
    } catch (final NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
      throw (Error)new ExceptionInInitializerError(reflectiveOperationException.getMessage()).initCause(reflectiveOperationException);
    }
  }


  /*
   * Instance fields.
   */


  private final I object;

  private final Destructor<I> destructor;

  private final Destruction destruction;

  private volatile boolean closed;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Instance}.
   *
   * @param contextualInstance a contextual instance that has just been created; may be {@code null}
   *
   * @param destructor a {@link Destructor} capable of (eventually) destroying the supplied {@code contextualInstance};
   * may be {@code null}
   *
   * @param destruction a {@link Destruction}; may be {@code null}
   */
  public Instance(final I contextualInstance,
                  final Destructor<I> destructor,
                  final Destruction destruction) {
    super();
    this.destruction = destruction;
    this.object = contextualInstance;
    this.destructor = destructor == null ? Instance::sink : destructor;
  }


  /*
   * Instance methods.
   */


  @Override // AutoCloseable
  public final void close() {
    if (CLOSED.compareAndSet(this, false, true)) { // volatile read/write
      try (this.destruction) {
        this.destructor.destroy(this.object, this.destruction);
      }
    }
  }

  /**
   * Returns {@code true} if and only if this {@link Instance} has been {@linkplain #close() closed}.
   *
   * @return {@code true} if and only if this {@link Instance} has been {@linkplain #close() closed}
   */
  public final boolean closed() {
    return this.closed; // volatile read
  }

  @Override // Object
  public final boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other != null && this.getClass() == other.getClass()) {
      // We don't want "closedness" to factor in here because it isn't part of hashCode(). But we want to use the
      // results of get(). Fortunately, that method is final. So we can just use direct field access.
      return Objects.equals(this.object, ((Instance<?>)other).object);
    } else {
      return false;
    }
  }

  /**
   * Returns the contextual instance this {@link Instance} holds, which may be {@code null}.
   *
   * @return the contextual instance this {@link Instance} holds, which may be {@code null}
   */
  @Override // Supplier<I>
  public final I get() {
    if (this.closed()) { // volatile read, effectively
      throw new IllegalStateException("closed");
    }
    return this.object;
  }

  @Override // Object
  public final int hashCode() {
    // We don't want "closedness" to factor in here because it isn't part of equals(). But we want to use the results of
    // get(). Fortunately, that method is final. So we can just use direct field access.
    return this.object.hashCode();
  }

  @Override // Object
  public final String toString() {
    return String.valueOf(this.object);
  }


  /*
   * Static methods.
   */


  private static final void sink() {

  }

  private static final <A, B> void sink(final A a, final B b) {

  }


  /*
   * Inner and nested classes.
   */


  /**
   * An interface whose implementations can destroy contextual instances.
   *
   * <p>This is commonly implemented in terms of a method reference to the {@link
   * org.microbean.bean.Factory#destroy(Object, Destruction)} method.</p>
   *
   * @param <I> the contextual instance type
   *
   * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
   *
   * @see Destruction
   *
   * @see org.microbean.bean.Factory#destroy(Object, Destruction)
   */
  @FunctionalInterface
  public static interface Destructor<I> {

    /**
     * Destroys the supplied contextual instance.
     *
     * @param i the contextual instance to destroy; may be {@code null}
     *
     * @param creation the {@link Destruction} view of the {@link org.microbean.bean.Creation} implementation that
     * caused the contextual instance to be created; may be {@code null}
     */
    public void destroy(final I i, final Destruction creation);

  }

}
