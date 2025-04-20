/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2025 microBean™.
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

import java.util.Optional;

import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Creation;
import org.microbean.bean.Destruction;
import org.microbean.bean.DisposableReference;
import org.microbean.bean.Factory;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

/**
 * A {@link Scopelet} implementation that does not cache objects at all.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class NoneScopelet extends Scopelet<NoneScopelet> implements Constable {

  private static final boolean useDisposableReferences =
    Boolean.parseBoolean(System.getProperty("useDisposableReferences", "false"));

  /**
   * Creates a new {@link NoneScopelet}.
   */
  public NoneScopelet() {
    super();
  }

  // All parameters are nullable.
  // Non-final to permit subclasses to, e.g., add logging.
  @Override // Scopelet<NoneScopelet>
  public <I> I instance(final Object ignoredBeanId, final Factory<I> factory, final Creation<I> creation) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    } else if (factory == null) {
      return null;
    }
    final I returnValue = factory.create(creation);
    if (factory.destroys()) {
      if (useDisposableReferences) {
        // Merely creating a DisposableReference will cause it to get disposed *IF* garbage collection runs (which is not
        // guaranteed).
        new DisposableReference<>(returnValue, referent -> factory.destroy(referent, (Destruction)creation));
      } else if (creation instanceof AutoCloseableRegistry acr) {
        acr.register(new Instance<I>(returnValue, factory::destroy, (Destruction)creation));
      } else {
        // TODO: warn or otherwise point out that dependencies will not be destroyed
      }
    }
    return returnValue;
  }

  @Override // Constable
  public Optional<? extends ConstantDesc> describeConstable() {
    return
      Optional.of(DynamicConstantDesc.of(BSM_INVOKE,
                                         MethodHandleDesc.ofConstructor(ClassDesc.of(this.getClass().getName()))));
  }

}
