/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2025–2026 microBean™.
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

import javax.lang.model.AnnotatedConstruct;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import javax.lang.model.type.TypeMirror;

import org.microbean.assign.Annotated;
import org.microbean.assign.Selectable;

import org.microbean.bean.AmbiguousResolutionException;
import org.microbean.bean.Bean;
import org.microbean.bean.Creation;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.ReferencesSelector;

import org.microbean.construct.Domain;

import org.microbean.construct.element.SyntheticAnnotationMirror;
import org.microbean.construct.element.SyntheticAnnotationTypeElement;

import org.microbean.construct.type.UniversalType;

import org.microbean.reference.Instances;

import static java.util.Objects.requireNonNull;

import static org.microbean.construct.element.AnnotationMirrors.sameAnnotation;

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
   * Instance fields.
   */

  
  private final Domain domain;
  
  private final org.microbean.bean.Qualifiers bq;

  private final Qualifiers sq;

  private final Scopes scopes;
  
  private final TypeMirror scopeletType;

  // Deliberately not a scope or a qualifier
  private final AnnotationMirror considerActiveness;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ScopedInstances}.
   *
   * @param domain a non-{@code null} {@link Domain}
   *
   * @param bq a non-{@code null} {@link org.microbean.bean.Qualifiers}
   *
   * @param sq a non-{@code null} {@link Qualifiers}
   *
   * @param scopes a non-{@code null} {@link Scopes}
   *
   * @param considerActiveness an {@link AnnotationMirror} used to signal that <dfn>activeness</dfn> should be taken
   * into consideration during typesafe resolution; may be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public ScopedInstances(final Domain domain,
                         final org.microbean.bean.Qualifiers bq,
                         final Qualifiers sq,
                         final Scopes scopes,
                         final AnnotationMirror considerActiveness) {
    super();
    this.domain = domain;
    this.scopeletType =
      domain.declaredType(null, domain.typeElement(Scopelet.class.getCanonicalName()), domain.wildcardType());
    this.scopes = requireNonNull(scopes, "scopes");
    this.bq = bq;
    this.sq = requireNonNull(sq, "sq");
    this.considerActiveness =
      considerActiveness == null ?
      new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement("ConsiderActiveness")) :
      considerActiveness;
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
    return id.types().proxiable() && this.findNormalScope(id) != null;
  }

  /**
   * Returns a {@link Selectable Selectable&lt;AnnotatedConstruct, Bean&lt;?&gt;&gt;} that properly considers the fact
   * that a {@link Scopelet} may be {@linkplain Scopelet#active() active or inactive} at any point for any reason.
   *
   * @param selectable a {@link Selectable} that will be used for all {@link AnnotatedConstruct}s other than {@link
   * Scopelet} types being sought for the purpose of instantiating or acquiring contextual instances; must not be
   * {@code null}
   *
   * @return a non-{@code null} {@link Selectable}
   *
   * @exception  NullPointerException if any argument is {@code null}
   */
  public final Selectable<Annotated<? extends AnnotatedConstruct>, Bean<?>> selectableOf(final Selectable<? super Annotated<? extends AnnotatedConstruct>, Bean<?>> selectable) {
    requireNonNull(selectable, "selectable");
    final Selectable<Annotated<? extends AnnotatedConstruct>, Bean<?>> scopeletSelectable = aac -> {
      Bean<?> activeScopeletBean = null;
      for (final Bean<?> b : selectable.select(aac)) {
        if (((Scopelet<?>)b.factory()).active()) {
          if (activeScopeletBean == null) {
            activeScopeletBean = b;
          } else {
            throw new TooManyActiveScopeletsException("scopelet1: " + activeScopeletBean + "; scopelet: " + b);
          }
        }
      }
      return activeScopeletBean == null ? List.of() : List.of(activeScopeletBean);
    };
    return aac ->
      this.domain.sameType(this.scopeletType, type(aac)) && this.considerActiveness(aac.annotations()) ?
      // A ScopedInstances is requesting a Scopelet for the purposes of instantiating something else. Use the
      // scopeletSelectable.
      scopeletSelectable.select(aac) :
      // A ScopedInstances is requesting something "normal". Use the unadorned supplied Selectable.
      selectable.select(aac);
  }

  private static final TypeMirror type(final Annotated<? extends AnnotatedConstruct> a) {
    final AnnotatedConstruct ac = a.annotated();
    if (ac instanceof TypeMirror t) {
      return t;
    }
    return ((Element)ac).asType();
  }

  @Override // Instances
  public final <I> Supplier<? extends I> supplier(final Bean<I> bean, final Creation<I> request) {
    final Id id = bean.id();
    final AnnotationMirror scopeId = this.findScope(id);
    // In this implementation, all ids must have scopes.
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
    final Annotated<TypeMirror> ast = this.annotatedScopeletType(scopeId);
    // Get the Scopelet and have it provide the instance
    return () -> {
      return request.<Scopelet<?>>reference(ast)
        .instance(id, factory, request); // assumes Scopelet inactivity is handled
    };
  }

  /**
   * Returns {@code true} if and only if the supplied {@link Collection} of {@link AnnotationMirror}s is deemed to
   * designate something as <dfn>primordial</dfn>.
   *
   * <p>The default implementation of this method returns {@code true} if and only if the supplied {@link Collection}
   * contains an {@link AnnotationMirror} that is the {@linkplain
   * org.microbean.construct.element.AnnotationMirrors#sameAnnotation(AnnotationMirror, AnnotationMirror) same
   * annotation} as the {@link org.microbean.bean.Qualifiers#primordialQualifier() primordial qualifier}.</p>
   *
   * @param c a {@link Collection} of {@link AnnotationMirror}s; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Collection} of {@link AnnotationMirror}s is deemed to
   * designate something as <dfn>primordial</dfn>
   *
   * @exception NullPointerException if {@code c} is {@code null}
   *
   * @see Qualifiers#primordialQualifier()
   */
  private final boolean primordial(final Collection<? extends AnnotationMirror> c) {
    for (final AnnotationMirror a : c) {
      if (this.sq.primordialMetaQualifier(a)) {
        return true;
      }
    }
    return false;
  }

  private final boolean primordial(final AnnotationMirror a) {
    return this.primordial(a.getAnnotationType().asElement().getAnnotationMirrors());
  }

  private final AnnotationMirror findNormalScope(final Id id) {
    AnnotationMirror scopeId = null;
    for (final AnnotationMirror a : id.annotations()) {
      if (this.bq.anyQualifier(a)) {
        scopeId = this.scopes.findNormalScope(a.getAnnotationType().asElement().getAnnotationMirrors());
        break;
      }
    }
    return scopeId;
  }
  
  private final AnnotationMirror findScope(final Id id) {
    // Looks for an Any qualifier, which every bean must possess, and then looks on *it* for the scope. This allows us
    // to "tunnel" scopes (which are Qualifiers in this implementation) without disrupting typesafe resolution, since
    // meta-annotations are not part of an AnnotationMirror's equality computation.
    AnnotationMirror scopeId = null;
    for (final AnnotationMirror a : id.annotations()) {
      if (this.bq.anyQualifier(a)) {
        scopeId = this.scopes.findScope(a.getAnnotationType().asElement().getAnnotationMirrors());
        break;
      }
    }
    if (scopeId == null) {
      throw new IllegalArgumentException("id: " + id);
    }
    return scopeId;
  }

  private final Annotated<TypeMirror> annotatedScopeletType(final AnnotationMirror scopeId) {
    return Annotated.of(new UniversalType(List.of(scopeId, this.considerActiveness),
                                          this.scopeletType,
                                          this.domain));
  }

  private final boolean considerActiveness(final Collection<? extends AnnotationMirror> c) {
    for (final AnnotationMirror a : c) {
      if (sameAnnotation(this.considerActiveness, a)) {
        return true;
      }
    }
    return false;
  }

}
