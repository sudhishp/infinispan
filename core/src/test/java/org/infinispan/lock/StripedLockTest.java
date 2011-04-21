/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.lock;

import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.util.concurrent.locks.StripedLock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Tester class for {@link org.infinispan.util.concurrent.locks.StripedLock}.
 *
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "unit", testName = "lock.StripedLockTest")
public class StripedLockTest extends AbstractInfinispanTest {

   StripedLock stripedLock;

   public static final int CAN_ACQUIRE_WL = 1;
   public static final int CAN_ACQUIRE_RL = 2;
   public static final int ACQUIRE_WL = 3;
   public static final int ACQUIRE_RL = 4;
   /* this value will make sure that the index of the underlying shared lock is not 0*/
   private static final String KEY = "21321321321321321";


   @BeforeMethod
   public void cretateStripedLock() {
      stripedLock = new StripedLock(5);
   }

   public void testGlobalReadLockSimple() throws Exception {
      assert canAquireWL();
      assert canAquireRL();
      assert stripedLock.aquireGlobalLock(false, 0);
      assert stripedLock.getTotalReadLockCount() == stripedLock.getSharedLockCount();
      assert !canAquireWL();
      assert canAquireRL();
   }

   public void testGlobalReadLockIsAtomic() throws Exception {
      assert aquireWL();
      assert 1 == stripedLock.getTotalWriteLockCount();
      assert !stripedLock.aquireGlobalLock(false, 0);
      assert stripedLock.getTotalReadLockCount() == 0 : "No read locks should be held if the operation failed";
   }

   public void testGlobalReadLockOverExistingReadLocks() throws Exception {
      assert aquireRL();
      assert aquireRL();
      assert stripedLock.getTotalReadLockCount() == 2;
      assert stripedLock.aquireGlobalLock(false, 0);
      assert stripedLock.getTotalReadLockCount() == stripedLock.getSharedLockCount() + 2;
   }

   public void testAquireGlobalAndRelease() {
      assert stripedLock.aquireGlobalLock(false, 0);
      assert stripedLock.getTotalReadLockCount() == stripedLock.getSharedLockCount();
      assert stripedLock.getTotalWriteLockCount() == 0;
      try {
         stripedLock.releaseGlobalLock(true); //this should not fail
         assert false : "this should fail as we do not have a monitor over the locks";
      } catch (Exception e) {
         //expected
      }
      stripedLock.releaseGlobalLock(false);
      assert stripedLock.getTotalReadLockCount() == 0;
      assert stripedLock.getTotalWriteLockCount() == 0;

      assert stripedLock.aquireGlobalLock(true, 0);
      assert stripedLock.getTotalReadLockCount() == 0;
      assert stripedLock.getTotalWriteLockCount() == stripedLock.getSharedLockCount();

      try {
         stripedLock.releaseGlobalLock(false); //this should not fail
         assert false : "this should fail as we do not have a monitor over the locks";
      } catch (Exception e) {
         //expected
      }
      stripedLock.releaseGlobalLock(true);
      assert stripedLock.getTotalReadLockCount() == 0;
      assert stripedLock.getTotalWriteLockCount() == 0;


   }

   private boolean aquireWL() throws Exception {
      OtherThread otherThread = new OtherThread();
      otherThread.start();
      otherThread.operationQueue.put(ACQUIRE_WL);
      return otherThread.responseQueue.take();
   }

   private boolean aquireRL() throws Exception {
      OtherThread otherThread = new OtherThread();
      otherThread.start();
      otherThread.operationQueue.put(ACQUIRE_RL);
      return otherThread.responseQueue.take();
   }

   private boolean canAquireRL() throws Exception {
      OtherThread otherThread = new OtherThread();
      otherThread.start();
      otherThread.operationQueue.put(CAN_ACQUIRE_RL);
      return otherThread.responseQueue.take();
   }

   private boolean canAquireWL() throws Exception {
      OtherThread otherThread = new OtherThread();
      otherThread.start();
      otherThread.operationQueue.put(CAN_ACQUIRE_WL);
      return otherThread.responseQueue.take();
   }

   public class OtherThread extends Thread {
      volatile BlockingQueue<Integer> operationQueue = new ArrayBlockingQueue<Integer>(1);
      volatile BlockingQueue<Boolean> responseQueue = new ArrayBlockingQueue<Boolean>(1);

      public void run() {
         try {
            int operation = operationQueue.take();
            Boolean response;
            switch (operation) {
               case CAN_ACQUIRE_RL: {
                  response = stripedLock.acquireLock(KEY, false, 0);
                  if (response) stripedLock.releaseLock(KEY);
                  break;
               }
               case CAN_ACQUIRE_WL: {
                  response = stripedLock.acquireLock(KEY, true, 0);
                  if (response) stripedLock.releaseLock(KEY);
                  break;
               }
               case ACQUIRE_RL: {
                  response = stripedLock.acquireLock(KEY, false, 0);
                  break;
               }
               case ACQUIRE_WL: {
                  response = stripedLock.acquireLock(KEY, true, 0);
                  break;
               }
               default: {
                  throw new IllegalStateException("Unknown operation: " + operation);
               }
            }
            responseQueue.put(response);
         } catch (Throwable e) {
            e.printStackTrace();
         }
      }
   }
}
