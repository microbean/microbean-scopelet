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

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.function.Supplier;

import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Creation;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.References;

import org.microbean.qualifier.NamedAttributeMap;

public abstract class MapBackedScopelet<M extends MapBackedScopelet<M>> extends Scopelet<M> {

  private final ConcurrentMap<Object, Instance<?>> instances;

  private final ConcurrentMap<Object, ReentrantLock> creationLocks;

  protected MapBackedScopelet(final NamedAttributeMap<?> scopeId) {
    super(scopeId);
    this.creationLocks = new ConcurrentHashMap<>();
    this.instances = new ConcurrentHashMap<>();
  }

  @Override // Scopelet<M>
  public <I> I instance(final Object beanId,
                        final Factory<I> factory,
                        final Creation<I> c,
                        final References<?> r) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    final Supplier<? extends I> s = this.supplier(beanId, factory, c, r);
    return s == null ? null : s.get();
  }

  private final <I> Supplier<I> supplier(final Object id,
                                         final Factory<I> factory,
                                         final Creation<I> creation,
                                         final References<?> r) {
    // (Don't use computeIfAbsent().)
    @SuppressWarnings("unchecked")
    final Supplier<I> supplier = (Supplier<I>)this.instances.get(id);
    if (supplier != null || factory == null) {
      // If we had a Supplier, return it, and if we didn't, and we have no means of creating a new one, return null.
      return supplier;
    }

    // We can't use computeIfAbsent so things get a little tricky here.
    //
    // There wasn't anything in the instances map.  So we want to effectively synchronize on instance creation.  We're
    // going to do this by maintaining Locks in a map, one per id in question.  Please pay close attention to the
    // locking semantics below.

    // Create a new lock, but don't lock() it just yet.
    final ReentrantLock newLock = new ReentrantLock();

    final ReentrantLock creationLock = this.creationLock(id, newLock);
    assert creationLock.isLocked() : "!creationLock.isLocked(): " + creationLock;

    if (creationLock == newLock) {

      try {

        // We successfully put newLock into the map.
        assert this.creationLocks.get(id) == newLock;

        // Perform creation.
        @SuppressWarnings("unchecked")
        final Instance<I> newInstance =
          new Instance<>(factory == this ? (I)this : factory.create(creation, r),
                         factory::destroy,
                         creation,
                         r);

        // Put the created instance into our instance map.  There will not be a pre-existing instance.
        final Object previous = this.instances.put(id, newInstance);
        assert previous == null : "Unexpected prior instance: " + previous;

        // Return the newly created instance.
        return newInstance;

      } finally {
        try {
          final Object removedLock = this.creationLocks.remove(id);
          assert removedLock == newLock : "Unexpected removedLock: " + removedLock + "; newLock: " + newLock;
        } finally {
          newLock.unlock();
        }
      }
    }

    // There was a Lock in the creationLocks map already that was not the newLock we just created.  That's either
    // another thread performing creation (an OK situation) or we have re-entered this method on the current thread
    // and have encountered a newLock ancestor (probably not such a great situation).  In any event, "our" newLock was
    // never inserted into the map.  It will therefore be unlocked (it was never locked in the first place). Discard
    // it in preparation for switching locks to creationLock instead.
    assert !newLock.isLocked() : "newLock was locked: " + newLock;
    assert !this.creationLocks.containsValue(newLock) : "Creation lock contained " + newLock + "; creationLock: " + creationLock;
    // Lock and unlock in rapid succession.  Why?  lock() will block if another thread is currently creating, and will
    // return immediately if it is not.  This is kind of a cheap way of doing Object.wait().
    try {
      creationLock.lock(); // potentially blocks
    } finally {
      creationLock.unlock();
    }

    // If we legitimately blocked in the lock() operation, then another thread was truly creating, and it is
    // guaranteed that thread will have removed the lock from our creationLocks map.  If on the other hand the lock
    // was held by this very thread, then no blocking occurred above, and we're in a cycle, and the lock will NOT have
    // been removed from the creationLocks map (see above).  Thus we can detect cycles by blindly attempting a
    // removal: if it returns a non-null Object, we're in a cycle, otherwise everything is cool.  Moreover, it is
    // guaranteed that if the removal returns a non-null Object, that will be the creationLock this very thread
    // inserted earlier.  No matter what, we must make sure that the creationLocks map no longer contains a lock for
    // the id in question.
    final Object removedLock = this.creationLocks.remove(id);
    if (removedLock != null) {
      assert removedLock == creationLock : "Unexpected removedLock: " + removedLock + "; creationLock: " + creationLock;
      throw new CreationCycleDetectedException();
    }
    // The other thread finished creating; let's try again to pick up its results.
    return this.supplier(id, factory, creation, r); // RECURSIVE
  }

  @Override // Scopelet<M>
  public boolean remove(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    if (id != null) {
      final Instance<?> instance = this.instances.remove(id);
      if (instance != null) {
        instance.close();
        return true;
      }
    }
    return false;
  }

  @Override // Scopelet<M>
  public void close() {
    if (this.closed()) {
      return;
    }
    super.close();
    final Iterator<Entry<Object, ReentrantLock>> i = this.creationLocks.entrySet().iterator();
    while (i.hasNext()) {
      try {
        i.next().getValue().unlock();
      } finally {
        i.remove();
      }
    }
    final Iterator<Entry<Object, Instance<?>>> i2 = this.instances.entrySet().iterator();
    while (i2.hasNext()) {
      try {
        i2.next().getValue().close();
      } finally {
        i2.remove();
      }
    }
  }

  private final ReentrantLock creationLock(final Object id, final ReentrantLock candidate) {
    if (candidate.isLocked()) {
      throw new IllegalArgumentException("candidate.isLocked(): " + candidate);
    }
    try {
      return this.creationLocks.computeIfAbsent(id, x -> lock(candidate));
    } catch (final RuntimeException | Error justBeingCareful) {
      // ReentrantLock#lock() is not documented to throw anything, but if it does, make sure we unlock it.
      try {
        candidate.unlock();
      } catch (final RuntimeException | Error suppressMe) {
        justBeingCareful.addSuppressed(suppressMe);
      }
      throw justBeingCareful;
    }
  }

  private static final <T extends Lock> T lock(final T candidate) {
    candidate.lock();
    return candidate;
  }

}
