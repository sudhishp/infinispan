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
package org.infinispan.container.entries;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.infinispan.marshall.Ids;
import org.infinispan.marshall.Marshallable;

/**
 * An immortal cache value, to correspond with {@link org.infinispan.container.entries.ImmortalCacheEntry}
 * 
 * @author Manik Surtani
 * @since 4.0
 */
@Marshallable(externalizer = ImmortalCacheValue.Externalizer.class, id = Ids.IMMORTAL_VALUE)
public class ImmortalCacheValue implements InternalCacheValue, Cloneable {

   Object value;

   ImmortalCacheValue(Object value) {
      this.value = value;
   }

   public InternalCacheEntry toInternalCacheEntry(Object key) {
      return new ImmortalCacheEntry(key, value);
   }

   public final Object setValue(Object value) {
      Object old = this.value;
      this.value = value;
      return old;
   }

   public Object getValue() {
      return value;
   }

   public boolean isExpired() {
      return false;
   }

   public boolean canExpire() {
      return false;
   }

   public long getCreated() {
      return -1;
   }

   public long getLastUsed() {
      return -1;
   }

   public long getLifespan() {
      return -1;
   }

   public long getMaxIdle() {
      return -1;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ImmortalCacheValue)) return false;

      ImmortalCacheValue that = (ImmortalCacheValue) o;

      if (value != null ? !value.equals(that.value) : that.value != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      return value != null ? value.hashCode() : 0;
   }

   @Override
   public String toString() {
      return "ImmortalCacheValue{" +
            "value=" + value +
            '}';
   }

   @Override
   public ImmortalCacheValue clone() {
      try {
         return (ImmortalCacheValue) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new RuntimeException("Should never happen", e);
      }
   }
   
   public static class Externalizer implements org.infinispan.marshall.Externalizer {
      public void writeObject(ObjectOutput output, Object subject) throws IOException {
         ImmortalCacheValue icv = (ImmortalCacheValue) subject;
         output.writeObject(icv.value);
      }

      public Object readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         Object v = input.readObject();
         return new ImmortalCacheValue(v);
      }
   }
}
