/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2025 microBean™.
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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;

import java.util.List;
import java.util.Map;

import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;
import org.microbean.attributes.Value;

import org.microbean.bean.Bean;
import org.microbean.bean.Creation;
import org.microbean.bean.Factory;

import static java.lang.invoke.MethodHandles.lookup;

import static org.microbean.assign.Qualifiers.primordialQualifier;
import static org.microbean.assign.Qualifiers.qualifier;

/**
 * A manager of object lifespans on behalf of one or more notional <dfn>scopes</dfn>.
 *
 * @param <S> the {@link Scopelet} subtype extending this class
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #instance(Object, Factory, Creation)
 */
public abstract class Scopelet<S extends Scopelet<S>> implements AutoCloseable, Factory<S> {


  /*
   * Static fields.
   */


  private static final VarHandle CLOSED;

  private static final VarHandle ME;

  static {
    final Lookup lookup = lookup();
    try {
      CLOSED = lookup.findVarHandle(Scopelet.class, "closed", boolean.class);
      ME = lookup.findVarHandle(Scopelet.class, "me", Scopelet.class);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * An {@link Attributes} identifying the <dfn>scope designator</dfn>.
   */
  public static final Attributes SCOPE = Attributes.of("Scope");

  private static final Map<String, Value<?>> normalScope = Map.of("normal", BooleanValue.of(true));

  private static final Map<String, Value<?>> pseudoScope = Map.of("normal", BooleanValue.of(false));

  /**
   * An {@link Attributes} identifying the (well-known) <dfn>singleton pseudo-scope</dfn>.
   *
   * <p>The {@link Attributes} constituting the singleton pseudo-scope identifier is {@linkplain Attributes#attributes()
   * attributed} with {@linkplain #SCOPE the scope designator}, {@linkplain org.microbean.assign.Qualifiers#qualifier()
   * the qualifier designator}, and {@linkplain org.microbean.assign.Qualifiers#primordialQualifier() the primordial
   * qualifier}, indicating that the scope it identifies governs itself.</p>
   */
  public static final Attributes SINGLETON_ID =
    Attributes.of("Singleton", pseudoScope, Map.of(), Map.of("Singleton", List.of(qualifier(), SCOPE, primordialQualifier())));

  /**
   * An {@link Attributes} identifying the (well-known and <dfn>normal</dfn>) <dfn>application scope</dfn>.
   */
  public static final Attributes APPLICATION_ID =
    Attributes.of("Application", normalScope, Map.of(), Map.of("Application", List.of(qualifier(), SCOPE, SINGLETON_ID)));

  /**
   * An {@link Attributes} identifying the (well-known) <dfn>none pseudo-scope</dfn>.
   */
  public static final Attributes NONE_ID =
    Attributes.of("None", pseudoScope, Map.of(), Map.of("None", List.of(qualifier(), SCOPE, SINGLETON_ID)));


  /*
   * Instance fields.
   */


  private volatile S me;

  private volatile boolean closed;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Scopelet}.
   */
  protected Scopelet() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Creates this {@link Scopelet} by simply returning it.
   *
   * @return this {@link Scopelet}
   */
  @Override // Factory<S>
  @SuppressWarnings("unchecked")
  public final S create(final Creation<S> r) {
    if (ME.compareAndSet(this, null, this)) { // volatile write
      if (r != null) {
        // TODO: emit initialized event
      }
    }
    return (S)this;
  }

  /**
   * Returns this {@link Scopelet} if it has been created via the {@link #create(Creation)} method, or {@code null} if
   * that method has not yet been invoked.
   *
   * @return this {@link Scopelet} if it has been "{@linkplain #create(Creation) created}"; {@code null} otherwise
   *
   * @see #create(Creation)
   */
  @Override // Factory<S>
  public final S singleton() {
    return this.me; // volatile read
  }

  /**
   * Returns {@code true} when invoked to indicate that {@link Scopelet} implementations {@linkplain
   * Factory#destroy(Object, org.microbean.bean.Destruction) destroy} what they {@linkplain #create(Creation) create}.
   *
   * @return {@code true} when invoked
   *
   * @see Factory#destroy(Object, org.microbean.bean.Destruction)
   *
   * @see #create(Creation)
   */
  @Override // Factory<S>
  public final boolean destroys() {
    return true;
  }


  /*
   * Repository-like concerns.
   */


  /**
   * Returns {@code true} if and only if this {@link Scopelet} is <dfn>active</dfn> at the moment of the call.
   *
   * <p>Overrides of this method must ensure that if {@link #closed()} returns {@code true}, this method must return
   * {@code false}.</p>
   *
   * @return {@code true} if and only if this {@link Scopelet} is <dfn>active</dfn> at the moment of the call
   *
   * @see #closed()
   */
  public boolean active() {
    return !this.closed(); // volatile read
  }

  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then returns a pre-existing or
   * created-on-demand contextual instance suitable for the combination of identifier, {@link Factory} and {@link
   * Creation}, or {@code null}
   *
   * @param <I> the type of contextual instance
   *
   * @param id an identifier that can identify a contextual instance; may be {@code null}
   *
   * @param factory a {@link Factory}; may be {@code null}
   *
   * @param creation a {@link Creation}, typically the one in effect that is causing this method to be invoked in the
   * first place; may be {@code null}
   *
   * @return a contextual instance, possibly pre-existing, or possibly created just in time, or {@code null}
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   *
   * @exception ClassCastException if {@code creation} is non-{@code null} and does not implement {@link
   * org.microbean.bean.Destruction}, a requirement of its contract
   *
   * @see Creation
   */
  public abstract <I> I instance(final Object id, final Factory<I> factory, final Creation<I> creation);

  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then removes any contextual instance
   * stored under the supplied {@code id}, returning {@code true} if and only if removal actually took place.
   *
   * <p><strong>The default implementation of this method always returns {@code false}.</strong> Subclasses are
   * encouraged to override it as appropriate.</p>
   *
   * @param id an identifier; may be {@code null}
   *
   * @return {@code true} if and only if removal actually occurred
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   */
  public boolean remove(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return false;
  }

  /**
   * Irrevocably closes this {@link Scopelet}, and, by doing so, notionally makes it irrevocably {@linkplain #closed()
   * closed} and {@linkplain #active() inactive}.
   *
   * <p>Overrides of this method <strong>must</strong> call {@link Scopelet#close() super.close()} as part of their
   * implementation or undefined behavior may result.</p>
   *
   * @see #closed()
   *
   * @see #active()
   */
  @Override // AutoCloseable
  public void close() {
    CLOSED.compareAndSet(this, false, true); // volatile write
  }

  /**
   * Returns {@code true} if and only if at the moment of invocation this {@link Scopelet} is (irrevocably) closed (and
   * therefore also {@linkplain #active() not active}).
   *
   * @return {@code true} if and only if at the moment of invocation this {@link Scopelet} is (irrevocably) closed (and
   * therefore also {@linkplain #active() not active})
   *
   * @see #active()
   */
  protected final boolean closed() {
    return this.closed; // volatile read
  }

}
