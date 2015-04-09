package org.robolectric.shadows;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.util.TypedValue;
import java.util.Iterator;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.NamedStream;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.Join;
import org.robolectric.internal.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static org.robolectric.internal.Shadow.directlyOn;

@SuppressWarnings({"UnusedDeclaration"})
@Implements(BitmapFactory.class)
public class ShadowBitmapFactory {
  private static Map<String, Point> widthAndHeightMap = new HashMap<String, Point>();

  static {
    // Stops ImageIO from creating temp files when reading images
    // from input stream.
    ImageIO.setUseCache(false);
  }

  @Implementation
  public static Bitmap decodeResourceStream(Resources res, TypedValue value, InputStream is, Rect pad, BitmapFactory.Options opts) {
    Bitmap bitmap = directlyOn(BitmapFactory.class, "decodeResourceStream",
        ClassParameter.from(Resources.class, res),
        ClassParameter.from(TypedValue.class, value),
        ClassParameter.from(InputStream.class, is),
        ClassParameter.from(Rect.class, pad),
        ClassParameter.from(BitmapFactory.Options.class, opts));

    if (value != null && value.string != null && value.string.toString().contains(".9.")) {
      // todo: better support for nine-patches
      ReflectionHelpers.callInstanceMethod(bitmap, "setNinePatchChunk", ClassParameter.from(byte[].class, new byte[0]));
    }
    return bitmap;
  }

  @Implementation
  public static Bitmap decodeResource(Resources res, int id, BitmapFactory.Options options) {
    Bitmap bitmap = create("resource:" + getResourceName(id), options);
    Shadows.shadowOf(bitmap).createdFromResId = id;
    return bitmap;
  }

  @Implementation
  public static Bitmap decodeResource(Resources res, int id) {
    return decodeResource(res, id, null);
  }

  private static String getResourceName(int id) {
    return Shadows.shadowOf(RuntimeEnvironment.application).getResourceLoader().getNameForId(id);
  }

  @Implementation
  public static Bitmap decodeFile(String pathName) {
    return decodeFile(pathName, null);
  }

  @Implementation
  public static Bitmap decodeFile(String pathName, BitmapFactory.Options options) {
    Bitmap bitmap = create("file:" + pathName, options);
    ShadowBitmap shadowBitmap = Shadows.shadowOf(bitmap);
    shadowBitmap.createdFromPath = pathName;
    return bitmap;
  }

  @Implementation
  public static Bitmap decodeStream(InputStream is) {
    return decodeStream(is, null, null);
  }

  @Implementation
  public static Bitmap decodeStream(InputStream is, Rect outPadding, BitmapFactory.Options opts) {
    String name = is instanceof NamedStream ? is.toString().replace("stream for ", "") : null;
    Point imageSize = is instanceof NamedStream ? null : getImageSizeFromStream(is);
    Bitmap bitmap = create(name, opts, imageSize);
    ShadowBitmap shadowBitmap = Shadows.shadowOf(bitmap);
    shadowBitmap.createdFromStream = is;
    return bitmap;
  }

  @Implementation
  public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
    Bitmap bitmap = decodeByteArray(data, offset, length, new BitmapFactory.Options());
    ShadowBitmap shadowBitmap = Shadows.shadowOf(bitmap);
    shadowBitmap.createdFromBytes = data;
    return bitmap;
  }

  @Implementation
  public static Bitmap decodeByteArray(byte[] data, int offset, int length, BitmapFactory.Options opts) {
    String desc = new String(data);
    if (!Charset.forName("US-ASCII").newEncoder().canEncode(desc)) {
      Checksum checksumEngine = new CRC32();
      checksumEngine.update(data, 0, data.length);

      desc = "byte array, checksum: " + checksumEngine.getValue();
    }

    if (offset != 0 || length != data.length) {
      desc += " bytes " + offset + ".." + length;
    }
    return create(desc, opts);
  }

  static Bitmap create(String name) {
    return create(name, null);
  }

  public static Bitmap create(String name, BitmapFactory.Options options) {
    return create(name, options, null);
  }

  public static Bitmap create(final String name, final BitmapFactory.Options options, final Point widthAndHeight) {
    Bitmap bitmap = Shadow.newInstanceOf(Bitmap.class);
    ShadowBitmap shadowBitmap = Shadows.shadowOf(bitmap);
    shadowBitmap.appendDescription(name == null ? "Bitmap" : "Bitmap for " + name);

    Bitmap.Config config;
    if (options != null && options.inPreferredConfig != null) {
      config = options.inPreferredConfig;
    } else {
      config = Bitmap.Config.ARGB_8888;
    }
    shadowBitmap.setConfig(config);

    String optionsString = stringify(options);
    if (!optionsString.isEmpty()) {
      shadowBitmap.appendDescription(" with options ");
      shadowBitmap.appendDescription(optionsString);
    }

    Point p = new Point(selectWidthAndHeight(name, widthAndHeight));
    if (options != null && options.inSampleSize > 1) {
      p.x = p.x / options.inSampleSize;
      p.y = p.y / options.inSampleSize;

      p.x = p.x == 0 ? 1 : p.x;
      p.y = p.y == 0 ? 1 : p.y;
    }

    shadowBitmap.setWidth(p.x);
    shadowBitmap.setHeight(p.y);
    if (options != null) {
      options.outWidth = p.x;
      options.outHeight = p.y;
    }
    return bitmap;
  }

  public static void provideWidthAndHeightHints(Uri uri, int width, int height) {
    widthAndHeightMap.put(uri.toString(), new Point(width, height));
  }

  public static void provideWidthAndHeightHints(int resourceId, int width, int height) {
    widthAndHeightMap.put("resource:" + getResourceName(resourceId), new Point(width, height));
  }

  public static void provideWidthAndHeightHints(String file, int width, int height) {
    widthAndHeightMap.put("file:" + file, new Point(width, height));
  }

  private static String stringify(BitmapFactory.Options options) {
    if (options == null) return "";
    List<String> opts = new ArrayList<String>();

    if (options.inJustDecodeBounds) opts.add("inJustDecodeBounds");
    if (options.inSampleSize > 1) opts.add("inSampleSize=" + options.inSampleSize);

    return Join.join(", ", opts);
  }

  @Resetter
  public static void reset() {
    widthAndHeightMap.clear();
  }

  private static Point selectWidthAndHeight(final String name, final Point widthAndHeight) {
    final Point widthAndHeightFromMap = widthAndHeightMap.get(name);

    if (widthAndHeightFromMap != null) {
      return widthAndHeightFromMap;
    }

    if (widthAndHeight != null) {
      return widthAndHeight;
    }

    return new Point(100, 100);
  }

  private static Point getImageSizeFromStream(InputStream is) {
    try {
      ImageInputStream imageStream = ImageIO.createImageInputStream(is);
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
      if (!readers.hasNext()) return null;

      ImageReader reader = readers.next();
      try {
        reader.setInput(imageStream);
        return new Point(reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
