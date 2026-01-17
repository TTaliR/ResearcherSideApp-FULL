package org.eclipse.jdt.internal.jarinjarloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class RsrcURLStreamHandler extends URLStreamHandler {
   private ClassLoader classLoader;

   public RsrcURLStreamHandler(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   @Override
   protected URLConnection openConnection(URL u) throws IOException {
      return new RsrcURLConnection(u, this.classLoader);
   }

   @Override
   protected void parseURL(URL url, String spec, int start, int limit) {
      String ref = null;
      String file;
      if (spec.startsWith("rsrc:")) {
         file = spec.substring(5);
      } else if ("./".equals(url.getFile())) {
         file = spec;
      } else if (url.getFile().endsWith("/")) {
         file = url.getFile() + spec;
      } else if ("#runtime".equals(spec)) {
         file = url.getFile();
         ref = "runtime";
      } else {
         file = spec;
      }

      this.setURL(url, "rsrc", "", -1, null, null, file, null, ref);
   }
}
