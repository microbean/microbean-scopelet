/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2025 microBean™.
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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.lang.model.type.TypeMirror;

import org.microbean.attributes.Attributed;
import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;

import org.microbean.bean.AmbiguousReductionException;
import org.microbean.bean.AttributedType;
import org.microbean.bean.Bean;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.RankedReducer;
import org.microbean.bean.Reducer;
import org.microbean.bean.Reducible;
import org.microbean.bean.Request;
import org.microbean.bean.Selectable;
import org.microbean.bean.Reducer;

import org.microbean.construct.Domain;

import org.microbean.reference.Instances;

import static org.microbean.assign.Qualifiers.anyQualifier;
import static org.microbean.assign.Qualifiers.primordialQualifier;
import static org.microbean.assign.Qualifiers.qualifier;

/**
 * An {@link Instances} implementation that is based on scopes.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class ScopedInstances implements Instances {

  private static final Attributes FOR_INSTANTIATION = Attributes.of("ForInstantiation");

  private final TypeMirror scopeletType;

  /**
   * Creates a new {@link ScopedInstances}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   */
  public ScopedInstances(final Domain domain) {
    super();
    this.scopeletType = scopeletType(domain);
  }


  /*
   * Instance methods.
   */


  /**
   * Calls the {@link #findScopeId(Collection)} method with the result of an invocation of the {@link
   * Attributes#attributes()} method on the supplied {@link Attributes} and returns the result.
   *
   * @param a an {@link Attributes}; normally itself a scope; must not be {@code null}
   *
   * @return the first {@link Attributes} found in the supplied {@link Attributes}' {@linkplain Attributes#attributes()
   * attributes} that is a scope, or {@code null}
   *
   * @exception NullPointerException if {@code a} is {@code null}
   *
   * @see #findScopeId(Collection)
   */
  private final Attributes findScopeId(final Attributes a) {
    return this.findScopeId(a.attributes());
  }

  private final Attributes findScopeId(final Id id) {
    // Looks for an Any qualifier, which every bean must possess, and then looks on *it* for the scope. This allows us
    // to "tunnel" scopes (which are Qualifiers in this implementation) without disrupting typesafe resolution, since
    // meta-attributes are not part of an Attributes' equality computation.
    final Object anyQualifier = anyQualifier();
    Attributes scopeId = null;
    for (final Attributes a : id.attributes()) {
      if (a.equals(anyQualifier)) {
        scopeId = this.findScopeId(a);
        break;
      }
    }
    if (scopeId == null) {
      throw new IllegalArgumentException("id: " + id);
    }
    return scopeId;
  }
  
  /**
   * Finds and returns the <dfn>nearest</dfn> scope identifier in the forest represented by the supplied {@link
   * Attributes}.
   *
   * @param c a {@link Collection} of {@link Attributes}; must not be {@code null}
   *
   * @return the <dfn>nearest</dfn> scope identifier in the forest represented by the supplied {@link
   * Attributes}, or {@code null}
   *
   * @exception NullPointerException if {@code c} is {@code null}
   */
  protected Attributes findScopeId(final Collection<? extends Attributes> c) {
    if (c.isEmpty()) {
      return null;
    }
    // Breadth first on purpose. Scope Attributes closer to the Attributes they attribute win over Scope Attributes
    // further away.
    final Queue<Attributes> q = new ArrayDeque<>(c);
    while (!q.isEmpty()) {
      final Attributes a = q.poll();
      if (this.isScopeId(a)) {
        return a;
      }
      q.addAll(a.attributes());
    }
    return null;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link Attributes} is deemed to be an identifier of a
   * <dfn>scope</dfn>.
   *
   * @param a an {@link Attributes}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Attributes} is deemed to be an identifier of a scope
   *
   * @exception NullPointerException if {@code a} is {@code null}
   */
  protected boolean isScopeId(final Attributes a) {
    boolean scopeFound = false;
    boolean qualifierFound = false;
    for (final Attributes a0 : a.attributes()) {
      if (scopeFound) {
        if (!qualifierFound && a0.equals(qualifier())) {
          return true;
        }
      } else if (qualifierFound) {
        if (a0.equals(Scopelet.SCOPE)) {
          return true;
        }
      } else if (a0.equals(Scopelet.SCOPE)) {
        scopeFound = true;
      } else if (a0.equals(qualifier())) {
        qualifierFound = true;
      }
    }
    return false;
  }

  private final boolean normal(final Attributes a) {
    final BooleanValue v = a.value("normal");
    return v != null && v.value();
  }

  private final boolean primordial(final Attributed a) {
    return this.primordial(a.attributes());
  }
  
  /**
   * Returns {@code true} if and only if the supplied {@link Collection} of {@link Attributes} is deemed to designate
   * something as <dfn>primordial</dfn>.
   *
   * <p>The default implementation of this method returns {@code true} if and only if the supplied {@link Collection}
   * {@linkplain Collection#contains(Object) contains} the {@linkplain
   * org.microbean.assign.Qualifiers#primordialQualifier() primordial qualifier}.</p>
   *
   * @param c a {@link Collection}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Collection} of {@link Attributes} is deemed to designate
   * something as <dfn>primordial</dfn>
   *
   * @exception NullPointerException if {@code c} is {@code null}
   */
  protected boolean primordial(final Collection<? extends Attributes> c) {
    return c.contains(primordialQualifier());
  }

  /**
   * Returns {@code true} if and only if the supplied {@link Request} is deemed to be <dfn>primordial</dfn>.
   *
   * <p>A {@link Request} is normally primordial if it is {@code null} or if an invocation of its {@link
   * Request#primordial() primordial()} method returns {@code true}.</p>
   *
   * @param r a {@link Request}; may be {@code null} in which case {@code true} will be returned
   *
   * @return {@code true} if and only if the supplied {@link Request} is deemed to be primordial
   */
  protected boolean primordial(final Request<?> r) {
    return r == null || r.primordial();
  }

  /**
   * Returns {@code true} if and only if the supplied {@link Id} is <dfn>proxiable</dfn>.
   *
   * @param id an {@link Id}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Id} is <dfn>proxiable</dfn>
   *
   * @exception NullPointerException if {@code id} is {@code null}
   */
  protected boolean proxiable(final Id id) {
    if (!id.types().proxiable()) {
      return false;
    }
    final Attributes scopeId = this.findScopeId(id);
    return scopeId != null && this.normal(scopeId);
  }

  @Override // Instances
  public final boolean proxiable(final Request<?> r) {
    return !this.primordial(r) && this.proxiable(r.beanReduction().bean().id());
  }

  @Override // Instances
  public final <I> Supplier<? extends I> supplier(final Request<I> request) {
    if (this.primordial(request)) {
      // The supplied Request is a request for a Request, i.e. it's primordial, so return a Supplier that simply returns
      // the request.
      @SuppressWarnings("unchecked")
      final I instance = (I)request;
      return () -> instance;
    }
    final Bean<I> bean = request.beanReduction().bean();
    final Factory<I> factory = bean.factory();
    final Id id = bean.id();
    final Attributes scopeId = this.findScopeId(id);
    // In this implementation, all Ids must have scopes.
    if (scopeId == null) {
      throw new IllegalStateException();
    }
    if (factory instanceof Scopelet<?> && this.primordial(scopeId)) {
      // This is a request for, e.g., the Singleton Scopelet, which backs the primordial (notional) singleton scope.
      // Scopelets are always their own factories. The Scopelet implementing the primordial scope (normally Singleton)
      // is not made or stored by any other Scopelet.
      final I scopelet = factory.singleton();
      if (scopelet == null) {
        return () -> factory.create(request);
      }
      assert scopelet == factory : "scopelet != factory: " + scopelet + " != " + factory;
      return factory::singleton;
    }
    final AttributedType t = AttributedType.of(this.scopeletType, findScopeId(scopeId), FOR_INSTANTIATION);
    return () -> request.<Scopelet<?>>reference(t).instance(id, factory, request); // assumes a specific kind of reduction; see #reducible
  }


  /*
   * Static methods.
   */


  // Invoked by method reference only
  static final Bean<?> handleInactiveScopelets(final Collection<? extends Bean<?>> beans, final AttributedType attributedType) {
    if (beans.size() < 2) { // 2 because we're disambiguating
      throw new IllegalArgumentException("beans: " + beans);
    }
    Bean<?> b2 = null;
    Scopelet<?> s2 = null;
    final Iterator<? extends Bean<?>> i = beans.iterator(); // we use Iterator for good reasons
    while (i.hasNext()) {
      final Bean<?> b1 = i.next();
      if (b1.factory() instanceof Scopelet<?> s1) {
        if (s2 == null) {
          assert b2 == null;
          if (i.hasNext()) {
            b2 = i.next();
            if (b2.factory() instanceof Scopelet<?> s) {
              s2 = s;
            } else {
              s2 = null;
              b2 = null;
              break;
            }
          } else {
            s2 = s1;
            b2 = b1;
            break;
          }
        }
        assert b2 != null;
        // if (s2.scopeId().equals(s1.scopeId())) { // TODO: would like to make this go away
          if (s2.active()) {
            if (s1.active()) {
              throw new TooManyActiveScopeletsException("scopelet1: " + s1 + "; scopelet2: " + s2);
            }
            // drop s1; keep s2
          } else if (s1.active()) {
            // drop s2; keep s1
            s2 = s1;
            b2 = b1;
          } else {
            // both are inactive; drop 'em both and keep going
            s2 = null;
            b2 = null;
          }
        // } else {
        //   s2 = null;
        //   b2 = null;
        //   break;
        // }
      } else {
        s2 = null;
        b2 = null;
        break;
      }
    }
    if (s2 == null) {
      throw new AmbiguousReductionException(attributedType,
                                            beans,
                                            "TODO: this message needs to be better; can't resolve these alternates: " + beans);
    }
    assert b2 != null;
    return b2;
  }

  static final TypeMirror scopeletType(final Domain domain) {
    return domain.declaredType(null, domain.typeElement(Scopelet.class.getCanonicalName()), domain.wildcardType());
  }

  /**
   * Returns a {@link Reducible} suitable for use with {@link Scopelet}s.
   *
   * @param domain a {@link Domain} (that is normally shared among other cooperating components); must not be {@code null}
   *
   * @param selectable a {@link Selectable}; must not be {@code null}
   *
   * @return a non-{@code null} {@link Reducible}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see #reducible(Domain, Selectable, Reducer)
   *
   * @see RankedReducer#of()
   */
  public static final Reducible<AttributedType, Bean<?>> reducible(final Domain domain,
                                                                   final Selectable<AttributedType, Bean<?>> selectable) {
    return reducible(domain, selectable, RankedReducer.of());
  }

  /**
   * Returns a {@link Reducible} suitable for use with {@link Scopelet}s.
   *
   * @param domain a {@link Domain} (that is normally shared among other cooperating components); must not be {@code null}
   *
   * @param selectable a {@link Selectable}; must not be {@code null}
   *
   * @param reducer a {@link Reducer}; must not be {@code null}
   *
   * @return a non-{@code null} {@link Reducible}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see #reducible(Domain, Selectable, Reducer, BiFunction)
   *
   * @see Reducer#fail(List, Object)
   */
  public static final Reducible<AttributedType, Bean<?>> reducible(final Domain domain,
                                                                   final Selectable<AttributedType, Bean<?>> selectable,
                                                                   final Reducer<AttributedType, Bean<?>> reducer) {
    return reducible(domain, selectable, reducer, Reducer::fail);
  }

  /**
   * Returns a {@link Reducible} suitable for use with {@link Scopelet}s.
   *
   * @param domain a {@link Domain} (that is normally shared among other cooperating components); must not be {@code null}
   *
   * @param selectable a {@link Selectable}; must not be {@code null}
   *
   * @param reducer a {@link Reducer}; must not be {@code null}
   *
   * @param failureHandler a {@link BiFunction} serving as the supplied {@link Reducer}'s <dfn>failure handler</dfn>;
   * must not be {@code null}
   *
   * @return a non-{@code null} {@link Reducible}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public static final Reducible<AttributedType, Bean<?>>
    reducible(final Domain domain,
              final Selectable<AttributedType, Bean<?>> selectable,
              final Reducer<AttributedType, Bean<?>> reducer,
              final BiFunction<? super List<? extends Bean<?>>, ? super AttributedType, ? extends Bean<?>> failureHandler) {
    // Normal reductions are cached.
    final Reducible<AttributedType, Bean<?>> cachingReducible = Reducible.ofCaching(selectable, reducer, failureHandler);
    // Reductions of scopelets can't be cached because a Scopelet may be active or inactive at any point for any reason.
    final Reducible<AttributedType, Bean<?>> scopeletReducible =
      Reducible.<AttributedType, Bean<?>>of(selectable, reducer, ScopedInstances::handleInactiveScopelets);
    final TypeMirror scopeletType = scopeletType(domain);
    return c ->
      (domain.sameType(scopeletType, c.type()) && c.attributes().contains(FOR_INSTANTIATION) ?
       // A ScopedInstances is requesting a Scopelet for the purposes of instantiating something else. Use the
       // scopeletReducible.
       scopeletReducible :
       // A ScopedInstances is requesting something "normal". Use the cachingReducible.
       cachingReducible)
      .reduce(c);
  }

}
