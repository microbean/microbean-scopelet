/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2026 microBean™.
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

import org.microbean.bean.Creation;
import org.microbean.bean.Destruction;
import org.microbean.bean.Factory;
import org.microbean.bean.ReferencesSelector;

import static java.lang.invoke.MethodHandles.lookup;

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
   * @param c a {@link Creation}; <strong>may be {@code null}</strong> in certain primordial cases
   *
   * @return this {@link Scopelet}
   */
  @Override // Factory<S>
  @SuppressWarnings("unchecked")
  public final S create(final Creation<S> c) {
    if (this.closed()) {
      throw new IllegalStateException("closed");
    }
    if (ME.compareAndSet(this, null, this)) { // volatile write
      if (c != null) {
        this.fireScopeletInitialized(c);
      }
    }
    return (S)this;
  }

  @Override
  public final void destroy(final S me, final Destruction creation) {
    if (this.closed()) {
      throw new IllegalStateException("closed");
    }
    if (creation == null) {
      Factory.super.destroy(me, creation);
      this.me = null; // volatile write
      return;
    } else if (!(creation instanceof Creation<?>)) {
      throw new IllegalArgumentException("creation: " + creation);
    }
    final Creation<S> c = (Creation<S>)creation;
    this.fireScopeletDestroying(c);
    Factory.super.destroy(me, creation);
    this.me = null; // volatile write
    this.fireScopeletDestroyed(c);
  }

  /**
   * Informs any interested observers that this {@link Scopelet} is about to be destroyed.
   *
   * @param r a {@link ReferencesSelector}; must not be {@code null}
   *
   * @exception NullPointerException if {@code r} is {@code null}
   */
  protected void fireScopeletDestroying(final ReferencesSelector r) {

  }

  /**
   * Informs any interested observers that this {@link Scopelet} has been irrevocably destroyed.
   *
   * @param r a {@link ReferencesSelector}; must not be {@code null}
   *
   * @exception NullPointerException if {@code r} is {@code null}
   */
  protected void fireScopeletDestroyed(final ReferencesSelector r) {

  }

  /**
   * Informs any interested observers that this {@link Scopelet} has just been initialized.
   *
   * @param r a {@link ReferencesSelector}; must not be {@code null}
   *
   * @exception NullPointerException if {@code r} is {@code null}
   */
  // The specification says scopes should fire an event when they're open for business but there are lots of weird
  // ramifications to this. We break this out into a protected method so overrides can do what they want, or nothing at
  // all.
  protected void fireScopeletInitialized(final ReferencesSelector r) {
    // final Domain d = r.domain();
    // final Events e = r.reference(new AttributedType(d.declaredType(d.typeElement(Events.class.getCanonicalName())),
    //                                                 defaultQualifiers()));
    // if (e != null) {
    //   e.fire(null, // typeArgumentSource; not needed here; maybe could do wild S reflective introspection
    //          List.of(), // qualifiers/attributes; TODO: @Initialized
    //          this, // event object; can be anything
    //          c);
    // }
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
    if (this.closed()) {
      throw new IllegalStateException("closed");
    }
    return this.me; // volatile read
  }

  /**
   * Returns {@code true} when invoked to indicate that {@link Scopelet} implementations {@linkplain
   * Factory#destroy(Object, Destruction) destroy} what they {@linkplain #create(Creation) create}.
   *
   * @return {@code true} when invoked
   *
   * @see Factory#destroy(Object, Destruction)
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
   * @exception ClassCastException if destruction is called for, {@code creation} is non-{@code null}, and {@code
   * creation} does not implement {@link Destruction}, a requirement of its contract
   *
   * @see Creation
   *
   * @see Destruction
   *
   * @see Factory#destroys()
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
   * Returns {@code true} if and only if this {@link Scopelet} stores contextual instances, and hence is capable of
   * {@linkplain #remove(Object) removing} them.
   *
   * <p><strong>The default implementation of this method returns {@code false}.</strong> Subclasses are encouraged to
   * override it as appropriate.</p>
   *
   * @return {@code true} if and only if this {@link Scopelet} stores contextual instances, and hence is capable of
   * {@linkplain #remove(Object) removing} them
   *
   * @exception InactiveScopeletException if this {@link Scopelet} is not {@linkplain #active() active}
   *
   * @see #remove(Object)
   */
  public boolean removes() {
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
