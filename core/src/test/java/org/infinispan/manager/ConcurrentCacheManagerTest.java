package org.infinispan.manager;

import org.infinispan.Cache;
import org.infinispan.test.AbstractCacheTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test that verifies that CacheContainer.getCache() can be called concurrently.
 *
 * @author Galder Zamarreño
 * @since 4.2
 */
@Test(groups = "functional", testName = "manager.ConcurrentCacheManagerTest")
public class ConcurrentCacheManagerTest extends AbstractCacheTest {
   static final int NUM_CACHES = 4;
   static final int NUM_THREADS = 25;

   private EmbeddedCacheManager cacheManager;

   @BeforeMethod
   protected void setup() throws Exception {
      EmbeddedCacheManager manager = TestCacheManagerFactory.createCacheManager();
      for (int i = 0; i < NUM_CACHES; i++) {
         manager.defineConfiguration("cache" + i, TestCacheManagerFactory.getDefaultCacheConfiguration(true).build());
      }
      cacheManager = manager;
   }

   @AfterMethod
   protected void teardown() {
      TestingUtil.killCacheManagers(cacheManager);
   }

   public void testConcurrentGetCacheCalls() throws Exception {
      final CyclicBarrier barrier = new CyclicBarrier(NUM_THREADS + 1);
      List<Future<Void>> futures = new ArrayList<Future<Void>>(NUM_THREADS);
      ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS, getTestThreadFactory("Worker"));
      for (int i = 0; i < NUM_THREADS; i++) {
         log.debug("Schedule execution");
         final String name = "cache" + (i % NUM_CACHES);

         Future<Void> future = executorService.submit(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
               try {
                  barrier.await();
                  log.tracef("Creating cache %s", name);
                  Cache<Object,Object> cache = cacheManager.getCache(name);
                  cache.put("a", "b");
                  return null;
               } catch (Throwable t) {
                  log.error("Got", t);
                  throw new RuntimeException(t);
               }  finally {
                  log.debug("Wait for all execution paths to finish");
                  barrier.await();
               }
            }
         });
         futures.add(future);
      }
      barrier.await(); // wait for all threads to be ready
      barrier.await(); // wait for all threads to finish

      log.debug("All threads finished, let's shutdown the executor and check whether any exceptions were reported");
      for (Future<Void> future : futures) future.get();
      executorService.shutdownNow();
   }
}
