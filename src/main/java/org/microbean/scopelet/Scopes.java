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
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.microbean.bean.Qualifiers;

import org.microbean.attributes.Attributes;
import org.microbean.attributes.BooleanValue;
import org.microbean.attributes.Value;

import static java.util.Objects.requireNonNull;

/**
 * A utility class for working with <dfn>scopes</dfn> and their identifiers.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class Scopes { // deliberately not final

  private static final Map<String, Value<?>> NORMAL = Map.of("normal", BooleanValue.of(true));

  private static final Map<String, Value<?>> PSEUDO = Map.of("normal", BooleanValue.of(false));

  private static final Attributes SCOPE = Attributes.of("Scope");

  private final Attributes NONE;

  private final Attributes SINGLETON;

  private final Qualifiers qualifiers;

  /**
   * Creates a new {@link Scopes}.
   *
   * @param qualifiers a {@link Qualifiers}; must not be {@code null}
   *
   * @exception NullPointerException if {@code qualifiers} is {@code null}
   */
  public Scopes(final Qualifiers qualifiers) {
    super();
    this.qualifiers = requireNonNull(qualifiers, "qualifiers");
    this.SINGLETON =
      Attributes.of("Singleton",
                    PSEUDO,
                    Map.of(),
                    Map.of("Singleton",
                           List.of(qualifiers.qualifier(),
                                   SCOPE,
                                   qualifiers.primordialQualifier())));
    this.NONE =
      Attributes.of("None",
                    PSEUDO,
                    Map.of(),
                    Map.of("None",
                           List.of(qualifiers.qualifier(),
                                   SCOPE,
                                   SINGLETON)));
  }

  /**
   * Returns the first {@link Attributes} that is present in the supplied {@link Collection} of {@link Attributes}, and
   * their {@linkplain Attributes#attributes() meta-attributes}, for which an invocation of the {@link
   * #scope(Attributes)} method returns {@code true}, or {@code null}, if no such {@link Attributes} exists.
   *
   * <p>The search is conducted in a breadth-first manner.</p>
   *
   * @param c a {@link Collection} of {@link Attributes}; must not be {@code null}
   *
   * @return the first {@link Attributes} that is present in the supplied {@link Collection} of {@link Attributes}, and
   * their {@linkplain Attributes#attributes() meta-attributes}, for which an invocation of the {@link
   * #scope(Attributes)} method returns {@code true}, or {@code null}, if no such {@link Attributes} exists
   *
   * @exception NullPointerException if {@code c} is {@code null}
   */
  public Attributes findScope(final Collection<? extends Attributes> c) {
    if (c.isEmpty()) {
      return null;
    }
    // Breadth first on purpose. Scope Attributes closer to the Attributes they attribute win over Scope Attributes
    // further away.
    final Queue<Attributes> q = new ArrayDeque<>(c);
    while (!q.isEmpty()) {
      final Attributes a = q.poll();
      if (this.scope(a)) {
        return a;
      }
      q.addAll(a.attributes());
    }
    return null;
  }

  /**
   * Returns a non-{@code null}, determinate {@link Attributes} representing the identifier for the <dfn>none</dfn>
   * scope.
   *
   * @return a non-{@code null}, determinate {@link Attributes} representing the identifier for the <dfn>none</dfn>
   * scope
   */
  public Attributes none() {
    return NONE;
  }

  /**
   * Returns a non-{@code null}, determinate, immutable {@link Map} of {@linkplain Map#size() size} {@code 1} that can
   * be used with a {@linkplain #scope() scope} to designate it as a <dfn>normal scope</dfn>.
   *
   * @return a non-{@code null}, determinate, immutable {@link Map} of {@linkplain Map#size() size} {@code 1} that can
   * be used with a {@linkplain #scope() scope} to designate it as a <dfn>normal scope</dfn>
   */
  public Map<String, Value<?>> normal() {
    return NORMAL;
  }

  /**
   * Returns {@code true} if the supplied {@link Attributes} has elements that might be used to indicate that it is a
   * <dfn>normal scope</dfn>.
   *
   * @param a an {@link Attributes}; must not be {@code null}
   *
   * @return {@code true} if the supplied {@link Attributes} has elements that might be used to indicate that it is a
   * <dfn>normal scope</dfn>
   *
   * @exception NullPointerException if {@code a} is {@code null}
   *
   * @see #scope(Attributes)
   *
   * @see #normal()
   */
  public boolean normal(final Attributes a) {
    final BooleanValue v = a.value(this.normal().keySet().iterator().next());
    return v != null && v.value();
  }

  /**
   * Returns a non-{@code null}, determinate, immutable {@link Map} of {@linkplain Map#size() size} {@code 1} that can
   * be used with a {@linkplain #scope() scope} to designate it as a <dfn>pseudo scope</dfn>.
   *
   * @return a non-{@code null}, determinate, immutable {@link Map} of {@linkplain Map#size() size} {@code 1} that can
   * be used with a {@linkplain #scope() scope} to designate it as a <dfn>pseudo scope</dfn>
   */
  public Map<String, Value<?>> pseudo() {
    return PSEUDO;
  }

  /**
   * Returns the non-{@code null}, determinate {@link Attributes} that can be used to designate other {@link Attributes}
   * as a <dfn>scope</dfn>.
   *
   * @return the non-{@code null}, determinate {@link Attributes} that can be used to designate other {@link Attributes}
   * as a <dfn>scope</dfn>
   */
  public Attributes scope() {
    return SCOPE;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link Attributes} is a <dfn>scope identifier</dfn>.
   *
   * @param a an {@link Attributes}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Attributes} is a <dfn>scope identifier</dfn>
   *
   * @exception NullPointerException if {@code a} is {@code null}
   */
  public boolean scope(final Attributes a) {
    boolean scopeFound = false;
    boolean qualifierFound = false;
    for (final Attributes ma : a.attributes()) {
      if (scopeFound) {
        if (!qualifierFound && ma.equals(this.qualifiers.qualifier())) {
          qualifierFound = true;
          break;
        }
      } else if (qualifierFound) {
        if (ma.equals(this.scope())) {
          scopeFound = true;
          break;
        }
      } else if (ma.equals(this.scope())) {
        // a is annotated with @Scope
        scopeFound = true;
      } else if (ma.equals(this.qualifiers.qualifier())) {
        // a is annotated with @Qualifier
        qualifierFound = true;
      }
    }
    return scopeFound && qualifierFound;
  }

  /**
   * Returns a non-{@code null}, determinate {@link Attributes} representing the identifier for the <dfn>singleton</dfn>
   * pseudo-scope.
   *
   * @return a non-{@code null}, determinate {@link Attributes} representing the identifier for the <dfn>singleton</dfn>
   * pseudo-scope
   */
  public Attributes singleton() {
    return SINGLETON;
  }

}
