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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;

// import org.microbean.bean.Qualifiers;

import org.microbean.construct.Domain;

import org.microbean.construct.element.SyntheticAnnotationMirror;
import org.microbean.construct.element.SyntheticAnnotationTypeElement;
import org.microbean.construct.element.SyntheticAnnotationValue;

import static java.util.Objects.requireNonNull;

import static java.util.function.Predicate.not;

import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;
import static javax.lang.model.element.ElementKind.METHOD;

import static org.microbean.construct.element.AnnotationMirrors.sameAnnotation;
import static org.microbean.construct.element.AnnotationMirrors.streamBreadthFirst;

/**
 * A utility class for working with <dfn>scopes</dfn> and their identifiers.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class Scopes { // deliberately not final

  private final AnnotationMirror metaNormalScope;
  
  private final AnnotationMirror metaPseudoScope;

  private final AnnotationMirror noneScope;

  private final AnnotationMirror singletonScope;

  private final org.microbean.assign.Qualifiers aq;
  
  private final Qualifiers sq;

  /**
   * Creates a new {@link Scopes}.
   *
   * @param d a non-{@code null} {@link Domain}
   *
   * @param aq a non-{@code null} {@link org.microbean.assign.Qualifiers}
   *
   * @param sq a non-{@code null} {@link Qualifiers}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see #Scopes(Domain, org.microbean.assign.Qualifiers, Qualifiers, AnnotationMirror, AnnotationMirror, AnnotationMirror,
   * AnnotationMirror)
   */
  public Scopes(final Domain d, final org.microbean.assign.Qualifiers aq, final Qualifiers sq) {
    this(d, aq, sq, null, null, null, null);
  }

  /**
   * Creates a new {@link Scopes}.
   *
   * @param aq a non-{@code null} {@link org.microbean.assign.Qualifiers}
   *
   * @param sq a non-{@code null} {@link Qualifiers}
   *
   * @param metaNormalScope a non-{@code null} {@link AnnotationMirror} designating another {@link AnnotationMirror} as
   * identifying a <dfn>normal scope</dfn>
   *
   * @param metaPseudoScope a non-{@code null} {@link AnnotationMirror} designating another {@link AnnotationMirror} as
   * identifying a <dfn>pseudo scope</dfn>
   *
   * @param singletonScope a non-{@code null} {@link AnnotationMirror} identifying the <dfn>singleton scope</dfn>
   *
   * @param noneScope a non-{@code null} {@link AnnotationMirror} identifying the <dfn>none scope</dfn>
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see #Scopes(Domain, org.mcirobean.assign.Qualifiers, Qualifires, AnnotationMirror, AnnotationMirror,
   * AnnotationMirror, AnnotationMirror)
   */
  public Scopes(final org.microbean.assign.Qualifiers aq,
                final Qualifiers sq,
                final AnnotationMirror metaNormalScope,
                final AnnotationMirror metaPseudoScope,
                final AnnotationMirror singletonScope,
                final AnnotationMirror noneScope) {
    this(null,
         aq,
         sq,
         requireNonNull(metaNormalScope, "metaNormalScope"),
         requireNonNull(metaPseudoScope, "metaPseudoScope"),
         requireNonNull(singletonScope, "singletonScope"),
         requireNonNull(noneScope, "noneScope"));
  }
  
  /**
   * Creates a new {@link Scopes}.
   *
   * @param d a {@link Domain}; if {@code null}, then {@code metaNormalScope}, {@code metaPseudoScope}, {@code
   * singletonScope}, and {@code noneScope} must be non-{@code null}
   *
   * @param aq a non-{@code null} {@link org.microbean.assign.Qualifiers}
   *
   * @param sq a non-{@code null} {@link Qualifiers}
   *
   * @param metaNormalScope an {@link AnnotationMirror} designating another {@link AnnotationMirror} as identifying a
   * <dfn>normal scope</dfn>; may be {@code null} in which case {@code d} must be non-{@code null}
   *
   * @param metaPseudoScope an {@link AnnotationMirror} designating another {@link AnnotationMirror} as identifying a
   * <dfn>pseudo scope</dfn>; may be {@code null} in which case {@code d} must be non-{@code null}
   *
   * @param singletonScope an {@link AnnotationMirror} identifying the <dfn>singleton scope</dfn>; may be {@code null}
   * in which case {@code d} must be non-{@code null}
   *
   * @param noneScope an {@link AnnotationMirror} identifying the <dfn>none scope</dfn>; may be {@code null} in which
   * case {@code d} must be non-{@code null}
   *
   * @exception NullPointerException if {@code aq} or {@code sq} is {@code null}, or if {@code d} is {@code null} in
   * certain situations
   */
  public Scopes(final Domain d,
                final org.microbean.assign.Qualifiers aq,
                final Qualifiers sq,
                final AnnotationMirror metaNormalScope,
                final AnnotationMirror metaPseudoScope,
                final AnnotationMirror singletonScope,
                final AnnotationMirror noneScope) {
    super();    
    this.aq = requireNonNull(aq, "aq");
    this.sq = requireNonNull(sq, "sq");
    if (metaNormalScope == null || metaPseudoScope == null || singletonScope == null || noneScope == null) {
      
      final List<? extends AnnotationMirror> as = d.typeElement("java.lang.annotation.Documented").getAnnotationMirrors();
      assert as.size() == 3; // @Documented, @Retention, @Target, in that order, all annotated in turn with each other

      final AnnotationMirror documentedAnnotation = as.get(0);
      final AnnotationMirror retentionAnnotation = as.get(1);
      final List<AnnotationMirror> documentedRetentionTarget =
        List.of(documentedAnnotation, // @Documented
                retentionAnnotation, // @Retention(TargetType.RUNTIME) (happens fortuitously to be RUNTIME)
                as.get(2)); // @Target(ANNOTATION_TYPE)
      
      this.metaNormalScope =
        metaNormalScope == null ?
        new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement(documentedRetentionTarget, "NormalScope")) :
        metaNormalScope;

      this.metaPseudoScope =
        metaPseudoScope == null ?
        new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement(documentedRetentionTarget, "Scope")) :
        metaPseudoScope;

      final List<SyntheticAnnotationValue> savs = new ArrayList<>(4);
      for (final Element e : d.typeElement("java.lang.annotation.ElementType").getEnclosedElements()) {
        if (e.getKind() == ENUM_CONSTANT && e instanceof VariableElement ve) {
          final Name n = e.getSimpleName();
          if (n.contentEquals("TYPE") || n.contentEquals("METHOD") || n.contentEquals("FIELD") || n.contentEquals("PARAMETER")) {
            savs.add(new SyntheticAnnotationValue(ve));
          }
        }
      }
      final List<AnnotationMirror> metaAnnotations =
        List.of(aq.metaQualifier(),
                this.metaPseudoScope,
                sq.primordialMetaQualifier(),
                retentionAnnotation,
                new SyntheticAnnotationMirror(d.typeElement("java.lang.annotation.Target"), Map.of("value", savs)),
                documentedAnnotation);
      
      this.singletonScope =
        singletonScope == null ?
        new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement(metaAnnotations, "Singleton")) :
        singletonScope;

      this.noneScope =
        noneScope == null ?
        new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement(metaAnnotations, "None")) :
        noneScope;
    } else {
      this.metaNormalScope = metaNormalScope;
      this.metaPseudoScope = metaPseudoScope;
      this.singletonScope = singletonScope;
      this.noneScope = noneScope;
    }
  }

  /**
   * Returns the first {@link AnnotationMirror} that is present in the supplied {@link Collection} of {@link
   * AnnotationMirror}s, and their {@linkplain Element#getAnnotationMirrors() meta-annotations}, for which an invocation
   * of the {@link #normalScope(AnnotationMirror)} method returns {@code true}, or {@code null}, if no such {@link
   * AnnotationMirror} exists.
   *
   * <p>The search is conducted in a breadth-first manner.</p>
   *
   * @param c a {@link Collection} of {@link AnnotationMirror}s; must not be {@code null}
   *
   * @return the first {@link AnnotationMirror} that is present in the supplied {@link Collection} of {@link
   * AnnotationMirror}s, and their {@linkplain Element#getAnnotationMirrors() meta-annotations}, for which an invocation
   * of the {@link #normalScope(AnnotationMirror)} method returns {@code true}, or {@code null}, if no such {@link
   * AnnotationMirror} exists
   *
   * @exception NullPointerException if {@code c} is {@code null}
   */
  public AnnotationMirror findNormalScope(final Collection<? extends AnnotationMirror> c) {
    return c.isEmpty() ? null : streamBreadthFirst(c)
      .dropWhile(not(this::normalScope))
      .findFirst()
      .orElse(null);
  }
  
  /**
   * Returns the first {@link AnnotationMirror} that is present in the supplied {@link Collection} of {@link
   * AnnotationMirror}s, and their {@linkplain Element#getAnnotationMirrors() meta-annotations}, for which an invocation
   * of the {@link #scope(AnnotationMirror)} method returns {@code true}, or {@code null}, if no such {@link
   * AnnotationMirror} exists.
   *
   * <p>The search is conducted in a breadth-first manner.</p>
   *
   * @param c a {@link Collection} of {@link AnnotationMirror}s; must not be {@code null}
   *
   * @return the first {@link AnnotationMirror} that is present in the supplied {@link Collection} of {@link
   * AnnotationMirror}s, and their {@linkplain Element#getAnnotationMirrors() meta-annotations}, for which an invocation
   * of the {@link #scope(AnnotationMirror)} method returns {@code true}, or {@code null}, if no such {@link
   * AnnotationMirror} exists
   *
   * @exception NullPointerException if {@code c} is {@code null}
   */
  public AnnotationMirror findScope(final Collection<? extends AnnotationMirror> c) {
    return c.isEmpty() ? null : streamBreadthFirst(c)
      .dropWhile(not(this::scope))
      .findFirst()
      .orElse(null);
  }

  /**
   * Returns a non-{@code null}, determinate {@link AnnotationMirror} representing the identifier for the <dfn>none</dfn>
   * scope.
   *
   * @return a non-{@code null}, determinate {@link AnnotationMirror} representing the identifier for the <dfn>none</dfn>
   * scope
   */
  public final AnnotationMirror noneScope() {
    return this.noneScope;
  }

  /**
   * Returns {@code true} if the supplied {@link AnnotationMirror} has elements that might be used to indicate that it
   * is a <dfn>normal scope</dfn>.
   *
   * @param a a non-{@code null}, determinate {@link AnnotationMirror}
   *
   * @return {@code true} if the supplied {@link AnnotationMirror} has elements that might be used to indicate that it
   * is a <dfn>normal scope</dfn>
   *
   * @exception NullPointerException if {@code a} is {@code null}
   *
   * @see #scope(AnnotationMirror)
   */
  public boolean normal(final AnnotationMirror a) {
    for (final Element ee : a.getAnnotationType().asElement().getEnclosedElements()) {
      if (ee.getKind() == METHOD && ee.getSimpleName().contentEquals("normal")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the non-{@code null}, determinate {@link AnnotationMirror} that can be used to designate other {@link
   * AnnotationMirror}s as <dfn>normal scopes</dfn>.
   *
   * @return the non-{@code null}, determinate {@link AnnotationMirror} that can be used to designate other {@link
   * AnnotationMirror}s as <dfn>normal scopes</dfn>
   */
  public final AnnotationMirror metaNormalScope() {
    return this.metaNormalScope;
  }
  
  /**
   * Returns the non-{@code null}, determinate {@link AnnotationMirror} that can be used to designate other {@link
   * AnnotationMirror}s as <dfn>scopes</dfn>.
   *
   * @return the non-{@code null}, determinate {@link AnnotationMirror} that can be used to designate other {@link
   * AnnotationMirror}s as <dfn>scopes</dfn>
   */
  public final AnnotationMirror metaPseudoScope() {
    return this.metaPseudoScope;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link AnnotationMirror} is a <dfn>normal scope identifier</dfn>.
   *
   * @param a an {@link AnnotationMirror}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link AnnotationMirror} is a <dfn>normal scope identifier</dfn>
   *
   * @exception NullPointerException if {@code a} is {@code null}
   */
  public boolean normalScope(final AnnotationMirror a) {
    // In this implementation, all scopes, normal or otherwise, must also be qualifiers.
    boolean metaNormalScopeFound = false;
    boolean metaQualifierFound = false;
    for (final AnnotationMirror ma : a.getAnnotationType().asElement().getAnnotationMirrors()) {
      if (!metaNormalScopeFound) {
        if (sameAnnotation(ma, this.metaNormalScope())) {
          metaNormalScopeFound = true;
          continue;
        }
      }

      if (!metaQualifierFound) {
        if (sameAnnotation(ma, this.aq.metaQualifier())) {
          metaQualifierFound = true;
          continue;
        }
      }

      if (sameAnnotation(ma, this.metaPseudoScope())) {
        // Can't be a pseudo- and a normal scope at the same time.
        return false;
      }
    }
    return metaNormalScopeFound && metaQualifierFound;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link AnnotationMirror} is a <dfn>pseudo-scope identifier</dfn>.
   *
   * @param a an {@link AnnotationMirror}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link AnnotationMirror} is a <dfn>pseudo-scope identifier</dfn>
   *
   * @exception NullPointerException if {@code a} is {@code null}
   */
  public boolean pseudoScope(final AnnotationMirror a) {
    // In this implementation, all scopes, pseudo- or otherwise, must also be qualifiers.
    // TODO: should also check that normalScope annotation is not present
    boolean metaPseudoScopeFound = false;
    boolean metaQualifierFound = false;
    for (final AnnotationMirror ma : a.getAnnotationType().asElement().getAnnotationMirrors()) {
      if (!metaPseudoScopeFound) {
        if (sameAnnotation(ma, this.metaPseudoScope())) {
          metaPseudoScopeFound = true;
          continue;
        }
      }

      if (!metaQualifierFound) {
        if (sameAnnotation(ma, this.aq.metaQualifier())) {
          metaQualifierFound = true;
          continue;
        }
      }

      if (sameAnnotation(ma, this.metaNormalScope())) {
        // Can't be a pseudo- and a normal scope at the same time.
        return false;
      }
    }
    return metaPseudoScopeFound && metaQualifierFound;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link AnnotationMirror} is a <dfn>scope identifier</dfn> (either
   * a {@linkplain #pseudoScope(AnnotationMirror) <dfn>pseudo-scope identifier</dfn>} or a {@linkplain
   * #normalScope(AnnotationMirror) <dfn>normal scope identifier</dfn>}.
   *
   * @param a an {@link AnnotationMirror}; must not be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link AnnotationMirror} is a <dfn>scope identifier</dfn> (either
   * a {@linkplain #pseudoScope(AnnotationMirror) <dfn>pseudo-scope identifier</dfn>} or a {@linkplain
   * #normalScope(AnnotationMirror) <dfn>normal scope identifier</dfn>}
   *
   * @exception NullPointerException if {@code a} is {@code null}
   */
  public final boolean scope(final AnnotationMirror a) {
    return this.pseudoScope(a) || this.normalScope(a);
  }

  /**
   * Returns a non-{@code null}, determinate {@link AnnotationMirror} representing the identifier for the
   * <dfn>singleton</dfn> pseudo-scope.
   *
   * @return a non-{@code null}, determinate {@link AnnotationMirror} representing the identifier for the
   * <dfn>singleton</dfn> pseudo-scope
   */
  public final AnnotationMirror singletonScope() {
    return this.singletonScope;
  }

}
