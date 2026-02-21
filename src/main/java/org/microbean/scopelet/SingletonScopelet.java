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

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;

import java.util.Optional;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static java.lang.constant.MethodHandleDesc.ofConstructor;

/**
 * A {@link MapBackedScopelet} implementation that caches singletons.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class SingletonScopelet extends MapBackedScopelet<SingletonScopelet> implements Constable {

  /**
   * Creates a new {@link SingletonScopelet}.
   */
  public SingletonScopelet() {
    super();
  }

  @Override // Constable
  public Optional<? extends ConstantDesc> describeConstable() {
    return Optional.of(DynamicConstantDesc.of(BSM_INVOKE, ofConstructor(this.getClass().describeConstable().orElseThrow())));
  }

}
