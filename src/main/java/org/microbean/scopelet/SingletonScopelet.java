/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.scopelet;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;

import java.util.List;
import java.util.Optional;

import org.microbean.bean.Creation;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.ReferenceTypeList;

import org.microbean.lang.Lang;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static org.microbean.bean.Qualifiers.anyQualifier;

import static org.microbean.lang.Lang.declaredType;
import static org.microbean.lang.Lang.typeElement;

import static org.microbean.scope.Scope.SINGLETON_ID;

public final class SingletonScopelet extends MapBackedScopelet<SingletonScopelet> implements Constable {

  private static final ClassDesc CD_SingletonScopelet = ClassDesc.of(SingletonScopelet.class.getName());

  public static final Id ID =
    new Id(List.of(declaredType(SingletonScopelet.class),
                   declaredType(null,
                                typeElement(Scopelet.class),
                                declaredType(SingletonScopelet.class))),
           List.of(SINGLETON_ID, anyQualifier()), // qualifiers
           SINGLETON_ID); // the scope we belong to

  public SingletonScopelet() {
    super(SINGLETON_ID); // the scope we implement
  }

  @Override // Scopelet<SingletonScopelet>
  public final Id id() {
    return ID;
  }

  @Override // Constable
  public final Optional<DynamicConstantDesc<SingletonScopelet>> describeConstable() {
    return Optional.of(DynamicConstantDesc.of(BSM_INVOKE, MethodHandleDesc.ofConstructor(CD_SingletonScopelet)));
  }

}
