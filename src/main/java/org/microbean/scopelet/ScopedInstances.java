/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2024–2025 microBean™.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Supplier;

import javax.lang.model.type.TypeMirror;

import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;

import org.microbean.bean.AmbiguousReductionException;
import org.microbean.bean.AttributedType;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanReduction;
import org.microbean.bean.BeanTypeList;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.Reducer;
import org.microbean.bean.Reducible;
import org.microbean.bean.Request;
import org.microbean.bean.Selectable;
import org.microbean.bean.UnsatisfiedReductionException;

import org.microbean.construct.Domain;

import org.microbean.reference.Instances;

import static org.microbean.assign.Qualifiers.primordialQualifier;
import static org.microbean.assign.Qualifiers.qualifier;

/**
 * An {@link Instances} implementation that is based on scopes.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public final class ScopedInstances implements Instances {

  private final Domain domain;

  private final ConcurrentMap<AttributedType, Bean<?>> scopeletBeanCache;

  private final Reducible<AttributedType, Bean<?>> scopeletReducible;

  private final TypeMirror scopeletType;

  /**
   * Creates a new {@link ScopedInstances}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param selectable a {@link Selectable} used to select {@link Scopelet}s; must not be {@code null}
   *
   * @param reducer a {@link Reducer} used to disambiguate {@linkplain Scopelet#active() active} {@link Scopelet}s; must
   * not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public ScopedInstances(final Domain domain,
                         final Selectable<AttributedType, Bean<?>> selectable,
                         final Reducer<AttributedType, Bean<?>> reducer) {
    super();
    final ConcurrentMap<AttributedType, Bean<?>> scopeletBeanCache = new ConcurrentHashMap<>();
    this.scopeletBeanCache = scopeletBeanCache;
    this.scopeletReducible =
      Reducible.<AttributedType, Bean<?>>ofCaching(selectable, reducer, ScopedInstances::handleInactiveScopelets, scopeletBeanCache::computeIfAbsent);
    this.scopeletType = domain.declaredType(null, domain.typeElement(Scopelet.class.getCanonicalName()), domain.wildcardType());
    this.domain = domain;
  }

  @Override // Instances (AutoCloseable)
  public final void close() {
    this.scopeletBeanCache.clear();
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

  @Override // Instances
  public boolean proxiable(final Request<?> r) {
    if (r.primordial()) {
      return false;
    }
    final Id id = r.beanReduction().bean().id();
    if (!id.types().proxiable()) {
      return false;
    }
    final Attributes scopeId = this.findScopeId(id.attributes());
    return scopeId != null && this.normal(scopeId);
  }

  @Override // Instances (InstanceRemover)
  public final boolean remove(final Id id) {
    return id != null && this.scopelet(this.scopeletBeanReduction(this.findScopeId(id.attributes()))).remove(id);
  }

  // Called by #remove(Id)
  private final Scopelet<?> scopelet(final BeanReduction<Scopelet<?>> scopeletBeanReduction) {
    return this.scopelet(scopeletBeanReduction, null);
  }

  // Yes, you need the BeanReduction parameter, not just Bean, because you're going to call r.newChild(BeanReduction)
  private final Scopelet<?> scopelet(final BeanReduction<Scopelet<?>> scopeletBeanReduction, final Request<?> r) {
    final Bean<Scopelet<?>> scopeletBean = scopeletBeanReduction.bean();
    final BeanReduction<Scopelet<?>> governingScopeletBeanReduction =
      this.scopeletBeanReduction(this.findScopeId(scopeletBean.id().attributes()));
    if (scopeletBean.equals(governingScopeletBeanReduction.bean())) {
      final Factory<Scopelet<?>> scopeletFactory = scopeletBean.factory();
      final Scopelet<?> scopelet = scopeletFactory.singleton();
      return
        scopelet == null ?
        scopeletFactory.create(r == null ? null : r.child(scopeletBeanReduction)) :
        scopelet;
    }
    return scopelet(governingScopeletBeanReduction, r); // recurse
  }

  private final BeanReduction<Scopelet<?>> scopeletBeanReduction(final Attributes scopeId) {
    final AttributedType scopeletAttributedType = new AttributedType(this.scopeletType, List.of(scopeId));
    @SuppressWarnings("unchecked")
    final Bean<Scopelet<?>> scopeletBean = (Bean<Scopelet<?>>)this.scopeletReducible.reduce(scopeletAttributedType);
    if (scopeletBean == null) {
      throw new UnsatisfiedReductionException(scopeletAttributedType, null, null);
    }
    return new BeanReduction<>(scopeletAttributedType, scopeletBean);
  }

  @Override // Instances
  @SuppressWarnings("unchecked")
  public final <I> Supplier<? extends I> supplier(final Request<I> request) {
    if (request.primordial()) {
      final I instance = (I)request;
      return () -> instance;
    }
    final Bean<I> bean = request.beanReduction().bean();
    final Factory<I> factory = bean.factory();
    final I singleton = factory.singleton();
    if (singleton == null) {
      final Id id = bean.id();
      final BeanReduction<Scopelet<?>> scopeletBeanReduction = this.scopeletBeanReduction(this.findScopeId(id.attributes()));
      if (bean.equals(scopeletBeanReduction.bean())) {
        return () -> factory.create(request);
      }
      final Scopelet<?> scopelet = this.scopelet(scopeletBeanReduction, request);
      assert scopelet != null;
      return () -> scopelet.instance(id, factory, request);
    }
    return factory::singleton;
  }


  /*
   * Static methods.
   */


  // Invoked by method reference only
  private static final Bean<?> handleInactiveScopelets(final Collection<? extends Bean<?>> beans,
                                                       final AttributedType attributedType) {
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
        if (s2.scopeId().equals(s1.scopeId())) {
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

}
