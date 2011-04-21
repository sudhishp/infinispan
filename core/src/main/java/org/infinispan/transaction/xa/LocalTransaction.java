/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other
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
package org.infinispan.transaction.xa;

import org.infinispan.commands.write.WriteCommand;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.BidirectionalLinkedHashMap;
import org.infinispan.util.BidirectionalMap;
import org.infinispan.util.InfinispanCollections;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import javax.transaction.Transaction;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Mircea.Markus@jboss.com
 * @since 4.2
 */
public class LocalTransaction extends AbstractCacheTransaction {

   private static Log log = LogFactory.getLog(LocalTransaction.class);
   private static final boolean trace = log.isTraceEnabled();

   private Set<Address> remoteLockedNodes;

   /** mark as volatile as this might be set from the tx thread code on view change*/
   private volatile boolean isMarkedForRollback;

   private final Transaction transaction;
   private Xid xid;

   public LocalTransaction(Transaction transaction, GlobalTransaction tx) {
      super.tx = tx;
      this.transaction = transaction;
   }

   public void addModification(WriteCommand mod) {
      if (trace) log.trace("Adding modification %s. Mod list is %s", mod, modifications);
      if (modifications == null) {
         modifications = new LinkedList<WriteCommand>();
      }
      modifications.add(mod);
   }

   public boolean hasRemoteLocksAcquired(List<Address> leavers) {
      if (log.isTraceEnabled()) {
         log.trace("My remote locks: " + remoteLockedNodes + ", leavers are:" + leavers);
      }
      return (remoteLockedNodes != null) && !Collections.disjoint(remoteLockedNodes, leavers);
   }

   public void locksAcquired(Collection<Address> nodes) {
      if (remoteLockedNodes == null) remoteLockedNodes = new HashSet<Address>();
      remoteLockedNodes.addAll(nodes);
   }
   
   public Collection<Address> getRemoteLocksAcquired(){
	   if (remoteLockedNodes == null) return Collections.emptySet();
	   return remoteLockedNodes;
   }

   public void markForRollback() {
      isMarkedForRollback = true;
   }

   public boolean isMarkedForRollback() {
      return isMarkedForRollback;
   }

   public Transaction getTransaction() {
      return transaction;
   }

   public BidirectionalMap<Object, CacheEntry> getLookedUpEntries() {
      return (BidirectionalMap<Object, CacheEntry>)
            (lookedUpEntries == null ? InfinispanCollections.emptyBidirectionalMap() : lookedUpEntries);
   }

   public void putLookedUpEntry(Object key, CacheEntry e) {
      if (lookedUpEntries == null) lookedUpEntries = new BidirectionalLinkedHashMap<Object, CacheEntry>(4);
      lookedUpEntries.put(key, e);
   }

   public boolean isReadOnly() {
      return (modifications == null || modifications.isEmpty()) && (lookedUpEntries == null || lookedUpEntries.isEmpty());
   }

   public void setXid(Xid xid) {
      this.xid = xid;
   }

   public Xid getXid() {
      return xid;
   }

   /**
    * As per the JTA spec, XAResource.start is called on enlistment. That method also sets the xid for this local
    * transaction.
    */
   public boolean isEnlisted() {
      return xid != null;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LocalTransaction that = (LocalTransaction) o;

      if (xid != null ? !xid.equals(that.xid) : that.xid != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      return xid != null ? xid.hashCode() : 0;
   }

   @Override
   public String toString() {
      return "LocalTransaction{" +
            "remoteLockedNodes=" + remoteLockedNodes +
            ", isMarkedForRollback=" + isMarkedForRollback +
            ", transaction=" + transaction +
            ", xid=" + xid +
            "} " + super.toString();
   }
}
