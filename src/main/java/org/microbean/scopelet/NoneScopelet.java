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

import java.lang.System.Logger;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;

import java.util.Optional;

import org.microbean.bean.Creation;
import org.microbean.bean.Destruction;
import org.microbean.bean.Factory;

import org.microbean.reference.DestructorRegistry;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static java.lang.constant.MethodHandleDesc.ofConstructor;

import static java.lang.System.getLogger;

import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link Scopelet} implementation that does not cache objects at all.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class NoneScopelet extends Scopelet<NoneScopelet> implements Constable {


  /*
   * Static fields.
   */


  private static final Logger LOGGER = getLogger(NoneScopelet.class.getName());


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link NoneScopelet}.
   */
  public NoneScopelet() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Checks to see if this {@link Scopelet} {@linkplain #active() is active} and then returns a contextual instance
   * {@linkplain Factory#create(Creation) created by the supplied <code>Factory</code>}.
   *
   * <p>This method (and its overrides) may return {@code null}.</p>
   *
   * <p>If the supplied {@link Factory} is {@code null}, this method will (and its overrides must) return {@code null}.
   *
   * @param ignoredBeanId an identifier; ignored by the default implementation; may be {@code null}
   *
   * @param factory a {@link Factory}; may be {@code null} in which case {@code null} will and must be returned
   *
   * @param creation a {@link Creation}, typically the one in effect that is causing this method to be invoked in the
   * first place; may be {@code null}; most commonly also an instance of {@link DestructorRegistry}
   *
   * @return a contextual instance, or {@code null}
   *
   * @exception InactiveScopeletException if this {@link Scopelet} {@linkplain #active() is not active}
   *
   * @exception ClassCastException if destruction is called for, {@code creation} is non-{@code null}, and {@code
   * creation} does not implement {@link org.microbean.bean.Destruction}, a requirement of its contract
   *
   * @see DestructorRegistry
   *
   * @see Creation
   *
   * @see Destruction
   *
   * @see Factory#destroys()
   */
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
      if (creation instanceof DestructorRegistry dr && creation instanceof Destruction d) {
        dr.register(returnValue, () -> factory.destroy(returnValue, d));
      } else if (LOGGER.isLoggable(WARNING)) {
        LOGGER.log(WARNING, "Dependent objects will not be destroyed");
      }
    }
    return returnValue;
  }

  @Override // Constable
  public Optional<? extends ConstantDesc> describeConstable() {
    return Optional.of(DynamicConstantDesc.of(BSM_INVOKE, ofConstructor(ClassDesc.of(this.getClass().getName()))));
  }

}
