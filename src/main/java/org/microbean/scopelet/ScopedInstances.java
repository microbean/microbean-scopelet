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
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.lang.model.type.TypeMirror;

import org.microbean.assign.AttributedType;
import org.microbean.assign.Selectable;

import org.microbean.attributes.Attributed;
import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;

import org.microbean.bean.AmbiguousResolutionException;
import org.microbean.bean.Bean;
import org.microbean.bean.Creation;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.Qualifiers;
import org.microbean.bean.ReferencesSelector;

import org.microbean.construct.Domain;

import org.microbean.reference.Instances;

import static java.util.Objects.requireNonNull;

/**
 * An {@link Instances} implementation that is based on scopes.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #supplier(Bean, Creation)
 *
 * @see Instances
 */
public class ScopedInstances implements Instances {


  /*
   * Static fields.
   */


  // Note: deliberately not a scope or qualifier
  private static final Attributes CONSIDER_ACTIVENESS = Attributes.of("ConsiderActiveness");
  

  /*
   * Instance fields.
   */


  private final Qualifiers qualifiers;

  private final Scopes scopes;
  
  private final TypeMirror scopeletType;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ScopedInstances}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param qualifiers a {@link Qualifiers}; must not be {@code null}
   *
   * @param scopes a {@link Scopes}; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public ScopedInstances(final Domain domain, final Qualifiers qualifiers, final Scopes scopes) {
    super();
    this.qualifiers = requireNonNull(qualifiers, "qualifiers");
    this.scopes = requireNonNull(scopes, "scopes");
    this.scopeletType = scopeletType(domain);
  }


  /*
   * Instance methods.
   */


  /**
   * Returns {@code true} if and only if the supplied {@link Id} is <dfn>proxiable</dfn>.
   *
   * @param id an {@link Id}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Id} is <dfn>proxiable</dfn>
   *
   * @exception NullPointerException if {@code id} is {@code null}
   */
  @Override // Instances
  public boolean proxiable(final Id id) {
    if (!id.types().proxiable()) {
      return false;
    }
    final Attributes scopeId = this.findScope(id);
    return scopeId != null && this.scopes.normal(scopeId);
  }

  @Override // Instances
  public final <I> Supplier<? extends I> supplier(final Bean<I> bean, final Creation<I> request) {
    final Id id = bean.id();
    final Attributes scopeId = this.findScope(id);
    // In this implementation, all Ids must have scopes.
    if (scopeId == null) {
      throw new IllegalStateException();
    }
    final Factory<I> factory = bean.factory();
    if (factory instanceof Scopelet<?> && this.primordial(scopeId)) {
      // This is a request for, e.g., the Singleton Scopelet, which backs the primordial (notional) singleton scope.
      // Scopelets are always their own factories. The Scopelet implementing the primordial scope (normally Singleton)
      // is not made or stored by any other Scopelet.
      I scopelet = factory.singleton();
      if (scopelet == null) {
        return () -> factory.create(request);
      }
      assert scopelet == factory : "scopelet != factory: " + scopelet + " != " + factory;
      return factory::singleton;
    }
    final AttributedType st = this.scopeletAttributedType(scopeId);
    // Get the Scopelet and have it provide the instance
    return () -> request.<Scopelet<?>>reference(st).instance(id, factory, request); // assumes Scopelet inactivity is handled
  }

  /*
   * Returns {@code true} if and only if the supplied {@link Attributes} is deemed to be an identifier of a
   * <dfn>scope</dfn>.
   *
   * @param a an {@link Attributes}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Attributes} is deemed to be an identifier of a scope
   *
   * @exception NullPointerException if {@code a} is {@code null}
   *
   * @see Scopes#scope(Attributes)
   *
   * @deprecated Use {@link Scopes#scope(Attributes)} instead.
   */
  // @Deprecated(forRemoval = true)
  // protected boolean isScopeId(final Attributes a) {
  //   return this.scopes.scope(a);
  // }

  /**
   * Returns {@code true} if and only if the supplied {@link Collection} of {@link Attributes} is deemed to designate
   * something as <dfn>primordial</dfn>.
   *
   * <p>The default implementation of this method returns {@code true} if and only if the supplied {@link Collection}
   * {@linkplain Collection#contains(Object) contains} the {@linkplain
   * org.microbean.bean.Qualifiers#primordialQualifier() primordial qualifier}.</p>
   *
   * @param c a {@link Collection}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Collection} of {@link Attributes} is deemed to designate
   * something as <dfn>primordial</dfn>
   *
   * @exception NullPointerException if {@code c} is {@code null}
   *
   * @see Qualifiers#primordialQualifier()
   */
  protected boolean primordial(final Collection<? extends Attributes> c) {
    return c.contains(this.qualifiers.primordialQualifier());
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
   *
   * @see Scopes#findScope(Collection)
   *
   * @deprecated Please use {@link Scopes#findScope(Collection)} instead.
   */
  @Deprecated(forRemoval = true)
  final Attributes findScopeId(final Collection<? extends Attributes> c) {
    return this.scopes.findScope(c);
  }

  private final Attributes findScope(final Id id) {
    // Looks for an Any qualifier, which every bean must possess, and then looks on *it* for the scope. This allows us
    // to "tunnel" scopes (which are Qualifiers in this implementation) without disrupting typesafe resolution, since
    // meta-attributes are not part of an Attributes' equality computation.
    final Object anyQualifier = this.qualifiers.anyQualifier();
    Attributes scopeId = null;
    for (final Attributes a : id.attributes()) {
      if (a.equals(anyQualifier)) {
        scopeId = this.scopes.findScope(a.attributes());
        break;
      }
    }
    if (scopeId == null) {
      throw new IllegalArgumentException("id: " + id);
    }
    return scopeId;
  }

  private final boolean primordial(final Attributed a) {
    return this.primordial(a.attributes());
  }

  private final AttributedType scopeletAttributedType(final Attributes scopeId) {
    return AttributedType.of(this.scopeletType, scopeId, CONSIDER_ACTIVENESS);
  }


  /*
   * Static methods.
   */


  /**
   * Returns a {@link Selectable Selectable&lt;AttributedType, Bean&lt;?&gt;&gt;} that properly considers the fact that
   * a {@link Scopelet} may be {@linkplain Scopelet#active() active or inactive} at any point for any reason.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param selectable a {@link Selectable} that will be used for all {@link AttributedType}s other than {@link
   * Scopelet} types being sought for the purpose of instantiating or acquiring contextual instances; must not be {@code
   * null}
   *
   * @return a non-{@code null} {@link Selectable}
   *
   * @exception  NullPointerException if any argument is {@code null}
   */
  public static final Selectable<AttributedType, Bean<?>> selectableOf(final Domain domain,
                                                                       final Selectable<AttributedType, Bean<?>> selectable) {
    Objects.requireNonNull(selectable, "selectable");
    final Selectable<AttributedType, Bean<?>> scopeletSelectable = c -> {
      Bean<?> activeScopeletBean = null;
      for (final Bean<?> b : selectable.select(c)) {
        if (((Scopelet<?>)b.factory()).active()) {
          if (activeScopeletBean == null) {
            activeScopeletBean = b;
          } else {
            throw new TooManyActiveScopeletsException("scopelet1: " + activeScopeletBean + "; scopelet2: " + b);
          }
        }
      }
      return activeScopeletBean == null ? List.of() : List.of(activeScopeletBean);
    };
    final TypeMirror scopeletType = scopeletType(domain);
    return c ->
      domain.sameType(scopeletType, c.type()) && c.attributes().contains(CONSIDER_ACTIVENESS) ?
      // A ScopedInstances is requesting a Scopelet for the purposes of instantiating something else. Use the
      // scopeletSelectable.
      scopeletSelectable.select(c) :
      // A ScopedInstances is requesting something "normal". Use the unadorned supplied Selectable.
      selectable.select(c);
  }

  // Invoked by method reference only
  // (Actually, not used?)
  @Deprecated(forRemoval = true)
  private static final Bean<?> handleInactiveScopelets(final Collection<? extends Bean<?>> beans, final AttributedType attributedType) {
    if (beans.size() < 2) { // 2 because we're disambiguating
      throw new IllegalArgumentException("beans: " + beans);
    }
    Bean<?> b2 = null;
    Scopelet<?> s2 = null;
    final Iterator<? extends Bean<?>> i = beans.iterator();
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
      } else {
        s2 = null;
        b2 = null;
        break;
      }
    }
    if (s2 == null) {
      throw new AmbiguousResolutionException(attributedType,
                                             beans,
                                             "TODO: this message needs to be better; can't resolve these alternates: " + beans);
    }
    assert b2 != null;
    return b2;
  }

  private static final TypeMirror scopeletType(final Domain domain) {
    return domain.declaredType(null, domain.typeElement(Scopelet.class.getCanonicalName()), domain.wildcardType());
  }

}
