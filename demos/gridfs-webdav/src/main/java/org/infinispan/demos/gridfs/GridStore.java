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
package org.infinispan.demos.gridfs;


import net.sf.webdav.*;
import net.sf.webdav.exceptions.WebdavException;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.security.Principal;

import org.infinispan.Cache;
import org.infinispan.io.GridFile;
import org.infinispan.io.GridFilesystem;
import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * @author Bela Ban
 */
public class GridStore implements IWebdavStore {
   private static final Log log = LogFactory.getLog(GridStore.class);
   private static int BUF_SIZE = 65536;
   private final Cache<String, byte[]> data;
   private final Cache<String, GridFile.Metadata> metadata;
   private final GridFilesystem fs;


   private File root = null;

   public GridStore(File root) {
      data = CacheManagerHolder.cacheContainer.getCache(CacheManagerHolder.dataCacheName);
      metadata = CacheManagerHolder.cacheContainer.getCache(CacheManagerHolder.metadataCacheName);
      
      try {
         data.start();
         metadata.start();
      } catch (Exception e) {
         throw new RuntimeException("creation of cluster failed", e);
      }

      fs = new GridFilesystem(data, metadata);

      this.root = fs.getFile(root.getPath());
      if (!this.root.mkdirs())
         throw new WebdavException("root path: " + root.getAbsolutePath() + " does not exist and could not be created");
   }

   public void destroy() {
      if (data != null)
         data.stop();
      if (metadata != null)
         metadata.stop();
   }

   public ITransaction begin(Principal principal) throws WebdavException {
      log.trace("GridStore.begin()");
      if (!root.exists()) {
         if (!root.mkdirs()) {
            throw new WebdavException("root path: "
                    + root.getAbsolutePath()
                    + " does not exist and could not be created");
         }
      }
      return null;
   }

   public void checkAuthentication(ITransaction transaction) throws SecurityException {
      log.trace("GridStore.checkAuthentication()");
   }

   public void commit(ITransaction transaction) throws WebdavException {
      log.trace("GridStore.commit()");
   }

   public void rollback(ITransaction transaction) throws WebdavException {
      log.trace("GridStore.rollback()");
   }

   public void createFolder(ITransaction transaction, String uri) throws WebdavException {
      log.trace("GridStore.createFolder(" + uri + ")");
      File file = fs.getFile(root, uri);
      if (!file.mkdir())
         throw new WebdavException("cannot create folder: " + uri);
   }

   public void createResource(ITransaction transaction, String uri) throws WebdavException {
      log.trace("GridStore.createResource(" + uri + ")");
      File file = fs.getFile(root, uri);
      try {
         if (!file.createNewFile())
            throw new WebdavException("cannot create file: " + uri);
      }
      catch (IOException e) {
         log.error("GridStore.createResource(" + uri + ") failed");
         throw new WebdavException(e);
      }
   }

   public long setResourceContent(ITransaction transaction, String uri,
                                  InputStream is, String contentType, String characterEncoding)
           throws WebdavException {

      log.trace("GridStore.setResourceContent(" + uri + ")");
      File file = fs.getFile(root, uri);
      try {
         OutputStream os = fs.getOutput((GridFile) file);
         // OutputStream os=new BufferedOutputStream(fs.getOutput((GridFile)file), BUF_SIZE);
         try {
            int read;
            byte[] copyBuffer = new byte[BUF_SIZE];

            while ((read = is.read(copyBuffer, 0, copyBuffer.length)) != -1) {
               os.write(copyBuffer, 0, read);
            }
         }
         finally {
            Util.close(is);
            Util.close(os);
         }
      }
      catch (IOException e) {
         log.error("GridStore.setResourceContent(" + uri + ") failed");
         throw new WebdavException(e);
      }
      long length = -1;

      try {
         length = file.length();
      }
      catch (SecurityException e) {
         log.error("GridStore.setResourceContent(" + uri + ") failed" + "\nCan't get file.length");
      }
      return length;
   }

   public String[] getChildrenNames(ITransaction transaction, String uri) throws WebdavException {
      log.trace("GridStore.getChildrenNames(" + uri + ")");
      File file = fs.getFile(root, uri);
      String[] childrenNames = null;
      if (file.isDirectory()) {
         File[] children = file.listFiles();
         List<String> childList = new ArrayList<String>();
         String name = null;
         for (int i = 0; i < children.length; i++) {
            name = children[i].getName();
            childList.add(name);
            log.trace("Child " + i + ": " + name);
         }
         childrenNames = new String[childList.size()];
         childrenNames = childList.toArray(childrenNames);
      }
      return childrenNames;
   }

   public void removeObject(ITransaction transaction, String uri) throws WebdavException {
      File file = fs.getFile(root, uri);
      boolean success = file.delete();
      log.trace("GridStore.removeObject(" + uri + ")=" + success);
      if (!success)
         throw new WebdavException("cannot delete object: " + uri);
   }

   public InputStream getResourceContent(ITransaction transaction, String uri) throws WebdavException {
      log.trace("GridStore.getResourceContent(" + uri + ")");
      File file = fs.getFile(root, uri);

      InputStream in;
      try {
         // in=new BufferedInputStream(fs.getInput(file));
         in = fs.getInput(file);
      }
      catch (IOException e) {
         log.error("GridStore.getResourceContent(" + uri + ") failed");
         throw new WebdavException(e);
      }
      return in;
   }

   public long getResourceLength(ITransaction transaction, String uri) throws WebdavException {
      log.trace("GridStore.getResourceLength(" + uri + ")");
      File file = fs.getFile(root, uri);
      return file.length();
   }

   public StoredObject getStoredObject(ITransaction transaction, String uri) {

      StoredObject so = null;

      File file = fs.getFile(root, uri);
      if (file.exists()) {
         so = new StoredObject();
         so.setFolder(file.isDirectory());
         so.setLastModified(new Date(file.lastModified()));
         so.setCreationDate(new Date(file.lastModified()));
         so.setResourceLength(getResourceLength(transaction, uri));
      }

      return so;
   }

}
