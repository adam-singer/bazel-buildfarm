// Copyright 2019 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buildfarm;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import build.bazel.remote.execution.v2.Digest;
import build.buildfarm.cas.CASFileCache;
import build.buildfarm.common.DigestUtil;
import build.buildfarm.common.DigestUtil.HashFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

class CASTest {
  private static class LocalCASFileCache extends CASFileCache {
    LocalCASFileCache(
        Path root,
        long maxSizeInBytes,
        DigestUtil digestUtil,
        ExecutorService expireService,
        Executor accessRecorder) {
      super(root, maxSizeInBytes, maxSizeInBytes, digestUtil, expireService, accessRecorder);
    }

    @Override
    protected InputStream newExternalInput(Digest digest, long offset) throws IOException {
      throw new IOException();
    }
  }

  public static long GBtoBytes(long sizeGb) {
    return sizeGb * 1024 * 1024 * 1024;
  }

  public static long BytestoGB(long sizeBytes) {
    return sizeBytes / 1024 / 1024 / 1024;
  }

  /*
    When starting the CAS, ensure that the "max size" appropriately reflects the content size of the CAS's root.
    Otherwise, reaching the "max size" will result in files being deleted from the root.
    The appropriate size may not be obvious by observing actual disk usage (this especially true for zero filled test data)
    A closer calculation for ample "max size" could be calculated with "du -hs --apparent-size".
  */
  public static void main(String[] args) throws Exception {

    Path root = Paths.get(args[0]);
    CASFileCache fileCache =
        new LocalCASFileCache(
            root,
            /* maxSizeInBytes=*/ GBtoBytes(500),
            new DigestUtil(HashFunction.SHA1),
            /* expireService=*/ newDirectExecutorService(),
            /* accessRecorder=*/ directExecutor());

    // Start cache and measure startup time (reported internally).
    fileCache.start(newDirectExecutorService());

    // Report information on started cache.
    System.out.println("CAS Started.");
    System.out.println("Total Entry Count: " + fileCache.entryCount());
    System.out.println("Unreferenced Entry Count: " + fileCache.unreferencedEntryCount());
    System.out.println("Directory Count: " + fileCache.directoryStorageCount());
    System.out.println("Current Size: " + BytestoGB(fileCache.size()) + "GB");
  }
}
