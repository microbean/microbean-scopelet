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

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.microbean.bean.Destruction;
import org.microbean.bean.Id;

/**
 * An {@link AutoCloseable} pairing of an instance that can be
 * destroyed with a {@link Destruction} holding its dependent objects.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public final class Instance<I> implements AutoCloseable, Supplier<I> {

  private static final VarHandle CLOSED;

  static {
    final Lookup lookup = MethodHandles.lookup();
    try {
      CLOSED = lookup.findVarHandle(Instance.class, "closed", boolean.class);
    } catch (final NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
      throw (Error)new ExceptionInInitializerError(reflectiveOperationException.getMessage()).initCause(reflectiveOperationException);
    }
  }
  
  private final I object;

  private final Destruction releaser;

  private final BiConsumer<? super I, ? super Destruction> destroyer;

  private volatile boolean closed;

  public Instance(final I object,
                  final BiConsumer<? super I, ? super Destruction> destroyer,
                  final Destruction releaser) {
    super();
    this.object = object;
    this.releaser = releaser;
    this.destroyer = destroyer;
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
      Throwable t = null;
      try {
        if (this.destroyer != null) {
          this.destroyer.accept(this.object, this.releaser);
        }
      } catch (final RuntimeException | Error e) {
        t = e;
        throw e;
      } finally {
        if (this.releaser != null) {
          try {
            this.releaser.close();
          } catch (final RuntimeException | Error e) {
            if (t == null) {
              throw e;
            }
            t.addSuppressed(e);
          }
        }
      }
    }
  }

  public final boolean closed() {
    return this.closed; // volatile read
  }

  @Override
  public final int hashCode() {
    // We don't want "closedness" to factor in here because it isn't
    // part of equals().  But we want to use the results of get().
    // Fortunately, that method is final.  So we can just use direct
    // field access.
    return this.object.hashCode();
  }

  @Override
  public final boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other != null && this.getClass() == other.getClass()) {
      // We don't want "closedness" to factor in here because it isn't
      // part of hashCode().  But we want to use the results of get().
      // Fortunately, that method is final.  So we can just use direct
      // field access.
      return Objects.equals(this.object, ((Instance<?>)other).object);
    } else {
      return false;
    }
  }

  @Override
  public final String toString() {
    return String.valueOf(this.get());
  }

}
