/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2024 microBean™.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.util.Objects;

import java.util.function.Supplier;

import org.microbean.bean.Creation;
import org.microbean.bean.ReferenceSelector;

/**
 * An {@link AutoCloseable} pairing of an instance that can be destroyed with a {@link Destructor} that can destroy it
 * and an {@link AutoCloseable} that can release its dependent objects when needed.
 *
 * @param <I> the type of the instance
 *
 * @author <a href="https://about.me/lairdnelson" target="_parent">Laird Nelson</a>
 */
public final class Instance<I> implements AutoCloseable, Supplier<I> {

  private static final VarHandle CLOSED;

  static {
    try {
      CLOSED = MethodHandles.lookup().findVarHandle(Instance.class, "closed", boolean.class);
    } catch (final NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
      throw (Error)new ExceptionInInitializerError(reflectiveOperationException.getMessage()).initCause(reflectiveOperationException);
    }
  }

  private final I object;

  private final Destructor<I> destroyer;

  private final AutoCloseable releaser;

  private final Creation<I> creation;

  private final ReferenceSelector referenceSelector;

  private volatile boolean closed;

  public Instance(final I object,
                  final Destructor<I> destroyer,
                  final Creation<I> creation,
                  final ReferenceSelector referenceSelector) {
    this(object, destroyer, creation, creation, referenceSelector);
  }

  private Instance(final I object,
                   final Destructor<I> destroyer,
                   final AutoCloseable releaser, // often the same object as creation
                   final Creation<I> creation, // often the same object as releaser
                   final ReferenceSelector referenceSelector) {
    super();
    // All of these things are nullable on purpose.
    this.object = object;
    this.releaser = releaser;
    this.destroyer = destroyer;
    this.creation = creation;
    this.referenceSelector = referenceSelector;
  }

  @Override
  public final I get() {
    if (this.closed()) {
      throw new IllegalStateException("closed");
    }
    return this.object;
  }

  @Override
  public final void close() {
    if (CLOSED.compareAndSet(this, false, true)) { // volatile read/write
      RuntimeException t = null;
      try {
        if (this.destroyer != null) {
          this.destroyer.destroy(this.object, this.releaser, this.creation, this.referenceSelector);
        }
      } catch (final RuntimeException e) {
        t = e;
      } finally {
        if (this.releaser != null) {
          try {
            this.releaser.close();
          } catch (final RuntimeException | Error e) {
            if (t == null) {
              throw e;
            }
            t.addSuppressed(e);
          } catch (final Exception e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            if (t == null) {
              throw new ScopeletException(e.getMessage(), e);
            }
            t.addSuppressed(e);
          }
        }
      }
      if (t != null) {
        throw t;
      }
    }
  }

  public final boolean closed() {
    return this.closed; // volatile read
  }

  @Override
  public final int hashCode() {
    // We don't want "closedness" to factor in here because it isn't part of equals().  But we want to use the results
    // of get().  Fortunately, that method is final.  So we can just use direct field access.
    return this.object.hashCode();
  }

  @Override
  public final boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other != null && this.getClass() == other.getClass()) {
      // We don't want "closedness" to factor in here because it isn't part of hashCode().  But we want to use the
      // results of get().  Fortunately, that method is final.  So we can just use direct field access.
      return Objects.equals(this.object, ((Instance<?>)other).object);
    } else {
      return false;
    }
  }

  @Override
  public final String toString() {
    return String.valueOf(this.get());
  }

  public static interface Destructor<I> {

    public void destroy(final I i, final AutoCloseable acr, final Creation<I> c, final ReferenceSelector rs);

  }

}
