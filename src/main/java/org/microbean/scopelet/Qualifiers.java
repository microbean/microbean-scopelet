/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2026 microBean™.
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

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;

import org.microbean.construct.Domain;

import org.microbean.construct.element.SyntheticAnnotationMirror;
import org.microbean.construct.element.SyntheticAnnotationTypeElement;
import org.microbean.construct.element.SyntheticAnnotationValue;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static java.lang.constant.MethodHandleDesc.ofConstructor;

import static java.util.Objects.requireNonNull;

import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;

import static org.microbean.construct.element.AnnotationMirrors.sameAnnotation;

/**
 * A utility class for working with qualifiers.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class Qualifiers implements Constable {

  private final org.microbean.assign.Qualifiers baseQualifiers;

  private final AnnotationMirror primordialMetaQualifier;

  private final List<AnnotationMirror> primordialMetaQualifiers;

  /**
   * Creates a new {@link Qualifiers}.
   *
   * @param domain a non-{@code null} {@link Domain}
   *
   * @param baseQualifiers a non-{@code null} {@link org.microbean.assign.Qualifiers}
   *
   * @see #Qualifiers(Domain, org.microbean.assign.Qualifiers, AnnotationMirror)
   */
  public Qualifiers(final Domain domain,
                    final org.microbean.assign.Qualifiers baseQualifiers) {
    this(domain, baseQualifiers, null);
  }

  /**
   * Creates a new {@link Qualifiers}.
   *
   * @param domain a {@link Domain}; may be {@code null} in which case {@code primordialMetaQualifier} must not be
   * {@code null}
   *
   * @param baseQualifiers a non-{@code null} {@link org.microbean.assign.Qualifiers}
   *
   * @param primordialMetaQualifier an {@link AnnotationMirror} identifying the <dfn>primordial meta-qualifier</dfn>;
   * may be {@code null} in which case {@code domain} must be non-{@code null}
   *
   * @exception NullPointerException if {@code baseQualifiers} is {@code null}, or if {@code domain} is {@code null} in
   * certain circumstances, or if {@code primordialMetaQualifier} is {@code null} in certain circumstances
   */
  public Qualifiers(final Domain domain,
                    final org.microbean.assign.Qualifiers baseQualifiers,
                    final AnnotationMirror primordialMetaQualifier) {
    super();
    this.baseQualifiers = requireNonNull(baseQualifiers, "baseQualifiers");
    if (primordialMetaQualifier == null) {
      final List<? extends AnnotationMirror> as = domain.typeElement("java.lang.annotation.Documented").getAnnotationMirrors();
      assert as.size() == 3; // @Documented, @Retention, @Target, in that order, all annotated in turn with each other
      final List<SyntheticAnnotationValue> savs = new ArrayList<>(4);
      for (final Element e : domain.typeElement("java.lang.annotation.ElementType").getEnclosedElements()) {
        if (e.getKind() == ENUM_CONSTANT && e instanceof VariableElement ve) {
          final Name n = e.getSimpleName();
          if (n.contentEquals("TYPE") || n.contentEquals("METHOD") || n.contentEquals("FIELD") || n.contentEquals("PARAMETER")) {
            savs.add(new SyntheticAnnotationValue(ve));
          }
        }
      }
      final AnnotationMirror targetAnnotation =
        new SyntheticAnnotationMirror(domain.typeElement("java.lang.annotation.Target"), Map.of("value", savs));
      final List<AnnotationMirror> metaAnnotations =
        List.of(baseQualifiers.metaQualifier(),
                as.get(1), // @Retention
                targetAnnotation, // @Target
                as.get(0)); // @Documented
      this.primordialMetaQualifier =
        primordialMetaQualifier == null ?
        new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement(metaAnnotations, "Primordial")) :
        primordialMetaQualifier;
    } else {
      this.primordialMetaQualifier = primordialMetaQualifier;
    }
    this.primordialMetaQualifiers = List.of(this.primordialMetaQualifier);
  }

  @Override // Constable
  public Optional<? extends ConstantDesc> describeConstable() {
    return this.baseQualifiers instanceof Constable ? ((Constable)this.baseQualifiers).describeConstable() : Optional.<ConstantDesc>empty()
      .flatMap(baseQualifiersDesc -> this.primordialMetaQualifier instanceof Constable c ? c.describeConstable() : Optional.<ConstantDesc>empty()
               .map(primordialQualifierDesc -> DynamicConstantDesc.of(BSM_INVOKE,
                                                                      ofConstructor(this.getClass().describeConstable().orElseThrow(),
                                                                                    org.microbean.assign.Qualifiers.class.describeConstable().orElseThrow(),
                                                                                    AnnotationMirror.class.describeConstable().orElseThrow()),
                                                                      baseQualifiersDesc,
                                                                      primordialQualifierDesc)));
  }

  /**
   * Returns the non-{@code null}, determinate {@link AnnotationMirror} representing the <dfn>primordial
   * meta-qualifier</dfn>.
   *
   * @return the non-{@code null}, determinate {@link AnnotationMirror} representing the <dfn>primordial
   * meta-qualifier</dfn>
   */
  public final AnnotationMirror primordialMetaQualifier() {
    return this.primordialMetaQualifier;
  }

  /**
   * Returns {@code true} if and only if the supplied {@link AnnotationMirror} is {@linkplain
   * org.microbean.assign.Qualifiers#sameAnnotation(AnnotationMirror, AnnotationMirror) the same} as the {@linkplain
   * #primordialMetaQualifier() <dfn>primordial meta-qualifier</dfn>}.
   *
   * @param a a non-{@code null} {@link AnnotationMirror}
   *
   * @return {@code true} if and only if the supplied {@link AnnotationMirror} is {@linkplain
   * org.microbean.assign.Qualifiers#sameAnnotation(AnnotationMirror, AnnotationMirror) the same} as the {@linkplain
   * #primordialMetaQualifier() <dfn>primordial meta-qualifier</dfn>}
   *
   * @exception NullPointerException if {@code a} is {@code null}
   *
   * @see #primordialMetaQualifier()
   *
   * @see org.microbean.assign.Qualifiers#sameAnnotation(AnnotationMirror, AnnotationMirror)
   */
  public final boolean primordialMetaQualifier(final AnnotationMirror a) {
    return this.baseQualifiers.sameAnnotation(this.primordialMetaQualifier, a);
  }

  /**
   * Returns a non-{@code null}, determinate, immutable {@link List} housing only the {@linkplain
   * #primordialMetaQualifier() <dfn>primordial meta-qualifier</dfn>}.
   *
   * @return a non-{@code null}, determinate, immutable {@link List} housing only the {@linkplain
   * #primordialMetaQualifier() <dfn>primordial meta-qualifier</dfn>}
   */
  public final List<AnnotationMirror> primordialMetaQualifiers() {
    return this.primordialMetaQualifiers;
  }

}
