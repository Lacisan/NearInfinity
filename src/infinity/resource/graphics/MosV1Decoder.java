// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.graphics;

import infinity.resource.key.ResourceEntry;
import infinity.util.DynamicArray;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public class MosV1Decoder extends MosDecoder
{
  private static final int BlockDimension = 64;   // default block dimension
  private static final int HeaderSize = 24;

  private byte[] mosData;
  private int width, height, cols, rows, ofsPalette, ofsLookup, ofsData;
  private boolean transparencyEnabled;
  private int[] workingPalette;           // storage space for palettes
  private BufferedImage workingCanvas;    // storage space big enough for a single block
  private int lastBlockIndex;             // hold the index of the last data block drawn onto workingCanvas

  public MosV1Decoder(ResourceEntry mosEntry)
  {
    super(mosEntry);
    init();
    transparencyEnabled = false;
  }

  /**
   * Returns whether the transparent palette entry is drawn or not.
   */
  public boolean isTransparencyEnabled()
  {
    return transparencyEnabled;
  }

  /**
   * Specify whether to draw the transparent palette entry.
   */
  public void setTransparencyEnabled(boolean set)
  {
    transparencyEnabled = set;
  }

  /**
   * Returns the number of data blocks per row.
   */
  public int getColumnCount()
  {
    return cols;
  }

  /**
   * Returns the number of data block rows.
   */
  public int getRowCount()
  {
    return rows;
  }

  /**
   * Returns the palette of the specified data block as an int array of 256 entries. (Format: ARGB)
   * @param blockIdx The block index.
   * @return Palette as int array of 256 entries. Returns <code>null</code> on error.
   */
  public int[] getPalette(int blockIdx)
  {
    if (isValidBlock(blockIdx)) {
      int[] palette = new int[256];
      if (getPalette(blockIdx, palette)) {
        return palette;
      } else {
        palette = null;
      }
    }
    return null;
  }

  /**
   * Writes the palette of the specified MOS block into the buffer.
   * @param blockIdx The block index.
   * @param buffer The buffer to write the palette entries into.
   * @return <code>true</code> if palette data has been written, <code>false</code> otherwise.
   */
  public boolean getPalette(int blockIdx, int[] buffer)
  {
    if (isValidBlock(blockIdx) && buffer != null) {
      int ofs = getPaletteOffset(blockIdx);
      if (ofs > 0) {
        int maxSize = (buffer.length < 256) ? buffer.length : 256;
        boolean transSet = false;
        for (int i = 0; i < maxSize; i++, ofs += 4) {
          int color = 0xff000000 | DynamicArray.getInt(mosData, ofs);
          if (transparencyEnabled && !transSet && (color & 0x00ffffff) == 0x0000ff00) {
            color &= 0x00ffffff;
          }
          buffer[i] = color;
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Returns unprocessed MOS block data.
   * @param blockIdx The block index.
   * @return The buffer containing raw block data.
   */
  public byte[] getRawBlockData(int blockIdx)
  {
    if (isValidBlock(blockIdx)) {
      byte[] buffer = new byte[getBlockWidth(blockIdx)*getBlockHeight(blockIdx)];
      if (getRawBlockData(blockIdx, buffer)) {
        return buffer;
      }
      buffer = null;
    }
    return null;
  }

  /**
   * Writes unprocessed MOS block data into the buffer. The buffer size can be calculated:
   * <pre>size = {@link #getBlockWidth(int)}*{@link #getBlockHeight(int)}.</pre>
   * @param blockIdx The block index.
   * @param buffer The buffer to write the raw block data into.
   * @return <code>true</code> if block data has been written, <code>false</code> otherwise.
   */
  public boolean getRawBlockData(int blockIdx, byte[] buffer)
  {
    if (isValidBlock(blockIdx) && buffer != null) {
      int ofs = getBlockOffset(blockIdx);
      if (ofs > 0) {
        int size = getBlockWidth(blockIdx)*getBlockHeight(blockIdx);
        int maxSize = (buffer.length < size) ? buffer.length : size;
        System.arraycopy(mosData, ofs, buffer, 0, maxSize);
        return true;
      }
    }
    return false;
  }


  @Override
  public void close()
  {
    mosData = null;
    width = height = cols = rows = 0;
    ofsPalette = ofsLookup = ofsData = 0;
    lastBlockIndex = -1;
    workingPalette = null;
    if (workingCanvas != null) {
      workingCanvas.flush();
      workingCanvas = null;
    }
  }

  @Override
  public void reload()
  {
    init();
  }

  @Override
  public byte[] getResourceData()
  {
    return mosData;
  }

  @Override
  public int getWidth()
  {
    return width;
  }

  @Override
  public int getHeight()
  {
    return height;
  }

  @Override
  public Image getImage()
  {
    if (isInitialized()) {
      BufferedImage image = ColorConvert.createCompatibleImage(getWidth(), getHeight(), true);
      if (renderMos(image)) {
        return image;
      } else {
        image = null;
      }
    }
    return null;
  }

  @Override
  public boolean getImage(Image canvas)
  {
    if (canvas != null) {
      return renderMos(canvas);
    }
    return false;
  }

  @Override
  public int[] getImageData()
  {
    if (isInitialized()) {
      int[] buffer = new int[getWidth()*getHeight()];
      if (renderMos(buffer, getWidth(), getHeight())) {
        return buffer;
      } else {
        buffer = null;
      }
    }
    return null;
  }

  @Override
  public boolean getImageData(int[] buffer)
  {
    if (isInitialized() && buffer != null) {
      return renderMos(buffer, getWidth(), getHeight());
    }
    return false;
  }

  @Override
  public int getBlockCount()
  {
    return cols*rows;
  }

  @Override
  public int getBlockWidth(int blockIdx)
  {
    int ofs = getBlockOffset(blockIdx);
    if (ofs > 0) {
      int col = blockIdx % cols;
      if (col < cols - 1) {
        return BlockDimension;
      } else {
        return getWidth() - col*BlockDimension;
      }
    }
    return 0;
  }

  @Override
  public int getBlockHeight(int blockIdx)
  {
    int ofs = getBlockOffset(blockIdx);
    if (ofs > 0) {
      int row = blockIdx / cols;
      if (row < rows - 1) {
        return BlockDimension;
      } else {
        return getHeight() - row*BlockDimension;
      }
    }
    return 0;
  }

  @Override
  public Image getBlock(int blockIdx)
  {
    if (isValidBlock(blockIdx)) {
      BufferedImage image = ColorConvert.createCompatibleImage(getBlockWidth(blockIdx),
                                                               getBlockHeight(blockIdx), true);
      if (getBlock(blockIdx, image)) {
        return image;
      } else {
        image = null;
      }
    }
    return null;
  }

  @Override
  public boolean getBlock(int blockIdx, Image canvas)
  {
    if (canvas != null && updateWorkingCanvas(blockIdx)) {
      int w = getBlockWidth(blockIdx);
      int h = getBlockHeight(blockIdx);
      if (canvas.getWidth(null) < w) w = canvas.getWidth(null);
      if (canvas.getHeight(null) < h) h = canvas.getHeight(null);
      Graphics2D g = (Graphics2D)canvas.getGraphics();
      try {
        g.drawImage(workingCanvas, 0, 0, w, h, 0, 0, w, h, null);
      } finally {
        g.dispose();
        g = null;
      }
      return true;
    }
    return false;
  }

  @Override
  public int[] getBlockData(int blockIdx)
  {
    if (isValidBlock(blockIdx)) {
      int[] buffer = new int[getBlockWidth(blockIdx)*getBlockHeight(blockIdx)];
      if (getBlockData(blockIdx, buffer)) {
        return buffer;
      } else {
        buffer = null;
      }
    }
    return null;
  }

  @Override
  public boolean getBlockData(int blockIdx, int[] buffer)
  {
    if (isValidBlock(blockIdx) && buffer != null) {
      BufferedImage image = ColorConvert.toBufferedImage(getBlock(blockIdx), true);
      if (image != null) {
        int[] src = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        int maxSize = (buffer.length < src.length) ? buffer.length : src.length;
        System.arraycopy(src, 0, buffer, 0, maxSize);
        image.flush();
        image = null;
        return true;
      }
    }
    return false;
  }

  private void init()
  {
    close();

    if (getResourceEntry() != null) {
      try {
        mosData = getResourceEntry().getResourceData();
        String signature = DynamicArray.getString(mosData, 0x00, 4);
        String version = DynamicArray.getString(mosData, 0x04, 4);
        if ("MOSC".equals(signature)) {
          setType(Type.MOSC);
          mosData = Compressor.decompress(mosData);
          signature = DynamicArray.getString(mosData, 0x00, 4);
          version = DynamicArray.getString(mosData, 0x04, 4);
        } else if ("MOS ".equals(signature) && "V1  ".equals(version)) {
          setType(Type.MOSV1);
        } else {
          throw new Exception("Invalid MOS type");
        }
        // Data should now be in MOS v1 format
        if (!"MOS ".equals(signature) || !"V1  ".equals(version)) {
          throw new Exception("Invalid MOS type");
        }

        // evaluating header data
        width = DynamicArray.getUnsignedShort(mosData, 0x08);
        if (width <= 0) {
          throw new Exception("Invalid MOS width: " + width);
        }
        height = DynamicArray.getUnsignedShort(mosData, 0x0a);
        if (height <= 0) {
          throw new Exception("Invalid MOS height: " + height);
        }
        cols = DynamicArray.getUnsignedShort(mosData, 0x0c);
        rows = DynamicArray.getUnsignedShort(mosData, 0x0e);
        if (cols <= 0 || rows <= 0) {
          throw new Exception("Invalid number of data blocks: " + (cols*rows));
        }
        int blockSize = DynamicArray.getInt(mosData, 0x10);
        if (blockSize != BlockDimension) {
          throw new Exception("Invalid block size: " + blockSize);
        }
        ofsPalette = DynamicArray.getInt(mosData, 0x14);
        if (ofsPalette < HeaderSize) {
          throw new Exception("Invalid palette offset: " + ofsPalette);
        }
        ofsLookup = ofsPalette + (getBlockCount() << 10);
        ofsData = ofsLookup + (getBlockCount() << 2);

        workingPalette = new int[256];
        workingCanvas = new BufferedImage(BlockDimension, BlockDimension, BufferedImage.TYPE_INT_ARGB);
      } catch (Exception e) {
        e.printStackTrace();
        close();
      }
    }
  }

  // Returns if a valid MOS has been initialized
  private boolean isInitialized()
  {
    return (mosData != null && cols > 0 && rows > 0 && width > 0 && height >0);
  }

  // Returns whether the specified block index is valid
  private boolean isValidBlock(int blockIdx)
  {
    return (blockIdx >= 0 && blockIdx < cols*rows);
  }

  // Returns the starting offset of the specified data block
  private int getBlockOffset(int blockIdx)
  {
    if (isValidBlock(blockIdx)) {
      return ofsData + DynamicArray.getInt(mosData, ofsLookup + (blockIdx << 2));
    }
    return -1;
  }

  // Returns the starting offset of the specified palette
  private int getPaletteOffset(int blockIdx)
  {
    if (isValidBlock(blockIdx)) {
      return ofsPalette + (blockIdx << 10);
    }
    return -1;
  }


  private boolean renderMos(Image image)
  {
    int blockCount = getBlockCount();
    if (image != null && blockCount > 0) {
      Graphics2D g = (Graphics2D)image.getGraphics();
      try {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC)); // overwriting target regardless of transparency
        int imgWidth = image.getWidth(null);
        int imgHeight = image.getHeight(null);
        for (int i = 0; i < blockCount; i++) {
          int x = (i % cols) * BlockDimension;
          int y = (i  / cols) * BlockDimension;
          int w = getBlockWidth(i);
          int h = getBlockHeight(i);
          if (x + w > imgWidth) w = imgWidth - x;
          if (y + h > imgHeight) h = imgHeight - y;
          if (w > 0 && h > 0 && updateWorkingCanvas(i)) {
            g.drawImage(workingCanvas, x, y, x + w, y + h, 0, 0, w, h, null);
          }
        }
      } finally {
        g.dispose();
        g = null;
      }
      return true;
    }
    return false;
  }

  private boolean renderMos(int[] buffer, int width, int height)
  {
    int blockCount = getBlockCount();
    if (buffer != null && width > 0 && height > 0 && blockCount > 0) {
      for (int i = 0; i < blockCount; i++) {
        int left = (i % cols) * BlockDimension;
        int top = (i  / cols) * BlockDimension;
        int w = getBlockWidth(i);
        int h = getBlockHeight(i);
        if (left + w > width) w = width - left;
        if (top + h > height) h = height - top;
        if (w > 0 && h > 0 && updateWorkingCanvas(i)) {
          int[] src = ((DataBufferInt)workingCanvas.getRaster().getDataBuffer()).getData();
          int srcOfs = 0;
          int dstOfs = top*width + left;
          for (int y = 0; y < h; y++) {
            System.arraycopy(src, srcOfs, buffer, dstOfs, w);
            srcOfs += BlockDimension;
            dstOfs += width;
          }
        }
      }
      return true;
    }
    return false;
  }

  // Draws the specified block onto the working canvas. Returns success state.
  private boolean updateWorkingCanvas(int blockIdx)
  {
    if (blockIdx != lastBlockIndex) {
      int srcOfs = getBlockOffset(blockIdx);
      if (srcOfs > 0) {
        // initializations
        getPalette(blockIdx, workingPalette);
        int w = getBlockWidth(blockIdx);
        int h = getBlockHeight(blockIdx);
        int maxSrcOfs = srcOfs + h*w;
        int dstOfs = 0;
        int maxDstOfs = h*BlockDimension;

        // removing old content
        int[] buffer = ((DataBufferInt)workingCanvas.getRaster().getDataBuffer()).getData();
        Arrays.fill(buffer, 0);

        // drawing new content
        while (srcOfs < maxSrcOfs && dstOfs < maxDstOfs) {
          for (int x = 0; x < w; x++, srcOfs++, dstOfs++) {
            buffer[dstOfs] = workingPalette[mosData[srcOfs] & 0xff];
          }
          dstOfs += BlockDimension - w;
        }

        lastBlockIndex = blockIdx;
        return true;
      }
      return false;
    } else {
      return blockIdx >= 0;
    }
  }
}