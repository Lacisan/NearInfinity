// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.Writeable;
import org.infinity.util.DynamicArray;
import org.infinity.util.io.FileInputStreamNI;
import org.infinity.util.io.FileNI;
import org.infinity.util.io.FileReaderNI;
import org.infinity.util.io.FileWriterNI;

public final class BIFFResourceEntry extends ResourceEntry implements Writeable
{
  private static final Charset DefaultCharset = Charset.forName("windows-1252");

  private final String resourceName;
  private final int type;
  private boolean hasOverride = false;
  private int locator;

  public BIFFResourceEntry(BIFFEntry bifentry, String resourcename, int offset)
  {
    this.resourceName = resourcename;
    type = ResourceFactory.getKeyfile().getExtensionType(resourcename.substring(resourcename.indexOf((int)'.') + 1));
    int bifindex = bifentry.getIndex();
    locator = bifindex << 20;
    if (type == Keyfile.TYPE_TIS) // TIS
      locator |= offset << 14;
    else
      locator |= offset;
  }

  public BIFFResourceEntry(byte buffer[], int offset, int stringLength)
  {
    StringBuffer sb = new StringBuffer(DynamicArray.getString(buffer, offset, stringLength, DefaultCharset));
    type = (int)DynamicArray.getShort(buffer, offset + stringLength);
    locator = DynamicArray.getInt(buffer, offset + stringLength + 2);
    resourceName = sb.append('.').append(getExtension()).toString();
  }

// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    FileWriterNI.writeString(os, resourceName.substring(0, resourceName.lastIndexOf((int)'.')), 8);
    FileWriterNI.writeShort(os, (short)type);
    FileWriterNI.writeInt(os, locator);
  }

// --------------------- End Interface Writeable ---------------------

  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof BIFFResourceEntry))
      return false;
    BIFFResourceEntry other = (BIFFResourceEntry)o;
    return locator == other.locator && resourceName.equals(other.resourceName) && type == other.type;
  }

  @Override
  public String toString()
  {
    return resourceName;
  }

  public void deleteOverride()
  {
    File override = FileNI.getFile(Profile.getRootFolders(),
                             Profile.getOverrideFolderName() + File.separatorChar + resourceName);
    if (override != null && override.exists() && !override.isDirectory())
      override.delete();
    hasOverride = false;
  }

  @Override
  public File getActualFile(boolean ignoreoverride)
  {
    if (!ignoreoverride) {
      File override = FileNI.getFile(Profile.getRootFolders(),
                               Profile.getOverrideFolderName() + File.separatorChar + resourceName);
      if (override.exists() && !override.isDirectory())
        return override;
    }
    try {
      return ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry()).getFile();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public BIFFEntry getBIFFEntry()
  {
    int sourceindex = locator >> 20 & 0xfff;
    return ResourceFactory.getKeyfile().getBIFFEntry(sourceindex);
  }

  @Override
  public String getExtension()
  {
    String ext = ResourceFactory.getKeyfile().getExtension(type);
    if (ext == null)
      return "Unknown (" + Integer.toHexString(type) + "h)";
    return ext;
  }

  public int getLocator()
  {
    return locator;
  }

  @Override
  public byte[] getResourceData(boolean ignoreoverride) throws Exception
  {
    if (!ignoreoverride) {
      File override = FileNI.getFile(Profile.getRootFolders(),
                               Profile.getOverrideFolderName() + File.separatorChar + resourceName);
      if (override.exists() && !override.isDirectory()) {
        InputStream is = new BufferedInputStream(new FileInputStreamNI(override));
        byte buffer[] = FileReaderNI.readBytes(is, (int)override.length());
        is.close();
        return buffer;
      }
    }
    BIFFArchive biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
    if (type == Keyfile.TYPE_TIS) // TIS
      return biff.getResource(locator >> 14 & 0x3f, true);
    return biff.getResource(locator & 0x3fff, false);
  }

  @Override
  public InputStream getResourceDataAsStream(boolean ignoreoverride) throws Exception
  {
    if (!ignoreoverride) {
      File override = FileNI.getFile(Profile.getRootFolders(),
                               Profile.getOverrideFolderName() + File.separatorChar + resourceName);
      if (override.exists() && !override.isDirectory()) {
        return new BufferedInputStream(new FileInputStreamNI(override));
      }
    }
    BIFFArchive biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
    if (type == Keyfile.TYPE_TIS) // TIS
      return biff.getResourceAsStream(locator >> 14 & 0x3f, true);
    return biff.getResourceAsStream(locator & 0x3fff, false);
  }

  @Override
  public int[] getResourceInfo(boolean ignoreoverride) throws IOException
  {
    if (!ignoreoverride) {
      File override = FileNI.getFile(Profile.getRootFolders(),
                               Profile.getOverrideFolderName() + File.separatorChar + resourceName);
      if (override.exists() && !override.isDirectory())
        return getLocalFileInfo(override);
    }
    BIFFArchive biff = ResourceFactory.getKeyfile().getBIFFFile(getBIFFEntry());
    if (type == Keyfile.TYPE_TIS) // TIS
      return biff.getResourceInfo(locator >> 14 & 0x3f, true);
    return biff.getResourceInfo(locator & 0x3fff, false);
  }

  @Override
  public String getResourceName()
  {
    return resourceName;
  }

  @Override
  public String getTreeFolder()
  {
    if (BrowserMenuBar.getInstance() != null &&
        BrowserMenuBar.getInstance().getOverrideMode() == BrowserMenuBar.OVERRIDE_IN_OVERRIDE &&
        hasOverride()) {
      return Profile.getOverrideFolderName();
    }
    return getExtension();
  }

  public int getType()
  {
    return type;
  }

  @Override
  public boolean hasOverride()
  {
    if (!BrowserMenuBar.getInstance().cacheOverride()) {
      File override = FileNI.getFile(Profile.getRootFolders(),
                               Profile.getOverrideFolderName() + File.separatorChar + resourceName);
      hasOverride = override.exists() && !override.isDirectory();
    }
    return hasOverride;
  }

  public void setOverride(boolean hasOverride)
  {
    this.hasOverride = hasOverride;
  }

  synchronized void adjustSourceIndex(int index)
  {
    int sourceindex = locator >> 20 & 0xfff;
    if (sourceindex > index) {
      sourceindex--;
      locator = sourceindex << 20 | locator & 0xfffff;
    }
  }
}
