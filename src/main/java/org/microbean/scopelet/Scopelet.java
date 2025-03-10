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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;
import org.microbean.attributes.Value;

import org.microbean.bean.Bean;
import org.microbean.bean.Id;
import org.microbean.bean.Factory;
import org.microbean.bean.Request;

import org.microbean.attributes.Attributes;

import static org.microbean.assign.Qualifiers.primordialQualifier;
import static org.microbean.assign.Qualifiers.qualifier;

/**
 * A manager of object lifespans identified by a scope.
 *
 * @param <S> the {@link Scopelet} subtype extending this class
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public abstract class Scopelet<S extends Scopelet<S>> implements AutoCloseable, Factory<S> {


  /*
   * Static fields.
   */


  private static final VarHandle CLOSED;

  private static final VarHandle ME;

  static {
    final Lookup lookup = MethodHandles.lookup();
    try {
      CLOSED = lookup.findVarHandle(Scopelet.class, "closed", boolean.class);
      ME = lookup.findVarHandle(Scopelet.class, "me", Scopelet.class);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }


  //
  // Experimental; relocating/dismissing microbean-scope
  //


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


  //
  // End experimental
  //


  /*
   * Instance fields.
   */


  private volatile S me;

  private volatile boolean closed;

  private final Attributes scopeId;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Scopelet}.
   *
   * @param scopeId an {@link Attributes} identifying the scope being implemented; must not be {@code null}
   *
   * @exception NullPointerException if {@code scopeId} is {@code null}
   */
  protected Scopelet(final Attributes scopeId) {
    super();
    this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
  }


  /*
   * Instance methods.
   */


  /**
   * Returns an {@link Id} representing this {@link Scopelet}.
   *
   * <p>Implementations of this method must return determinate values.</p>
   *
   * @return an {@link Id} representing this {@link Scopelet}; never {@code null}
   *
   * @see #bean()
   */
  public abstract Id id();

  /**
   * A convenience method that eturns a {@link Bean} for this {@link Scopelet}.
   *
   * <p>This {@link Scopelet} will be used as the {@link Bean}'s {@linkplain Bean#factory() associated
   * <code>Factory</code>}. This {@link Scopelet}'s {@link #id() Id} will be used as the {@link Bean}'s {@linkplain
   * Bean#id() identifier}.</p>
   *
   * @return a {@link Bean} for this {@link Scopelet}; never {@code null}
   *
   * @see #id()
   */
  public final Bean<S> bean() {
    return new Bean<>(this.id(), this);
  }

  /**
   * Creates this {@link Scopelet} by simply returning it.
   *
   * @return this {@link Scopelet}
   */
  @Override // Factory<S>
  @SuppressWarnings("unchecked")
  public final S create(final Request<S> r) {
    if (ME.compareAndSet(this, null, this)) { // volatile write
      if (r != null) {
        // TODO: emit initialized event
      }
    }
    return (S)this;
  }

  /**
   * Returns this {@link Scopelet} if it has been created via the {@link #create(Request)} method, or {@code null} if
   * that method has not yet been invoked.
   *
   * @return this {@link Scopelet} if it has been "{@linkplain #create(Request) created}"; {@code null} otherwise
   *
   * @see #create(Request)
   */
  @Override // Factory<S>
  public final S singleton() {
    return this.me; // volatile read
  }

  /**
   * Returns {@code true} when invoked to indicate that {@link Scopelet} implementations {@linkplain
   * Factory#destroy(Object, Request) destroy} what they {@linkplain #create(Request) create}.
   *
   * @return {@code true} when invoked
   *
   * @see Factory#destroy(Object, Request)
   *
   * @see #create(Request)
   */
  @Override // Factory<S>
  public final boolean destroys() {
    return true;
  }

  /**
   * Returns a hashcode for this {@link Scopelet}.
   *
   * @return a hashcode for this {@link Scopelet}
   */
  @Override // Object
  public int hashCode() {
    int hashCode = 17;
    hashCode = 31 * hashCode + this.id().hashCode();
    hashCode = 31 * hashCode + this.scopeId().hashCode();
    return hashCode;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link Object} is not {@code null}, has the same class as this
   * {@link Scopelet}, has an {@link #id() Id} {@linkplain Id#equals(Object) equal to} that of this {@link Scopelet},
   * and a {@linkplain #scopeId() scope identifier} {@linkplain Attributes#equals(Object) equal to} that of this
   * {@link Scopelet}.
   *
   * @param other the {@link Object} to test; may be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Object} is not {@code null}, has the same class as this
   * {@link Scopelet}, has an {@link #id() Id} {@linkplain Id#equals(Object) equal to} that of this {@link Scopelet},
   * and a {@linkplain #scopeId() scope identifier} {@linkplain Attributes#equals(Object) equal to} that of this
   * {@link Scopelet}
   */
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


  /*
   * Repository-like concerns.
   */


  /**
   * Returns the {@link Attributes} that identifies this {@link Scopelet}'s affiliated scope.
   *
   * @return the {@link Attributes} that identifies this {@link Scopelet}'s affiliated scope; never {@code null}
   */
  public final Attributes scopeId() {
    return this.scopeId;
  }

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
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then returns {@code true} if and only
   * if, at the moment of an invocation, this {@link Scopelet} {@linkplain #active() is active} and already contains an
   * object identified by the supplied {@link Object}.
   *
   * <p>The default implementation of this method checks to see if this {@link Scopelet} {@linkplain #active() is
   * active}, and then {@code true} if and only if the result of invoking the {@link #instance(Object, Factory,
   * Request)} method with the supplied {@code id}, {@code null}, and {@code null} is not {@code null}.</p>
   *
   * <p>Subclasses are encouraged to override this method to be more efficient or to use a different algorithm.</p>
   *
   * @param id the {@link Object} serving as an identifier; may be {@code null} in certain pathological cases
   *
   * @return {@code true} if and only if, at the moment of an invocation, this {@link Scopelet} {@linkplain #active() is
   * active} and contains a preexisting object identified by the supplied {@link Object}
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   *
   * @see #active()
   *
   * @see #instance(Object, Factory, Request)
   */
  public boolean containsId(final Object id) {
    return (id instanceof Request<?> r ? this.instance(r) : this.instance(id, null, null)) != null;
  }

  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active}, and then returns the preexisting
   * contextual instance identified by the supplied {@link Object}, or {@code null} if no such instance exists.
   *
   * <p>This convenience method checks to see if this {@link Scopelet} {@linkplain #active() is active}, and then, if
   * the supplied {@link Object} is not a {@link Request}, calls the {@link #instance(Object, Factory, Request)} method
   * with the supplied {@code id}, {@code null}, and {@code null}, and returns its result.</p>
   *
   * <p>If the supplied {@link Object} <em>is</em> a {@link Request}, this method calls the {@link #instance(Request)}
   * method with the supplied (cast) {@code id} and returns its result.</p>
   *
   * @param <I> the type of contextual instance
   *
   * @param id an {@link Object} serving as an identifier; may be {@code null} in certain pathological cases
   *
   * @return the contextual instance identified by the supplied {@link Object}, or {@code null} if no such instance
   * exists
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   *
   * @see #instance(Object, Factory, Request)
   *
   * @see #instance(Request)
   */
  // id is nullable.
  @SuppressWarnings("unchecked")
  public final <I> I get(final Object id) {
    return id instanceof Request<?> r ? this.instance((Request<I>)r) : this.instance(id, null, null);
  }

  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then eturns a contextual instance
   * identified by the {@linkplain Request#beanReduction() identifying information} present within the supplied {@link
   * Request}, creating the instance and associating it with the {@linkplain Request#beanReduction() identifying
   * information} present within the supplied {@link Request} if necessary.
   *
   * @param <I> the type of contextual instance
   *
   * @param request a {@link Request}; may be {@code null} in which case the return value of an invocation of {@link
   * #instance(Object, Factory, Request)} with {@code null} supplied for all three arguments will be returned instead
   *
   * @return an appropriate contextual instance, or {@code null}
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   *
   * @see Request#beanReduction()
   *
   * @see #instance(Object, Factory, Request)
   */
  public final <I> I instance(final Request<I> request) {
    if (request == null) {
      return this.instance(null, null, null);
    }
    final Bean<I> bean = request.beanReduction().bean();
    return this.instance(bean.id(), bean.factory(), request);
  }

  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then returns a pre-existing or
   * created-on-demand contextual instance suitable for the combination of identifier, {@link Factory} and {@link
   * Request}.
   *
   * @param <I> the type of contextual instance
   *
   * @param id an identifier that can identify a contextual instance; may be {@code null}
   *
   * @param factory a {@link Factory}; may be {@code null}
   *
   * @param request a {@link Request}, typically the one in effect that is causing this method to be invoked in the
   * first place; may be {@code null}
   *
   * @return a contextual instance, possibly pre-existing, or possibly created just in time, or {@code null}
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   */
  // All parameters are nullable, perhaps pathologically. This helps permit super early bootstrapping.
  public abstract <I> I instance(final Object id, final Factory<I> factory, final Request<I> request);

  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then removes any contextual instance
   * stored under the supplied {@code id}, returning {@code true} if and only if removal actually took place.
   *
   * <p>The default implementation of this method always returns {@code false}. Subclasses are encouraged to override
   * it as appropriate.</p>
   *
   * @param id an identifier; may be {@code null}
   *
   * @return {@code true} if and only if removal actually occurred
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   */
  // id is nullable.
  public boolean remove(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return false;
  }

  /**
   * Irrevocably closes this {@link Scopelet}, and, by doing so, notionally makes it irrevocably {@linkplain #active()
   * inactive}.
   *
   * @see #closed()
   *
   * @see #active()
   */
  // Most scopelets will want to override this to do additional work. They must call super.close() to ensure #closed()
  // returns an appropriate value.
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
   */
  protected final boolean closed() {
    return this.closed; // volatile read
  }

}
