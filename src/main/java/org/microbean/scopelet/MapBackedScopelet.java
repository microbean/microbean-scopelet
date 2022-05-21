/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2022 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.scopelet;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.function.Supplier;

import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.Factory;

import org.microbean.qualifier.Qualifier;

public class MapBackedScopelet<S extends MapBackedScopelet<S>> extends AbstractScopelet<S> {


  private static final long serialVersionUID = 1L;


  /*
   * Instance fields.
   */


  private final ConcurrentMap<Object, Instance<?>> instances;

  private final ConcurrentMap<Object, Lock> creationLocks;


  /*
   * Constructors.
   */


  public MapBackedScopelet(final Qualifier<?> scopeId,
                           final Id id) {
    super(scopeId, id);
    this.instances = new ConcurrentHashMap<>();
    this.creationLocks = new ConcurrentHashMap<>();
  }


  /*
   * Instance methods.
   */


  @Override
  public boolean containsKey(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return this.instances.containsKey(id);
  }

  @Override // AbstractScopeAffiliatedSupplier
  public final <I> I supply(final Object id, final Factory<I> factory, final Creation<I> cc) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    final Supplier<I> s = this.supplySupplier(id, factory, cc);
    return s == null ? null : s.get();
  }
  

  /*
   * Private instance methods.
   */


  @SuppressWarnings("unchecked")
  private final <I> Supplier<I> getSupplier(final Object id) {
    return (Supplier<I>)this.instances.get(id);
  }

  private final <I> Supplier<I> supplySupplier(final Object id, final Factory<I> factory, final Creation<I> creation) {
    // https://gitlab.com/microbean.systems/ristretto/-/issues/1
    // (Don't use computeIfAbsent().)
    Supplier<I> supplier = this.getSupplier(id);
    if (supplier != null || factory == null) {
      return supplier;
    }

    // We can't use computeIfAbsent so things get a little tricky
    // here.
    //
    // There wasn't anything in the instances map.  So we want to
    // effectively synchronize on instance creation.  We're going to
    // do this by maintaining Locks in a map, one per id in question.
    // Please pay close attention to the locking semantics below.
    //
    // Create a new lock, but don't lock() it just yet.
    final ReentrantLock newLock = new ReentrantLock();
    Lock creationLock;
    try {
      creationLock = this.creationLocks.computeIfAbsent(id, b -> {
          newLock.lock();
          return newLock;
        });
    } catch (final RuntimeException | Error justBeingCareful) {
      try {
        newLock.unlock();
      } catch (final RuntimeException | Error suppressMe) {
        justBeingCareful.addSuppressed(suppressMe);
      }
      throw justBeingCareful;
    }
    assert creationLock instanceof ReentrantLock rl && rl.isLocked() : "Unexpected creationLock: " + creationLock;

    if (creationLock == newLock) {
      try {
        // We successfully put newLock into the map.
        assert this.creationLocks.get(id) == newLock;
        assert newLock.isLocked() : "newLock was unlocked: " + newLock;
        // Perform creation.
        @SuppressWarnings("unchecked")
        final Instance<I> newInstance =
          new Instance<>(factory == this ? (I)this : factory.create(creation),
                         factory::destroy,
                         creation.destruction());
        // Put the created instance into our instance map.  There will
        // not be a pre-existing instance.
        final Object previous = this.instances.put(id, newInstance);
        assert previous == null : "Unexpected prior instance: " + previous;
        // The newly created instance will become our return value.
        supplier = (Supplier<I>)newInstance;
      } finally {
        try {
          final Object removedLock = this.creationLocks.remove(id);
          assert removedLock == null || removedLock == newLock : "Unexpected removedLock: " + removedLock + "; newLock: " + newLock;
        } finally {
          newLock.unlock();
        }
      }
    } else {
      // There was a Lock in the creationLocks map already that was
      // not the newLock we just created.  That's either another
      // thread performing creation (an OK situation) or we have
      // re-entered this method on the current thread and have
      // encountered a newLock ancestor (probably not such a great
      // situation).  In any event, "our" newLock was never inserted
      // into the map.  It will therefore be unlocked (it was never
      // locked in the first place). Discard it in preparation for
      // switching locks to creationLock instead.
      assert !newLock.isLocked() : "newLock was locked: " + newLock;
      assert !this.creationLocks.containsValue(newLock) : "Creation lock contained " + newLock + "; creationLock: " + creationLock;
      // Lock and unlock in rapid succession.  Why?  lock() will block
      // if another thread is currently creating, and will return
      // immediately if it is not.  This is kind of a cheap way of
      // doing Object.wait().
      try {
        creationLock.lock(); // potentially blocks
      } finally {
        creationLock.unlock();
      }
      // If we legitimately blocked in the lock() operation, then
      // another thread was truly creating, and it is guaranteed that
      // thread will have removed the lock from our creationLocks map.
      // If on the other hand the lock was held by this very thread,
      // then no blocking occurred above, and we're in a cycle, and
      // the lock will NOT have been removed from the creationLocks
      // map (see above).  Thus we can detect cycles by blindly
      // attempting a removal: if it returns a non-null Object, we're
      // in a cycle, otherwise everything is cool.  Moreover, it is
      // guaranteed that if the removal returns a non-null Object,
      // that will be the creationLock this very thread inserted
      // earlier.  No matter what, we must make sure that the
      // creationLocks map no longer contains a lock for the id in
      // question.
      final Object removedLock = this.creationLocks.remove(id);
      if (removedLock == null) {
        // The other thread finished creating; let's try again to
        // pick up its results.
        supplier = this.supplySupplier(id, factory, creation); // XXX recursive
      } else {
        assert removedLock == creationLock : "Unexpected removedLock: " + removedLock + "; creationLock: " + creationLock;
        throw new CreationCycleDetectedException();
      }
    }
    return supplier;
  }

  @Override
  public void remove(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    if (id != null) {
      final Instance<?> instance = this.instances.remove(id);
      if (instance != null) {
        instance.close();
      }
    }
  }

  @Override
  public void close() {
    this.creationLocks.clear();
    this.instances.forEach(this::closeInstance);
    this.instances.clear();
  }

  private final void closeInstance(final Object ignored, final Instance<?> instance) {
    instance.close();
  }

}

